package io.github.physanto.opsecengine.oraculos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Criterio;
import io.github.physanto.opsecengine.dominio.caso.EstrategiaLocalizacion;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.mapa.MapaApp;
import io.github.physanto.opsecengine.dominio.mapa.Objetivo;
import io.github.physanto.opsecengine.dominio.mapa.Ventana;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
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
import java.util.Set;
import org.junit.jupiter.api.Test;

class OraculoCanariosEnConsolaTest {
    private static final String MARCA = "K7Q2CANARIO39";
    private static final String VALOR = "prefijo-" + MARCA + "-sufijo";
    private final OraculoContrasenaEnConsola oraculo = new OraculoContrasenaEnConsola();

    @Test
    void rastreaUnFragmentoDelPayloadEnAmbosFlujosSinCopiarSecretos() throws Exception {
        List<Hallazgo> hallazgos = oraculo.evaluar(positiva(), contexto("CANARIO39", MARCA));
        assertThat(hallazgos).hasSize(2);
        assertThat(hallazgos)
                .extracting(Hallazgo::evidencia)
                .containsExactly(List.of(1L, 3L), List.of(1L, 4L));
        assertThat(hallazgos)
                .allSatisfy(h -> {
                    assertThat(h.oraculoId()).isEqualTo("CONTRASENA-EN-CONSOLA");
                    assertThat(h.cwe()).isEqualTo("CWE-532");
                    assertThat(h.confianza()).isEqualTo(Confianza.CONFIRMADO);
                    assertThat(h.explicacion())
                            .contains("canario", "paso 1")
                            .doesNotContain(MARCA, VALOR, "dato2");
                });
        assertThat(hallazgos.getFirst().explicacion()).contains("salida estándar");
        assertThat(hallazgos.getLast().explicacion()).contains("salida de error");
    }

    @Test
    void noActivaLaVersionCorregidaNiElCasoLimpioAunqueHayaMetadatos() throws Exception {
        assertThat(
                        oraculo.evaluar(
                                cargar("canario-contrasena-no-en-consola.jsonl"),
                                contexto("CANARIO39", MARCA)))
                .isEmpty();
        Traza limpia = new CargadorTrazas().cargarEjemploUnico(TrazaEjemplo.CASO_LIMPIO);
        assertThat(oraculo.evaluar(limpia, contexto(limpia.casoId(), MARCA))).isEmpty();
    }

    @Test
    void unCanarioDeUsuarioBusquedaOBypassNoEsUnaContrasena() throws Exception {
        for (String campo : List.of("Usuario", "Busqueda", "Bypass", "Compass", "Passenger")) {
            Traza traza =
                    datos(
                            positiva(),
                            1,
                            Map.of(
                                    "accion",
                                    "LLENAR",
                                    "objetivo",
                                    "nombre=" + campo,
                                    "valor",
                                    VALOR));
            traza = datos(traza, 2, Map.of("codigo", "OK", "componente", "javax.swing.JTextField"));
            assertThat(oraculo.evaluar(traza, contexto("CANARIO39", MARCA))).as(campo).isEmpty();
        }
    }

    @Test
    void noAdivinaCanariosConMetadatosAusentesMalTipadosOCasoDistinto() throws Exception {
        assertThatThrownBy(() -> contexto("CANARIO39", "ab"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(oraculo.evaluar(positiva(), contexto("CANARIO39", null))).isEmpty();
        assertThat(oraculo.evaluar(positiva(), contexto("OTRO", MARCA))).isEmpty();
        assertThat(oraculo.evaluar(positiva(), null)).isEmpty();
    }

    @Test
    void elTipoDeComponenteDebeCorresponderAlPasoQueEscribe() throws Exception {
        Traza traza = positiva();
        List<Evento> eventos =
                traza.eventos().stream()
                        .map(e ->
                                e.secuencia() == 2
                                        ? new Evento(
                                                e.secuencia(),
                                                e.instante(),
                                                e.tipo(),
                                                new ContextoEvento("CANARIO39", 99, null, false),
                                                e.datos())
                                        : e)
                        .toList();
        assertThat(oraculo.evaluar(new Traza("CANARIO39", eventos), contexto("CANARIO39", MARCA)))
                .isEmpty();
        assertThat(
                        oraculo.evaluar(
                                datos(traza, 2, Map.of("codigo", "OK", "componente", 42)),
                                contexto("CANARIO39", MARCA)))
                .isEmpty();
        assertThat(
                        oraculo.evaluar(
                                datos(
                                        traza,
                                        2,
                                        Map.of(
                                                "codigo",
                                                "ERROR",
                                                "componente",
                                                "javax.swing.JPasswordField")),
                                contexto("CANARIO39", MARCA)))
                .isEmpty();
    }

    @Test
    void conservaLaDeteccionCompletaPorTipoSinAtribuirLineasAnterioresALaEscritura()
            throws Exception {
        Traza traza = datos(positiva(), 0, Map.of("flujo", "SALIDA", "texto", VALOR));
        traza = datos(traza, 3, Map.of("flujo", "SALIDA", "texto", VALOR));
        assertThat(oraculo.evaluar(traza, null))
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.evidencia()).containsExactly(3L);
                    assertThat(h.explicacion())
                            .contains("campo de contraseña", "paso 1")
                            .doesNotContain(VALOR);
                });
    }

    @Test
    void reconoceNombresCamelCaseYCamposVisiblesSinCopiarElLocalizador() throws Exception {
        for (String objetivo :
                List.of(
                        "nombre=txtPassword",
                        "etiqueta=Contraseña",
                        "nombre=campoClave1",
                        "nombre=jPasswordField")) {
            Traza traza =
                    datos(
                            positiva(),
                            1,
                            Map.of("accion", "LLENAR", "objetivo", objetivo, "valor", VALOR));
            traza = datos(traza, 2, Map.of("codigo", "OK", "componente", "javax.swing.JTextField"));
            assertThat(oraculo.evaluar(traza, contexto("CANARIO39", MARCA)))
                    .as(objetivo)
                    .hasSize(2)
                    .allSatisfy(h -> assertThat(h.explicacion()).doesNotContain(objetivo));
        }
    }

    @Test
    void noRastreaUnaVariableCanarioQueNoSeHaSustituido() throws Exception {
        Traza traza =
                datos(
                        positiva(),
                        1,
                        Map.of(
                                "accion",
                                "LLENAR",
                                "objetivo",
                                "nombre=dato2",
                                "valor",
                                "prefijo-{canario}-sufijo"));
        traza = datos(traza, 3, Map.of("flujo", "SALIDA", "texto", "marca: {canario}"));
        assertThat(oraculo.evaluar(traza, contexto("CANARIO39", null))).isEmpty();
        assertThatThrownBy(() -> contexto("CANARIO39", "{canario}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usaElCanarioDelGenerador37YElEvaluadorNoDuplicaHallazgos() {
        Localizador campo =
                new Localizador(
                        List.of(new Criterio(EstrategiaLocalizacion.NOMBRE_COMPONENTE, "dato2")));
        Caso base =
                new Caso(
                        "BASE39",
                        null,
                        null,
                        List.of(
                                new Paso(1, Accion.LLENAR, campo, "valor-valido", null, null),
                                new Paso(
                                        2,
                                        Accion.PULSAR,
                                        Localizador.porTexto("Enviar"),
                                        null,
                                        null,
                                        null)),
                        null,
                        null,
                        null);
        var formulario =
                new AdaptadorValidacion.Formulario(
                        "LOGIN39",
                        base,
                        2,
                        List.of(new AdaptadorValidacion.Campo(1, "CONTRASENA")));
        var payload =
                new AdaptadorValidacion.Payload(
                        "PAYLOAD39",
                        "inicio-{canario}-fin",
                        Set.of("CONTRASENA"),
                        true,
                        List.of(oraculo.id()));
        var generador =
                new GeneradorValidacion(
                        new AdaptadorValidacion(List.of(formulario), List.of(payload)));
        Proyecto proyecto = proyecto();
        Caso caso =
                generador
                        .generar(
                                new MapaApp(
                                        Set.of(new Ventana("LOGIN39", "Login", "app.Login")),
                                        List.of()),
                                Objetivo.todaLaApp(),
                                proyecto)
                        .getFirst();
        String marca = caso.canario();
        Contexto ctx =
                Contexto.deCaso(
                        proyecto,
                        new Suite("ejemplo39", null, null, null, List.of(caso), null),
                        caso);
        List<Evento> eventos =
                List.of(
                        evento(
                                0,
                                TipoEvento.PASO_INICIO,
                                caso.id(),
                                1,
                                Map.of(
                                        "accion",
                                        "LLENAR",
                                        "objetivo",
                                        "nombre_componente=dato2",
                                        "valor",
                                        caso.pasos().getFirst().valor())),
                        evento(
                                1,
                                TipoEvento.PASO_FIN,
                                caso.id(),
                                1,
                                Map.of("codigo", "OK", "componente", "javax.swing.JPasswordField")),
                        evento(2, TipoEvento.PASO_INICIO, caso.id(), 2, Map.of("accion", "PULSAR")),
                        evento(
                                3,
                                TipoEvento.CONSOLA,
                                caso.id(),
                                2,
                                Map.of("flujo", "SALIDA", "texto", "fragmento=" + marca)),
                        evento(4, TipoEvento.PASO_FIN, caso.id(), 2, Map.of("codigo", "OK")));
        var sesion = new EvaluadorOraculos(List.of(oraculo)).iniciarCaso(ctx);
        eventos.forEach(sesion::registrarEvento);
        var resultado = sesion.terminar(null);
        assertThat(resultado.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(resultado.hallazgos())
                .singleElement()
                .satisfies(h -> assertThat(h.evidencia()).containsExactly(0L, 3L));
    }

    private static Contexto contexto(String casoId, String marca) {
        Caso caso = new Caso(casoId, null, null, null, null, null, marca, null, null, Map.of());
        return Contexto.deCaso(
                proyecto(), new Suite("canarios", null, null, null, List.of(caso), null), caso);
    }

    private static Proyecto proyecto() {
        return new Proyecto(
                "clientes", Path.of("app.jar"), null, null, null, null, null, null, null, null);
    }

    private static Traza positiva() throws Exception {
        return cargar("canario-contrasena-en-consola.jsonl");
    }

    private static Traza cargar(String archivo) throws Exception {
        try (var lector =
                new InputStreamReader(
                        OraculoCanariosEnConsolaTest.class.getResourceAsStream(archivo),
                        StandardCharsets.UTF_8)) {
            return new CargadorTrazas().leer(lector, archivo).getFirst();
        }
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

    private static Evento evento(
            long secuencia, TipoEvento tipo, String casoId, int paso, Map<String, Object> datos) {
        return new Evento(
                secuencia,
                Instant.EPOCH,
                tipo,
                new ContextoEvento(casoId, paso, null, false),
                datos);
    }
}
