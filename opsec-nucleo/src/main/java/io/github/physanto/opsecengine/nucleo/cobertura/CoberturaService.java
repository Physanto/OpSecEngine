package io.github.physanto.opsecengine.nucleo.cobertura;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.runtime.WildcardMatcher;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.ISourceFileLocator;
import org.jacoco.report.MultiSourceFileLocator;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.xml.XMLFormatter;

/**
 * Genera el informe de cobertura de la aplicación probada a partir de los datos que recogió JaCoCo.
 *
 * <p>Cruza el archivo {@code jacoco.exec} con las clases del JAR de la aplicación (solo las que
 * cumplen los patrones indicados) y escribe en la carpeta de destino un informe HTML ({@value
 * #INDICE_HTML}) y otro XML ({@value #ARCHIVO_XML}). Si se da una carpeta de fuentes, el HTML
 * muestra el código línea por línea; si no, solo el nivel de clases y métodos. Las clases que nunca
 * se ejecutaron aparecen con 0 %.
 *
 * <p>Solo analiza las clases que están directamente en el JAR; las que vienen dentro de otros JAR
 * no se miden. No tiene estado; es seguro entre hilos.
 */
public final class CoberturaService {

    /** Página principal del informe HTML, dentro de la carpeta de destino. */
    public static final String INDICE_HTML = "html/index.html";

    /** Informe XML, dentro de la carpeta de destino. */
    public static final String ARCHIVO_XML = "cobertura.xml";

    private static final String EXTENSION_CLASE = ".class";

    /** Crea el servicio. */
    public CoberturaService() {
        // Sin estado.
    }

    /**
     * Genera los informes.
     *
     * @param archivoExec archivo {@code jacoco.exec} con los datos de la ejecución; no puede ser
     *     null
     * @param jarApp JAR de la aplicación probada, con sus clases; no puede ser null
     * @param incluir patrones de nombres de clase que se informan, con {@code *} y {@code ?} y
     *     puntos como separador; null o vacío para todas
     * @param fuentes carpeta con el código fuente para el HTML; puede ser null
     * @param destino carpeta donde se escriben los informes; se crea si no existe; no puede ser
     *     null
     * @return el resumen de la cobertura y la ubicación de los informes
     * @throws IOException si falta el archivo de datos o el JAR, si no se puede leer alguno, o si
     *     no se pueden escribir los informes
     * @throws NullPointerException si {@code archivoExec}, {@code jarApp} o {@code destino} son
     *     null
     */
    public ResumenCobertura generar(
            Path archivoExec, Path jarApp, List<String> incluir, Path fuentes, Path destino)
            throws IOException {
        Objects.requireNonNull(archivoExec, "El archivo de datos no puede ser null");
        Objects.requireNonNull(jarApp, "El JAR de la aplicación no puede ser null");
        Objects.requireNonNull(destino, "La carpeta de destino no puede ser null");
        if (!Files.isRegularFile(archivoExec)) {
            throw new IOException(
                    "No existe el archivo de cobertura "
                            + archivoExec
                            + "; ¿se ejecutó con la cobertura activada y se cerró la aplicación?");
        }
        if (!Files.isRegularFile(jarApp)) {
            throw new IOException("No existe el JAR de la aplicación: " + jarApp);
        }
        ExecFileLoader datos = new ExecFileLoader();
        datos.load(archivoExec.toFile());

        CoverageBuilder cobertura = new CoverageBuilder();
        Analyzer analizador = new Analyzer(datos.getExecutionDataStore(), cobertura);
        analizar(analizador, jarApp, incluir);
        IBundleCoverage paquete = cobertura.getBundle(jarApp.getFileName().toString());

        Files.createDirectories(destino);
        Path html = destino.resolve(INDICE_HTML);
        Path xml = destino.resolve(ARCHIVO_XML);
        escribir(datos, paquete, fuentes, destino, xml);
        return resumen(paquete, cobertura, html, xml);
    }

    private static void analizar(Analyzer analizador, Path jarApp, List<String> incluir)
            throws IOException {
        WildcardMatcher filtro =
                new WildcardMatcher(
                        incluir == null || incluir.isEmpty() ? "*" : String.join(":", incluir));
        try (JarFile jar = new JarFile(jarApp.toFile())) {
            Enumeration<JarEntry> entradas = jar.entries();
            while (entradas.hasMoreElements()) {
                JarEntry entrada = entradas.nextElement();
                String nombre = entrada.getName();
                if (entrada.isDirectory() || !nombre.endsWith(EXTENSION_CLASE)) {
                    continue;
                }
                String clase =
                        nombre.substring(0, nombre.length() - EXTENSION_CLASE.length())
                                .replace('/', '.');
                if (clase.equals("module-info")
                        || clase.endsWith(".module-info")
                        || clase.startsWith("META-INF.")
                        || !filtro.matches(clase)) {
                    continue;
                }
                try (InputStream bytes = jar.getInputStream(entrada)) {
                    analizador.analyzeClass(bytes, nombre);
                }
            }
        }
    }

    private static void escribir(
            ExecFileLoader datos, IBundleCoverage paquete, Path fuentes, Path destino, Path xml)
            throws IOException {
        ISourceFileLocator localizador = localizadorDeFuentes(fuentes);
        HTMLFormatter formato = new HTMLFormatter();
        formato.setOutputEncoding(StandardCharsets.UTF_8.name());
        IReportVisitor html =
                formato.createVisitor(new FileMultiReportOutput(destino.resolve("html").toFile()));
        html.visitInfo(
                datos.getSessionInfoStore().getInfos(),
                datos.getExecutionDataStore().getContents());
        html.visitBundle(paquete, localizador);
        html.visitEnd();

        try (OutputStream salida = Files.newOutputStream(xml)) {
            IReportVisitor visitante = new XMLFormatter().createVisitor(salida);
            visitante.visitInfo(
                    datos.getSessionInfoStore().getInfos(),
                    datos.getExecutionDataStore().getContents());
            visitante.visitBundle(paquete, localizador);
            visitante.visitEnd();
        }
    }

    private static ISourceFileLocator localizadorDeFuentes(Path fuentes) {
        if (fuentes != null && Files.isDirectory(fuentes)) {
            return new DirectorySourceFileLocator(fuentes.toFile(), "UTF-8", 4);
        }
        return new MultiSourceFileLocator(4);
    }

    private static ResumenCobertura resumen(
            IBundleCoverage paquete, CoverageBuilder cobertura, Path html, Path xml) {
        List<ClaseCobertura> clases = new ArrayList<>();
        for (IClassCoverage clase : cobertura.getClasses()) {
            clases.add(
                    new ClaseCobertura(
                            clase.getName().replace('/', '.'),
                            contador(clase.getInstructionCounter()),
                            contador(clase.getBranchCounter()),
                            contador(clase.getLineCounter())));
        }
        clases.sort(Comparator.comparing(ClaseCobertura::nombre));
        return new ResumenCobertura(
                contador(paquete.getInstructionCounter()),
                contador(paquete.getBranchCounter()),
                contador(paquete.getLineCounter()),
                clases,
                html,
                xml);
    }

    private static Contador contador(ICounter contador) {
        return new Contador(contador.getCoveredCount(), contador.getMissedCount());
    }
}
