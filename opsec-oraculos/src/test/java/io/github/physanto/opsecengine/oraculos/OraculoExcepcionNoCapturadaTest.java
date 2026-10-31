package io.github.physanto.opsecengine.oraculos;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.soporte.CargadorTrazas;
import io.github.physanto.opsecengine.soporte.TrazaEjemplo;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/**
 * Ejemplo de referencia para probar un oráculo: una traza que lo activa y otra que no, ambas
 * cargadas con {@link CargadorTrazas} de {@code opsec-soporte-pruebas}.
 */
class OraculoExcepcionNoCapturadaTest {

    private static final String TRAZA_EXCEPCION = "excepcion-no-capturada.jsonl";

    private final OraculoExcepcionNoCapturada oraculo = new OraculoExcepcionNoCapturada();
    private final CargadorTrazas cargador = new CargadorTrazas();

    @Test
    void encuentraLaExcepcionQueNadieCapturo() throws Exception {
        Traza traza = trazaDePrueba(TRAZA_EXCEPCION);

        List<Hallazgo> hallazgos = oraculo.evaluar(traza, contexto(traza.casoId()));

        assertThat(hallazgos).singleElement().satisfies(this::esElDeLaSecuencia5);
    }

    @Test
    void noEncuentraNadaEnElCasoLimpio() throws Exception {
        Traza traza = cargador.cargarEjemploUnico(TrazaEjemplo.CASO_LIMPIO);

        assertThat(oraculo.evaluar(traza, contexto(traza.casoId()))).isEmpty();
    }

    @Test
    void ignoraLasExcepcionesCapturadasYLasTrazasNulas() throws Exception {
        Traza traza = trazaDePrueba(TRAZA_EXCEPCION);

        assertThat(oraculo.evaluar(traza, contexto(traza.casoId())))
                .extracting(Hallazgo::evidencia)
                .doesNotContain(List.of(4L));
        assertThat(oraculo.evaluar(null, contexto("X"))).isEmpty();
    }

    @Test
    void seEvaluaPorEventoYAplicaACualquierCaso() {
        assertThat(oraculo.id()).isEqualTo("EXCEPCION-NO-CAPTURADA");
        assertThat(oraculo.momento()).isEqualTo(Momento.EVENTO);
        assertThat(oraculo.aplica(caso("X"), contexto("X"))).isTrue();
    }

    @Test
    void estaRegistradoParaServiceLoader() {
        assertThat(ServiceLoader.load(Oraculo.class).stream())
                .extracting(ServiceLoader.Provider::type)
                .contains(OraculoExcepcionNoCapturada.class);
    }

    private void esElDeLaSecuencia5(Hallazgo hallazgo) {
        assertThat(hallazgo.oraculoId()).isEqualTo(OraculoExcepcionNoCapturada.ID);
        assertThat(hallazgo.confianza()).isEqualTo(Confianza.CONFIRMADO);
        assertThat(hallazgo.cwe()).isNull();
        assertThat(hallazgo.evidencia()).containsExactly(5L);
        assertThat(hallazgo.titulo()).contains("java.lang.NullPointerException");
        assertThat(hallazgo.explicacion()).contains("persona es null").contains("AWT-EventQueue-0");
    }

    private Traza trazaDePrueba(String archivo) throws Exception {
        try (Reader lector =
                new InputStreamReader(
                        getClass().getResourceAsStream(archivo), StandardCharsets.UTF_8)) {
            return cargador.leer(lector, archivo).get(0);
        }
    }

    private static Contexto contexto(String casoId) {
        Proyecto proyecto =
                new Proyecto(
                        "clientes",
                        Path.of("app.jar"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        Caso caso = caso(casoId);
        return Contexto.deCaso(
                proyecto, new Suite("ejemplo", null, null, null, List.of(caso), null), caso);
    }

    private static Caso caso(String id) {
        Paso paso = new Paso(1, Accion.PULSAR, Localizador.porTexto("Buscar"), null, null, null);
        return new Caso(id, null, null, List.of(paso), null, null, null);
    }
}
