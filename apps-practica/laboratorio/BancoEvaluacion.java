import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Banco multiplataforma ejecutable como archivo fuente con JDK 21 y el JAR de la CLI en el
 * classpath. Ejecuta proyectos declarados, contrasta hallazgos con una verdad de referencia y
 * conserva informes, tiempos y hashes. La agregación se puede repetir sin volver a abrir la app. No
 * es seguro entre hilos; cada invocación debe usar una carpeta de salida vacía.
 */
public final class BancoEvaluacion {
    private static final JsonMapper JSON =
            JsonMapper.builder()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .build();
    private static final List<String> CABECERA =
            List.of(
                    "escenario",
                    "aplicacion",
                    "version",
                    "esperados",
                    "detectados",
                    "falsos_positivos",
                    "no_detectados",
                    "no_concluyentes",
                    "tiempo_proceso_ms",
                    "tiempo_casos_ms",
                    "codigo_cli",
                    "estado",
                    "error");

    private BancoEvaluacion() {}

    /**
     * Ejecuta el banco o reconstruye sus tablas. Argumentos: {@code ejecutar banco.json --salida
     * carpeta} o {@code resumir carpeta}. Sale con 0 si las mediciones coinciden, 1 ante errores, 3
     * si hay falsos positivos o detecciones ausentes, y 4 si solo quedan casos no concluyentes. No
     * lanza errores al llamador.
     *
     * @param args comando y rutas; las de banco.json son relativas a su propia carpeta
     */
    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        int codigo;
        try {
            Path salida;
            if (args.length == 4 && args[0].equals("ejecutar") && args[2].equals("--salida")) {
                salida = Path.of(args[3]).toAbsolutePath().normalize();
                ejecutar(Path.of(args[1]).toAbsolutePath().normalize(), salida);
            } else if (args.length == 2 && args[0].equals("resumir")) {
                salida = Path.of(args[1]).toAbsolutePath().normalize();
            } else {
                throw new IllegalArgumentException(
                        "Uso: ejecutar banco.json --salida carpeta | resumir carpeta");
            }
            codigo = resumir(salida);
            System.out.println(
                    Files.readString(salida.resolve("tabla.md"), StandardCharsets.UTF_8));
            System.out.println("Resultados: " + salida);
        } catch (Exception error) {
            System.err.println("Banco de evaluación: " + error.getMessage());
            codigo = 1;
        }
        System.exit(codigo);
    }

    private static void ejecutar(Path archivo, Path salida) throws Exception {
        Configuracion configuracion = leerConfiguracion(archivo);
        List<Preparado> preparados = new ArrayList<>();
        ProyectoService servicio = new ProyectoService();
        for (Escenario escenario : configuracion.escenarios()) {
            Path proyecto = archivo.getParent().resolve(escenario.proyecto()).normalize();
            Path suite = archivo.getParent().resolve(escenario.suite()).normalize();
            Proyecto cargado = servicio.cargarProyecto(proyecto);
            Suite cargada = servicio.cargarSuite(suite, cargado);
            segmento(cargada.nombre());
            Set<String> ids =
                    cargada.casos().stream().map(caso -> caso.id()).collect(Collectors.toSet());
            Set<String> declarados = new HashSet<>(escenario.esperados().keySet());
            declarados.remove("$suite");
            exigir(
                    !ids.isEmpty() && ids.equals(declarados),
                    escenario.id()
                            + ": declare esperados para cada caso de la suite, también los corregidos");
            Path app = proyecto.getParent().resolve(cargado.jar()).normalize();
            preparados.add(
                    new Preparado(
                            escenario,
                            proyecto,
                            suite,
                            app,
                            cargada.nombre(),
                            hash(proyecto),
                            hash(suite),
                            hash(app)));
        }
        Path cli =
                Path.of(
                        ProyectoService.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI());
        exigir(Files.isRegularFile(cli), "Use el JAR ejecutable de la CLI en --class-path");
        if (Files.exists(salida)) {
            try (var contenido = Files.list(salida)) {
                exigir(
                        contenido.findAny().isEmpty(),
                        "La carpeta de salida debe estar vacía: " + salida);
            }
        } else {
            Files.createDirectories(salida);
        }
        Files.copy(archivo, salida.resolve("banco.json"));
        List<Registro> registros = new ArrayList<>();
        String inicio = Instant.now().toString();
        int limite =
                configuracion.tiempoMaximoSegundos() == 0
                        ? 120
                        : configuracion.tiempoMaximoSegundos();
        for (Preparado preparado : preparados) {
            System.out.println("Ejecutando " + preparado.escenario().id());
            registros.add(ejecutarEscenario(preparado, cli, salida, limite));
            guardarEjecucion(
                    salida,
                    new Ejecucion(
                            1,
                            inicio,
                            System.getProperty("java.version"),
                            System.getProperty("os.name") + " " + System.getProperty("os.version"),
                            hash(cli),
                            hash(salida.resolve("banco.json")),
                            registros));
        }
    }

    private static Registro ejecutarEscenario(
            Preparado preparado, Path cli, Path salida, int limite) throws Exception {
        Path carpeta = salida.resolve(preparado.escenario().id());
        Files.createDirectory(carpeta);
        Path informe =
                carpeta.resolve("resultados")
                        .resolve(preparado.nombreSuite())
                        .resolve("resultado.json");
        long inicio = System.nanoTime();
        int codigo = -1;
        String error = null;
        Process proceso = null;
        try {
            Path java = Path.of(System.getProperty("java.home"), "bin", "java");
            proceso =
                    new ProcessBuilder(
                                    java.toString(),
                                    "-Dfile.encoding=UTF-8",
                                    "-Dstdout.encoding=UTF-8",
                                    "-Dstderr.encoding=UTF-8",
                                    "-jar",
                                    cli.toString(),
                                    "ejecutar",
                                    preparado.proyecto().toString(),
                                    "--suite",
                                    preparado.suite().toString(),
                                    "--resultados",
                                    carpeta.resolve("resultados").toString())
                            .redirectOutput(carpeta.resolve("cli.log").toFile())
                            .redirectError(carpeta.resolve("cli-errores.log").toFile())
                            .start();
            if (!proceso.waitFor(limite, TimeUnit.SECONDS)) {
                error = "Tiempo agotado tras " + limite + " segundos";
            } else {
                codigo = proceso.exitValue();
                if (!Set.of(0, 3, 4).contains(codigo)) {
                    error =
                            "La CLI terminó con código "
                                    + codigo
                                    + "; consulte los logs del escenario";
                } else if (!Files.isRegularFile(informe)) {
                    error = "La CLI no generó resultado.json";
                }
            }
        } catch (IOException fallo) {
            error = fallo.getMessage();
        } finally {
            if (proceso != null && proceso.isAlive()) {
                proceso.descendants().forEach(ProcessHandle::destroyForcibly);
                proceso.destroyForcibly();
            }
        }
        long duracion = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
        String ruta =
                StreamSupport.stream(salida.relativize(informe).spliterator(), false)
                        .map(Path::toString)
                        .collect(Collectors.joining("/"));
        return new Registro(
                preparado.escenario().id(),
                codigo,
                duracion,
                preparado.nombreSuite(),
                preparado.hashProyecto(),
                preparado.hashSuite(),
                preparado.hashApp(),
                ruta,
                Files.isRegularFile(informe) ? hash(informe) : null,
                error);
    }

    private static int resumir(Path salida) throws Exception {
        Configuracion configuracion = leerConfiguracion(salida.resolve("banco.json"));
        Ejecucion ejecucion =
                JSON.readValue(
                        Files.readString(salida.resolve("ejecucion.json"), StandardCharsets.UTF_8),
                        Ejecucion.class);
        exigir(ejecucion.version() == 1, "Versión de ejecución no admitida");
        exigir(
                hash(salida.resolve("banco.json")).equals(ejecucion.sha256Configuracion()),
                "banco.json cambió después de ejecutar; no se puede reproducir la tabla");
        exigir(
                ejecucion.registros().size() == configuracion.escenarios().size(),
                "La ejecución quedó incompleta; no se imputan resultados a los escenarios ausentes");
        List<List<String>> filas = new ArrayList<>();
        int codigoBanco = 0;
        for (int indice = 0; indice < configuracion.escenarios().size(); indice++) {
            Escenario escenario = configuracion.escenarios().get(indice);
            Registro registro = ejecucion.registros().get(indice);
            exigir(
                    escenario.id().equals(registro.id()),
                    "Orden de registros incompatible con el banco");
            Medidas medidas = null;
            String error = registro.error();
            if (error == null) {
                try {
                    Path informe = salida.resolve(registro.informe()).normalize();
                    exigir(
                            informe.startsWith(salida) && !Path.of(registro.informe()).isAbsolute(),
                            "Ruta de informe fuera de la salida");
                    exigir(
                            hash(informe).equals(registro.sha256Informe()),
                            "El informe cambió después de ejecutar");
                    medidas =
                            medir(
                                    JSON.readTree(
                                            Files.readString(informe, StandardCharsets.UTF_8)),
                                    escenario,
                                    registro);
                } catch (Exception fallo) {
                    error = fallo.getMessage();
                }
            }
            String estado =
                    error != null
                            ? "ERROR"
                            : medidas.falsosPositivos() + medidas.noDetectados() > 0
                                    ? "REGRESION"
                                    : medidas.noConcluyentes() > 0 ? "NO_CONCLUYENTE" : "OK";
            int codigoFila =
                    switch (estado) {
                        case "ERROR" -> 1;
                        case "REGRESION" -> 3;
                        case "NO_CONCLUYENTE" -> 4;
                        default -> 0;
                    };
            if (codigoBanco != 1 && (codigoFila == 1 || codigoBanco != 3 && codigoFila != 0)) {
                codigoBanco = codigoFila;
            }
            int esperados = escenario.esperados().values().stream().mapToInt(List::size).sum();
            filas.add(
                    List.of(
                            escenario.id(),
                            escenario.aplicacion(),
                            escenario.version(),
                            String.valueOf(esperados),
                            medidas == null ? "" : String.valueOf(medidas.detectados()),
                            medidas == null ? "" : String.valueOf(medidas.falsosPositivos()),
                            medidas == null ? "" : String.valueOf(medidas.noDetectados()),
                            medidas == null ? "" : String.valueOf(medidas.noConcluyentes()),
                            String.valueOf(registro.tiempoProcesoMs()),
                            medidas == null ? "" : String.valueOf(medidas.tiempoCasosMs()),
                            String.valueOf(registro.codigoCli()),
                            estado,
                            error == null ? "" : error));
        }
        escribirTablas(salida, filas);
        return codigoBanco;
    }

    private static Medidas medir(JsonNode informe, Escenario escenario, Registro registro) {
        exigir(
                informe.path("nombreSuite").asText().equals(registro.nombreSuite()),
                "Suite inesperada");
        exigir(
                informe.path("casos").isArray() && informe.path("hallazgosSuite").isArray(),
                "Informe sin listas de casos o hallazgos de suite");
        Set<String> ids = new HashSet<>();
        int detectados = 0, fp = 0, fn = 0, inconcluyentes = 0, codigo = 0;
        long tiempo = 0;
        for (JsonNode caso : informe.path("casos")) {
            String id = caso.path("casoId").asText();
            exigir(
                    escenario.esperados().containsKey(id) && ids.add(id),
                    "Caso ausente, repetido o no declarado: " + id);
            JsonNode veredicto = caso.path("veredicto");
            String estado = veredicto.path("estado").asText();
            exigir(
                    Set.of("CUMPLE", "NO_CUMPLE", "NO_CONCLUYENTE").contains(estado),
                    "Veredicto desconocido");
            if (estado.equals("NO_CUMPLE")) codigo = 3;
            if (estado.equals("NO_CONCLUYENTE") && codigo == 0) codigo = 4;
            Set<String> observados = observados(veredicto.path("hallazgos"));
            Set<String> esperados = oraculos(escenario.esperados().get(id));
            Set<String> faltantes = new HashSet<>(esperados);
            faltantes.removeAll(observados);
            detectados += esperados.size() - faltantes.size();
            Set<String> inesperados = new HashSet<>(observados);
            inesperados.removeAll(esperados);
            fp += inesperados.size();
            boolean incompleto =
                    estado.equals("NO_CONCLUYENTE")
                            || !faltantes.isEmpty() && !veredicto.path("motivo").isNull();
            if (incompleto) inconcluyentes++;
            else fn += faltantes.size();
            Duration duracion = Duration.parse(caso.path("duracion").asText());
            exigir(!duracion.isNegative(), "Duración negativa");
            tiempo = Math.addExact(tiempo, duracion.toMillis());
        }
        Set<String> declarados = new HashSet<>(escenario.esperados().keySet());
        declarados.remove("$suite");
        exigir(ids.equals(declarados), "El informe no contiene todos los casos declarados");
        exigir(
                codigo == registro.codigoCli(),
                "El código de la CLI no coincide con los veredictos");
        Set<String> esperadosSuite =
                oraculos(escenario.esperados().getOrDefault("$suite", List.of()));
        Set<String> observadosSuite = observados(informe.path("hallazgosSuite"));
        Set<String> faltantesSuite = new HashSet<>(esperadosSuite);
        faltantesSuite.removeAll(observadosSuite);
        detectados += esperadosSuite.size() - faltantesSuite.size();
        observadosSuite.removeAll(esperadosSuite);
        fp += observadosSuite.size();
        if (inconcluyentes == 0) fn += faltantesSuite.size();
        return new Medidas(detectados, fp, fn, inconcluyentes, tiempo);
    }

    private static Set<String> observados(JsonNode hallazgos) {
        exigir(hallazgos.isArray(), "Falta la lista de hallazgos");
        Set<String> ids = new HashSet<>();
        for (JsonNode hallazgo : hallazgos) {
            String confianza = hallazgo.path("confianza").asText();
            exigir(
                    Set.of("INFO", "PROBABLE", "CONFIRMADO").contains(confianza),
                    "Confianza desconocida");
            String id = hallazgo.path("oraculoId").asText();
            exigir(!id.isBlank(), "Hallazgo sin oráculo");
            if (!confianza.equals("INFO")) ids.add(id);
        }
        return ids;
    }

    private static Configuracion leerConfiguracion(Path archivo) throws IOException {
        Configuracion configuracion =
                JSON.readValue(
                        Files.readString(archivo, StandardCharsets.UTF_8), Configuracion.class);
        exigir(
                configuracion.version() == 1
                        && configuracion.escenarios() != null
                        && !configuracion.escenarios().isEmpty(),
                "Banco vacío o versión no admitida");
        exigir(
                configuracion.tiempoMaximoSegundos() >= 0
                        && configuracion.tiempoMaximoSegundos() <= 3600,
                "tiempoMaximoSegundos debe estar entre 1 y 3600 (0 usa 120)");
        Set<String> ids = new HashSet<>();
        for (Escenario escenario : configuracion.escenarios()) {
            exigir(
                    escenario.id() != null
                            && escenario.id().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
                            && ids.add(escenario.id()),
                    "Identificador de escenario inválido o repetido");
            exigir(
                    escenario.aplicacion() != null
                            && !escenario.aplicacion().isBlank()
                            && escenario.version() != null
                            && !escenario.version().isBlank(),
                    "Falta aplicación o versión");
            exigir(
                    escenario.proyecto() != null
                            && escenario.suite() != null
                            && escenario.esperados() != null
                            && !escenario.esperados().isEmpty(),
                    "Faltan proyecto, suite o esperados");
            escenario
                    .esperados()
                    .forEach((id, valores) -> {
                        exigir(!id.isBlank(), "Identificador de caso vacío");
                        oraculos(valores);
                    });
        }
        return configuracion;
    }

    private static Set<String> oraculos(List<String> valores) {
        exigir(valores != null, "Declare [] para los casos sin fallos esperados");
        Set<String> ids = new HashSet<>();
        for (String id : valores) {
            exigir(id != null && !id.isBlank() && ids.add(id), "Oráculo esperado vacío o repetido");
        }
        return ids;
    }

    private static void segmento(String texto) {
        exigir(
                !texto.isBlank()
                        && !texto.equals(".")
                        && !texto.equals("..")
                        && !texto.contains("/")
                        && !texto.contains("\\"),
                "Nombre de suite no válido como carpeta");
    }

    private static String hash(Path archivo) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var entrada = Files.newInputStream(archivo)) {
            byte[] bloque = new byte[65536];
            int leidos;
            while ((leidos = entrada.read(bloque)) != -1) digest.update(bloque, 0, leidos);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void guardarEjecucion(Path salida, Ejecucion ejecucion) throws IOException {
        Files.writeString(
                salida.resolve("ejecucion.json"),
                JSON.writeValueAsString(ejecucion) + "\n",
                StandardCharsets.UTF_8);
    }

    private static void escribirTablas(Path salida, List<List<String>> filas) throws IOException {
        List<List<String>> todas = new ArrayList<>();
        todas.add(CABECERA);
        todas.addAll(filas);
        StringBuilder csv = new StringBuilder();
        StringBuilder md = new StringBuilder();
        for (int indice = 0; indice < todas.size(); indice++) {
            List<String> fila = todas.get(indice);
            csv.append(
                            fila.stream()
                                    .map(valor -> "\"" + valor.replace("\"", "\"\"") + "\"")
                                    .collect(Collectors.joining(",")))
                    .append('\n');
            md.append("| ")
                    .append(
                            fila.stream()
                                    .map(valor ->
                                            valor.replace("\\", "\\\\")
                                                    .replace("|", "\\|")
                                                    .replace("\r", " ")
                                                    .replace("\n", "<br>"))
                                    .collect(Collectors.joining(" | ")))
                    .append(" |\n");
            if (indice == 0)
                md.append("| ")
                        .append(
                                CABECERA.stream()
                                        .map(valor -> "---")
                                        .collect(Collectors.joining(" | ")))
                        .append(" |\n");
        }
        Files.writeString(salida.resolve("tabla.csv"), csv, StandardCharsets.UTF_8);
        Files.writeString(salida.resolve("tabla.md"), md, StandardCharsets.UTF_8);
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) throw new IllegalArgumentException(mensaje);
    }

    private record Configuracion(
            int version, int tiempoMaximoSegundos, List<Escenario> escenarios) {}

    private record Escenario(
            String id,
            String aplicacion,
            String version,
            String proyecto,
            String suite,
            Map<String, List<String>> esperados) {}

    private record Preparado(
            Escenario escenario,
            Path proyecto,
            Path suite,
            Path app,
            String nombreSuite,
            String hashProyecto,
            String hashSuite,
            String hashApp) {}

    private record Registro(
            String id,
            int codigoCli,
            long tiempoProcesoMs,
            String nombreSuite,
            String sha256Proyecto,
            String sha256Suite,
            String sha256App,
            String informe,
            String sha256Informe,
            String error) {}

    private record Ejecucion(
            int version,
            String inicio,
            String java,
            String sistema,
            String sha256Cli,
            String sha256Configuracion,
            List<Registro> registros) {}

    private record Medidas(
            int detectados,
            int falsosPositivos,
            int noDetectados,
            int noConcluyentes,
            long tiempoCasosMs) {}
}
