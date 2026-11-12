package io.github.physanto.opsecengine.informes;

import io.github.physanto.opsecengine.dominio.extension.Reportero;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Manifiesto;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reportero del formato {@value #FORMATO}: escribe {@value #ARCHIVO}, un informe HTML en un solo
 * archivo autocontenido.
 *
 * <p>El informe tiene un resumen de la suite (cuántos casos cumplen, no cumplen o quedaron no
 * concluyentes), el manifiesto de la ejecución y, por cada caso, su veredicto, sus hallazgos con el
 * CWE y la confianza, y la línea de tiempo de la traza. Los eventos que respaldan un hallazgo se
 * resaltan y se enlazan desde él. Las capturas ({@link TipoEvento#CAPTURA}) se incrustan en el
 * propio HTML cuando el archivo existe dentro de la carpeta de resultados; si falta, se muestra un
 * aviso en su lugar.
 *
 * <p>Es un archivo autocontenido: el estilo va dentro, no usa JavaScript ni recursos externos, y
 * una política de seguridad de contenido impide cargar nada de fuera. Todo texto que viene de la
 * traza se escapa. Respeta el modo claro y oscuro del navegador.
 *
 * <p>Un informe sin fallos dice «No se encontraron fallos en lo probado», nunca que la aplicación
 * sea segura.
 *
 * <p>Se registra en {@code
 * META-INF/services/io.github.physanto.opsecengine.dominio.extension.Reportero}. No tiene estado
 * mutable y es seguro entre hilos.
 */
public final class ReporteroHtml implements Reportero {

    /** Formato que produce. */
    public static final String FORMATO = "html";

    /** Archivo que escribe en la carpeta de resultados. */
    public static final String ARCHIVO = "informe.html";

    /** Tamaño máximo de una captura que se incrusta; las más grandes se omiten con un aviso. */
    static final long MAXIMO_BYTES_CAPTURA = 8L * 1024 * 1024;

    private static final int MAXIMO_DETALLE = 400;

    private static final Logger REGISTRO = Logger.getLogger(ReporteroHtml.class.getName());

    /** Crea el reportero; lo usa {@link java.util.ServiceLoader}. */
    public ReporteroHtml() {}

    /**
     * Devuelve el formato del reportero.
     *
     * @return {@value #FORMATO}
     */
    @Override
    public String formato() {
        return FORMATO;
    }

    /**
     * Escribe {@value #ARCHIVO} en la carpeta de destino; lo reemplaza si ya existe.
     *
     * @param resultado resultado de la suite; no puede ser null
     * @param destino carpeta de resultados, que ya existe; de ella se leen las capturas; no puede
     *     ser null
     * @throws IOException si no puede escribir el archivo
     */
    @Override
    public void escribir(ResultadoSuite resultado, Path destino) throws IOException {
        Objects.requireNonNull(resultado, "El resultado no puede ser null");
        Objects.requireNonNull(destino, "La carpeta de destino no puede ser null");
        Files.writeString(
                destino.resolve(ARCHIVO), generar(resultado, destino), StandardCharsets.UTF_8);
    }

    /** Arma el documento completo. */
    private static String generar(ResultadoSuite resultado, Path destino) {
        StringBuilder h = new StringBuilder(32 * 1024);
        String titulo = "Informe de la suite " + resultado.nombreSuite();
        h.append("<!DOCTYPE html>\n<html lang=\"es\">\n<head>\n<meta charset=\"UTF-8\">\n");
        h.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        h.append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; ");
        h.append("img-src data:; style-src 'unsafe-inline'\">\n");
        h.append("<title>").append(Html.escapar(titulo)).append("</title>\n");
        Filtros filtros = new Filtros(resultado);
        h.append("<style>\n").append(ESTILO).append(filtros.css());
        h.append("</style>\n</head>\n<body>\n");
        h.append("<header>\n<h1>").append(Html.escapar(titulo)).append("</h1>\n");
        h.append("<p class=\"sub\">OpSecEngine · ");
        h.append(Html.escapar(resultado.inicio().toString())).append(" → ");
        h.append(Html.escapar(resultado.fin().toString())).append(" (");
        h.append(segundos(Duration.between(resultado.inicio(), resultado.fin())));
        h.append(")</p>\n</header>\n<main>\n");
        resumen(h, resultado);
        manifiesto(h, resultado.manifiesto());
        if (!resultado.casos().isEmpty()) {
            filtros.controles(h);
        }
        h.append("<section>\n<h2>Casos</h2>\n");
        if (resultado.casos().isEmpty()) {
            h.append("<p class=\"vacio\">La suite no tiene casos.</p>\n");
        }
        for (int i = 0; i < resultado.casos().size(); i++) {
            caso(h, i, resultado.casos().get(i), destino, filtros);
        }
        h.append("</section>\n");
        if (!resultado.hallazgosSuite().isEmpty()) {
            h.append("<section>\n<h2>Hallazgos de la suite</h2>\n");
            h.append(
                    "<p class=\"sub\">Comparan varios casos entre sí.</p>\n<ul class=\"hallazgos\">\n");
            for (Hallazgo hallazgo : resultado.hallazgosSuite()) {
                hallazgo(h, hallazgo, -1, filtros.clases(hallazgo));
            }
            h.append("</ul>\n</section>\n");
        }
        h.append(
                "</main>\n<footer>Generado por OpSecEngine. Un informe sin hallazgos solo indica ");
        h.append("que no se encontraron fallos en lo probado.</footer>\n</body>\n</html>\n");
        return h.toString();
    }

    private static void resumen(StringBuilder h, ResultadoSuite resultado) {
        Map<EstadoVeredicto, Integer> conteo = resultado.resumen();
        int noCumple = conteo.get(EstadoVeredicto.NO_CUMPLE);
        int noConcluyente = conteo.get(EstadoVeredicto.NO_CONCLUYENTE);
        long inestables = resultado.casos().stream().filter(ResultadoCaso::inestable).count();
        h.append("<section>\n<h2>Resumen</h2>\n<div class=\"tarjetas\">\n");
        tarjeta(h, "cumple", "Cumplen", conteo.get(EstadoVeredicto.CUMPLE));
        tarjeta(h, "no-cumple", "No cumplen", noCumple);
        tarjeta(h, "no-concluyente", "No concluyentes", noConcluyente);
        tarjeta(h, "inestable", "Inestables", (int) inestables);
        h.append("</div>\n<p class=\"conclusion\">");
        if (noCumple > 0) {
            h.append("Se encontraron fallos en ").append(noCumple);
            h.append(noCumple == 1 ? " caso." : " casos.");
        } else if (noConcluyente > 0) {
            h.append("No se encontraron fallos en lo probado, pero ").append(noConcluyente);
            h.append(noConcluyente == 1 ? " caso no se pudo" : " casos no se pudieron");
            h.append(" decidir. Esto no significa que la aplicación sea segura.");
        } else if (resultado.casos().isEmpty()) {
            h.append("No se ejecutó ningún caso.");
        } else {
            h.append("No se encontraron fallos en lo probado. ");
            h.append("Esto no significa que la aplicación sea segura.");
        }
        h.append("</p>\n</section>\n");
    }

    private static void tarjeta(StringBuilder h, String clase, String etiqueta, int valor) {
        h.append("<div class=\"tarjeta ").append(clase).append("\"><span class=\"num\">");
        h.append(valor).append("</span><span>").append(etiqueta).append("</span></div>\n");
    }

    private static void manifiesto(StringBuilder h, Manifiesto m) {
        h.append("<section>\n<h2>Manifiesto de la ejecución</h2>\n");
        h.append("<p class=\"sub\">Datos para poder repetir la ejecución.</p>\n<dl>\n");
        fila(h, "Versión del motor", m.versionMotor());
        fila(h, "Java de la aplicación", m.versionJava());
        fila(h, "Sistema operativo", m.sistemaOperativo());
        fila(h, "Semilla", String.valueOf(m.semilla()));
        fila(h, "Fecha", m.fecha().toString());
        fila(h, "Hash del JAR", m.hashJar());
        fila(h, "Hash de la suite", m.hashSuite());
        h.append("</dl>\n</section>\n");
    }

    private static void fila(StringBuilder h, String nombre, String valor) {
        h.append("<dt>").append(Html.escapar(nombre)).append("</dt><dd>");
        h.append(Html.escapar(valor)).append("</dd>\n");
    }

    private static void caso(
            StringBuilder h, int indice, ResultadoCaso caso, Path destino, Filtros filtros) {
        EstadoVeredicto estado = caso.veredicto().estado();
        String clase = claseEstado(estado);
        h.append("<details class=\"caso ").append(clase).append(filtros.clases(caso));
        h.append("\" id=\"c").append(indice);
        h.append('"').append(estado == EstadoVeredicto.CUMPLE ? "" : " open").append(">\n");
        h.append("<summary><span class=\"insignia ").append(clase).append("\">");
        h.append(etiquetaEstado(estado)).append("</span> <strong>");
        h.append(Html.escapar(caso.casoId())).append("</strong> <span class=\"meta\">");
        h.append(segundos(caso.duracion())).append(" · ").append(caso.intentos());
        h.append(caso.intentos() == 1 ? " intento" : " intentos");
        if (caso.inestable()) {
            h.append(" · <span class=\"insignia inestable\">inestable</span>");
        }
        h.append("</span></summary>\n<div class=\"cuerpo\">\n");
        if (caso.inestable()) {
            h.append("<p class=\"aviso\">El resultado cambió entre intentos; ");
            h.append("puede no ser reproducible.</p>\n");
        }
        if (caso.veredicto().motivo() != null) {
            h.append("<p class=\"motivo\"><strong>Motivo:</strong> ");
            h.append(Html.escapar(caso.veredicto().motivo())).append("</p>\n");
        }
        Set<Long> evidencia = new HashSet<>();
        if (caso.veredicto().hallazgos().isEmpty()) {
            if (estado == EstadoVeredicto.CUMPLE) {
                h.append("<p class=\"vacio\">No se encontraron fallos en lo probado.</p>\n");
            }
        } else {
            h.append("<h3>Hallazgos</h3>\n<ul class=\"hallazgos\">\n");
            for (Hallazgo hallazgo : caso.veredicto().hallazgos()) {
                hallazgo(h, hallazgo, indice, "");
                evidencia.addAll(hallazgo.evidencia());
            }
            h.append("</ul>\n");
        }
        traza(h, indice, caso, evidencia, destino);
        h.append("</div>\n</details>\n");
    }

    private static void hallazgo(
            StringBuilder h, Hallazgo hallazgo, int indiceCaso, String clases) {
        h.append("<li class=\"hallazgo").append(clases).append("\">\n<div class=\"titulo\">");
        h.append(Html.escapar(hallazgo.titulo())).append("</div>\n<div class=\"etiquetas\">");
        h.append("<span class=\"etiqueta\">").append(Html.escapar(hallazgo.oraculoId()));
        h.append("</span>");
        Optional<CatalogoCwe.DescripcionCwe> debilidad =
                CatalogoCwe.oficial().buscar(hallazgo.cwe());
        if (hallazgo.cwe() != null) {
            h.append("<span class=\"etiqueta\"");
            debilidad.ifPresent(
                    d -> h.append(" title=\"").append(Html.escapar(d.nombre())).append('"'));
            h.append('>').append(Html.escapar(hallazgo.cwe())).append("</span>");
        }
        h.append("<span class=\"etiqueta conf-")
                .append(hallazgo.confianza().name().toLowerCase(Locale.ROOT));
        h.append("\">").append(hallazgo.confianza().name()).append("</span></div>\n");
        h.append("<p>").append(Html.escapar(hallazgo.explicacion())).append("</p>\n");
        debilidad.ifPresent(d -> explicacionCwe(h, d));
        if (!hallazgo.evidencia().isEmpty() && indiceCaso >= 0) {
            h.append("<p class=\"evidencia-enlaces\">Evidencia: eventos ");
            for (int i = 0; i < hallazgo.evidencia().size(); i++) {
                long secuencia = hallazgo.evidencia().get(i);
                h.append(i == 0 ? "" : ", ").append("<a href=\"#c").append(indiceCaso);
                h.append("-e").append(secuencia).append("\">#").append(secuencia).append("</a>");
            }
            h.append("</p>\n");
        }
        h.append("</li>\n");
    }

    /** Explica la debilidad del hallazgo con el catálogo oficial: qué es y cómo corregirla. */
    private static void explicacionCwe(StringBuilder h, CatalogoCwe.DescripcionCwe d) {
        h.append("<details class=\"cwe\"><summary>¿Qué es ").append(Html.escapar(d.id()));
        h.append(" (").append(Html.escapar(d.nombre())).append(") y cómo se corrige?</summary>\n");
        h.append("<p>").append(Html.escapar(d.explicacion())).append("</p>\n");
        h.append("<p><strong>Cómo corregirlo:</strong> ");
        h.append(Html.escapar(d.recomendacion())).append("</p>\n</details>\n");
    }

    private static void traza(
            StringBuilder h, int indice, ResultadoCaso caso, Set<Long> evidencia, Path destino) {
        List<Evento> eventos = caso.traza().eventos();
        h.append("<h3>Línea de tiempo</h3>\n");
        if (eventos.isEmpty()) {
            h.append("<p class=\"vacio\">El caso no llegó a producir eventos.</p>\n");
            return;
        }
        long base = eventos.get(0).instante().toEpochMilli();
        h.append(
                "<div class=\"tabla\"><table>\n<thead><tr><th>#</th><th>Tiempo</th><th>Evento</th>");
        h.append("<th>Paso</th><th>Rol / sesión</th><th>Detalle</th></tr></thead>\n<tbody>\n");
        for (Evento evento : eventos) {
            boolean resaltado = evidencia.contains(evento.secuencia());
            h.append("<tr id=\"c").append(indice).append("-e").append(evento.secuencia());
            h.append('"').append(resaltado ? " class=\"evidencia\"" : "").append(">");
            h.append("<td>").append(evento.secuencia()).append("</td>");
            h.append("<td>+").append(evento.instante().toEpochMilli() - base).append(" ms</td>");
            h.append("<td><span class=\"tipo\">")
                    .append(evento.tipo().name())
                    .append("</span></td>");
            h.append("<td>").append(evento.contexto().numeroPaso()).append("</td>");
            h.append("<td>").append(contexto(evento)).append("</td><td>");
            if (evento.tipo() == TipoEvento.CAPTURA) {
                captura(h, evento, destino);
            } else {
                h.append(Html.escapar(Html.recortar(detalle(evento), MAXIMO_DETALLE)));
            }
            h.append("</td></tr>\n");
        }
        h.append("</tbody></table></div>\n");
    }

    private static String contexto(Evento evento) {
        String rol = evento.contexto().rol() == null ? "sin rol" : evento.contexto().rol();
        return Html.escapar(
                rol + (evento.contexto().sesionActiva() ? " · con sesión" : " · sin sesión"));
    }

    private static void captura(StringBuilder h, Evento evento, Path destino) {
        String archivo = evento.texto("archivo").orElse("");
        Optional<String> imagen = imagenBase64(destino, archivo);
        if (imagen.isPresent()) {
            h.append("<figure><img alt=\"Captura ").append(Html.escapar(archivo));
            h.append("\" src=\"data:image/png;base64,").append(imagen.get()).append("\">");
            h.append("<figcaption>").append(Html.escapar(archivo)).append("</figcaption></figure>");
        } else {
            h.append("<span class=\"sin-captura\">Captura no disponible: ");
            h.append(Html.escapar(archivo.isEmpty() ? "(sin archivo)" : archivo)).append("</span>");
        }
    }

    /**
     * Lee una captura y la devuelve en Base64.
     *
     * @return la imagen, o vacío si no existe, queda fuera de la carpeta de resultados, no es un
     *     PNG, es demasiado grande o no se puede leer
     */
    static Optional<String> imagenBase64(Path destino, String archivo) {
        if (archivo == null || archivo.isBlank()) {
            return Optional.empty();
        }
        try {
            Path base = destino.toAbsolutePath().normalize();
            Path ruta = base.resolve(archivo).normalize();
            if (!ruta.startsWith(base)
                    || !Files.isRegularFile(ruta)
                    || Files.size(ruta) > MAXIMO_BYTES_CAPTURA) {
                return Optional.empty();
            }
            byte[] bytes = Files.readAllBytes(ruta);
            if (!esPng(bytes)) {
                return Optional.empty();
            }
            return Optional.of(Base64.getEncoder().encodeToString(bytes));
        } catch (IOException | InvalidPathException | SecurityException e) {
            REGISTRO.log(Level.FINE, "No se pudo incrustar la captura " + archivo, e);
            return Optional.empty();
        }
    }

    private static boolean esPng(byte[] bytes) {
        byte[] firma = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        if (bytes.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (bytes[i] != firma[i]) {
                return false;
            }
        }
        return true;
    }

    /** Resume en una línea los datos de un evento, según su tipo. */
    private static String detalle(Evento e) {
        return switch (e.tipo()) {
            case PASO_INICIO ->
                    unir(
                            texto(e, "accion"),
                            texto(e, "objetivo"),
                            e.texto("valor").map(v -> "= \"" + v + "\"").orElse(""),
                            e.texto("modo").map(m -> "[" + m + "]").orElse(""));
            case PASO_FIN ->
                    unir(
                            texto(e, "codigo"),
                            texto(e, "detalle"),
                            e.numero("duracionMs").map(d -> d + " ms").orElse(""));
            case VENTANA_ABIERTA, VENTANA_CERRADA -> unir(texto(e, "titulo"), texto(e, "clase"));
            case DIALOGO -> unir(texto(e, "titulo"), texto(e, "texto"));
            case SQL ->
                    unir(
                            texto(e, "sql"),
                            e.numero("filasAfectadas")
                                    .map(f -> "(" + f + (f.longValue() == 1 ? " fila)" : " filas)"))
                                    .orElse(""),
                            texto(e, "error"));
            case CONSOLA -> unir(texto(e, "flujo"), texto(e, "texto"));
            case EXCEPCION ->
                    unir(
                            texto(e, "clase"),
                            texto(e, "mensaje"),
                            e.booleano("capturada")
                                    .map(c -> c ? "(capturada)" : "(no capturada)")
                                    .orElse(""));
            case ESTADO ->
                    unir(
                            texto(e, "momento"),
                            texto(e, "consulta"),
                            e.booleano("existe").map(x -> x ? "existe" : "no existe").orElse(""));
            case SESION ->
                    unir(
                            e.booleano("activa")
                                    .map(a -> a ? "sesión activa" : "sin sesión")
                                    .orElse(""),
                            texto(e, "rol"),
                            texto(e, "usuario"));
            default -> generico(e);
        };
    }

    private static String texto(Evento e, String clave) {
        return e.texto(clave).orElse("");
    }

    /** Une las partes no vacías con un espacio. */
    private static String unir(String... partes) {
        StringBuilder salida = new StringBuilder();
        for (String parte : partes) {
            if (parte != null && !parte.isBlank()) {
                salida.append(salida.length() == 0 ? "" : " ").append(parte);
            }
        }
        return salida.toString();
    }

    /** Muestra todos los datos del evento como {@code clave=valor}, ordenados por clave. */
    private static String generico(Evento e) {
        StringBuilder salida = new StringBuilder();
        for (Map.Entry<String, Object> campo : new TreeMap<>(e.datos()).entrySet()) {
            salida.append(salida.length() == 0 ? "" : " · ");
            salida.append(campo.getKey()).append('=').append(campo.getValue());
        }
        return salida.toString();
    }

    private static String segundos(Duration duracion) {
        return String.format(Locale.ROOT, "%.1f s", duracion.toMillis() / 1000.0);
    }

    private static String claseEstado(EstadoVeredicto estado) {
        return switch (estado) {
            case CUMPLE -> "cumple";
            case NO_CUMPLE -> "no-cumple";
            case NO_CONCLUYENTE -> "no-concluyente";
        };
    }

    private static String etiquetaEstado(EstadoVeredicto estado) {
        return switch (estado) {
            case CUMPLE -> "CUMPLE";
            case NO_CUMPLE -> "NO CUMPLE";
            case NO_CONCLUYENTE -> "NO CONCLUYENTE";
        };
    }

    /** Hoja de estilo del informe; claro y oscuro según el navegador. */
    static final String ESTILO =
            """
            :root{--fondo:#f6f7f9;--panel:#fff;--texto:#1c2128;--suave:#5b6572;--borde:#d8dde4;\
            --ok:#1a7f37;--ok-fondo:#dafbe1;--mal:#cf222e;--mal-fondo:#ffebe9;\
            --duda:#9a6700;--duda-fondo:#fff8c5;--info:#0969da;--info-fondo:#ddf4ff;\
            --resalte:#fff1c2}
            @media (prefers-color-scheme:dark){:root{--fondo:#0d1117;--panel:#161b22;\
            --texto:#e6edf3;--suave:#8b949e;--borde:#30363d;--ok:#56d364;--ok-fondo:#12261e;\
            --mal:#ff7b72;--mal-fondo:#2d1517;--duda:#e3b341;--duda-fondo:#2d2411;\
            --info:#79c0ff;--info-fondo:#0d2238;--resalte:#3a3115}}
            *{box-sizing:border-box}
            body{margin:0;background:var(--fondo);color:var(--texto);\
            font:15px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
            header,main,footer{max-width:1100px;margin:0 auto;padding:0 16px}
            header{padding-top:24px}
            h1{margin:0;font-size:1.6rem}h2{margin:28px 0 8px;font-size:1.2rem}
            h3{margin:16px 0 6px;font-size:1rem}
            .sub,.meta,footer{color:var(--suave)}.sub{margin:4px 0}
            footer{padding:24px 16px 40px;font-size:.85rem}
            .tarjetas{display:flex;gap:12px;flex-wrap:wrap}
            .tarjeta{flex:1 1 140px;background:var(--panel);border:1px solid var(--borde);\
            border-radius:8px;padding:12px;display:flex;flex-direction:column}
            .tarjeta .num{font-size:1.8rem;font-weight:700}
            .tarjeta.cumple .num{color:var(--ok)}.tarjeta.no-cumple .num{color:var(--mal)}
            .tarjeta.no-concluyente .num,.tarjeta.inestable .num{color:var(--duda)}
            .conclusion{font-weight:600}
            dl{display:grid;grid-template-columns:max-content 1fr;gap:4px 16px;\
            background:var(--panel);border:1px solid var(--borde);border-radius:8px;padding:12px}
            dt{color:var(--suave)}dd{margin:0;word-break:break-all}
            details.caso{background:var(--panel);border:1px solid var(--borde);\
            border-left:4px solid var(--borde);border-radius:8px;margin:10px 0}
            details.cumple{border-left-color:var(--ok)}
            details.no-cumple{border-left-color:var(--mal)}
            details.no-concluyente{border-left-color:var(--duda)}
            summary{cursor:pointer;padding:10px 12px}.cuerpo{padding:0 12px 12px}
            .insignia{display:inline-block;padding:1px 8px;border-radius:12px;\
            font-size:.75rem;font-weight:700}
            .insignia.cumple{background:var(--ok-fondo);color:var(--ok)}
            .insignia.no-cumple{background:var(--mal-fondo);color:var(--mal)}
            .insignia.no-concluyente,.insignia.inestable{background:var(--duda-fondo);\
            color:var(--duda)}
            .hallazgos{list-style:none;padding:0;margin:0}
            .hallazgo{border:1px solid var(--borde);border-radius:6px;padding:8px 12px;margin:8px 0}
            .hallazgo .titulo{font-weight:700}.hallazgo p{margin:6px 0}
            .etiquetas{margin:4px 0}
            .etiqueta{display:inline-block;margin-right:6px;padding:0 8px;border-radius:4px;\
            background:var(--info-fondo);color:var(--info);font-size:.8rem}
            .conf-confirmado{background:var(--mal-fondo);color:var(--mal)}
            .conf-probable{background:var(--duda-fondo);color:var(--duda)}
            .aviso,.motivo{background:var(--duda-fondo);border-radius:6px;padding:6px 10px}
            .vacio,.sin-captura{color:var(--suave);font-style:italic}
            .tabla{overflow-x:auto}table{border-collapse:collapse;width:100%;font-size:.85rem}
            th,td{border-bottom:1px solid var(--borde);padding:4px 8px;text-align:left;\
            vertical-align:top}
            td:last-child{word-break:break-word}
            tr.evidencia{background:var(--resalte)}
            .tipo{font-family:ui-monospace,Consolas,monospace;font-size:.8rem}
            figure{margin:0}img{max-width:100%;border:1px solid var(--borde);border-radius:4px}
            figcaption{color:var(--suave);font-size:.8rem}
            a{color:var(--info)}
            .grupos{display:flex;gap:12px;flex-wrap:wrap}
            fieldset{flex:1 1 220px;background:var(--panel);border:1px solid var(--borde);\
            border-radius:8px;margin:0;padding:6px 12px 10px}
            legend{font-weight:700;padding:0 6px}
            fieldset label{display:block;cursor:pointer;padding:1px 0}
            fieldset input{margin-right:6px}
            details.cwe{margin:6px 0;padding:4px 10px;border-left:3px solid var(--info);\
            background:var(--info-fondo);border-radius:4px}
            details.cwe summary{cursor:pointer;padding:2px 0;font-size:.9rem}
            details.cwe p{margin:6px 0}
            """;
}
