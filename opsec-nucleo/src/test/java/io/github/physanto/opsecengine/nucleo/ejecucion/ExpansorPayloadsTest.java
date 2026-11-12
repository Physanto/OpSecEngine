package io.github.physanto.opsecengine.nucleo.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.ComparacionLogin;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifica sustituciones literales, aislamiento, compatibilidad y errores antes de ejecutar. */
class ExpansorPayloadsTest {

    @Test
    void conservaLaSuiteSinPayloads() {
        Suite original = suite(new Caso("normal", null, null, List.of(), null, null, null));
        assertThat(ExpansorPayloads.expandir(original)).isSameAs(original);
    }

    @Test
    void expandeEnOrdenSinModificarElOriginalNiReinterpretarElPayload() {
        List<String> valores = List.of("", "'", "$1\\dato", "{payload}", "ñ\nsegunda línea");
        Caso caso = caso("LOGIN", valores, Accion.LLENAR, "prefijo:{payload}:fin");
        Suite original = suite(caso);
        Suite expandida = ExpansorPayloads.expandir(original);
        assertThat(expandida.casos())
                .extracting(Caso::id)
                .containsExactly("LOGIN-P1", "LOGIN-P2", "LOGIN-P3", "LOGIN-P4", "LOGIN-P5");
        for (int i = 0; i < valores.size(); i++) {
            Caso copia = expandida.casos().get(i);
            assertThat(copia.pasos().get(0).valor())
                    .isEqualTo("prefijo:" + valores.get(i) + ":fin");
            assertThat(copia.pasos().get(0).objetivo()).isEqualTo(caso.pasos().get(0).objetivo());
            assertThat(copia.oraculos()).containsExactly("TRAZA-EN-DIALOGO");
            assertThat(copia.extensiones()).containsOnlyKeys("otra");
        }
        assertThat(caso.pasos().get(0).valor()).isEqualTo("prefijo:{payload}:fin");
        assertThat(caso.extensiones()).containsKey("payloads");
        assertThat(ExpansorPayloads.expandir(expandida)).isSameAs(expandida);
    }

    @Test
    void rechazaListaVaciaValoresNoTextualesYConfiguracionesQueNoSonListas() {
        for (Object configuracion :
                List.of(
                        List.of(),
                        List.of(1),
                        "texto",
                        Map.of("a", "b"),
                        Arrays.asList("a", null))) {
            assertThatThrownBy(() ->
                            ExpansorPayloads.expandir(
                                    suite(caso("A", configuracion, Accion.LLENAR, "{payload}"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("lista no vacía de textos");
        }
    }

    @Test
    void exigeUnMarcadorEnLlenarYNoEnOtrasAcciones() {
        assertThatThrownBy(() ->
                        ExpansorPayloads.expandir(
                                suite(caso("A", List.of("x"), Accion.LLENAR, "fijo"))))
                .hasMessageContaining("falta {payload}");
        assertThatThrownBy(() ->
                        ExpansorPayloads.expandir(
                                suite(caso("A", List.of("x"), Accion.PULSAR, "{payload}"))))
                .hasMessageContaining("solo se admite");
    }

    @Test
    void evitaColisionesDeIdsConCasosExistentes() {
        Caso normal = new Caso("A-P1", null, null, List.of(), null, null, null);
        assertThatThrownBy(() ->
                        ExpansorPayloads.expandir(
                                suite(caso("A", List.of("x"), Accion.LLENAR, "{payload}"), normal)))
                .hasMessageContaining("A-P1", "ya existe");
    }

    @Test
    void noCambiaLasReferenciasDeUnaComparacionDeEnumeracion() {
        Caso primero = caso("A", List.of("x"), Accion.LLENAR, "{payload}");
        Caso segundo = new Caso("B", null, null, List.of(), null, null, null);
        Suite original =
                new Suite(
                        "s",
                        "1",
                        null,
                        null,
                        List.of(primero, segundo),
                        new ComparacionLogin("A", 1, "B", 1),
                        null);
        assertThatThrownBy(() -> ExpansorPayloads.expandir(original))
                .hasMessageContaining("enumeracionUsuarios");
    }

    @Test
    void conservaElRolYLasPoliticasPeroNoMezclaPayloadsConAtaquesDelCatalogo() {
        Caso plantilla = caso("A", List.of("x"), Accion.LLENAR, "{payload}");
        Caso conRol =
                new Caso(
                        "A",
                        "prueba",
                        "admin",
                        plantilla.pasos(),
                        List.of(),
                        plantilla.oraculos(),
                        "CANARIO",
                        null,
                        null,
                        plantilla.extensiones());
        Caso copia = ExpansorPayloads.expandir(suite(conRol)).casos().getFirst();
        assertThat(copia.rol()).isEqualTo("admin");
        assertThat(copia.canario()).isEqualTo("CANARIO");
        Caso combinado =
                new Caso(
                        "A",
                        null,
                        null,
                        plantilla.pasos(),
                        List.of("SQLI-CANARIO"),
                        null,
                        plantilla.extensiones());
        assertThatThrownBy(() -> ExpansorPayloads.expandir(suite(combinado)))
                .hasMessageContaining("no combine ataques");
    }

    private static Caso caso(String id, Object payloads, Accion accion, String valor) {
        Paso paso = new Paso(1, accion, Localizador.porEtiqueta("Usuario"), valor, null, null);
        return new Caso(
                id,
                "prueba",
                null,
                List.of(paso),
                null,
                List.of("TRAZA-EN-DIALOGO"),
                Map.of("payloads", payloads, "otra", "conservada"));
    }

    private static Suite suite(Caso... casos) {
        return new Suite("payloads", "1", null, null, List.of(casos), null);
    }
}
