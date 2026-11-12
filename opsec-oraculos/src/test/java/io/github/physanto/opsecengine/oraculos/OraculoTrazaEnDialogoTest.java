package io.github.physanto.opsecengine.oraculos;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.traza.ContextoEvento;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.soporte.CargadorTrazas;
import io.github.physanto.opsecengine.soporte.TrazaEjemplo;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OraculoTrazaEnDialogoTest {
    private final OraculoTrazaEnDialogo oraculo = new OraculoTrazaEnDialogo();

    @Test
    void detectaLaTrazaVisibleSinCopiarSusDatosAlHallazgo() throws Exception {
        Traza traza = cargar("traza-en-dialogo.jsonl");
        var hallazgos = oraculo.evaluar(traza, contexto());
        assertThat(hallazgos)
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.oraculoId()).isEqualTo("TRAZA-EN-DIALOGO");
                    assertThat(h.cwe()).isEqualTo("CWE-209");
                    assertThat(h.confianza()).isEqualTo(Confianza.CONFIRMADO);
                    assertThat(h.evidencia()).containsExactly(0L);
                    assertThat(h.explicacion())
                            .doesNotContain("dato sensible de prueba", "Clientes.java");
                });
        var sesion = new EvaluadorOraculos(List.of(oraculo)).iniciarCaso(contexto());
        assertThat(sesion.registrarEvento(traza.eventos().getFirst()).estado())
                .isEqualTo(EstadoVeredicto.NO_CUMPLE);
        traza.eventos().stream().skip(1).forEach(sesion::registrarEvento);
        assertThat(sesion.terminar(null).hallazgos()).containsExactlyElementsOf(hallazgos);
    }

    @Test
    void noConfundeLaTrazaEnConsolaConUnDialogoDeLaAppCorregida() throws Exception {
        assertThat(oraculo.evaluar(cargar("traza-no-en-dialogo.jsonl"), contexto())).isEmpty();
        Traza limpia = new CargadorTrazas().cargarEjemploUnico(TrazaEjemplo.CASO_LIMPIO);
        assertThat(oraculo.evaluar(limpia, contexto())).isEmpty();
        assertThat(oraculo.evaluar(null, contexto())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "java.lang.NullPointerException: no disponible",
                "java.sql.SQLException",
                "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space",
                "Caused by: java.lang.Exception: fallo",
                "SQLException: fallo de conexión",
                "AssertionError: invariante incumplida",
                "\tat app.Clientes.buscar(Clientes.java:42)",
                "\tat java.base@21.0.1/java.lang.Thread.run(Thread.java:840)",
                "\tat app.Clientes.buscar(Unknown Source)",
                "\tat app.Clientes.buscar(Native Method)"
            })
    void reconoceExcepcionesYMarcosDePila(String texto) {
        assertThat(oraculo.evaluar(traza(TipoEvento.DIALOGO, Map.of("texto", texto)), contexto()))
                .hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Error al guardar los datos.",
                "Se produjo una excepción. Contacte con soporte.",
                "Exception: no se pudo completar la operación.",
                "Usuario o contraseña incorrectos",
                "La documentación explica java.lang.String y at java.",
                "at app.Clientes.buscar(no es una ubicación)",
                "El campo contiene NullPointerException sin un mensaje técnico."
            })
    void noMarcaMensajesSinFirmasTecnicas(String texto) {
        assertThat(oraculo.evaluar(traza(TipoEvento.DIALOGO, Map.of("texto", texto)), contexto()))
                .isEmpty();
    }

    @Test
    void ignoraDatosAusentesMalTipadosYCamposDistintosDeTexto() {
        assertThat(oraculo.evaluar(traza(TipoEvento.DIALOGO, Map.of()), contexto())).isEmpty();
        assertThat(oraculo.evaluar(traza(TipoEvento.DIALOGO, Map.of("texto", 42)), contexto()))
                .isEmpty();
        assertThat(
                        oraculo.evaluar(
                                traza(
                                        TipoEvento.DIALOGO,
                                        Map.of("titulo", "java.lang.NullPointerException")),
                                contexto()))
                .isEmpty();
        assertThat(
                        oraculo.evaluar(
                                traza(
                                        TipoEvento.CONSOLA,
                                        Map.of("texto", "java.lang.NullPointerException")),
                                contexto()))
                .isEmpty();
    }

    @Test
    void produceUnHallazgoPorDialogoYSeDescubrePorServiceLoader() {
        Evento primero =
                traza(TipoEvento.DIALOGO, Map.of("texto", "java.sql.SQLException"))
                        .eventos()
                        .getFirst();
        Evento segundo =
                new Evento(
                        2, Instant.EPOCH, TipoEvento.DIALOGO, primero.contexto(), primero.datos());
        assertThat(oraculo.evaluar(new Traza("DIALOGO", List.of(primero, segundo)), contexto()))
                .extracting(h -> h.evidencia())
                .containsExactly(List.of(1L), List.of(2L));
        assertThat(oraculo.momento()).isEqualTo(Momento.EVENTO);
        assertThat(oraculo.aplica(contexto().caso(), contexto())).isTrue();
        assertThat(ServiceLoader.load(Oraculo.class).stream())
                .extracting(ServiceLoader.Provider::type)
                .contains(OraculoTrazaEnDialogo.class);
    }

    private static Traza cargar(String archivo) throws Exception {
        try (var lector =
                new InputStreamReader(
                        OraculoTrazaEnDialogoTest.class.getResourceAsStream(archivo),
                        StandardCharsets.UTF_8)) {
            return new CargadorTrazas().leer(lector, archivo).getFirst();
        }
    }

    private static Traza traza(TipoEvento tipo, Map<String, Object> datos) {
        return new Traza(
                "DIALOGO",
                List.of(
                        new Evento(
                                1,
                                Instant.EPOCH,
                                tipo,
                                new ContextoEvento("DIALOGO", 0, null, false),
                                datos)));
    }

    private static Contexto contexto() {
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
        Caso caso = new Caso("DIALOGO", null, null, null, null, null, null);
        return Contexto.deCaso(
                proyecto, new Suite("dialogos", null, null, null, List.of(caso), null), caso);
    }
}
