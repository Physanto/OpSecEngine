package io.github.physanto.opsecengine.oraculos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Senal;
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
import io.github.physanto.opsecengine.oraculos.EvaluadorCalidadGui.Control;
import io.github.physanto.opsecengine.oraculos.EvaluadorCalidadGui.RecorridoTeclado;
import io.github.physanto.opsecengine.oraculos.EvaluadorCalidadGui.Ventana;
import io.github.physanto.opsecengine.soporte.CargadorTrazas;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OraculosCalidadTest {
    private final OraculoInterfazCongelada edt = new OraculoInterfazCongelada();
    private final EvaluadorTiemposRespuesta tiempos = new EvaluadorTiemposRespuesta();
    private final EvaluadorCalidadGui gui = new EvaluadorCalidadGui();
    private final EvaluadorEscenarioRobustez robustez = new EvaluadorEscenarioRobustez();
    private final Senal esperado = new Senal(TipoEvento.ESTADO, Map.of("existe", false));
    private final Senal fallo = new Senal(TipoEvento.ESTADO, Map.of("existe", true));

    @Test
    void lasTrazasEdtActivanSoloAlSuperarElUmbralYSeEvaluaPorEvento() throws Exception {
        Traza traza = cargar("edt-bloqueado.jsonl");
        var sesion = new EvaluadorOraculos(List.of(edt)).iniciarCaso(contexto(traza.casoId()));
        var resultado = sesion.registrarEvento(traza.eventos().getFirst());
        assertThat(resultado.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(resultado.hallazgos())
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.oraculoId()).isEqualTo("INTERFAZ-CONGELADA");
                    assertThat(h.cwe()).isNull();
                    assertThat(h.confianza()).isEqualTo(Confianza.CONFIRMADO);
                    assertThat(h.evidencia()).containsExactly(0L);
                });
        assertThat(edt.evaluar(cargar("edt-dentro-umbral.jsonl"), null)).isEmpty();
        assertThat(edt.momento()).isEqualTo(Momento.EVENTO);
        assertThat(ServiceLoader.load(Oraculo.class).stream())
                .extracting(ServiceLoader.Provider::type)
                .contains(OraculoInterfazCongelada.class);
    }

    @Test
    void noConfundeRespuestaLentaConEdtNiRedondeaDatosInvalidos() {
        for (Object invalido :
                List.of(
                        "2000",
                        -1,
                        1.5,
                        Double.NaN,
                        Double.POSITIVE_INFINITY,
                        BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE))) {
            assertThat(edt.evaluar(medida("EDT_BLOQUEADO", invalido, 1), null)).isEmpty();
            assertThat(edt.evaluar(medida("EDT_BLOQUEADO", Long.MAX_VALUE, invalido), null))
                    .isEmpty();
        }
        assertThat(edt.evaluar(medida("EDT_BLOQUEADO", 100, 0), null)).isEmpty();
        assertThat(edt.evaluar(medida("RESPUESTA_ACCION", 2000, 1), null)).isEmpty();
        assertThat(
                        edt.evaluar(
                                new Traza(
                                        "C",
                                        List.of(
                                                evento(
                                                        0,
                                                        TipoEvento.TIEMPO,
                                                        Map.of(
                                                                "medida",
                                                                "EDT_BLOQUEADO",
                                                                "duracionMs",
                                                                2000)))),
                                null))
                .isEmpty();
        assertThat(edt.evaluar(medida("EDT_BLOQUEADO", 2, 1), contexto("OTRO"))).isEmpty();
        assertThat(edt.evaluar(new Traza("OTRO", medida("EDT_BLOQUEADO", 2, 1).eventos()), null))
                .isEmpty();
        assertThat(edt.evaluar(null, null)).isEmpty();
    }

    @Test
    void calculaMedianaYP95PorRangoProximoConRepeticiones() {
        List<Traza> repeticiones =
                IntStream.rangeClosed(1, 20)
                        .mapToObj(i -> medida("RESPUESTA_ACCION", i, 100))
                        .toList();
        var resultado = tiempos.evaluar(repeticiones, "C", 1, 20, 18);
        assertThat(resultado.resumen().muestras()).isEqualTo(20);
        assertThat(resultado.resumen().medianaMs()).isEqualByComparingTo("10.5");
        assertThat(resultado.resumen().percentil95Ms()).isEqualTo(19);
        assertThat(resultado.veredicto().estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(tiempos.evaluar(repeticiones, "C", 1, 20, 19).veredicto().estado())
                .isEqualTo(EstadoVeredicto.CUMPLE);
        var maximo =
                tiempos.evaluar(
                        List.of(
                                medida("RESPUESTA_ACCION", Long.MAX_VALUE, 1),
                                medida("RESPUESTA_ACCION", Long.MAX_VALUE, 1)),
                        "C",
                        1,
                        2,
                        Long.MAX_VALUE);
        assertThat(maximo.resumen().medianaMs())
                .isEqualByComparingTo(BigDecimal.valueOf(Long.MAX_VALUE));
    }

    @Test
    void noPublicaEstadisticasConMuestrasAusentesInvalidasOReintentosAmbiguos() {
        Traza buena = medida("RESPUESTA_ACCION", 10, 100);
        for (List<Traza> trazas :
                List.of(
                        List.<Traza>of(),
                        List.of(buena),
                        List.of(buena, medida("RESPUESTA_ACCION", "10", 100)),
                        List.of(buena, medida("EDT_BLOQUEADO", 10, 100)),
                        List.of(buena, new Traza("OTRO", buena.eventos())),
                        List.of(
                                buena,
                                new Traza(
                                        "C",
                                        List.of(
                                                buena.eventos().getFirst(),
                                                evento(
                                                        1,
                                                        TipoEvento.TIEMPO,
                                                        buena.eventos().getFirst().datos())))))) {
            var resultado = tiempos.evaluar(trazas, "C", 1, 2, 100);
            assertThat(resultado.resumen()).isNull();
            assertThat(resultado.veredicto().estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        }
        assertThatThrownBy(() -> tiempos.evaluar(List.of(buena), "C", 1, 1, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compruebaLiberacionExplicitaSinConfundirlaConCierreUOcultacion() {
        assertThat(gui.evaluarLiberacion(List.of("v"), List.of(new Ventana("v", true, 8))).estado())
                .isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(
                        gui.evaluarLiberacion(List.of("v"), List.of(new Ventana("v", false, 8)))
                                .estado())
                .isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(gui.evaluarLiberacion(List.of("v"), List.of()).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThatThrownBy(() ->
                        gui.evaluarLiberacion(
                                List.of("v"),
                                List.of(new Ventana("v", true, 1), new Ventana("v", false, 2))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exigeEtiquetaAsociadaONombreAccesibleSoloEnControlesInteractivosActivos() {
        Control sinNombre = new Control("c", true, true, true, null, " ", 10);
        var fallo = gui.evaluarEtiquetas(List.of(sinNombre));
        assertThat(fallo.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(fallo.hallazgos().getFirst().evidencia()).containsExactly(10L);
        assertThat(
                        gui.evaluarEtiquetas(
                                        List.of(
                                                new Control(
                                                        "c",
                                                        true,
                                                        true,
                                                        true,
                                                        "Etiqueta",
                                                        null,
                                                        10)))
                                .estado())
                .isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(
                        gui.evaluarEtiquetas(
                                        List.of(
                                                new Control(
                                                        "c", true, true, true, null, "Nombre", 10)))
                                .estado())
                .isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(
                        gui.evaluarEtiquetas(
                                        List.of(
                                                new Control(
                                                        "c", true, false, true, null, null, 10)))
                                .estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(gui.evaluarEtiquetas(List.of()).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
    }

    @Test
    void comparaOrdenRealDeTabulacionYActivacionesDeTeclado() {
        assertThat(
                        gui.evaluarTeclado(recorrido(List.of("a", "b"), Set.of("enviar"), true))
                                .estado())
                .isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(
                        gui.evaluarTeclado(recorrido(List.of("b", "a"), Set.of("enviar"), true))
                                .estado())
                .isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(gui.evaluarTeclado(recorrido(List.of("a", "b"), Set.of(), true)).estado())
                .isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(gui.evaluarTeclado(recorrido(List.of("a"), Set.of(), false)).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(gui.evaluarTeclado(null).estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
    }

    @Test
    void lasTrazasDeDoblePulsacionDistinguenResultadoDuplicadoYControlado() throws Exception {
        assertThat(
                        robustez.evaluar(
                                        cargar("robustez-resultado-incorrecto.jsonl"),
                                        1,
                                        2,
                                        esperado,
                                        fallo)
                                .estado())
                .isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(
                        robustez.evaluar(
                                        cargar("robustez-resultado-controlado.jsonl"),
                                        1,
                                        2,
                                        esperado,
                                        fallo)
                                .estado())
                .isEqualTo(EstadoVeredicto.CUMPLE);
    }

    @Test
    void robustezNoApruebaSoloPorOkYConservaExcepcionesYCierresInesperados() throws Exception {
        Traza base = cargar("robustez-resultado-controlado.jsonl");
        Traza sinRespuesta =
                new Traza(
                        base.casoId(),
                        base.eventos().stream()
                                .filter(e -> e.tipo() != TipoEvento.ESTADO)
                                .toList());
        assertThat(robustez.evaluar(sinRespuesta, 1, 2, esperado, fallo).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        for (TipoEvento tipo : List.of(TipoEvento.EXCEPCION, TipoEvento.APP_TERMINADA)) {
            Traza fallida =
                    new Traza(
                            base.casoId(),
                            base.eventos().stream()
                                    .map(e ->
                                            e.tipo() == TipoEvento.ESTADO
                                                    ? new Evento(
                                                            e.secuencia(),
                                                            e.instante(),
                                                            tipo,
                                                            e.contexto(),
                                                            Map.of(
                                                                    "capturada",
                                                                    false,
                                                                    "esperada",
                                                                    false))
                                                    : e)
                                    .toList());
            assertThat(robustez.evaluar(fallida, 1, 2, esperado, fallo).estado())
                    .isEqualTo(EstadoVeredicto.NO_CUMPLE);
        }
        assertThat(robustez.evaluar(null, 1, 2, esperado, fallo).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
    }

    @Test
    void robustezDescartaRespuestasDeOtroPasoYTardias() throws Exception {
        Traza base = cargar("robustez-resultado-controlado.jsonl");
        Traza ajena =
                new Traza(
                        base.casoId(),
                        base.eventos().stream()
                                .map(e ->
                                        e.tipo() == TipoEvento.ESTADO
                                                ? new Evento(
                                                        e.secuencia(),
                                                        e.instante(),
                                                        e.tipo(),
                                                        new ContextoEvento("OTRO", 2, null, false),
                                                        e.datos())
                                                : e)
                                .toList());
        assertThat(robustez.evaluar(ajena, 1, 2, esperado, fallo).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        Traza tardia =
                new Traza(
                        base.casoId(),
                        base.eventos().stream()
                                .map(e ->
                                        e.secuencia() == 3
                                                ? new Evento(
                                                        e.secuencia(),
                                                        e.instante(),
                                                        TipoEvento.PASO_INICIO,
                                                        new ContextoEvento(
                                                                base.casoId(), 3, null, false),
                                                        Map.of("accion", "CAPTURAR"))
                                                : e)
                                .toList());
        assertThat(robustez.evaluar(tardia, 1, 2, esperado, fallo).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
    }

    @Test
    void lasObservacionesSonInmutablesYNoConservanEstadoEntreEvaluaciones() {
        var orden = new java.util.ArrayList<>(List.of("a", "b"));
        var recorrido =
                new RecorridoTeclado(orden, orden, Set.of("enviar"), Set.of("enviar"), true, 1);
        orden.clear();
        assertThat(gui.evaluarTeclado(recorrido).estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(gui.evaluarEtiquetas(null).estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(edt.evaluar(medida("EDT_BLOQUEADO", 2, 1), null)).hasSize(1);
        assertThat(edt.evaluar(medida("EDT_BLOQUEADO", 1, 1), null)).isEmpty();
        assertThatThrownBy(() -> new Ventana(" ", true, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void robustezNoApruebaSiUnaAccionIntermediaFallo() throws Exception {
        Traza base = cargar("robustez-resultado-controlado.jsonl");
        Traza fallida =
                new Traza(
                        base.casoId(),
                        base.eventos().stream()
                                .map(e ->
                                        e.secuencia() == 1
                                                ? new Evento(
                                                        e.secuencia(),
                                                        e.instante(),
                                                        e.tipo(),
                                                        e.contexto(),
                                                        Map.of("codigo", "ERROR"))
                                                : e)
                                .toList());
        assertThat(robustez.evaluar(fallida, 1, 2, esperado, fallo).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
    }

    private static RecorridoTeclado recorrido(
            List<String> orden, Set<String> acciones, boolean completo) {
        return new RecorridoTeclado(
                List.of("a", "b"), orden, Set.of("enviar"), acciones, completo, 20);
    }

    private static Traza medida(String tipo, Object duracion, Object umbral) {
        return new Traza(
                "C",
                List.of(
                        evento(
                                0,
                                TipoEvento.TIEMPO,
                                Map.of(
                                        "medida",
                                        tipo,
                                        "duracionMs",
                                        duracion,
                                        "umbralMs",
                                        umbral))));
    }

    private static Evento evento(long secuencia, TipoEvento tipo, Map<String, Object> datos) {
        return new Evento(
                secuencia, Instant.EPOCH, tipo, new ContextoEvento("C", 1, null, false), datos);
    }

    private static Contexto contexto(String id) {
        Proyecto proyecto =
                new Proyecto(
                        "calidad",
                        Path.of("app.jar"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        Caso caso = new Caso(id, null, null, null, null, null, null);
        return Contexto.deCaso(
                proyecto, new Suite("calidad", null, null, null, List.of(caso), null), caso);
    }

    private static Traza cargar(String nombre) throws Exception {
        try (var lector =
                new InputStreamReader(
                        OraculosCalidadTest.class.getResourceAsStream(nombre),
                        StandardCharsets.UTF_8)) {
            return new CargadorTrazas().leer(lector, nombre).getFirst();
        }
    }
}
