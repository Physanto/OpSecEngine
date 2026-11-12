package io.github.physanto.opsecengine.oraculos;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.OperacionesSensibles;
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
import java.util.Set;
import org.junit.jupiter.api.Test;

class OraculoSinSesionTest {
    private final OraculoSinSesion oraculo = new OraculoSinSesion();

    @Test
    void detectaF2YRespetaLaSesionDeAdminEnLasTrazasOficiales() throws Exception {
        Traza vulnerable = new CargadorTrazas().cargarEjemploUnico(TrazaEjemplo.N3_LOCAL_SIN_LOGIN);
        var resultado = evaluar(vulnerable);
        assertThat(resultado.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(resultado.hallazgos())
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.oraculoId()).isEqualTo("SIN-SESION");
                    assertThat(h.cwe()).isEqualTo("CWE-306");
                    assertThat(h.confianza()).isEqualTo(Confianza.CONFIRMADO);
                    assertThat(h.evidencia()).containsExactly(16L);
                    assertThat(h.explicacion()).doesNotContain("persona", "jdbc:", "id = 3");
                });
        for (TrazaEjemplo ejemplo :
                List.of(TrazaEjemplo.A1_ADMIN_ELIMINA, TrazaEjemplo.CASO_LIMPIO)) {
            assertThat(evaluar(new CargadorTrazas().cargarEjemploUnico(ejemplo)).estado())
                    .isEqualTo(EstadoVeredicto.CUMPLE);
        }
    }

    @Test
    void detectaF3YNoActivaCuandoLaVersionCorregidaDeniegaElAcceso() {
        Evento ventana =
                evento(
                        0,
                        TipoEvento.VENTANA_ABIERTA,
                        false,
                        Map.of(
                                "clase",
                                "io.github.physanto.opsecengine.practica.gui.EliminarPersona"));
        Evento eliminar =
                evento(
                        1,
                        TipoEvento.SQL,
                        false,
                        Map.of(
                                "operacion",
                                "DELETE",
                                "sql",
                                "DELETE FROM persona WHERE uid='P002'",
                                "filasAfectadas",
                                1));
        assertThat(evaluar(new Traza("CASO", List.of(ventana, eliminar))).estado())
                .isEqualTo(EstadoVeredicto.NO_CUMPLE);
        Evento denegacion =
                evento(
                        1,
                        TipoEvento.DIALOGO,
                        false,
                        Map.of(
                                "texto",
                                "Acceso denegado: esta opción requiere una sesión de administrador."));
        assertThat(evaluar(new Traza("CASO", List.of(ventana, denegacion))).estado())
                .isEqualTo(EstadoVeredicto.CUMPLE);
    }

    @Test
    void elRolNoAutenticaYUnLoginPosteriorNoOcultaUnaEscrituraAnterior() {
        Evento antes = evento(0, TipoEvento.SQL, false, Map.of("operacion", "INSERT"));
        Evento login = evento(1, TipoEvento.SESION, true, Map.of("activa", true));
        Evento despues = evento(2, TipoEvento.SQL, true, Map.of("operacion", "DELETE"));
        assertThat(
                        oraculo.evaluar(
                                new Traza("CASO", List.of(antes, login, despues)),
                                contexto("CASO", null)))
                .singleElement()
                .satisfies(h -> assertThat(h.evidencia()).containsExactly(0L));
        assertThat(evaluar(new Traza("CASO", List.of(despues))).estado())
                .isEqualTo(EstadoVeredicto.CUMPLE);
    }

    @Test
    void respetaOperacionesConfiguradasIncluidasLasListasVacias() {
        Traza traza =
                new Traza(
                        "CASO",
                        List.of(
                                evento(0, TipoEvento.SQL, false, Map.of("operacion", "merge")),
                                evento(1, TipoEvento.FIRESTORE, false, Map.of("operacion", "add")),
                                evento(2, TipoEvento.SQL, false, Map.of("operacion", "DELETE"))));
        assertThat(
                        oraculo.evaluar(
                                traza,
                                contexto(
                                        "CASO",
                                        new OperacionesSensibles(Set.of("MERGE"), Set.of("add")))))
                .hasSize(2);
        Contexto desactivado = contexto("CASO", new OperacionesSensibles(Set.of(), Set.of()));
        assertThat(oraculo.aplica(desactivado.caso(), desactivado)).isFalse();
        assertThat(oraculo.evaluar(traza, desactivado)).isEmpty();
    }

    @Test
    void usaLaPrimeraPalabraSqlSinConfundirComentariosNiLiterales() {
        Traza traza =
                new Traza(
                        "CASO",
                        List.of(
                                evento(
                                        0,
                                        TipoEvento.SQL,
                                        false,
                                        Map.of(
                                                "sql",
                                                " -- comentario\n /* inicio */ delete from datos")),
                                evento(
                                        1,
                                        TipoEvento.SQL,
                                        false,
                                        Map.of("sql", "SELECT 'DELETE FROM datos'")),
                                evento(
                                        2,
                                        TipoEvento.SQL,
                                        false,
                                        Map.of("sql", "/* DELETE FROM datos */ SELECT 1")),
                                evento(
                                        3,
                                        TipoEvento.SQL,
                                        false,
                                        Map.of("sql", "/* comentario sin cierre")),
                                evento(4, TipoEvento.SQL, false, Map.of("sql", 5))));
        assertThat(oraculo.evaluar(traza, contexto("CASO", null)))
                .singleElement()
                .satisfies(h -> assertThat(h.evidencia()).containsExactly(0L));
    }

    @Test
    void firestoreDistingueEscriturasConsultasErroresYSesiones() {
        Traza traza =
                new Traza(
                        "CASO",
                        List.of(
                                evento(
                                        0,
                                        TipoEvento.FIRESTORE,
                                        false,
                                        Map.of("operacion", "delete", "ruta", "secreto/doc")),
                                evento(1, TipoEvento.FIRESTORE, false, Map.of("operacion", "get")),
                                evento(
                                        2,
                                        TipoEvento.FIRESTORE,
                                        false,
                                        Map.of("operacion", "set", "error", "PERMISSION_DENIED")),
                                evento(
                                        3,
                                        TipoEvento.FIRESTORE,
                                        true,
                                        Map.of("operacion", "update")),
                                evento(4, TipoEvento.FIRESTORE, false, Map.of("operacion", 7))));
        assertThat(oraculo.evaluar(traza, contexto("CASO", null)))
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.confianza()).isEqualTo(Confianza.PROBABLE);
                    assertThat(h.evidencia()).containsExactly(0L);
                    assertThat(h.explicacion()).doesNotContain("secreto/doc");
                });
    }

    @Test
    void noConfirmaEfectosAusentesInvalidosNiEstadosDeOtraOperacion() {
        for (Object filas : List.of(0, -1, 1.5, "1", Double.NaN, Double.POSITIVE_INFINITY)) {
            Traza traza =
                    new Traza(
                            "CASO",
                            List.of(
                                    evento(
                                            0,
                                            TipoEvento.SQL,
                                            false,
                                            Map.of("operacion", "UPDATE", "filasAfectadas", filas)),
                                    evento(
                                            1,
                                            TipoEvento.ESTADO,
                                            false,
                                            Map.of("momento", "DESPUES", "existe", false))));
            assertThat(oraculo.evaluar(traza, contexto("CASO", null)))
                    .singleElement()
                    .satisfies(h -> assertThat(h.confianza()).isEqualTo(Confianza.PROBABLE));
        }
    }

    @Test
    void descartaErroresYEventosDeOtrosCasosSinConservarEstado() {
        Evento ajeno =
                new Evento(
                        0,
                        Instant.EPOCH,
                        TipoEvento.SQL,
                        new ContextoEvento("OTRO", 1, null, false),
                        Map.of("operacion", "DELETE"));
        Traza traza =
                new Traza(
                        "CASO",
                        List.of(
                                ajeno,
                                evento(
                                        1,
                                        TipoEvento.SQL,
                                        false,
                                        Map.of("operacion", "DELETE", "error", "rechazado")),
                                evento(
                                        2,
                                        TipoEvento.SQL,
                                        false,
                                        Map.of("operacion", "DELETE", "error", false))));
        assertThat(oraculo.evaluar(traza, contexto("CASO", null))).isEmpty();
        assertThat(oraculo.evaluar(null, contexto("CASO", null))).isEmpty();
        assertThat(oraculo.evaluar(traza, null)).isEmpty();
        assertThat(oraculo.evaluar(new Traza("CASO", List.of()), contexto("CASO", null))).isEmpty();
    }

    @Test
    void estaRegistradoYSeEvaluaSoloAlFinalDelCaso() {
        assertThat(oraculo.momento()).isEqualTo(Momento.CASO);
        assertThat(ServiceLoader.load(Oraculo.class).stream())
                .extracting(ServiceLoader.Provider::type)
                .contains(OraculoSinSesion.class);
        var sesion = new EvaluadorOraculos(List.of(oraculo)).iniciarCaso(contexto("CASO", null));
        assertThat(
                        sesion.registrarEvento(
                                        evento(
                                                0,
                                                TipoEvento.SQL,
                                                false,
                                                Map.of("operacion", "DELETE", "filasAfectadas", 1)))
                                .hallazgos())
                .isEmpty();
        assertThat(sesion.terminar(null).hallazgos()).hasSize(1);
    }

    private io.github.physanto.opsecengine.dominio.resultado.Veredicto evaluar(Traza traza) {
        var sesion =
                new EvaluadorOraculos(List.of(oraculo)).iniciarCaso(contexto(traza.casoId(), null));
        traza.eventos().forEach(sesion::registrarEvento);
        return sesion.terminar(null);
    }

    private static Evento evento(
            long secuencia, TipoEvento tipo, boolean sesion, Map<String, Object> datos) {
        return new Evento(
                secuencia,
                Instant.EPOCH,
                tipo,
                new ContextoEvento("CASO", 1, "admin", sesion),
                datos);
    }

    private static Contexto contexto(String id, OperacionesSensibles sensibles) {
        Proyecto proyecto =
                new Proyecto(
                        "clientes",
                        Path.of("app.jar"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        sensibles,
                        null,
                        null);
        Caso caso = new Caso(id, null, null, null, null, null, null);
        return Contexto.deCaso(
                proyecto, new Suite("sin-sesion", null, null, null, List.of(caso), null), caso);
    }
}
