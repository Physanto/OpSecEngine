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

class OraculosCanarioBackendTest {
    private final Oraculo sql = new OraculoSqliCanario();
    private final Oraculo firestore = new OraculoFirestoreRutaCanario();
    private static final String MARCA = "K7Q2";

    @Test
    void c13DaNoCumpleYDetectaF4SinExponerElPayload() throws Exception {
        Traza traza = new CargadorTrazas().cargarEjemploUnico(TrazaEjemplo.C13_SQLI_LOGIN);
        var sesion =
                new EvaluadorOraculos(List.of(sql, firestore)).iniciarCaso(contexto("C13", MARCA));
        traza.eventos().forEach(sesion::registrarEvento);
        var resultado = sesion.terminar(null);
        assertThat(resultado.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(resultado.hallazgos())
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.oraculoId()).isEqualTo("SQLI-CANARIO");
                    assertThat(h.cwe()).isEqualTo("CWE-89");
                    assertThat(h.confianza()).isEqualTo(Confianza.PROBABLE);
                    assertThat(h.evidencia()).containsExactly(9L, 14L);
                    assertThat(h.explicacion())
                            .doesNotContain(MARCA, "SELECT", "cualquiera", "usuario");
                });
    }

    @Test
    void f4CorregidoUsaParametrosYElCasoLimpioNoActiva() throws Exception {
        Traza vulnerable = new CargadorTrazas().cargarEjemploUnico(TrazaEjemplo.C13_SQLI_LOGIN);
        Traza corregida =
                reemplazar(
                        vulnerable,
                        14,
                        Map.of(
                                "tipoSentencia",
                                "PREPARED",
                                "sql",
                                "SELECT rol FROM usuario WHERE nombre = ? AND clave = ?",
                                "parametros",
                                List.of("' OR '1'='1' -- K7Q2", "cualquiera")));
        var sesion = new EvaluadorOraculos(List.of(sql)).iniciarCaso(contexto("C13", MARCA));
        corregida.eventos().forEach(sesion::registrarEvento);
        assertThat(sesion.terminar(null).estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        Traza limpia = new CargadorTrazas().cargarEjemploUnico(TrazaEjemplo.CASO_LIMPIO);
        for (Oraculo oraculo : List.of(sql, firestore)) {
            assertThat(oraculo.evaluar(limpia, contexto(limpia.casoId(), MARCA))).isEmpty();
        }
    }

    @Test
    void firestoreRastreaLaRutaPeroNoLosCampos() throws Exception {
        Traza positiva = cargar("canario-en-ruta-firestore.jsonl");
        assertThat(firestore.evaluar(positiva, contexto("CANARIO42", MARCA)))
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.oraculoId()).isEqualTo("FIRESTORE-RUTA-CANARIO");
                    assertThat(h.cwe()).isEqualTo("CWE-89");
                    assertThat(h.confianza()).isEqualTo(Confianza.PROBABLE);
                    assertThat(h.evidencia()).containsExactly(0L, 2L);
                    assertThat(h.explicacion()).doesNotContain(MARCA, "personas/", "secreto");
                });
        Traza negativa = cargar("canario-no-en-ruta-firestore.jsonl");
        assertThat(firestore.evaluar(negativa, contexto("CANARIO42", MARCA))).isEmpty();
        assertThat(sql.evaluar(positiva, contexto("CANARIO42", MARCA))).isEmpty();
    }

    @Test
    void exigeDeclaracionEscrituraPreviaYCoincidenciaExactaEnElDestino() {
        for (Oraculo oraculo : List.of(sql, firestore)) {
            Evento destino = destino(oraculo, 1, MARCA);
            assertThat(oraculo.evaluar(new Traza("C", List.of(destino)), contexto("C", MARCA)))
                    .isEmpty();
            Traza tardia =
                    new Traza("C", List.of(destino(oraculo, 0, MARCA), entrada(1, "C", MARCA)));
            assertThat(oraculo.evaluar(tardia, contexto("C", MARCA))).isEmpty();
            Traza distinta =
                    new Traza("C", List.of(entrada(0, "C", MARCA), destino(oraculo, 1, "k7q2")));
            assertThat(oraculo.evaluar(distinta, contexto("C", MARCA))).isEmpty();
            Traza declarada = new Traza("C", List.of(entrada(0, "C", "prefijo-" + MARCA), destino));
            assertThat(oraculo.evaluar(declarada, contexto("C", MARCA))).hasSize(1);
            assertThat(oraculo.evaluar(declarada, contexto("C", null))).isEmpty();
            assertThat(oraculo.evaluar(declarada, null)).isEmpty();
            assertThat(oraculo.evaluar(null, contexto("C", MARCA))).isEmpty();
        }
    }

    @Test
    void ignoraMetadatosInvalidosPlantillasYCasosAjenos() {
        for (Oraculo oraculo : List.of(sql, firestore)) {
            Traza traza =
                    new Traza("C", List.of(entrada(0, "C", MARCA), destino(oraculo, 1, MARCA)));
            for (Object marca :
                    List.of(42, List.of(MARCA), "ab", "    ", "{canario}", "{payload}")) {
                assertThat(oraculo.evaluar(traza, contexto("C", marca))).isEmpty();
            }
            assertThat(oraculo.evaluar(traza, contexto("OTRO", MARCA))).isEmpty();
            assertThat(
                            oraculo.evaluar(
                                    new Traza(
                                            "C",
                                            List.of(
                                                    entrada(0, "OTRO", MARCA),
                                                    destino(oraculo, 1, MARCA))),
                                    contexto("C", MARCA)))
                    .isEmpty();
            Evento ajeno = destino(oraculo, 1, MARCA);
            ajeno =
                    new Evento(
                            1,
                            Instant.EPOCH,
                            ajeno.tipo(),
                            new ContextoEvento("OTRO", 2, null, false),
                            ajeno.datos());
            assertThat(
                            oraculo.evaluar(
                                    new Traza("C", List.of(entrada(0, "C", MARCA), ajeno)),
                                    contexto("C", MARCA)))
                    .isEmpty();
        }
    }

    @Test
    void noConfundeValoresParametrosNiErroresConElTextoSql() {
        for (Map<String, Object> datos :
                List.<Map<String, Object>>of(
                        Map.of("tipoSentencia", "PREPARED", "sql", MARCA),
                        Map.of(
                                "tipoSentencia",
                                "STATEMENT",
                                "sql",
                                "SELECT 1",
                                "parametros",
                                List.of(MARCA)),
                        Map.of("tipoSentencia", "STATEMENT", "sql", "SELECT 1", "error", MARCA),
                        Map.of("sql", MARCA),
                        Map.of("tipoSentencia", "STATEMENT", "sql", 42))) {
            Traza traza =
                    new Traza(
                            "C",
                            List.of(entrada(0, "C", MARCA), evento(1, TipoEvento.SQL, "C", datos)));
            assertThat(sql.evaluar(traza, contexto("C", MARCA))).isEmpty();
        }
    }

    @Test
    void laPropagacionNoSeDescartaPorUnErrorNiSeConfirmaPorFilasAfectadas() {
        for (Oraculo oraculo : List.of(sql, firestore)) {
            Evento base = destino(oraculo, 1, MARCA);
            var datos = new java.util.HashMap<String, Object>(base.datos());
            datos.put("error", "fallo del backend");
            datos.put("filasAfectadas", 1);
            Traza traza =
                    new Traza(
                            "C",
                            List.of(entrada(0, "C", MARCA), evento(1, base.tipo(), "C", datos)));
            assertThat(oraculo.evaluar(traza, contexto("C", MARCA)))
                    .singleElement()
                    .satisfies(h -> assertThat(h.confianza()).isEqualTo(Confianza.PROBABLE));
        }
    }

    @Test
    void noTrataOtrasAccionesComoEscrituraYNuncaConservaElCanarioEntreCasos() {
        for (Oraculo oraculo : List.of(sql, firestore)) {
            Traza traza =
                    new Traza(
                            "C",
                            List.of(
                                    evento(
                                            0,
                                            TipoEvento.PASO_INICIO,
                                            "C",
                                            Map.of("accion", "PULSAR", "valor", MARCA)),
                                    destino(oraculo, 1, MARCA)));
            assertThat(oraculo.evaluar(traza, contexto("C", MARCA))).isEmpty();
            assertThat(
                            oraculo.evaluar(
                                    new Traza(
                                            "C",
                                            List.of(
                                                    entrada(0, "C", MARCA),
                                                    destino(oraculo, 1, MARCA))),
                                    contexto("C", MARCA)))
                    .hasSize(1);
            assertThat(
                            oraculo.evaluar(
                                    new Traza("C", List.of(destino(oraculo, 1, MARCA))),
                                    contexto("C", MARCA)))
                    .isEmpty();
        }
    }

    @Test
    void ambosSeRegistranComoOraculosDeCaso() {
        assertThat(ServiceLoader.load(Oraculo.class).stream())
                .extracting(ServiceLoader.Provider::type)
                .contains(OraculoSqliCanario.class, OraculoFirestoreRutaCanario.class);
        for (Oraculo oraculo : List.of(sql, firestore)) {
            assertThat(oraculo.momento()).isEqualTo(Momento.CASO);
            Contexto contexto = contexto("C", MARCA);
            assertThat(oraculo.aplica(contexto.caso(), contexto)).isTrue();
            assertThat(oraculo.aplica(null, null)).isFalse();
            var sesion = new EvaluadorOraculos(List.of(oraculo)).iniciarCaso(contexto);
            sesion.registrarEvento(entrada(0, "C", MARCA));
            assertThat(sesion.registrarEvento(destino(oraculo, 1, MARCA)).hallazgos()).isEmpty();
            assertThat(sesion.terminar(null).hallazgos()).hasSize(1);
        }
    }

    private static Contexto contexto(String id, Object marca) {
        Proyecto proyecto =
                new Proyecto(
                        "canarios",
                        Path.of("app.jar"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        Caso caso =
                new Caso(
                        id,
                        null,
                        null,
                        null,
                        null,
                        null,
                        marca == null ? Map.of() : Map.of("validacion", Map.of("canario", marca)));
        return Contexto.deCaso(
                proyecto, new Suite("canarios", null, null, null, List.of(caso), null), caso);
    }

    private static Evento entrada(long secuencia, String caso, String valor) {
        return evento(
                secuencia,
                TipoEvento.PASO_INICIO,
                caso,
                Map.of("accion", "LLENAR", "valor", valor));
    }

    private static Evento destino(Oraculo oraculo, long secuencia, String marca) {
        return oraculo instanceof OraculoSqliCanario
                ? evento(
                        secuencia,
                        TipoEvento.SQL,
                        "C",
                        Map.of("tipoSentencia", "STATEMENT", "sql", "SELECT '" + marca + "'"))
                : evento(
                        secuencia,
                        TipoEvento.FIRESTORE,
                        "C",
                        Map.of("operacion", "get", "ruta", "personas/" + marca));
    }

    private static Evento evento(
            long secuencia, TipoEvento tipo, String caso, Map<String, Object> datos) {
        return new Evento(
                secuencia, Instant.EPOCH, tipo, new ContextoEvento(caso, 1, null, false), datos);
    }

    private static Traza reemplazar(Traza traza, long secuencia, Map<String, Object> datos) {
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

    private static Traza cargar(String nombre) throws Exception {
        try (var lector =
                new InputStreamReader(
                        OraculosCanarioBackendTest.class.getResourceAsStream(nombre),
                        StandardCharsets.UTF_8)) {
            return new CargadorTrazas().leer(lector, nombre).getFirst();
        }
    }
}
