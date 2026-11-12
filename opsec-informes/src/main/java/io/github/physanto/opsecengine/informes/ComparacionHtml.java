package io.github.physanto.opsecengine.informes;

import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Manifiesto;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.informes.ComparadorResultados.Cambio;
import io.github.physanto.opsecengine.informes.ComparadorResultados.CambioCaso;
import io.github.physanto.opsecengine.informes.ComparadorResultados.Comparacion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Escribe {@value #ARCHIVO}: un informe HTML autocontenido que compara dos ejecuciones de una suite
 * y muestra qué se arregló, qué empeoró y qué cambió.
 *
 * <p>Tiene el mismo estilo, la misma política de seguridad de contenido y el mismo escape de texto
 * que {@link ReporteroHtml}. Los casos se ordenan para que lo más importante vaya primero: los que
 * empeoraron, los nuevos, los que mejoraron, los eliminados y por último los iguales. Muestra
 * también el manifiesto de cada ejecución y avisa si cambió la aplicación, la suite o la versión
 * del motor, porque entonces la comparación puede no ser entre cosas equivalentes.
 *
 * <p>No es un {@link io.github.physanto.opsecengine.dominio.extension.Reportero}, porque ese
 * contrato recibe un solo resultado. Se usa desde la presentación con dos resultados ya leídos.
 *
 * <p>No tiene estado y no se instancia; sus métodos son seguros entre hilos.
 */
public final class ComparacionHtml {

    /** Archivo que escribe en la carpeta de destino. */
    public static final String ARCHIVO = "comparacion.html";

    private static final Comparator<CambioCaso> POR_IMPORTANCIA =
            Comparator.comparingInt(c -> c.cambio().ordinal());

    private ComparacionHtml() {}

    /**
     * Compara dos ejecuciones y escribe {@value #ARCHIVO} en la carpeta de destino; lo reemplaza si
     * ya existe.
     *
     * @param anterior ejecución anterior; no puede ser null
     * @param actual ejecución actual; no puede ser null
     * @param destino carpeta donde se escribe el informe, que ya existe; no puede ser null
     * @throws IOException si no puede escribir el archivo
     * @throws NullPointerException si algún parámetro es null
     */
    public static void escribir(ResultadoSuite anterior, ResultadoSuite actual, Path destino)
            throws IOException {
        Objects.requireNonNull(destino, "La carpeta de destino no puede ser null");
        Comparacion comparacion = ComparadorResultados.comparar(anterior, actual);
        Files.writeString(destino.resolve(ARCHIVO), generar(comparacion), StandardCharsets.UTF_8);
    }

    private static String generar(Comparacion c) {
        StringBuilder h = new StringBuilder(16 * 1024);
        String titulo =
                "Comparación de la suite "
                        + c.actual().nombreSuite()
                        + (c.anterior().nombreSuite().equals(c.actual().nombreSuite())
                                ? ""
                                : " con " + c.anterior().nombreSuite());
        h.append("<!DOCTYPE html>\n<html lang=\"es\">\n<head>\n<meta charset=\"UTF-8\">\n");
        h.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        h.append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; ");
        h.append("style-src 'unsafe-inline'\">\n<title>").append(Html.escapar(titulo));
        h.append("</title>\n<style>\n").append(ReporteroHtml.ESTILO).append(ESTILO_EXTRA);
        h.append("</style>\n</head>\n<body>\n<header>\n<h1>").append(Html.escapar(titulo));
        h.append("</h1>\n<p class=\"sub\">Anterior: ");
        h.append(Html.escapar(c.anterior().inicio().toString())).append(" · Actual: ");
        h.append(Html.escapar(c.actual().inicio().toString())).append("</p>\n</header>\n<main>\n");
        resumen(h, c);
        avisosDeComparabilidad(h, c.anterior().manifiesto(), c.actual().manifiesto());
        casos(h, c);
        suite(h, c);
        manifiestos(h, c.anterior().manifiesto(), c.actual().manifiesto());
        h.append("</main>\n<footer>Generado por OpSecEngine. Que un caso cumpla solo indica que ");
        h.append("no se encontraron fallos en lo probado.</footer>\n</body>\n</html>\n");
        return h.toString();
    }

    private static void resumen(StringBuilder h, Comparacion c) {
        h.append("<section>\n<h2>Resumen</h2>\n<div class=\"tarjetas\">\n");
        tarjeta(h, "no-cumple", "Empeoraron", c.cuenta(Cambio.EMPEORO));
        tarjeta(h, "no-concluyente", "Nuevos", c.cuenta(Cambio.NUEVO));
        tarjeta(h, "cumple", "Mejoraron", c.cuenta(Cambio.MEJORO));
        tarjeta(h, "inestable", "Eliminados", c.cuenta(Cambio.ELIMINADO));
        tarjeta(h, "igual", "Sin cambios", c.cuenta(Cambio.IGUAL));
        h.append("</div>\n<p class=\"conclusion\">");
        long arreglados =
                c.casos().stream().mapToLong(caso -> caso.resueltos().size()).sum()
                        + c.resueltosSuite().size();
        if (c.empeoro()) {
            h.append("Hay cambios que empeoran el resultado: revíselos primero.");
        } else if (arreglados > 0 || c.cuenta(Cambio.MEJORO) > 0) {
            h.append("No empeoró nada y se resolvió algo.");
        } else {
            h.append("No hay cambios en los veredictos ni en los hallazgos.");
        }
        h.append("</p>\n</section>\n");
    }

    private static void tarjeta(StringBuilder h, String clase, String etiqueta, long valor) {
        h.append("<div class=\"tarjeta ").append(clase).append("\"><span class=\"num\">");
        h.append(valor).append("</span><span>").append(etiqueta).append("</span></div>\n");
    }

    /** Avisa cuando las dos ejecuciones no son sobre lo mismo. */
    private static void avisosDeComparabilidad(StringBuilder h, Manifiesto a, Manifiesto b) {
        StringBuilder avisos = new StringBuilder();
        if (!a.hashJar().equals(b.hashJar())) {
            avisos.append("<li>La aplicación cambió (distinto hash del JAR).</li>\n");
        }
        if (!a.hashSuite().equals(b.hashSuite())) {
            avisos.append("<li>La suite cambió (distinto hash de la suite).</li>\n");
        }
        if (!a.versionMotor().equals(b.versionMotor())) {
            avisos.append("<li>Cambió la versión de OpSecEngine.</li>\n");
        }
        if (avisos.length() > 0) {
            h.append("<section>\n<p class=\"aviso\"><strong>Ojo al comparar:</strong></p>\n");
            h.append("<ul>\n").append(avisos).append("</ul>\n</section>\n");
        }
    }

    private static void casos(StringBuilder h, Comparacion c) {
        h.append("<section>\n<h2>Casos</h2>\n");
        if (c.casos().isEmpty()) {
            h.append("<p class=\"vacio\">Ninguna de las dos ejecuciones tiene casos.</p>\n");
        } else {
            h.append("<div class=\"tabla\"><table>\n<thead><tr><th>Caso</th><th>Antes</th>");
            h.append("<th>Ahora</th><th>Cambio</th><th>Hallazgos resueltos</th>");
            h.append("<th>Hallazgos nuevos</th></tr></thead>\n<tbody>\n");
            for (CambioCaso caso : c.casos().stream().sorted(POR_IMPORTANCIA).toList()) {
                h.append("<tr class=\"").append(caso.cambio().name().toLowerCase(Locale.ROOT));
                h.append("\"><td><strong>")
                        .append(Html.escapar(caso.casoId()))
                        .append("</strong></td>");
                h.append("<td>").append(estado(caso.anterior())).append("</td><td>");
                h.append(estado(caso.actual())).append("</td><td>");
                h.append(etiqueta(caso.cambio())).append("</td><td>");
                hallazgos(h, caso.resueltos());
                h.append("</td><td>");
                hallazgos(h, caso.nuevos());
                h.append("</td></tr>\n");
            }
            h.append("</tbody></table></div>\n");
        }
        h.append("</section>\n");
    }

    private static void suite(StringBuilder h, Comparacion c) {
        if (c.resueltosSuite().isEmpty() && c.nuevosSuite().isEmpty()) {
            return;
        }
        h.append("<section>\n<h2>Hallazgos de la suite</h2>\n<div class=\"tabla\"><table>\n");
        h.append("<thead><tr><th>Resueltos</th><th>Nuevos</th></tr></thead>\n<tbody><tr><td>");
        hallazgos(h, c.resueltosSuite());
        h.append("</td><td>");
        hallazgos(h, c.nuevosSuite());
        h.append("</td></tr></tbody></table></div>\n</section>\n");
    }

    private static void manifiestos(StringBuilder h, Manifiesto a, Manifiesto b) {
        h.append("<section>\n<h2>Manifiestos</h2>\n<div class=\"tabla\"><table>\n");
        h.append(
                "<thead><tr><th>Dato</th><th>Anterior</th><th>Actual</th></tr></thead>\n<tbody>\n");
        fila(h, "Versión del motor", a.versionMotor(), b.versionMotor());
        fila(h, "Java de la aplicación", a.versionJava(), b.versionJava());
        fila(h, "Sistema operativo", a.sistemaOperativo(), b.sistemaOperativo());
        fila(h, "Semilla", String.valueOf(a.semilla()), String.valueOf(b.semilla()));
        fila(h, "Hash del JAR", a.hashJar(), b.hashJar());
        fila(h, "Hash de la suite", a.hashSuite(), b.hashSuite());
        h.append("</tbody></table></div>\n</section>\n");
    }

    private static void fila(StringBuilder h, String dato, String antes, String ahora) {
        boolean cambio = !antes.equals(ahora);
        h.append("<tr").append(cambio ? " class=\"cambio\"" : "").append("><td>");
        h.append(Html.escapar(dato)).append("</td><td>").append(Html.escapar(antes));
        h.append("</td><td>").append(Html.escapar(ahora)).append(cambio ? " ⚠" : "");
        h.append("</td></tr>\n");
    }

    private static void hallazgos(StringBuilder h, List<Hallazgo> hallazgos) {
        if (hallazgos.isEmpty()) {
            h.append("<span class=\"vacio\">—</span>");
            return;
        }
        for (Hallazgo hallazgo : hallazgos) {
            h.append("<div><span class=\"etiqueta\">").append(Html.escapar(hallazgo.oraculoId()));
            h.append("</span> ").append(Html.escapar(hallazgo.titulo())).append("</div>");
        }
    }

    private static String estado(EstadoVeredicto estado) {
        if (estado == null) {
            return "<span class=\"vacio\">—</span>";
        }
        String clase = estado.name().toLowerCase(Locale.ROOT).replace('_', '-');
        return "<span class=\"insignia "
                + clase
                + "\">"
                + estado.name().replace('_', ' ')
                + "</span>";
    }

    private static String etiqueta(Cambio cambio) {
        return switch (cambio) {
            case EMPEORO -> "Empeoró";
            case NUEVO -> "Nuevo";
            case MEJORO -> "Mejoró";
            case ELIMINADO -> "Eliminado";
            case IGUAL -> "Sin cambios";
        };
    }

    /** Estilos propios de la comparación, además de los del informe. */
    private static final String ESTILO_EXTRA =
            """
            .tarjeta.igual .num{color:var(--suave)}
            tr.empeoro{background:var(--mal-fondo)}tr.mejoro{background:var(--ok-fondo)}
            tr.nuevo{background:var(--duda-fondo)}tr.eliminado .vacio{opacity:.7}
            tr.cambio td{font-weight:700}
            ul{margin:4px 0 12px}
            """;
}
