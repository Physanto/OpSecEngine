package io.github.physanto.opsecengine.oraculos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.PoliticaValidacion;
import io.github.physanto.opsecengine.dominio.caso.Senal;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.ContextoEvento;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.soporte.CargadorTrazas;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class OraculoEntradaInvalidaAceptadaTest {
    private static final OraculoEntradaInvalidaAceptada ORACULO =
            new OraculoEntradaInvalidaAceptada();

    private static Veredicto evaluar(Traza traza, PoliticaValidacion politica) {
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
        Caso caso = new Caso("VAL38", null, null, null, null, null, null, politica, null, null);
        return ORACULO.evaluarResultado(
                traza,
                Contexto.deCaso(
                        proyecto,
                        new Suite("validacion", null, null, null, List.of(caso), null),
                        caso));
    }

    @Test
    void aplicaSoloALosCasosConPoliticaDeValidacion() {
        Caso sin = new Caso("A", null, null, null, null, null, null);
        Caso con = new Caso("B", null, null, null, null, null, null, politica(), null, null);
        assertThat(ORACULO.aplica(sin, null)).isFalse();
        assertThat(ORACULO.aplica(con, null)).isTrue();
    }

    @Test
    void laAceptacionActivaElHallazgoConEvidenciaSinRevelarElValor() throws Exception {
        var resultado = evaluar(aceptada(), politica());
        assertThat(resultado.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(resultado.hallazgos())
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.oraculoId()).isEqualTo("ENTRADA-INVALIDA-ACEPTADA");
                    assertThat(h.cwe()).isEqualTo("CWE-20");
                    assertThat(h.confianza()).isEqualTo(Confianza.PROBABLE);
                    assertThat(h.evidencia()).containsExactly(0L, 1L, 2L, 3L);
                    assertThat(h.explicacion()).doesNotContain("Admin");
                });
        assertThat(resultado.motivo()).isNull();
    }

    @Test
    void elControlNegativoRequiereRechazoExplicitoYEnvioTerminado() throws Exception {
        var resultado = evaluar(rechazada(), politica());
        assertThat(resultado.estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(resultado.hallazgos()).isEmpty();
        Traza sinRespuesta =
                new Traza(
                        "VAL38",
                        rechazada().eventos().stream().filter(e -> e.secuencia() != 3).toList());
        assertThat(evaluar(sinRespuesta, politica()).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        Traza envioFallido = datos(rechazada(), 4, Map.of("codigo", "ERROR"));
        assertThat(evaluar(envioFallido, politica()).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
    }

    @Test
    void losDatosAusentesYLasEscriturasFallidasNoSeConfundenConValidacionCorrecta()
            throws Exception {
        assertThat(evaluar(null, politica()).estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(evaluar(aceptada(), null).estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(evaluar(new Traza("OTRO", aceptada().eventos()), politica()).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        for (Map<String, Object> campos :
                List.<Map<String, Object>>of(
                        Map.of("accion", "LLENAR", "objetivo", "etiqueta=Usuario"),
                        Map.of("accion", "LLENAR", "objetivo", "etiqueta=Usuario", "valor", false),
                        Map.of(
                                "accion",
                                "LLENAR",
                                "objetivo",
                                "etiqueta=Usuario",
                                "valor",
                                "valido"))) {
            assertThat(evaluar(datos(aceptada(), 0, campos), politica()).estado())
                    .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        }
        assertThat(
                        evaluar(datos(aceptada(), 1, Map.of("codigo", "NO_ALCANZABLE")), politica())
                                .estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(evaluar(new Traza("VAL38", List.of()), politica()).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
    }

    @Test
    void unaAceptacionNoDesaparecePorUnMensajeDeRechazoOPorUnEnvioIncompleto() throws Exception {
        var incompleto = evaluar(datos(aceptada(), 4, Map.of("codigo", "ERROR")), politica());
        assertThat(incompleto.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(incompleto.motivo()).contains("finalización");
        List<Evento> eventos = new ArrayList<>(rechazada().eventos());
        eventos.add(evento(5, TipoEvento.SESION, 5, Map.of("activa", true)));
        assertThat(evaluar(new Traza("VAL38", eventos), politica()).estado())
                .isEqualTo(EstadoVeredicto.NO_CUMPLE);
    }

    @Test
    void descartaSenalesAnterioresYMarcaLaAceptacionTardiaComoAmbigua() throws Exception {
        List<Evento> eventos = new ArrayList<>();
        for (Evento e : aceptada().eventos()) {
            if (e.secuencia() != 3) {
                eventos.add(
                        new Evento(
                                e.secuencia() + 10,
                                e.instante(),
                                e.tipo(),
                                e.contexto(),
                                e.datos()));
            }
        }
        eventos.add(evento(1, TipoEvento.SESION, 5, Map.of("activa", true)));
        assertThat(evaluar(new Traza("VAL38", eventos), politica()).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        eventos = new ArrayList<>(rechazada().eventos());
        eventos.add(
                evento(
                        5,
                        TipoEvento.PASO_INICIO,
                        6,
                        Map.of("accion", "PULSAR", "objetivo", "texto=Otra acción")));
        eventos.add(evento(6, TipoEvento.SESION, 5, Map.of("activa", true)));
        assertThat(evaluar(new Traza("VAL38", eventos), politica()).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
    }

    @Test
    void noUsaSenalesDeOtroCasoOPasoNiIniciosDuplicados() throws Exception {
        for (ContextoEvento contexto :
                List.of(
                        new ContextoEvento("OTRO", 5, null, false),
                        new ContextoEvento("VAL38", 4, null, false),
                        ContextoEvento.fueraDeCaso())) {
            List<Evento> eventos = new ArrayList<>(aceptada().eventos());
            Evento anterior = eventos.get(3);
            eventos.set(
                    3,
                    new Evento(
                            anterior.secuencia(),
                            anterior.instante(),
                            anterior.tipo(),
                            contexto,
                            anterior.datos()));
            assertThat(evaluar(new Traza("VAL38", eventos), politica()).estado())
                    .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        }
        List<Evento> eventos = new ArrayList<>(aceptada().eventos());
        eventos.add(evento(5, TipoEvento.PASO_INICIO, 5, Map.of("accion", "PULSAR")));
        assertThat(evaluar(new Traza("VAL38", eventos), politica()).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
    }

    @Test
    void noAtribuyeAceptacionAlValorInvalidoSiElCampoFueSobrescrito() throws Exception {
        List<Evento> base = aceptada().eventos();
        List<Evento> eventos =
                List.of(
                        base.get(0),
                        base.get(1),
                        evento(
                                2,
                                TipoEvento.PASO_INICIO,
                                4,
                                Map.of(
                                        "accion",
                                        "LLENAR",
                                        "objetivo",
                                        "etiqueta=Usuario",
                                        "valor",
                                        "valido")),
                        evento(3, TipoEvento.PASO_FIN, 4, Map.of("codigo", "OK")),
                        evento(4, TipoEvento.PASO_INICIO, 5, base.get(2).datos()),
                        evento(5, TipoEvento.SESION, 5, Map.of("activa", true)),
                        evento(6, TipoEvento.PASO_FIN, 5, Map.of("codigo", "OK")));
        assertThat(evaluar(new Traza("VAL38", eventos), politica()).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
    }

    @Test
    void lasSenalesSonTipadasYNoAceptanOkComoPruebaDeAceptacion() {
        Senal numerica =
                new Senal(
                        TipoEvento.ESTADO,
                        Map.of("momento", "DESPUES", "cantidad", new BigDecimal("1.0")));
        assertThat(
                        numerica.coincide(
                                evento(
                                        1,
                                        TipoEvento.ESTADO,
                                        5,
                                        Map.of("momento", "DESPUES", "cantidad", 1))))
                .isTrue();
        assertThat(
                        numerica.coincide(
                                evento(
                                        1,
                                        TipoEvento.ESTADO,
                                        5,
                                        Map.of("momento", "ANTES", "cantidad", 1))))
                .isFalse();
        assertThat(
                        numerica.coincide(
                                evento(
                                        1,
                                        TipoEvento.ESTADO,
                                        5,
                                        Map.of("momento", "DESPUES", "cantidad", "1"))))
                .isFalse();
        assertThat(
                        numerica.coincide(
                                evento(
                                        1,
                                        TipoEvento.ESTADO,
                                        5,
                                        Map.of("momento", "DESPUES", "cantidad", Double.NaN))))
                .isFalse();
        Map<String, Object> condiciones = new HashMap<>(Map.of("activa", true));
        Senal aceptacion = new Senal(TipoEvento.SESION, condiciones);
        condiciones.put("activa", false);
        assertThat(aceptacion.coincide(evento(1, TipoEvento.SESION, 5, Map.of("activa", true))))
                .isTrue();
        assertThat(aceptacion.coincide(evento(1, TipoEvento.SESION, 5, Map.of("activa", "true"))))
                .isFalse();
        assertThat(aceptacion.coincide(evento(1, TipoEvento.SESION, 5, Map.of()))).isFalse();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Senal(TipoEvento.DIALOGO, Map.of("texto", List.of("error"))));
    }

    @Test
    void rechazaPoliticasVaciasOContradictorias() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new PoliticaValidacion(
                                5, 3, "", politica().aceptacion(), politica().rechazo()));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> new PoliticaValidacion(3, 5, "", List.of(), politica().rechazo()));
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new PoliticaValidacion(
                                3, 5, "", politica().aceptacion(), politica().aceptacion()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Senal(TipoEvento.DIALOGO, Map.of()));
    }

    @Test
    void seRegistraComoOraculoDelMotor() {
        assertThat(
                        ServiceLoader.load(Oraculo.class).stream()
                                .map(ServiceLoader.Provider::get)
                                .map(Oraculo::id)
                                .toList())
                .contains(OraculoEntradaInvalidaAceptada.ID);
    }

    private static PoliticaValidacion politica() {
        return new PoliticaValidacion(
                3,
                5,
                "",
                List.of(new Senal(TipoEvento.SESION, Map.of("activa", true))),
                List.of(
                        new Senal(
                                TipoEvento.DIALOGO,
                                Map.of("texto", "Debe escribir usuario y contraseña."))));
    }

    private static Traza aceptada() throws Exception {
        return cargar("validacion-entrada-aceptada.jsonl");
    }

    private static Traza rechazada() throws Exception {
        return cargar("validacion-entrada-rechazada.jsonl");
    }

    private static Traza cargar(String archivo) throws Exception {
        try (var lector =
                new InputStreamReader(
                        OraculoEntradaInvalidaAceptadaTest.class.getResourceAsStream(archivo),
                        StandardCharsets.UTF_8)) {
            return new CargadorTrazas().leer(lector, archivo).getFirst();
        }
    }

    private static Evento evento(
            long secuencia, TipoEvento tipo, int paso, Map<String, Object> datos) {
        return new Evento(
                secuencia,
                Instant.EPOCH,
                tipo,
                new ContextoEvento("VAL38", paso, null, false),
                datos);
    }

    private static Traza datos(Traza traza, long secuencia, Map<String, Object> datos) {
        return new Traza(
                traza.casoId(),
                traza.eventos().stream()
                        .map(e ->
                                e.secuencia() == secuencia
                                        ? new Evento(
                                                e.secuencia(),
                                                e.instante(),
                                                e.tipo(),
                                                e.contexto(),
                                                datos)
                                        : e)
                        .toList());
    }
}
