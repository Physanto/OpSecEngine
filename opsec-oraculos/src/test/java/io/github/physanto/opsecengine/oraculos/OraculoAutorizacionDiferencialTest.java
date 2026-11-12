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
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class OraculoAutorizacionDiferencialTest {
    private final OraculoAutorizacionDiferencial oraculo = new OraculoAutorizacionDiferencial();
    private final Map<String, List<String>> politica = Map.of("Eliminar Persona", List.of("admin"));

    @Test
    void detectaF2EnLaSuiteDeTrazasOficialesSinConfundirAdminConAnonimo() throws Exception {
        var cargador = new CargadorTrazas();
        Traza anonimo = cargador.cargarEjemploUnico(TrazaEjemplo.N3_LOCAL_SIN_LOGIN);
        Traza admin = cargador.cargarEjemploUnico(TrazaEjemplo.A1_ADMIN_ELIMINA);
        var resultado =
                new EvaluadorOraculos(List.of(oraculo))
                        .evaluarSuite(contexto(politica, anonimo, admin));
        assertThat(resultado.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(resultado.hallazgos())
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.oraculoId()).isEqualTo("AUTORIZACION-DIFERENCIAL");
                    assertThat(h.cwe()).isEqualTo("CWE-862");
                    assertThat(h.confianza()).isEqualTo(Confianza.PROBABLE);
                    assertThat(h.evidencia()).containsExactly(11L);
                    assertThat(h.explicacion()).contains("N3").doesNotContain("DELETE", "jdbc:");
                });
        assertThat(oraculo.evaluar(null, contexto(politica, admin))).isEmpty();
    }

    @Test
    void laDenegacionCorregidaYElIntentoDePulsarNoDemuestranAcceso() {
        Traza corregida =
                traza(
                        "N",
                        null,
                        false,
                        TipoEvento.PASO_INICIO,
                        Map.of("accion", "PULSAR", "objetivo", "texto=Eliminar Persona"),
                        TipoEvento.DIALOGO,
                        Map.of("texto", "Acceso denegado"),
                        TipoEvento.PASO_FIN,
                        Map.of("codigo", "OK"));
        assertThat(oraculo.evaluar(null, contexto(politica, corregida))).isEmpty();
    }

    @Test
    void confirmaSoloElEfectoSqlDeLaAccionNombradaEnElMismoPaso() {
        Traza ejecucion =
                traza(
                        "C",
                        "cliente",
                        true,
                        TipoEvento.PASO_INICIO,
                        Map.of("accion", "PULSAR", "objetivo", "texto=Eliminar Persona"),
                        TipoEvento.SQL,
                        Map.of("operacion", "DELETE", "filasAfectadas", 1));
        assertThat(oraculo.evaluar(null, contexto(politica, ejecucion)))
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.confianza()).isEqualTo(Confianza.CONFIRMADO);
                    assertThat(h.evidencia()).containsExactly(0L, 1L);
                });
        Evento sqlOtroPaso =
                new Evento(
                        1,
                        Instant.EPOCH,
                        TipoEvento.SQL,
                        new ContextoEvento("C", 2, "cliente", true),
                        Map.of("operacion", "DELETE", "filasAfectadas", 1));
        assertThat(
                        oraculo.evaluar(
                                null,
                                contexto(
                                        politica,
                                        new Traza(
                                                "C",
                                                List.of(
                                                        ejecucion.eventos().getFirst(),
                                                        sqlOtroPaso)))))
                .isEmpty();
    }

    @Test
    void noInfiereSesionAPartirDelRolNiRolAPartirDelCaso() {
        for (String rol : new String[] {null, "admin", "cliente"}) {
            Traza sinSesion = ventana("C", rol, false, "Eliminar persona");
            assertThat(oraculo.evaluar(null, contexto(politica, sinSesion))).hasSize(1);
        }
        assertThat(
                        oraculo.evaluar(
                                null,
                                contexto(politica, ventana("C", null, true, "Eliminar persona"))))
                .hasSize(1);
        assertThat(
                        oraculo.evaluar(
                                null,
                                contexto(
                                        politica, ventana("C", "admin", true, "Eliminar persona"))))
                .isEmpty();
    }

    @Test
    void respetaPoliticaParcialListaVaciaYNombresSinCoincidenciaParcial() {
        assertThat(
                        oraculo.evaluar(
                                null,
                                contexto(
                                        politica,
                                        ventana(
                                                "C",
                                                "cliente",
                                                true,
                                                "Eliminar persona adicional"))))
                .isEmpty();
        assertThat(
                        oraculo.evaluar(
                                null,
                                contexto(
                                        Map.of("Eliminar persona", List.of()),
                                        ventana("C", "admin", true, "  ELIMINAR PERSONA  "))))
                .hasSize(1);
        assertThat(
                        oraculo.evaluar(
                                null, contexto(politica, ventana("C", "cliente", true, "Portada"))))
                .isEmpty();
    }

    @Test
    void sinPoliticaComparaContextosSinAsignarPrivilegiosNiDeclararFallo() {
        Traza cliente = ventana("C", "cliente", true, "Operación");
        Traza admin = ventana("A", "admin", true, "Operación");
        var resultado =
                new EvaluadorOraculos(List.of(oraculo))
                        .evaluarSuite(contexto(Map.of(), cliente, admin));
        assertThat(resultado.estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(resultado.hallazgos())
                .hasSize(2)
                .allSatisfy(h -> assertThat(h.confianza()).isEqualTo(Confianza.INFO));
        assertThat(oraculo.evaluar(null, contexto(Map.of(), cliente))).isEmpty();
        assertThat(
                        oraculo.evaluar(
                                null,
                                contexto(
                                        Map.of(),
                                        cliente,
                                        ventana("D", "cliente", true, "Operación"))))
                .isEmpty();
    }

    @Test
    void ignoraErroresConsultasEventosAjenosYNoConfirmaFilasInvalidas() {
        for (Map<String, Object> datos :
                List.<Map<String, Object>>of(
                        Map.of("operacion", "DELETE", "error", "denegado"),
                        Map.of("operacion", "DELETE", "error", false),
                        Map.of("operacion", "SELECT", "filasAfectadas", 1))) {
            assertThat(
                            oraculo.evaluar(
                                    null,
                                    contexto(
                                            politica,
                                            traza(
                                                    "C",
                                                    "cliente",
                                                    true,
                                                    TipoEvento.PASO_INICIO,
                                                    Map.of(
                                                            "accion",
                                                            "PULSAR",
                                                            "objetivo",
                                                            "texto=Eliminar Persona"),
                                                    TipoEvento.SQL,
                                                    datos))))
                    .isEmpty();
        }
        for (Object filas : List.of(0, -1, 1.5, "1", Double.NaN)) {
            assertThat(
                            oraculo.evaluar(
                                    null,
                                    contexto(
                                            politica,
                                            traza(
                                                    "C",
                                                    "cliente",
                                                    true,
                                                    TipoEvento.PASO_INICIO,
                                                    Map.of(
                                                            "accion",
                                                            "PULSAR",
                                                            "objetivo",
                                                            "texto=Eliminar Persona"),
                                                    TipoEvento.SQL,
                                                    Map.of(
                                                            "operacion",
                                                            "DELETE",
                                                            "filasAfectadas",
                                                            filas)))))
                    .singleElement()
                    .satisfies(h -> assertThat(h.confianza()).isEqualTo(Confianza.PROBABLE));
        }
        Traza ajena = new Traza("C", ventana("OTRO", null, false, "Eliminar persona").eventos());
        assertThat(oraculo.evaluar(null, contexto(politica, ajena))).isEmpty();
    }

    @Test
    void firestoreNoConfirmaYLasVentanasRepetidasNoDuplicanHallazgos() {
        Traza firestore =
                traza(
                        "C",
                        "cliente",
                        true,
                        TipoEvento.PASO_INICIO,
                        Map.of("accion", "PULSAR", "objetivo", "texto=Eliminar Persona"),
                        TipoEvento.FIRESTORE,
                        Map.of("operacion", "delete", "ruta", "datos/secreto"),
                        TipoEvento.VENTANA_ABIERTA,
                        Map.of("titulo", "Eliminar persona"));
        assertThat(oraculo.evaluar(null, contexto(politica, firestore)))
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.confianza()).isEqualTo(Confianza.PROBABLE);
                    assertThat(h.explicacion()).doesNotContain("datos/secreto");
                });
        assertThat(oraculo.evaluar(null, contexto(politica))).isEmpty();
    }

    @Test
    void noAsociaBackendTardioNiEventosSinNombreDeOperacion() {
        for (TipoEvento limite : List.of(TipoEvento.PASO_FIN, TipoEvento.PASO_INICIO)) {
            Traza tardia =
                    traza(
                            "C",
                            "cliente",
                            true,
                            TipoEvento.PASO_INICIO,
                            Map.of("accion", "PULSAR", "objetivo", "texto=Eliminar Persona"),
                            limite,
                            Map.of("codigo", "OK", "accion", "LLENAR"),
                            TipoEvento.SQL,
                            Map.of("operacion", "DELETE", "filasAfectadas", 1));
            assertThat(oraculo.evaluar(null, contexto(politica, tardia))).isEmpty();
        }
        Traza sinNombre =
                traza(
                        "C",
                        "cliente",
                        true,
                        TipoEvento.VENTANA_ABIERTA,
                        Map.of("titulo", 5),
                        TipoEvento.PASO_INICIO,
                        Map.of("accion", "PULSAR", "objetivo", "nombre=Eliminar Persona"),
                        TipoEvento.SQL,
                        Map.of("operacion", "DELETE", "filasAfectadas", 1));
        assertThat(oraculo.evaluar(null, contexto(politica, sinNombre))).isEmpty();
        Contexto vacio = contexto(politica);
        assertThat(
                        oraculo.evaluar(
                                null,
                                Contexto.deSuite(
                                        vacio.proyecto(),
                                        vacio.suite(),
                                        List.of(
                                                ventana(
                                                        "AJENA",
                                                        null,
                                                        false,
                                                        "Eliminar persona")))))
                .isEmpty();
    }

    @Test
    void estaRegistradoSoloParaSuiteYNoConservaEstado() {
        assertThat(oraculo.momento()).isEqualTo(Momento.SUITE);
        assertThat(ServiceLoader.load(Oraculo.class).stream())
                .extracting(ServiceLoader.Provider::type)
                .contains(OraculoAutorizacionDiferencial.class);
        assertThat(oraculo.evaluar(null, null)).isEmpty();
        assertThat(
                        oraculo.evaluar(
                                null,
                                contexto(politica, ventana("C", null, false, "Eliminar persona"))))
                .hasSize(1);
        assertThat(oraculo.evaluar(null, contexto(politica))).isEmpty();
    }

    private static Traza ventana(String id, String rol, boolean sesion, String titulo) {
        return traza(id, rol, sesion, TipoEvento.VENTANA_ABIERTA, Map.of("titulo", titulo));
    }

    private static Traza traza(String id, String rol, boolean sesion, Object... pares) {
        var eventos = new java.util.ArrayList<Evento>();
        for (int i = 0; i < pares.length; i += 2) {
            @SuppressWarnings("unchecked")
            Map<String, Object> datos = (Map<String, Object>) pares[i + 1];
            eventos.add(
                    new Evento(
                            i / 2,
                            Instant.EPOCH,
                            (TipoEvento) pares[i],
                            new ContextoEvento(id, 1, rol, sesion),
                            datos));
        }
        return new Traza(id, eventos);
    }

    private static Contexto contexto(Map<String, List<String>> politica, Traza... trazas) {
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
                        politica,
                        null);
        List<Caso> casos =
                java.util.Arrays.stream(trazas)
                        .map(t -> new Caso(t.casoId(), null, "admin", null, null, null, null))
                        .toList();
        return Contexto.deSuite(
                proyecto, new Suite("diferencial", null, null, null, casos, null), List.of(trazas));
    }
}
