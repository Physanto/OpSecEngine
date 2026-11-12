package io.github.physanto.opsecengine.nucleo.ejecucion;

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
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.nucleo.VersionMotor;
import io.github.physanto.opsecengine.nucleo.evaluacion.EvaluacionService;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.nucleo.sesion.ArranqueApp;
import io.github.physanto.opsecengine.nucleo.sesion.SesionService;
import io.github.physanto.opsecengine.protocolo.Mensaje;
import io.github.physanto.opsecengine.protocolo.TipoMensaje;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import io.github.physanto.opsecengine.soporte.AgenteFalso;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Ejemplo de referencia: prueba un servicio del núcleo contra {@link AgenteFalso}, sin lanzar
 * ninguna aplicación. Cada sesión conecta un agente falso nuevo con el mismo guion.
 */
class EjecucionServiceTest {

    private static final Duration ESPERA = Duration.ofSeconds(5);
    private static final Proyecto PROYECTO =
            new Proyecto(
                    "clientes",
                    Path.of("no-existe", "app.jar"),
                    "app.Portada",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
    private static final EventoDto MENU_ABIERTO =
            new EventoDto(
                    41,
                    1_793_287_800_123L,
                    "VENTANA_ABIERTA",
                    Map.of("ventanaId", "v2", "clase", "app.Menu", "titulo", "Menú"));

    private final List<AgenteFalso> agentes = new CopyOnWriteArrayList<>();
    private final ArranqueApp conAgenteFalso =
            (proyecto, puerto, token) -> {
                AgenteFalso agente = new AgenteFalso();
                agente.cuandoPaso(texto("Avanzar"))
                        .emitir(MENU_ABIERTO)
                        .responder(new ResultadoPasoDto(CodigoResultado.OK, null, 12));
                agente.cuandoPaso(texto("Romper"))
                        .responder(
                                new ResultadoPasoDto(CodigoResultado.ERROR, "fallo del driver", 3));
                agentes.add(agente);
                agente.conectar(puerto, token);
                return agente::close;
            };

    @AfterEach
    void cerrarAgentes() {
        agentes.forEach(AgenteFalso::close);
    }

    @Test
    void unCasoOkCumpleYUnoConNoEncontradoQuedaNoConcluyente() throws Exception {
        Caso ok = caso("OK", pulsar(1, "Avanzar"));
        Caso noEncontrado = caso("FALTA", pulsar(1, "Inexistente"), pulsar(2, "Avanzar"));

        ResultadoSuite resultado = ejecutar(List.of(), ok, noEncontrado);

        ResultadoCaso primero = resultado.casos().get(0);
        assertThat(primero.veredicto().estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(primero.traza().eventos())
                .extracting(Evento::tipo)
                .containsExactly(
                        TipoEvento.PASO_INICIO, TipoEvento.VENTANA_ABIERTA, TipoEvento.PASO_FIN);
        assertThat(primero.traza().eventos())
                .extracting(Evento::secuencia)
                .containsExactly(0L, 1L, 2L);
        assertThat(primero.traza().eventos())
                .allSatisfy(evento -> {
                    assertThat(evento.contexto().casoId()).isEqualTo("OK");
                    assertThat(evento.contexto().numeroPaso()).isEqualTo(1);
                });
        Evento inicio = primero.traza().eventos().get(0);
        assertThat(inicio.datos())
                .containsEntry("accion", "PULSAR")
                .containsEntry("objetivo", "texto=Avanzar")
                .containsEntry("modo", "EVENTOS");
        assertThat(primero.traza().eventos().get(2).datos())
                .containsEntry("codigo", "OK")
                .containsEntry("duracionMs", 12L);

        ResultadoCaso segundo = resultado.casos().get(1);
        assertThat(segundo.veredicto().estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(segundo.veredicto().motivo())
                .startsWith("El paso 1 (PULSAR texto=Inexistente) terminó con NO_ENCONTRADO");
        assertThat(segundo.traza().deTipo(TipoEvento.PASO_INICIO))
                .as("el paso 2 no se ejecuta después de un paso fallido")
                .hasSize(1);
    }

    @Test
    void abreUnaSesionNuevaPorCasoYLaCierra() throws Exception {
        ejecutar(List.of(), caso("A", pulsar(1, "Avanzar")), caso("B", pulsar(1, "Avanzar")));

        assertThat(agentes).hasSize(2);
        for (AgenteFalso agente : agentes) {
            assertThat(agente.esperarFin(ESPERA)).isTrue();
            assertThat(agente.mensajesRecibidos())
                    .extracting(Mensaje::getTipo)
                    .containsExactly(TipoMensaje.EJECUTAR_PASO, TipoMensaje.CERRAR);
        }
    }

    @Test
    void siElAgenteRespondeErrorElCasoQuedaNoConcluyenteConElMotivo() throws Exception {
        ResultadoSuite resultado = ejecutar(List.of(), caso("ROTO", pulsar(1, "Romper")));

        ResultadoCaso caso = resultado.casos().get(0);
        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(caso.veredicto().motivo()).endsWith("terminó con ERROR: fallo del driver");
        assertThat(caso.traza().primero(TipoEvento.PASO_FIN))
                .get()
                .extracting(evento -> evento.texto("codigo").orElseThrow())
                .isEqualTo("ERROR");
    }

    @Test
    void losOraculosDecidenElVeredictoConLaTrazaArmada() throws Exception {
        ResultadoSuite resultado =
                ejecutar(List.of(new OraculoVentanas()), caso("OK", pulsar(1, "Avanzar")));

        ResultadoCaso caso = resultado.casos().get(0);
        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(caso.veredicto().hallazgos())
                .singleElement()
                .extracting(Hallazgo::evidencia)
                .isEqualTo(List.of(1L));
    }

    @Test
    void unCasoConRolNoSeEjecutaTodavia() throws Exception {
        Caso conRol =
                new Caso("A1", null, "admin", List.of(pulsar(1, "Avanzar")), null, null, null);

        ResultadoSuite resultado = ejecutar(List.of(), conRol);

        assertThat(resultado.casos().get(0).veredicto().motivo()).contains("rol admin");
        assertThat(agentes).isEmpty();
    }

    @Test
    void capturarPideLaImagenLaGuardaYRegistraElEvento(@TempDir Path resultados) throws Exception {
        Paso captura = new Paso(2, Accion.CAPTURAR, Localizador.porTexto("Menú"), null, null, null);
        ArranqueApp conCarpeta =
                new ArranqueApp() {
                    @Override
                    public Closeable arrancar(Proyecto proyecto, int puerto, String token)
                            throws IOException {
                        return conAgenteFalso.arrancar(proyecto, puerto, token);
                    }

                    @Override
                    public Optional<Path> carpetaResultados() {
                        return Optional.of(resultados);
                    }
                };

        ResultadoSuite resultado =
                ejecutar(conCarpeta, List.of(), caso("C/01", pulsar(1, "Avanzar"), captura));

        ResultadoCaso caso = resultado.casos().get(0);
        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(caso.traza().eventos())
                .extracting(Evento::tipo)
                .containsSubsequence(
                        TipoEvento.PASO_INICIO, TipoEvento.CAPTURA, TipoEvento.PASO_FIN);
        Evento evento = caso.traza().primero(TipoEvento.CAPTURA).orElseThrow();
        assertThat(evento.datos())
                .containsEntry("archivo", "capturas/C_01-2.png")
                .containsEntry("formato", "PNG");
        assertThat(evento.contexto().numeroPaso()).isEqualTo(2);
        assertThat(resultados.resolve("capturas").resolve("C_01-2.png"))
                .hasBinaryContent(AgenteFalso.imagenPng());
        assertThat(agentes.get(0).mensajesRecibidos())
                .extracting(Mensaje::getTipo)
                .contains(TipoMensaje.CAPTURAR)
                .containsOnlyOnce(TipoMensaje.EJECUTAR_PASO);
        assertThat(
                        agentes.get(0)
                                .esperarMensaje(TipoMensaje.CAPTURAR, ESPERA)
                                .orElseThrow()
                                .getCuerpo()
                                .get("ventana")
                                .asText())
                .isEqualTo("Menú");
    }

    @Test
    void capturarSinCarpetaDeResultadosTerminaConError() throws Exception {
        Paso captura = new Paso(1, Accion.CAPTURAR, null, null, null, null);

        ResultadoSuite resultado = ejecutar(List.of(), caso("SIN", captura));

        ResultadoCaso caso = resultado.casos().get(0);
        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(caso.veredicto().motivo()).contains("CAPTURAR", "carpeta de resultados");
        assertThat(caso.traza().deTipo(TipoEvento.CAPTURA)).isEmpty();
    }

    @Test
    void armaElManifiestoYLosTiemposDeLaSuite() throws Exception {
        ResultadoSuite resultado = ejecutar(List.of(), caso("OK", pulsar(1, "Avanzar")));

        assertThat(resultado.nombreSuite()).isEqualTo("suite-prueba");
        assertThat(resultado.manifiesto().versionMotor()).isEqualTo(VersionMotor.motor());
        assertThat(resultado.manifiesto().hashJar()).isEqualTo(EjecucionService.HASH_NO_DISPONIBLE);
        assertThat(resultado.manifiesto().hashSuite()).startsWith("sha256:").hasSize(71);
        assertThat(resultado.fin()).isAfterOrEqualTo(resultado.inicio());
    }

    private ResultadoSuite ejecutar(List<Oraculo> oraculos, Caso... casos) {
        return ejecutar(conAgenteFalso, oraculos, casos);
    }

    private ResultadoSuite ejecutar(ArranqueApp arranque, List<Oraculo> oraculos, Caso... casos) {
        EvaluacionService evaluacion =
                new EvaluacionService(
                        new RegistroExtensiones(oraculos, List.of(), List.of(), List.of()));
        try (SesionService sesiones = new SesionService(arranque, ESPERA, ESPERA)) {
            Suite suite = new Suite("suite-prueba", "1", null, null, List.of(casos), null);
            return new EjecucionService(PROYECTO, sesiones, evaluacion).ejecutar(suite);
        }
    }

    private static Caso caso(String id, Paso... pasos) {
        return new Caso(id, null, null, List.of(pasos), null, null, null);
    }

    private static Paso pulsar(int orden, String texto) {
        return new Paso(orden, Accion.PULSAR, Localizador.porTexto(texto), null, null, null);
    }

    private static Predicate<PasoDto> texto(String texto) {
        return paso ->
                paso.getLocalizador() != null
                        && paso.getLocalizador().getCriterios().get(0).getValor().equals(texto);
    }

    /** Oráculo de prueba: marca cada ventana abierta como hallazgo. */
    private static final class OraculoVentanas implements Oraculo {

        @Override
        public String id() {
            return "VENTANAS";
        }

        @Override
        public Momento momento() {
            return Momento.EVENTO;
        }

        @Override
        public boolean aplica(Caso caso, Contexto ctx) {
            return true;
        }

        @Override
        public List<Hallazgo> evaluar(Traza traza, Contexto ctx) {
            return traza.deTipo(TipoEvento.VENTANA_ABIERTA).stream()
                    .map(evento ->
                            new Hallazgo(
                                    id(),
                                    null,
                                    Confianza.CONFIRMADO,
                                    "Ventana abierta",
                                    "Se abrió una ventana",
                                    List.of(evento.secuencia())))
                    .toList();
        }
    }
}
