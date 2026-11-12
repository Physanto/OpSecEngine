package io.github.physanto.opsecengine.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.nucleo.ejecucion.ProgresoEjecucion;
import io.github.physanto.opsecengine.nucleo.ejecucion.ProgresoEjecucion.Tipo;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de {@link ProgresoConsola}: se le pasa la secuencia de notificaciones que emite el núcleo
 * y se comprueba el texto que escribe.
 */
class ProgresoConsolaTest {

    private final StringWriter texto = new StringWriter();
    private final ProgresoConsola progreso = new ProgresoConsola(new PrintWriter(texto));

    @Test
    void muestraSuiteCasosYPasosEnOrden() {
        enviar(
                suite(Tipo.SUITE_INICIO, 2),
                caso(Tipo.CASO_INICIO, "C01", 2),
                paso(Tipo.PASO_INICIO, "C01", 1, 2, false, null),
                paso(Tipo.PASO_FIN, "C01", 1, 2, false, CodigoResultado.OK),
                paso(Tipo.PASO_FIN, "C01", 2, 2, false, CodigoResultado.NO_ENCONTRADO),
                caso(Tipo.CASO_FIN, "C01", 2),
                caso(Tipo.CASO_INICIO, "C02", 1),
                paso(Tipo.PASO_FIN, "C02", 1, 1, false, CodigoResultado.OK),
                caso(Tipo.CASO_FIN, "C02", 1),
                suite(Tipo.SUITE_FIN, 2));

        assertThat(lineas())
                .containsExactly(
                        "Ejecutando la suite login (2 casos)",
                        "  [1/2] Caso C01 (2 pasos)",
                        "        Paso 1/2: OK",
                        "        Paso 2/2: NO_ENCONTRADO",
                        "  [2/2] Caso C02 (1 paso)",
                        "        Paso 1/1: OK",
                        "Suite login terminada");
    }

    @Test
    void distingueLosPasosDelInicioDeSesion() {
        enviar(
                suite(Tipo.SUITE_INICIO, 1),
                caso(Tipo.CASO_INICIO, "C01", 1),
                paso(Tipo.PASO_FIN, "C01", 1, 3, true, CodigoResultado.OK),
                paso(Tipo.PASO_FIN, "C01", 1, 1, false, CodigoResultado.OK));

        assertThat(lineas())
                .contains("        Inicio de sesión, paso 1/3: OK", "        Paso 1/1: OK");
    }

    @Test
    void unaSuiteConUnCasoUsaElSingularYUnCasoSinPasosNoDiceCuantos() {
        enviar(
                suite(Tipo.SUITE_INICIO, 1),
                new ProgresoEjecucion(Tipo.CASO_INICIO, "login", 1, "C01", 0, 0, false, null));

        assertThat(lineas())
                .containsExactly("Ejecutando la suite login (1 caso)", "  [1/1] Caso C01");
    }

    @Test
    void elContadorDeCasosVuelveAEmpezarEnCadaSuite() {
        enviar(
                suite(Tipo.SUITE_INICIO, 1),
                caso(Tipo.CASO_INICIO, "A", 1),
                suite(Tipo.SUITE_FIN, 1),
                suite(Tipo.SUITE_INICIO, 1),
                caso(Tipo.CASO_INICIO, "B", 1));

        assertThat(lineas()).contains("  [1/2] Caso A (1 paso)", "  [1/2] Caso B (1 paso)");
        assertThat(lineas()).doesNotContain("  [2/2] Caso B (1 paso)");
    }

    @Test
    void unReintentoDelMismoPasoApareceComoOtraLinea() {
        enviar(
                caso(Tipo.CASO_INICIO, "C01", 1),
                paso(Tipo.PASO_FIN, "C01", 1, 1, false, CodigoResultado.TIEMPO_AGOTADO),
                paso(Tipo.PASO_FIN, "C01", 1, 1, false, CodigoResultado.OK));

        assertThat(lineas())
                .containsSubsequence("        Paso 1/1: TIEMPO_AGOTADO", "        Paso 1/1: OK");
    }

    @Test
    void elFinDeUnPasoSinCodigoNoFalla() {
        enviar(paso(Tipo.PASO_FIN, "C01", 1, 1, false, null));

        assertThat(lineas()).containsExactly("        Paso 1/1: ?");
    }

    @Test
    void cadaLineaSeEscribeAlMomento() {
        progreso.actualizar(suite(Tipo.SUITE_INICIO, 1));

        assertThat(texto.toString()).contains("Ejecutando la suite login");
    }

    private void enviar(ProgresoEjecucion... notificaciones) {
        for (ProgresoEjecucion notificacion : notificaciones) {
            progreso.actualizar(notificacion);
        }
    }

    private List<String> lineas() {
        return texto.toString().lines().toList();
    }

    private static ProgresoEjecucion suite(Tipo tipo, int casos) {
        return new ProgresoEjecucion(tipo, "login", casos, null, 0, 0, false, null);
    }

    private static ProgresoEjecucion caso(Tipo tipo, String id, int pasos) {
        return new ProgresoEjecucion(tipo, "login", 2, id, 0, pasos, false, null);
    }

    private static ProgresoEjecucion paso(
            Tipo tipo, String id, int numero, int total, boolean login, CodigoResultado codigo) {
        return new ProgresoEjecucion(tipo, "login", 2, id, numero, total, login, codigo);
    }
}
