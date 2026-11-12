package io.github.physanto.opsecengine.oraculos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class OraculoEnumeracionUsuariosTest {
    private final OraculoEnumeracionUsuarios oraculo = new OraculoEnumeracionUsuarios();
    private final ComparacionEnumeracionUsuarios comparacion =
            new ComparacionEnumeracionUsuarios("ausente", 2, "clave", 3);

    @Test
    void laTrazaOficialDetectaF5AlFinalDeLaSuiteSinExponerCredencialesNiMensajes()
            throws Exception {
        List<Traza> trazas = new CargadorTrazas().cargarEjemplo(TrazaEjemplo.C01_C02_MENSAJES);
        Contexto contexto =
                contexto(new ComparacionEnumeracionUsuarios("C01", 5, "C02", 5), trazas);
        var resultado = new EvaluadorOraculos(List.of(oraculo)).evaluarSuite(contexto);
        assertThat(resultado.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(resultado.hallazgos())
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.oraculoId()).isEqualTo("ENUMERACION-USUARIOS");
                    assertThat(h.cwe()).isEqualTo("CWE-204");
                    assertThat(h.confianza()).isEqualTo(Confianza.PROBABLE);
                    assertThat(h.evidencia()).containsExactly(15L, 32L);
                    assertThat(h.explicacion())
                            .contains("C01", "C02")
                            .doesNotContain(
                                    "noexiste",
                                    "Admin",
                                    "Clave-Prueba-2026",
                                    "El usuario no existe");
                });
    }

    @Test
    void laTrazaCorregidaNoActivaYNoDependeDeIdsNiMensajesConocidos() throws Exception {
        try (var lector =
                new InputStreamReader(
                        getClass().getResourceAsStream("enumeracion-corregida.jsonl"),
                        StandardCharsets.UTF_8)) {
            List<Traza> trazas = new CargadorTrazas().leer(lector, "enumeracion-corregida.jsonl");
            assertThat(
                            new EvaluadorOraculos(List.of(oraculo))
                                    .evaluarSuite(contexto(comparacion, trazas))
                                    .estado())
                    .isEqualTo(EstadoVeredicto.CUMPLE);
        }
        assertThat(
                        evaluar(
                                respuesta("ausente", 2, "Respuesta A"),
                                respuesta("clave", 3, "Respuesta B")))
                .hasSize(1);
    }

    @Test
    void exigeComparacionExplicitaAmbosCasosYUnaTrazaPorCaso() {
        List<Traza> trazas = List.of(respuesta("ausente", 2, "A"), respuesta("clave", 3, "B"));
        assertThat(oraculo.evaluar(null, contexto(null, trazas))).isEmpty();
        assertThat(oraculo.evaluar(null, contexto(Map.of("caso", "ausente"), trazas))).isEmpty();
        assertThat(oraculo.evaluar(null, null)).isEmpty();
        assertThat(oraculo.evaluar(null, contexto(comparacion, List.of(trazas.getFirst()))))
                .isEmpty();
        assertThat(
                        oraculo.evaluar(
                                null,
                                contexto(
                                        comparacion,
                                        List.of(
                                                trazas.getFirst(),
                                                trazas.getFirst(),
                                                trazas.getLast()))))
                .isEmpty();
        Contexto base = contexto(comparacion, trazas);
        Suite vacia = new Suite("vacia", null, null, null, List.of(), base.suite().extensiones());
        assertThat(oraculo.evaluar(null, Contexto.deSuite(base.proyecto(), vacia, trazas)))
                .isEmpty();
    }

    @Test
    void ignoraDialogosAjenosPreviosYDeOtrosPasos() {
        Traza original = respuesta("ausente", 2, "igual");
        var eventos = new ArrayList<Evento>();
        eventos.add(evento(0, "ausente", 1, TipoEvento.DIALOGO, Map.of("texto", "antes")));
        eventos.add(evento(1, "ausente", 2, TipoEvento.PASO_INICIO, Map.of("accion", "PULSAR")));
        eventos.add(evento(2, "OTRO", 2, TipoEvento.DIALOGO, Map.of("texto", "ajeno")));
        eventos.add(evento(3, "ausente", 9, TipoEvento.DIALOGO, Map.of("texto", "otro paso")));
        eventos.add(evento(4, "ausente", 2, TipoEvento.DIALOGO, Map.of("texto", "igual")));
        eventos.add(evento(5, "ausente", 2, TipoEvento.PASO_FIN, Map.of("codigo", "OK")));
        assertThat(evaluar(new Traza(original.casoId(), eventos), respuesta("clave", 3, "igual")))
                .isEmpty();
    }

    @Test
    void noComparaIntentosIncompletosFallidosSinTextoNiConSesionActiva() {
        Traza normal = respuesta("ausente", 2, "A");
        for (Map<String, Object> datos :
                List.<Map<String, Object>>of(
                        Map.of(), Map.of("texto", 42), Map.of("texto", "  "))) {
            assertThat(
                            evaluar(
                                    cambiar(normal, 1, TipoEvento.DIALOGO, datos),
                                    respuesta("clave", 3, "B")))
                    .isEmpty();
        }
        assertThat(
                        evaluar(
                                new Traza("ausente", normal.eventos().subList(0, 2)),
                                respuesta("clave", 3, "B")))
                .isEmpty();
        assertThat(
                        evaluar(
                                cambiar(normal, 2, TipoEvento.PASO_FIN, Map.of("codigo", "ERROR")),
                                respuesta("clave", 3, "B")))
                .isEmpty();
        assertThat(
                        evaluar(
                                cambiar(
                                        normal,
                                        0,
                                        TipoEvento.PASO_INICIO,
                                        Map.of("accion", "LLENAR")),
                                respuesta("clave", 3, "B")))
                .isEmpty();
        assertThat(
                        evaluar(
                                cambiar(normal, 1, TipoEvento.SESION, Map.of("activa", true)),
                                respuesta("clave", 3, "B")))
                .isEmpty();
        Evento dialogo = normal.eventos().get(1);
        Traza autenticada =
                new Traza(
                        "ausente",
                        List.of(
                                normal.eventos().getFirst(),
                                new Evento(
                                        1,
                                        Instant.EPOCH,
                                        TipoEvento.DIALOGO,
                                        new ContextoEvento("ausente", 2, "admin", true),
                                        dialogo.datos()),
                                normal.eventos().getLast()));
        assertThat(evaluar(autenticada, respuesta("clave", 3, "B"))).isEmpty();
    }

    @Test
    void unaNuevaAccionLosReintentosYLosDialogosTardiosDejanLaComparacionIncompleta() {
        Traza normal = respuesta("ausente", 2, "A");
        assertThat(
                        evaluar(
                                cambiar(
                                        normal,
                                        2,
                                        TipoEvento.PASO_INICIO,
                                        Map.of("accion", "PULSAR")),
                                respuesta("clave", 3, "B")))
                .isEmpty();
        var eventos = new ArrayList<>(normal.eventos());
        eventos.add(evento(3, "ausente", 2, TipoEvento.DIALOGO, Map.of("texto", "tardío")));
        assertThat(evaluar(new Traza("ausente", eventos), respuesta("clave", 3, "B"))).isEmpty();
        eventos.set(3, evento(3, "ausente", 2, TipoEvento.PASO_INICIO, Map.of("accion", "PULSAR")));
        assertThat(evaluar(new Traza("ausente", eventos), respuesta("clave", 3, "B"))).isEmpty();
    }

    @Test
    void comparaLaSecuenciaCompletaDeDialogosSinNormalizarDiferenciasVisibles() {
        Traza primera = varias("ausente", 2, "A", "B");
        assertThat(evaluar(primera, varias("clave", 3, "A", "B"))).isEmpty();
        assertThat(evaluar(primera, varias("clave", 3, "B", "A"))).hasSize(1);
        assertThat(evaluar(primera, respuesta("clave", 3, "A"))).hasSize(1);
        assertThat(
                        evaluar(
                                respuesta("ausente", 2, "  Mensaje  "),
                                respuesta("clave", 3, "Mensaje")))
                .isEmpty();
        assertThat(evaluar(respuesta("ausente", 2, "Mensaje"), respuesta("clave", 3, "mensaje")))
                .hasSize(1);
    }

    @Test
    void estaRegistradoComoSuiteYNoConservaEstado() {
        assertThat(oraculo.momento()).isEqualTo(Momento.SUITE);
        assertThat(ServiceLoader.load(Oraculo.class).stream())
                .extracting(ServiceLoader.Provider::type)
                .contains(OraculoEnumeracionUsuarios.class);
        assertThat(evaluar(respuesta("ausente", 2, "A"), respuesta("clave", 3, "B"))).hasSize(1);
        assertThat(evaluar(respuesta("ausente", 2, "igual"), respuesta("clave", 3, "igual")))
                .isEmpty();
    }

    @Test
    void elAdaptadorRechazaReferenciasInvalidas() {
        assertThatThrownBy(() -> new ComparacionEnumeracionUsuarios(null, 1, "b", 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ComparacionEnumeracionUsuarios("a", 1, "a", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComparacionEnumeracionUsuarios(" ", 1, "b", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComparacionEnumeracionUsuarios("a", 0, "b", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComparacionEnumeracionUsuarios("a", 1, "b", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<io.github.physanto.opsecengine.dominio.resultado.Hallazgo> evaluar(
            Traza... trazas) {
        return oraculo.evaluar(null, contexto(comparacion, List.of(trazas)));
    }

    private static Contexto contexto(Object comparacion, List<Traza> trazas) {
        Proyecto proyecto =
                new Proyecto(
                        "login",
                        Path.of("app.jar"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        List<Caso> casos =
                trazas.stream()
                        .map(Traza::casoId)
                        .distinct()
                        .map(id -> new Caso(id, null, null, null, null, null, null))
                        .toList();
        return Contexto.deSuite(
                proyecto,
                new Suite(
                        "login",
                        null,
                        null,
                        null,
                        casos,
                        comparacion == null
                                ? Map.of()
                                : Map.of("enumeracionUsuarios", comparacion)),
                trazas);
    }

    private static Traza respuesta(String id, int paso, String texto) {
        return varias(id, paso, texto);
    }

    private static Traza varias(String id, int paso, String... textos) {
        var eventos = new ArrayList<Evento>();
        eventos.add(evento(0, id, paso, TipoEvento.PASO_INICIO, Map.of("accion", "PULSAR")));
        for (String texto : textos) {
            eventos.add(
                    evento(eventos.size(), id, paso, TipoEvento.DIALOGO, Map.of("texto", texto)));
        }
        eventos.add(evento(eventos.size(), id, paso, TipoEvento.PASO_FIN, Map.of("codigo", "OK")));
        return new Traza(id, eventos);
    }

    private static Evento evento(
            long secuencia, String id, int paso, TipoEvento tipo, Map<String, Object> datos) {
        return new Evento(
                secuencia, Instant.EPOCH, tipo, new ContextoEvento(id, paso, null, false), datos);
    }

    private static Traza cambiar(
            Traza traza, long secuencia, TipoEvento tipo, Map<String, Object> datos) {
        return new Traza(
                traza.casoId(),
                traza.eventos().stream()
                        .map(e ->
                                e.secuencia() == secuencia
                                        ? new Evento(
                                                e.secuencia(),
                                                e.instante(),
                                                tipo,
                                                e.contexto(),
                                                datos)
                                        : e)
                        .toList());
    }
}
