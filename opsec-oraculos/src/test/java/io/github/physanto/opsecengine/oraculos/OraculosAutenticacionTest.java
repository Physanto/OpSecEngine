package io.github.physanto.opsecengine.oraculos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.IntentoLogin;
import io.github.physanto.opsecengine.dominio.caso.PoliticaAutenticacion;
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
import io.github.physanto.opsecengine.soporte.CargadorTrazas;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class OraculosAutenticacionTest {
    private final OraculoCredencialesDebiles debiles = new OraculoCredencialesDebiles();
    private final OraculoSinBloqueo bloqueo = new OraculoSinBloqueo();

    @Test
    void detectaF1ConLaParejaDeSoporteYSinCopiarCredenciales() throws Exception {
        Traza traza = cargar("f1-credenciales-debiles.jsonl");
        var sesion =
                new EvaluadorOraculos(List.of(debiles)).iniciarCaso(contexto(traza, soporte(true)));
        traza.eventos().forEach(sesion::registrarEvento);
        var resultado = sesion.terminar(null);
        assertThat(resultado.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(resultado.hallazgos())
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.oraculoId()).isEqualTo("CREDENCIALES-DEBILES");
                    assertThat(h.cwe()).isEqualTo("CWE-521");
                    assertThat(h.confianza()).isEqualTo(Confianza.CONFIRMADO);
                    assertThat(h.evidencia()).containsExactly(0L, 2L, 4L, 5L);
                    assertThat(h.explicacion()).doesNotContain("soporte", "soporte123");
                });
        assertThat(
                        debiles.evaluar(
                                cargar("f1-credenciales-rechazadas.jsonl"),
                                contexto(traza, soporte(true))))
                .isEmpty();
        assertThat(debiles.evaluar(traza, contexto(traza, soporte(false)))).isEmpty();
    }

    @Test
    void detectaUnaContrasenaVaciaSoloSiEstaDeclaradaYRealmenteSeAcepta() throws Exception {
        Traza base = cargar("f1-credenciales-debiles.jsonl");
        Traza vacia =
                datos(base, 2, Map.of("accion", "LLENAR", "objetivo", "nombre=Clave", "valor", ""));
        PoliticaAutenticacion politica =
                politica(
                        List.of(new IntentoLogin(1, 2, 3, "soporte", "", true)),
                        2,
                        Duration.ofSeconds(5));
        assertThat(debiles.evaluar(vacia, contexto(vacia, politica))).hasSize(1);
        assertThat(debiles.evaluar(base, contexto(base, politica))).isEmpty();
    }

    @Test
    void elOkDelBotonUnRolOUnDialogoNoConfirmanAutenticacion() throws Exception {
        Traza traza = cargar("f1-credenciales-debiles.jsonl");
        Evento original = traza.eventos().get(5);
        for (TipoEvento tipo :
                List.of(TipoEvento.DIALOGO, TipoEvento.PASO_FIN, TipoEvento.SESION)) {
            Evento falso =
                    new Evento(
                            original.secuencia(),
                            original.instante(),
                            tipo,
                            new ContextoEvento("F1", 3, "admin", false),
                            Map.of("texto", "Bienvenido", "codigo", "OK", "activa", false));
            assertThat(debiles.evaluar(reemplazar(traza, 5, falso), contexto(traza, soporte(true))))
                    .isEmpty();
        }
    }

    @Test
    void exigeEscriturasCorrectasSinSobrescriturasNiSesionPrevia() throws Exception {
        Traza traza = cargar("f1-credenciales-debiles.jsonl");
        for (long secuencia : List.of(1L, 3L)) {
            Traza fallida = datos(traza, secuencia, Map.of("codigo", "ERROR"));
            assertThat(debiles.evaluar(fallida, contexto(traza, soporte(true)))).isEmpty();
        }
        Traza mismoCampo =
                datos(
                        traza,
                        2,
                        Map.of(
                                "accion",
                                "LLENAR",
                                "objetivo",
                                "nombre=Usuario",
                                "valor",
                                "soporte123"));
        assertThat(debiles.evaluar(mismoCampo, contexto(traza, soporte(true)))).isEmpty();
        Evento envio = traza.eventos().get(4);
        Evento activo =
                new Evento(
                        envio.secuencia(),
                        envio.instante(),
                        envio.tipo(),
                        new ContextoEvento("F1", 3, "admin", true),
                        envio.datos());
        assertThat(debiles.evaluar(reemplazar(traza, 4, activo), contexto(traza, soporte(true))))
                .isEmpty();
        Traza sobrescrita =
                intercalar(
                        traza,
                        4,
                        TipoEvento.PASO_INICIO,
                        99,
                        Map.of("accion", "LLENAR", "objetivo", "nombre=Clave", "valor", "otra"));
        assertThat(debiles.evaluar(sobrescrita, contexto(traza, soporte(true)))).isEmpty();
    }

    @Test
    void detectaAceptacionTrasNRechazosSeguidosSinBloqueoNiRetardo() throws Exception {
        Traza traza = cargar("login-sin-bloqueo.jsonl");
        assertThat(bloqueo.evaluar(traza, contexto(traza, repetidos())))
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.oraculoId()).isEqualTo("SIN-BLOQUEO");
                    assertThat(h.cwe()).isEqualTo("CWE-307");
                    assertThat(h.confianza()).isEqualTo(Confianza.PROBABLE);
                    assertThat(h.evidencia()).contains(5L, 12L, 19L);
                    assertThat(h.explicacion()).doesNotContain("incorrecta0", "valida");
                });
        assertThat(bloqueo.evaluar(cargar("login-con-bloqueo.jsonl"), contexto(traza, repetidos())))
                .isEmpty();
        PoliticaAutenticacion masFallos =
                politica(repetidos().intentos(), 3, Duration.ofSeconds(5));
        assertThat(bloqueo.evaluar(traza, contexto(traza, masFallos))).isEmpty();
    }

    @Test
    void unRetardoAlMenosIgualAlUmbralYLosTiemposIncoherentesDescartanLaDeteccion()
            throws Exception {
        Traza traza = cargar("login-sin-bloqueo.jsonl");
        PoliticaAutenticacion politica = politica(repetidos().intentos(), 2, Duration.ofMillis(70));
        assertThat(bloqueo.evaluar(traza, contexto(traza, politica))).isEmpty();
        var eventos =
                traza.eventos().stream()
                        .map(e ->
                                e.secuencia() == 18
                                        ? new Evento(
                                                e.secuencia(),
                                                e.instante().minusSeconds(5),
                                                e.tipo(),
                                                e.contexto(),
                                                e.datos())
                                        : e)
                        .toList();
        assertThat(
                        bloqueo.evaluar(
                                new Traza(traza.casoId(), eventos), contexto(traza, repetidos())))
                .isEmpty();
    }

    @Test
    void unCambioDeUsuarioOUnRechazoDesconocidoRompeLaSerie() throws Exception {
        Traza traza = cargar("login-sin-bloqueo.jsonl");
        Traza desconocida = datos(traza, 12, Map.of("texto", "Error de red"));
        assertThat(bloqueo.evaluar(desconocida, contexto(traza, repetidos()))).isEmpty();
        Traza otro =
                datos(
                        traza,
                        7,
                        Map.of("accion", "LLENAR", "objetivo", "nombre=Usuario", "valor", "otro"));
        var intentos = new ArrayList<>(repetidos().intentos());
        intentos.set(1, new IntentoLogin(4, 5, 6, "otro", "incorrecta1", false));
        assertThat(
                        bloqueo.evaluar(
                                otro, contexto(otro, politica(intentos, 2, Duration.ofSeconds(5)))))
                .isEmpty();
    }

    @Test
    void unEnvioNoDeclaradoUnBloqueoOUnReinicioIntermedioRompenLaSerie() throws Exception {
        Traza traza = cargar("login-sin-bloqueo.jsonl");
        for (TipoEvento tipo :
                List.of(
                        TipoEvento.PASO_INICIO,
                        TipoEvento.SESION,
                        TipoEvento.APP_TERMINADA,
                        TipoEvento.DIALOGO)) {
            Traza intermedia =
                    intercalar(
                            traza,
                            14,
                            tipo,
                            99,
                            Map.of(
                                    "accion",
                                    "PULSAR",
                                    "activa",
                                    false,
                                    "texto",
                                    "Cuenta bloqueada"));
            assertThat(bloqueo.evaluar(intermedia, contexto(intermedia, repetidos()))).isEmpty();
        }
    }

    @Test
    void losReintentosLosDatosContradictoriosYLaAceptacionTardiaNoPruebanUnLogin()
            throws Exception {
        Traza traza = cargar("f1-credenciales-debiles.jsonl");
        Traza reintento =
                intercalar(traza, 5, TipoEvento.PASO_INICIO, 3, Map.of("accion", "PULSAR"));
        assertThat(debiles.evaluar(reintento, contexto(traza, soporte(true)))).isEmpty();
        Traza tardia =
                intercalar(traza, 5, TipoEvento.PASO_INICIO, 99, Map.of("accion", "CAPTURAR"));
        assertThat(debiles.evaluar(tardia, contexto(traza, soporte(true)))).isEmpty();
        Traza contradictoria =
                intercalar(
                        traza, 6, TipoEvento.DIALOGO, 3, Map.of("texto", "Credenciales inválidas"));
        assertThat(debiles.evaluar(contradictoria, contexto(traza, soporte(true)))).isEmpty();
    }

    @Test
    void exigePoliticaDelCasoYNoConservaEstado() throws Exception {
        Traza traza = cargar("f1-credenciales-debiles.jsonl");
        for (Oraculo oraculo : List.of(debiles, bloqueo)) {
            assertThat(oraculo.evaluar(null, contexto(traza, soporte(true)))).isEmpty();
            assertThat(oraculo.evaluar(traza, null)).isEmpty();
            assertThat(oraculo.evaluar(traza, contexto(traza, null))).isEmpty();
            Traza ajena = new Traza("OTRO", traza.eventos());
            assertThat(oraculo.evaluar(ajena, contexto(traza, soporte(true)))).isEmpty();
        }
        assertThat(debiles.evaluar(traza, contexto(traza, soporte(true)))).hasSize(1);
        assertThat(
                        debiles.evaluar(
                                cargar("f1-credenciales-rechazadas.jsonl"),
                                contexto(traza, soporte(true))))
                .isEmpty();
    }

    @Test
    void ambosSeRegistranComoOraculosDeCasoYSeEvaluaSoloAlTerminar() throws Exception {
        assertThat(ServiceLoader.load(Oraculo.class).stream())
                .extracting(ServiceLoader.Provider::type)
                .contains(OraculoSinBloqueo.class, OraculoCredencialesDebiles.class);
        Traza traza = cargar("f1-credenciales-debiles.jsonl");
        for (Oraculo oraculo : List.of(debiles, bloqueo)) {
            assertThat(oraculo.momento()).isEqualTo(Momento.CASO);
            var sesion =
                    new EvaluadorOraculos(List.of(oraculo))
                            .iniciarCaso(contexto(traza, soporte(true)));
            traza.eventos()
                    .forEach(e -> assertThat(sesion.registrarEvento(e).hallazgos()).isEmpty());
        }
    }

    @Test
    void validaPasosUmbralesYSeparacionDeLosPayloads() {
        assertThatThrownBy(() -> new IntentoLogin(1, 1, 3, "u", "c", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntentoLogin(1, 2, 2, "u", "c", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntentoLogin(1, 2, 3, null, "c", true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> politica(soporte(true).intentos(), 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> politica(soporte(true).intentos(), 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        politica(
                                List.of(
                                        soporte(true).intentos().getFirst(),
                                        soporte(true).intentos().getFirst()),
                                2,
                                Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaSesionDeOtroUsuarioOConUsuarioMalTipadoNoSeAtribuyeAlPayload() throws Exception {
        Traza traza = cargar("f1-credenciales-debiles.jsonl");
        for (Object usuario : List.of("otro", 42)) {
            Traza ajena = datos(traza, 5, Map.of("activa", true, "usuario", usuario));
            assertThat(debiles.evaluar(ajena, contexto(traza, soporte(true)))).isEmpty();
        }
    }

    private static PoliticaAutenticacion soporte(boolean debil) {
        return politica(
                List.of(new IntentoLogin(1, 2, 3, "soporte", "soporte123", debil)),
                2,
                Duration.ofSeconds(5));
    }

    private static PoliticaAutenticacion repetidos() {
        return politica(
                List.of(
                        new IntentoLogin(1, 2, 3, "usuario", "incorrecta0", false),
                        new IntentoLogin(4, 5, 6, "usuario", "incorrecta1", false),
                        new IntentoLogin(7, 8, 9, "usuario", "valida", false)),
                2,
                Duration.ofSeconds(5));
    }

    private static PoliticaAutenticacion politica(
            List<IntentoLogin> intentos, int n, Duration retardo) {
        return new PoliticaAutenticacion(
                intentos,
                n,
                retardo.toMillis(),
                List.of(new Senal(TipoEvento.DIALOGO, Map.of("texto", "Credenciales inválidas"))),
                List.of(new Senal(TipoEvento.DIALOGO, Map.of("texto", "Cuenta bloqueada"))));
    }

    private static Contexto contexto(Traza traza, PoliticaAutenticacion politica) {
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
        Caso caso =
                new Caso(
                        traza.casoId(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        politica,
                        Map.of());
        return Contexto.deCaso(
                proyecto, new Suite("login", null, null, null, List.of(caso), null), caso);
    }

    private static Traza datos(Traza traza, long secuencia, Map<String, Object> datos) {
        Evento e =
                traza.eventos().stream()
                        .filter(v -> v.secuencia() == secuencia)
                        .findFirst()
                        .orElseThrow();
        return reemplazar(
                traza,
                secuencia,
                new Evento(e.secuencia(), e.instante(), e.tipo(), e.contexto(), datos));
    }

    private static Traza reemplazar(Traza traza, long secuencia, Evento nuevo) {
        return new Traza(
                traza.casoId(),
                traza.eventos().stream().map(e -> e.secuencia() == secuencia ? nuevo : e).toList());
    }

    private static Traza intercalar(
            Traza traza, int posicion, TipoEvento tipo, int paso, Map<String, Object> datos) {
        var eventos = new ArrayList<Evento>();
        for (int i = 0; i < traza.eventos().size(); i++) {
            Evento e = traza.eventos().get(i);
            if (i == posicion) {
                eventos.add(
                        new Evento(
                                eventos.size(),
                                e.instante(),
                                tipo,
                                new ContextoEvento(traza.casoId(), paso, null, false),
                                datos));
            }
            eventos.add(
                    new Evento(eventos.size(), e.instante(), e.tipo(), e.contexto(), e.datos()));
        }
        return new Traza(traza.casoId(), eventos);
    }

    private static Traza cargar(String nombre) throws Exception {
        try (var lector =
                new InputStreamReader(
                        OraculosAutenticacionTest.class.getResourceAsStream(nombre),
                        StandardCharsets.UTF_8)) {
            return new CargadorTrazas().leer(lector, nombre).getFirst();
        }
    }
}
