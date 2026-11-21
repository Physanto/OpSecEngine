package io.github.physanto.opsecengine.nucleo.cobertura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.ExecutionDataWriter;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.LoggerRuntime;
import org.jacoco.core.runtime.RuntimeData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pruebas del informe de cobertura. Los datos se producen en el mismo proceso con la API de JaCoCo:
 * se instrumenta una clase, se ejecuta uno de sus métodos y se guardan los datos como los guarda el
 * agente.
 */
class CoberturaServiceTest {

    private static final String OBJETIVO = ObjetivoCobertura.class.getName();
    private static final String AJENA = Contador.class.getName();

    @TempDir Path carpeta;

    private Path exec;
    private Path jar;

    @BeforeEach
    void prepararDatosYJar() throws Exception {
        exec = carpeta.resolve("jacoco.exec");
        jar = carpeta.resolve("app.jar");
        byte[] original = bytes(OBJETIVO);
        escribirExec(original);
        try (OutputStream salida = Files.newOutputStream(jar);
                JarOutputStream paquete = new JarOutputStream(salida)) {
            anadir(paquete, OBJETIVO, original);
            anadir(paquete, AJENA, bytes(AJENA));
            paquete.putNextEntry(new JarEntry("recurso.txt"));
            paquete.write("no es una clase".getBytes(StandardCharsets.UTF_8));
            paquete.closeEntry();
        }
    }

    @Test
    void elInformeMuestraLoQueSeEjecutoYLoQueNo() throws Exception {
        Path destino = carpeta.resolve("informe");

        ResumenCobertura resumen = new CoberturaService().generar(exec, jar, null, null, destino);

        ClaseCobertura objetivo = clase(resumen, OBJETIVO);
        assertThat(objetivo.lineas().cubiertos()).as("líneas de usado()").isPositive();
        assertThat(objetivo.lineas().perdidos()).as("líneas de noUsado()").isPositive();
        assertThat(objetivo.ramas().cubiertos()).isPositive();
        assertThat(objetivo.ramas().perdidos()).isPositive();
        ClaseCobertura ajena = clase(resumen, AJENA);
        assertThat(ajena.instrucciones().cubiertos()).as("nunca se cargó").isZero();
        assertThat(ajena.instrucciones().perdidos()).isPositive();
        assertThat(resumen.lineas().total())
                .isEqualTo(objetivo.lineas().total() + ajena.lineas().total());
        assertThat(resumen.clases())
                .isSortedAccordingTo((a, b) -> a.nombre().compareTo(b.nombre()));
        assertThat(resumen.indiceHtml()).isEqualTo(destino.resolve(CoberturaService.INDICE_HTML));
        String paquete = OBJETIVO.substring(0, OBJETIVO.lastIndexOf('.'));
        assertThat(Files.readString(resumen.indiceHtml())).contains(paquete);
        assertThat(
                        Files.readString(
                                resumen.indiceHtml()
                                        .getParent()
                                        .resolve(paquete)
                                        .resolve("index.html")))
                .contains("ObjetivoCobertura");
        assertThat(Files.readString(resumen.xml(), StandardCharsets.UTF_8))
                .contains("<report", "type=\"LINE\"", "ObjetivoCobertura");
    }

    @Test
    void soloInformaLasClasesQueCumplenLosPatrones() throws Exception {
        ResumenCobertura resumen =
                new CoberturaService()
                        .generar(exec, jar, List.of(OBJETIVO), null, carpeta.resolve("informe"));

        assertThat(resumen.clases()).extracting(ClaseCobertura::nombre).containsExactly(OBJETIVO);
    }

    @Test
    void usaElCodigoFuenteSiSeDaLaCarpeta() throws Exception {
        Path fuentes = carpeta.resolve("fuentes");
        Path origen = Path.of("src", "test", "java").resolve(OBJETIVO.replace('.', '/') + ".java");
        Path copia = fuentes.resolve(OBJETIVO.replace('.', '/') + ".java");
        Files.createDirectories(copia.getParent());
        Files.copy(origen, copia);

        ResumenCobertura resumen =
                new CoberturaService()
                        .generar(exec, jar, List.of(OBJETIVO), fuentes, carpeta.resolve("informe"));

        Path paginaDeFuente =
                resumen.indiceHtml()
                        .getParent()
                        .resolve(OBJETIVO.substring(0, OBJETIVO.lastIndexOf('.')))
                        .resolve("ObjetivoCobertura.java.html");
        assertThat(Files.readString(paginaDeFuente)).contains("noUsado");
    }

    @Test
    void explicaQueFaltaElArchivoDeDatosOElJar() {
        CoberturaService servicio = new CoberturaService();

        assertThatThrownBy(() ->
                        servicio.generar(
                                carpeta.resolve("no-hay.exec"),
                                jar,
                                null,
                                null,
                                carpeta.resolve("i")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No existe el archivo de cobertura");
        assertThatThrownBy(() ->
                        servicio.generar(exec, carpeta.resolve("no-hay.jar"), null, null, carpeta))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("JAR de la aplicación");
    }

    @Test
    void elContadorCalculaElPorcentajeYTolerarCeroElementos() {
        assertThat(new Contador(3, 1).porcentaje()).isEqualTo(75.0);
        assertThat(new Contador(3, 1).total()).isEqualTo(4);
        assertThat(new Contador(0, 0).porcentaje()).isZero();
        assertThatThrownBy(() -> new Contador(-1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static ClaseCobertura clase(ResumenCobertura resumen, String nombre) {
        return resumen.clases().stream()
                .filter(c -> c.nombre().equals(nombre))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Falta la clase " + nombre));
    }

    private static byte[] bytes(String clase) throws IOException {
        try (InputStream entrada =
                ObjetivoCobertura.class
                        .getClassLoader()
                        .getResourceAsStream(clase.replace('.', '/') + ".class")) {
            return entrada.readAllBytes();
        }
    }

    private static void anadir(JarOutputStream paquete, String clase, byte[] contenido)
            throws IOException {
        paquete.putNextEntry(new JarEntry(clase.replace('.', '/') + ".class"));
        paquete.write(contenido);
        paquete.closeEntry();
    }

    /** Instrumenta la clase, ejecuta {@code usado(5)} y guarda los datos como lo hace el agente. */
    private void escribirExec(byte[] original) throws Exception {
        IRuntime runtime = new LoggerRuntime();
        byte[] instrumentada = new Instrumenter(runtime).instrument(original, OBJETIVO);
        RuntimeData datos = new RuntimeData();
        runtime.startup(datos);
        try {
            ClassLoader cargador =
                    new ClassLoader(getClass().getClassLoader()) {
                        @Override
                        protected Class<?> loadClass(String nombre, boolean resolver)
                                throws ClassNotFoundException {
                            if (!nombre.equals(OBJETIVO)) {
                                return super.loadClass(nombre, resolver);
                            }
                            Class<?> cargada = findLoadedClass(nombre);
                            return cargada != null
                                    ? cargada
                                    : defineClass(nombre, instrumentada, 0, instrumentada.length);
                        }
                    };
            Class<?> clase = cargador.loadClass(OBJETIVO);
            clase.getMethod("usado", int.class)
                    .invoke(clase.getDeclaredConstructor().newInstance(), 5);
            ExecutionDataStore ejecucion = new ExecutionDataStore();
            SessionInfoStore sesiones = new SessionInfoStore();
            datos.collect(ejecucion, sesiones, false);
            try (OutputStream salida = Files.newOutputStream(exec)) {
                ExecutionDataWriter escritor = new ExecutionDataWriter(salida);
                sesiones.accept(escritor);
                ejecucion.accept(escritor);
            }
        } finally {
            runtime.shutdown();
        }
    }
}
