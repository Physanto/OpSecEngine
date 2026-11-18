package io.github.physanto.opsecengine.nucleo.verificacion;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.EntornoDatos;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.configuracion.Rol;
import io.github.physanto.opsecengine.dominio.extension.ProveedorDatos;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.nucleo.proyecto.ExcepcionConfiguracion;
import io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService;
import io.github.physanto.opsecengine.nucleo.sesion.ArranqueApp;
import io.github.physanto.opsecengine.nucleo.sesion.ClienteAgente;
import io.github.physanto.opsecengine.nucleo.sesion.InicioSesion;
import io.github.physanto.opsecengine.nucleo.sesion.LanzadorProceso;
import io.github.physanto.opsecengine.nucleo.sesion.SesionService;
import io.github.physanto.opsecengine.nucleo.sesion.TraductorProtocolo;
import io.github.physanto.opsecengine.nucleo.verificacion.Comprobacion.Estado;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Autoverificación previa: comprueba, sin ejecutar ningún caso, que todo está listo.
 *
 * <p>Hace estas comprobaciones en orden y se detiene en la primera que termina en {@link
 * Estado#FALLO}:
 *
 * <ol>
 *   <li><b>Proyecto</b> y <b>suites</b>: se cargan y validan con {@link ProyectoService}.
 *   <li><b>Aplicación</b> y <b>agente</b>: existen el JAR de la aplicación y el del agente.
 *   <li><b>Conexión</b>: se arranca la aplicación con el agente, se espera su saludo y se cierra.
 *   <li><b>Localizadores</b>: por cada caso cuyo primer paso tiene localizador, se comprueba en la
 *       aplicación abierta (con el inicio de sesión del rol, si el caso tiene rol) que el
 *       localizador se resuelve. Si no aparece es un {@link Estado#AVISO}, no un fallo: puede
 *       aparecer después de otros pasos. Si no se puede comprobar un contexto, también es un aviso.
 *   <li><b>Datos</b>: si el proyecto declara un entorno de datos, se prepara con su {@link
 *       ProveedorDatos}; sin proveedor registrado es un aviso.
 * </ol>
 *
 * <p>La comprobación de localizadores envía un paso {@code ESPERAR} con el localizador, de modo que
 * usa la misma búsqueda que la ejecución, y abre la aplicación una vez por rol y modo oculto. No
 * cambia el estado de la aplicación.
 *
 * <p>No es seguro entre hilos: cada llamada abre sus propias sesiones, una a la vez.
 */
public final class VerificacionService {

    /** Nombre de la comprobación de conexión con el agente. */
    public static final String CONEXION = "Conexión";

    /** Nombre de la comprobación del JAR de la aplicación. */
    public static final String APLICACION = "Aplicación";

    /** Nombre de la comprobación del jar del agente. */
    public static final String AGENTE = "Agente";

    /** Tiempo que se espera a que aparezca cada localizador. */
    static final Duration ESPERA_LOCALIZADOR = Duration.ofSeconds(2);

    /** Líneas del final de la salida de error de la aplicación que se muestran al fallar. */
    static final int LINEAS_DE_ERROR = 8;

    /** Crea el arranque de la aplicación que usan las comprobaciones que abren sesiones. */
    @FunctionalInterface
    public interface FabricaArranque {

        /**
         * Crea el arranque.
         *
         * @param jarAgente jar del agente; nunca es null
         * @param carpetaRegistros carpeta, que ya existe, donde se escribe la salida de la
         *     aplicación; nunca es null
         * @return el arranque de la aplicación
         */
        ArranqueApp crear(Path jarAgente, Path carpetaRegistros);
    }

    private final ProyectoService proyectos;
    private final RegistroExtensiones extensiones;
    private final FabricaArranque fabrica;
    private final Duration esperaHola;

    /**
     * Crea el servicio con la espera de saludo por defecto de {@link SesionService}.
     *
     * @param proyectos carga y valida proyectos y suites; no puede ser null
     * @param extensiones registro donde se buscan los proveedores de datos; no puede ser null
     * @param fabrica crea el arranque de la aplicación; no puede ser null
     * @throws NullPointerException si algún parámetro es null
     */
    public VerificacionService(
            ProyectoService proyectos, RegistroExtensiones extensiones, FabricaArranque fabrica) {
        this(proyectos, extensiones, fabrica, SesionService.ESPERA_HOLA_POR_DEFECTO);
    }

    /**
     * Crea el servicio.
     *
     * @param proyectos carga y valida proyectos y suites; no puede ser null
     * @param extensiones registro donde se buscan los proveedores de datos; no puede ser null
     * @param fabrica crea el arranque de la aplicación; no puede ser null
     * @param esperaHola tiempo máximo para que el agente salude; debe ser positivo
     * @throws NullPointerException si algún parámetro es null
     * @throws IllegalArgumentException si la espera no es positiva
     */
    public VerificacionService(
            ProyectoService proyectos,
            RegistroExtensiones extensiones,
            FabricaArranque fabrica,
            Duration esperaHola) {
        this.esperaHola = Objects.requireNonNull(esperaHola, "La espera del saludo es null");
        if (esperaHola.isZero() || esperaHola.isNegative()) {
            throw new IllegalArgumentException("La espera del saludo debe ser positiva");
        }
        this.proyectos = Objects.requireNonNull(proyectos, "El servicio de proyectos es null");
        this.extensiones = Objects.requireNonNull(extensiones, "El registro es null");
        this.fabrica = Objects.requireNonNull(fabrica, "La fábrica de arranque es null");
    }

    /**
     * Verifica un proyecto.
     *
     * @param archivoProyecto archivo {@code proyecto.yaml}; sus suites se leen de la carpeta {@code
     *     suites/} contigua; no puede ser null
     * @param jarAgente jar del agente que se usa para abrir la aplicación; no puede ser null
     * @param alTerminar se invoca, en el hilo que llama, apenas termina cada comprobación (por
     *     ejemplo para mostrar el avance); no puede ser null
     * @return todas las comprobaciones hechas, hasta la primera con fallo
     * @throws NullPointerException si algún parámetro es null
     */
    public ResultadoVerificacion verificar(
            Path archivoProyecto, Path jarAgente, Consumer<Comprobacion> alTerminar) {
        Objects.requireNonNull(archivoProyecto, "El archivo del proyecto es null");
        Objects.requireNonNull(jarAgente, "El jar del agente es null");
        Objects.requireNonNull(alTerminar, "El observador es null");
        List<Comprobacion> hechas = new ArrayList<>();
        Consumer<Comprobacion> registro =
                c -> {
                    hechas.add(c);
                    alTerminar.accept(c);
                };
        ejecutar(archivoProyecto, jarAgente, registro);
        return new ResultadoVerificacion(hechas);
    }

    private void ejecutar(Path archivo, Path jarAgente, Consumer<Comprobacion> registro) {
        Proyecto proyecto;
        List<Suite> suites;
        try {
            proyecto = proyectos.cargarProyecto(archivo);
            registro.accept(
                    new Comprobacion(
                            "Proyecto",
                            Estado.OK,
                            proyecto.nombre()
                                    + ": válido ("
                                    + contar(proyecto.roles().size(), "rol", "roles")
                                    + ", "
                                    + contar(proyecto.pasosLogin().size(), "paso", "pasos")
                                    + " de inicio de sesión)"));
            Path carpeta =
                    archivo.toAbsolutePath().getParent().resolve(ProyectoService.CARPETA_SUITES);
            suites = proyectos.cargarSuites(carpeta, proyecto);
        } catch (ExcepcionConfiguracion e) {
            registro.accept(fallo("Proyecto", e.getMessage()));
            return;
        } catch (NoSuchFileException e) {
            registro.accept(fallo("Proyecto", "No existe el archivo " + e.getFile()));
            return;
        } catch (IOException e) {
            registro.accept(fallo("Proyecto", "No se pudo leer: " + e.getMessage()));
            return;
        }
        if (suites.isEmpty()) {
            registro.accept(
                    new Comprobacion(
                            "Suites",
                            Estado.AVISO,
                            "No hay suites en " + ProyectoService.CARPETA_SUITES + "/"));
        }
        for (Suite suite : suites) {
            registro.accept(
                    new Comprobacion(
                            "Suite " + suite.nombre(),
                            Estado.OK,
                            "válida (" + contar(suite.casos().size(), "caso", "casos") + ")"));
        }
        if (!Files.isRegularFile(proyecto.jar())) {
            registro.accept(
                    fallo(
                            APLICACION,
                            "No existe el JAR de la aplicación: "
                                    + proyecto.jar()
                                    + " (¿falta compilarla?)"));
            return;
        }
        registro.accept(new Comprobacion(APLICACION, Estado.OK, proyecto.jar().toString()));
        if (!Files.isRegularFile(jarAgente)) {
            registro.accept(fallo(AGENTE, "No existe el jar del agente: " + jarAgente));
            return;
        }
        registro.accept(new Comprobacion(AGENTE, Estado.OK, jarAgente.toString()));
        if (!abrirSesiones(proyecto, suites, jarAgente, registro)) {
            return;
        }
        verificarDatos(proyecto, registro);
    }

    /**
     * Prueba la conexión y, con la misma aplicación abierta, los localizadores; devuelve false si
     * hay que detenerse.
     */
    private boolean abrirSesiones(
            Proyecto proyecto,
            List<Suite> suites,
            Path jarAgente,
            Consumer<Comprobacion> registro) {
        Path registros;
        try {
            registros = Files.createTempDirectory("opsec-verificar-");
        } catch (IOException e) {
            registro.accept(fallo(CONEXION, "No se pudo crear la carpeta temporal: " + e));
            return false;
        }
        try (SesionService sesiones =
                new SesionService(
                        fabrica.crear(jarAgente, registros),
                        esperaHola,
                        ClienteAgente.TIEMPO_MAXIMO_POR_DEFECTO)) {
            try {
                sesiones.abrir(proyecto);
                registro.accept(
                        new Comprobacion(
                                CONEXION,
                                Estado.OK,
                                "el agente saludó (agente "
                                        + sesiones.saludo().getVersionAgente()
                                        + ", Java "
                                        + sesiones.saludo().getVersionJava()
                                        + ")"));
            } catch (IOException | RuntimeException e) {
                registro.accept(
                        fallo(
                                CONEXION,
                                "La aplicación no se conectó con el agente: "
                                        + e.getMessage()
                                        + salidaDeError(registros)
                                        + "\n       Registros completos en "
                                        + registros));
                return false;
            } finally {
                sesiones.cerrar();
            }
            verificarLocalizadores(proyecto, suites, sesiones, registro);
        }
        limpiar(registros);
        return true;
    }

    /** Comprueba el primer localizador de cada caso, una sesión por rol y modo oculto. */
    private void verificarLocalizadores(
            Proyecto proyecto,
            List<Suite> suites,
            SesionService sesiones,
            Consumer<Comprobacion> registro) {
        Map<String, List<Pendiente>> grupos = new LinkedHashMap<>();
        for (Suite suite : suites) {
            for (Caso caso : suite.casos()) {
                if (!caso.pasos().isEmpty() && caso.pasos().get(0).objetivo() != null) {
                    String clave = caso.rol() + "|" + suite.config().oculto();
                    grupos.computeIfAbsent(clave, k -> new ArrayList<>())
                            .add(new Pendiente(suite, caso));
                }
            }
        }
        for (List<Pendiente> grupo : grupos.values()) {
            verificarGrupo(proyecto, grupo, sesiones, registro);
        }
    }

    private void verificarGrupo(
            Proyecto proyecto,
            List<Pendiente> grupo,
            SesionService sesiones,
            Consumer<Comprobacion> registro) {
        String rolNombre = grupo.get(0).caso().rol();
        boolean oculto = grupo.get(0).suite().config().oculto();
        String contexto = rolNombre == null ? "sin rol" : "rol " + rolNombre;
        try {
            ClienteAgente cliente = sesiones.abrir(proyecto, oculto);
            String problema = iniciarSesion(proyecto, rolNombre, cliente, grupo);
            if (problema != null) {
                avisarGrupo(grupo, "no se comprobó (" + contexto + "): " + problema, registro);
                return;
            }
            for (Pendiente pendiente : grupo) {
                registro.accept(comprobar(pendiente, cliente));
            }
        } catch (IOException | RuntimeException e) {
            avisarGrupo(grupo, "no se comprobó (" + contexto + "): " + e.getMessage(), registro);
        } finally {
            sesiones.cerrar();
        }
    }

    /** Inicia sesión con el rol, si lo hay; devuelve por qué no se pudo, o null. */
    private static String iniciarSesion(
            Proyecto proyecto, String rolNombre, ClienteAgente cliente, List<Pendiente> grupo)
            throws IOException {
        if (rolNombre == null) {
            return null;
        }
        Optional<Rol> rol = proyecto.buscarRol(rolNombre);
        if (rol.isEmpty()) {
            return "el proyecto no declara el rol " + rolNombre;
        }
        return InicioSesion.iniciar(proyecto, rol.get(), cliente, grupo.get(0).suite().config());
    }

    private static Comprobacion comprobar(Pendiente pendiente, ClienteAgente cliente)
            throws IOException {
        Paso primero = pendiente.caso().pasos().get(0);
        String nombre = "Localizador " + pendiente.suite().nombre() + "/" + pendiente.caso().id();
        String objetivo = TraductorProtocolo.describir(primero.objetivo());
        Paso espera =
                new Paso(1, Accion.ESPERAR, primero.objetivo(), null, ESPERA_LOCALIZADOR, null);
        ResultadoPasoDto resultado =
                cliente.enviarPaso(TraductorProtocolo.aPasoDto(espera, pendiente.suite().config()));
        if (resultado.getCodigo() == CodigoResultado.OK) {
            return new Comprobacion(nombre, Estado.OK, objetivo + " se encuentra");
        }
        return new Comprobacion(
                nombre,
                Estado.AVISO,
                objetivo
                        + " no aparece al abrir la aplicación ("
                        + resultado.getCodigo()
                        + "); puede aparecer después de otros pasos");
    }

    private static void avisarGrupo(
            List<Pendiente> grupo, String detalle, Consumer<Comprobacion> registro) {
        for (Pendiente pendiente : grupo) {
            registro.accept(
                    new Comprobacion(
                            "Localizador "
                                    + pendiente.suite().nombre()
                                    + "/"
                                    + pendiente.caso().id(),
                            Estado.AVISO,
                            detalle));
        }
    }

    private void verificarDatos(Proyecto proyecto, Consumer<Comprobacion> registro) {
        EntornoDatos datos = proyecto.datos();
        if (datos == null || EntornoDatos.NINGUNO.equals(datos.tipo())) {
            registro.accept(
                    new Comprobacion("Datos", Estado.OK, "el proyecto no usa datos de prueba"));
            return;
        }
        Optional<ProveedorDatos> proveedor;
        try {
            proveedor = extensiones.nuevoProveedorDatos(datos.tipo());
        } catch (RuntimeException e) {
            registro.accept(
                    fallo(
                            "Datos",
                            "No se pudo crear el proveedor "
                                    + datos.tipo()
                                    + ": "
                                    + e.getMessage()));
            return;
        }
        if (proveedor.isEmpty()) {
            registro.accept(
                    new Comprobacion(
                            "Datos",
                            Estado.AVISO,
                            "no hay un proveedor de datos registrado para el tipo "
                                    + datos.tipo()));
            return;
        }
        try {
            proveedor.get().preparar(proyecto);
            registro.accept(
                    new Comprobacion(
                            "Datos", Estado.OK, datos.tipo() + " preparado correctamente"));
        } catch (RuntimeException e) {
            registro.accept(
                    fallo("Datos", "No se pudo preparar " + datos.tipo() + ": " + e.getMessage()));
        } finally {
            try {
                proveedor.get().cerrar();
            } catch (RuntimeException e) {
                // Liberar el entorno es un esfuerzo: el resultado ya se informó.
            }
        }
    }

    /** Líneas finales de la salida de error de la aplicación, listas para añadir a un mensaje. */
    private static String salidaDeError(Path registros) {
        StringBuilder texto = new StringBuilder();
        for (String linea : ultimasLineas(registros.resolve(LanzadorProceso.ARCHIVO_ERRORES))) {
            texto.append("\n       | ").append(linea);
        }
        return texto.toString();
    }

    /** Hasta {@value #LINEAS_DE_ERROR} líneas no vacías del final de un archivo de texto UTF-8. */
    static List<String> ultimasLineas(Path archivo) {
        try {
            List<String> lineas =
                    Files.readAllLines(archivo, StandardCharsets.UTF_8).stream()
                            .filter(linea -> !linea.isBlank())
                            .toList();
            return lineas.subList(Math.max(0, lineas.size() - LINEAS_DE_ERROR), lineas.size());
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /** Borra los registros de una verificación correcta; ignora lo que no se pueda borrar. */
    private static void limpiar(Path registros) {
        for (String nombre :
                List.of(LanzadorProceso.ARCHIVO_SALIDA, LanzadorProceso.ARCHIVO_ERRORES)) {
            try {
                Files.deleteIfExists(registros.resolve(nombre));
            } catch (IOException e) {
                return;
            }
        }
        try {
            Files.deleteIfExists(registros);
        } catch (IOException e) {
            // Una carpeta temporal que queda no afecta al resultado.
        }
    }

    private static Comprobacion fallo(String nombre, String detalle) {
        return new Comprobacion(nombre, Estado.FALLO, detalle);
    }

    private static String contar(int cantidad, String singular, String plural) {
        return cantidad + " " + (cantidad == 1 ? singular : plural);
    }

    /** Un caso cuyo primer localizador falta por comprobar. */
    private record Pendiente(Suite suite, Caso caso) {}
}
