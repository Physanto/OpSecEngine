package io.github.physanto.opsecengine.informes;

import io.github.physanto.opsecengine.dominio.extension.Reportero;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Reportero del formato {@value #FORMATO}: escribe {@value #ARCHIVO}, el resultado de la suite en
 * el formato JUnit XML que leen las herramientas de integración continua (por ejemplo el visor de
 * pruebas de GitHub Actions, Jenkins o GitLab).
 *
 * <p>Correspondencia con el resultado de OpSecEngine:
 *
 * <ul>
 *   <li>La suite es un {@code <testsuite>} y cada caso un {@code <testcase>} cuyo {@code classname}
 *       es el nombre de la suite y cuyo {@code name} es el id del caso.
 *   <li>Un caso {@code NO_CUMPLE} lleva un {@code <failure>} por cada hallazgo: {@code message} es
 *       el título, {@code type} es el CWE (o el id del oráculo si no tiene) y el texto incluye el
 *       oráculo, la confianza, la explicación y los eventos de evidencia.
 *   <li>Un caso {@code NO_CONCLUYENTE} lleva un {@code <skipped>} con el motivo: no se pudo
 *       decidir, así que no cuenta ni como pasado ni como fallido.
 *   <li>Un caso {@code CUMPLE} no lleva nada. Sus hallazgos informativos y su marca de inestable
 *       van en {@code <system-out>}.
 *   <li>Cada hallazgo de suite (los que comparan varios casos) es un {@code <testcase>} aparte
 *       llamado {@code (suite) <oráculo>} con su {@code <failure>}.
 * </ul>
 *
 * <p>El texto se escribe en UTF-8 y sin los caracteres que XML 1.0 no admite. Se registra en {@code
 * META-INF/services/io.github.physanto.opsecengine.dominio.extension.Reportero}. No tiene estado
 * mutable y es seguro entre hilos.
 */
public final class ReporteroJunitXml implements Reportero {

    /** Formato que produce. */
    public static final String FORMATO = "junit";

    /** Archivo que escribe en la carpeta de resultados. */
    public static final String ARCHIVO = "junit.xml";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** Crea el reportero; lo usa {@link java.util.ServiceLoader}. */
    public ReporteroJunitXml() {}

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
     * @param destino carpeta de resultados, que ya existe; no puede ser null
     * @throws IOException si no puede escribir el archivo
     */
    @Override
    public void escribir(ResultadoSuite resultado, Path destino) throws IOException {
        Objects.requireNonNull(resultado, "El resultado no puede ser null");
        Objects.requireNonNull(destino, "La carpeta de destino no puede ser null");
        Files.writeString(destino.resolve(ARCHIVO), generar(resultado), StandardCharsets.UTF_8);
    }

    private static String generar(ResultadoSuite resultado) {
        int total = resultado.casos().size() + resultado.hallazgosSuite().size();
        long fallidos =
                resultado.casos().stream()
                                .filter(c -> c.veredicto().estado() == EstadoVeredicto.NO_CUMPLE)
                                .count()
                        + resultado.hallazgosSuite().size();
        long omitidos =
                resultado.casos().stream()
                        .filter(c -> c.veredicto().estado() == EstadoVeredicto.NO_CONCLUYENTE)
                        .count();
        String tiempo = segundos(Duration.between(resultado.inicio(), resultado.fin()));
        String nombre = Xml.escapar(resultado.nombreSuite());
        String marca =
                FECHA.format(
                        LocalDateTime.ofInstant(resultado.inicio(), ZoneOffset.UTC)
                                .truncatedTo(ChronoUnit.SECONDS));

        StringBuilder x = new StringBuilder(8 * 1024);
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        x.append("<testsuites name=\"").append(nombre).append("\" tests=\"").append(total);
        x.append("\" failures=\"").append(fallidos).append("\" errors=\"0\" skipped=\"");
        x.append(omitidos).append("\" time=\"").append(tiempo).append("\">\n");
        x.append("  <testsuite name=\"").append(nombre).append("\" tests=\"").append(total);
        x.append("\" failures=\"").append(fallidos).append("\" errors=\"0\" skipped=\"");
        x.append(omitidos).append("\" time=\"").append(tiempo).append("\" timestamp=\"");
        x.append(marca).append("\">\n");
        x.append("    <properties>\n");
        propiedad(x, "motor.version", resultado.manifiesto().versionMotor());
        propiedad(x, "app.java", resultado.manifiesto().versionJava());
        propiedad(x, "sistema.operativo", resultado.manifiesto().sistemaOperativo());
        propiedad(x, "semilla", String.valueOf(resultado.manifiesto().semilla()));
        propiedad(x, "hash.jar", resultado.manifiesto().hashJar());
        propiedad(x, "hash.suite", resultado.manifiesto().hashSuite());
        x.append("    </properties>\n");
        for (ResultadoCaso caso : resultado.casos()) {
            caso(x, resultado.nombreSuite(), caso);
        }
        for (Hallazgo hallazgo : resultado.hallazgosSuite()) {
            x.append("    <testcase classname=\"").append(nombre).append("\" name=\"");
            x.append(Xml.escapar("(suite) " + hallazgo.oraculoId())).append("\" time=\"0.000\">\n");
            fallo(x, hallazgo);
            x.append("    </testcase>\n");
        }
        x.append("  </testsuite>\n</testsuites>\n");
        return x.toString();
    }

    private static void caso(StringBuilder x, String suite, ResultadoCaso caso) {
        x.append("    <testcase classname=\"").append(Xml.escapar(suite)).append("\" name=\"");
        x.append(Xml.escapar(caso.casoId())).append("\" time=\"");
        x.append(segundos(caso.duracion())).append("\">\n");
        EstadoVeredicto estado = caso.veredicto().estado();
        if (estado == EstadoVeredicto.NO_CUMPLE) {
            for (Hallazgo hallazgo : caso.veredicto().hallazgos()) {
                fallo(x, hallazgo);
            }
        } else if (estado == EstadoVeredicto.NO_CONCLUYENTE) {
            x.append("      <skipped message=\"");
            x.append(Xml.escapar(caso.veredicto().motivo())).append("\"/>\n");
        }
        List<String> notas = notas(caso);
        if (!notas.isEmpty()) {
            x.append("      <system-out>").append(Xml.escapar(String.join("\n", notas)));
            x.append("</system-out>\n");
        }
        x.append("    </testcase>\n");
    }

    /** Observaciones del caso que no son fallos: hallazgos informativos e inestabilidad. */
    private static List<String> notas(ResultadoCaso caso) {
        List<String> notas = new ArrayList<>();
        if (caso.inestable()) {
            notas.add(
                    "Inestable: el resultado cambió entre los "
                            + caso.intentos()
                            + " intentos; puede no ser reproducible.");
        }
        if (caso.veredicto().estado() != EstadoVeredicto.NO_CUMPLE) {
            for (Hallazgo hallazgo : caso.veredicto().hallazgos()) {
                notas.add(descripcion(hallazgo));
            }
        }
        return notas;
    }

    private static void fallo(StringBuilder x, Hallazgo hallazgo) {
        String tipo = hallazgo.cwe() != null ? hallazgo.cwe() : hallazgo.oraculoId();
        x.append("      <failure message=\"").append(Xml.escapar(hallazgo.titulo()));
        x.append("\" type=\"").append(Xml.escapar(tipo)).append("\">");
        x.append(Xml.escapar(descripcion(hallazgo))).append("</failure>\n");
    }

    private static String descripcion(Hallazgo hallazgo) {
        StringBuilder texto = new StringBuilder();
        texto.append(hallazgo.titulo()).append('\n');
        texto.append("Oráculo: ").append(hallazgo.oraculoId());
        texto.append(" · Confianza: ").append(hallazgo.confianza().name());
        if (hallazgo.cwe() != null) {
            texto.append(" · ").append(hallazgo.cwe());
        }
        texto.append('\n').append(hallazgo.explicacion());
        if (!hallazgo.evidencia().isEmpty()) {
            texto.append("\nEventos de evidencia: ").append(hallazgo.evidencia());
        }
        return texto.toString();
    }

    private static void propiedad(StringBuilder x, String nombre, String valor) {
        x.append("      <property name=\"").append(nombre).append("\" value=\"");
        x.append(Xml.escapar(valor)).append("\"/>\n");
    }

    private static String segundos(Duration duracion) {
        return String.format(Locale.ROOT, "%.3f", duracion.toMillis() / 1000.0);
    }
}
