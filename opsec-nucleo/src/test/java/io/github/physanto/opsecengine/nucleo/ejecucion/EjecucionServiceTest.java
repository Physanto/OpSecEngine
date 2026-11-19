package io.github.physanto.opsecengine.nucleo.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.ConfigEjecucion;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.ModoAccion;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.OperacionesSensibles;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.configuracion.Rol;
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
import io.github.physanto.opsecengine.protocolo.CodecMensajes;
import io.github.physanto.opsecengine.protocolo.Mensaje;
import io.github.physanto.opsecengine.protocolo.TipoMensaje;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.ConfigGanchosDto;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import io.github.physanto.opsecengine.soporte.AgenteFalso;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final String CONTRASENA = "Secreta-2026";
    private static final Proyecto PROYECTO_CON_LOGIN =
            new Proyecto(
                    "clientes",
                    Path.of("no-existe", "app.jar"),
                    "app.Portada",
                    null,
                    null,
                    List.of(new Rol("admin", "Admin", CONTRASENA)),
                    List.of(
                            llenar(1, "Usuario", "{usuario}"),
                            llenar(2, "Contraseña", "clave:{contrasena}"),
                            pulsar(3, "Ingresar")),
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
                agente.cuandoPaso(texto("Usuario").or(texto("Contraseña")).or(texto("Ingresar")))
                        .responder(new ResultadoPasoDto(CodigoResultado.OK, null, 5));
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
    void ejecutaCadaPayloadEnUnaSesionYConUnResultadoIndependiente() {
        Caso plantilla =
                new Caso(
                        "DATOS",
                        "Entradas",
                        null,
                        List.of(llenar(1, "Usuario", "{payload}")),
                        null,
                        null,
                        Map.of("payloads", List.of("", "'", "texto largo")));
        ResultadoSuite resultado = ejecutar(List.of(), plantilla);
        assertThat(resultado.casos())
                .extracting(ResultadoCaso::casoId)
                .containsExactly("DATOS-P1", "DATOS-P2", "DATOS-P3");
        assertThat(resultado.casos())
                .allSatisfy(caso ->
                        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.CUMPLE));
        assertThat(agentes).hasSize(3);
        for (int i = 0; i < agentes.size(); i++) {
            String esperado = List.of("", "'", "texto largo").get(i);
            assertThat(agentes.get(i).mensajesRecibidos())
                    .filteredOn(mensaje -> mensaje.getTipo() == TipoMensaje.EJECUTAR_PASO)
                    .singleElement()
                    .satisfies(mensaje ->
                            assertThat(mensaje.getCuerpo().path("valor").asText())
                                    .isEqualTo(esperado));
        }
    }

    @Test
    void unPayloadInvalidoNoAbreNingunaSesion() {
        Caso plantilla =
                new Caso(
                        "DATOS",
                        null,
                        null,
                        List.of(llenar(1, "Usuario", "fijo")),
                        null,
                        null,
                        Map.of("payloads", List.of("x")));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ejecutar(List.of(), plantilla))
                .hasMessageContaining("falta {payload}");
        assertThat(agentes).isEmpty();
    }

    @Test
    void transmiteElModoOcultoDeLaSuiteAlArranqueDeCadaCaso() {
        List<Boolean> modos = new ArrayList<>();
        ArranqueApp arranque =
                new ArranqueApp() {
                    @Override
                    public Closeable arrancar(Proyecto proyecto, int puerto, String token)
                            throws IOException {
                        throw new AssertionError("Se debe transmitir el modo oculto");
                    }

                    @Override
                    public Closeable arrancar(
                            Proyecto proyecto, int puerto, String token, boolean oculto)
                            throws IOException {
                        modos.add(oculto);
                        return conAgenteFalso.arrancar(proyecto, puerto, token);
                    }
                };
        EvaluacionService evaluacion =
                new EvaluacionService(
                        new RegistroExtensiones(List.of(), List.of(), List.of(), List.of()));
        try (SesionService sesiones = new SesionService(arranque, ESPERA, ESPERA)) {
            for (boolean oculto : List.of(true, false)) {
                ConfigEjecucion config =
                        new ConfigEjecucion(ModoAccion.SEMANTICO, oculto, ESPERA, 0, 0L, 1);
                Suite suite =
                        new Suite(
                                "oculta",
                                "1",
                                null,
                                config,
                                List.of(
                                        caso("A", pulsar(1, "Avanzar")),
                                        caso("B", pulsar(1, "Avanzar"))),
                                null);
                assertThat(
                                new EjecucionService(PROYECTO, sesiones, evaluacion)
                                        .ejecutar(suite)
                                        .casos())
                        .allSatisfy(caso ->
                                assertThat(caso.veredicto().estado())
                                        .isEqualTo(EstadoVeredicto.CUMPLE));
            }
        }
        assertThat(modos).containsExactly(true, true, false, false);
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
                    .containsExactly(
                            TipoMensaje.CONFIG_GANCHOS,
                            TipoMensaje.EJECUTAR_PASO,
                            TipoMensaje.CERRAR);
        }
    }

    @Test
    void losFallosDelCasoSeActivanDespuesDelLoginEnUnSegundoConfigGanchos() throws Exception {
        Caso caso =
                new Caso(
                        "A1",
                        null,
                        "admin",
                        List.of(pulsar(1, "Avanzar")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of("SQL_EJECUTAR:java.sql.SQLException:sin red"));

        ejecutar(PROYECTO_CON_LOGIN, List.of(), caso);

        List<Mensaje> recibidos = agentes.get(0).mensajesRecibidos();
        CodecMensajes codec = new CodecMensajes();
        assertThat(recibidos)
                .extracting(Mensaje::getTipo)
                .startsWith(TipoMensaje.CONFIG_GANCHOS)
                .containsSubsequence(
                        TipoMensaje.CONFIG_GANCHOS,
                        TipoMensaje.EJECUTAR_PASO,
                        TipoMensaje.CONFIG_GANCHOS,
                        TipoMensaje.EJECUTAR_PASO);
        assertThat(codec.leerCuerpo(recibidos.get(0), ConfigGanchosDto.class).getFallosInyectados())
                .isEmpty();
        ConfigGanchosDto conFallos = null;
        int configs = 0;
        for (Mensaje m : recibidos) {
            if (m.getTipo() == TipoMensaje.CONFIG_GANCHOS && ++configs == 2) {
                conFallos = codec.leerCuerpo(m, ConfigGanchosDto.class);
            }
        }
        assertThat(conFallos.getFallosInyectados())
                .containsExactly("SQL_EJECUTAR:java.sql.SQLException:sin red");
    }

    @Test
    void configuraLosGanchosConLosSensiblesDelProyectoYElCanarioDelCaso() throws Exception {
        Proyecto conSensibles =
                new Proyecto(
                        "clientes",
                        Path.of("no-existe", "app.jar"),
                        "app.Portada",
                        null,
                        null,
                        null,
                        null,
                        new OperacionesSensibles(Set.of("UPDATE", "DELETE"), Set.of("delete")),
                        null,
                        null);
        Paso escribir = llenar(1, "Usuario", "{canario}' OR '1'='1");
        Caso caso = new Caso("C13", null, null, List.of(escribir), null, null, null);

        ResultadoSuite resultado = ejecutar(conSensibles, List.of(), caso);

        ConfigGanchosDto config =
                new CodecMensajes()
                        .leerCuerpo(
                                agentes.get(0).mensajesRecibidos().get(0), ConfigGanchosDto.class);
        assertThat(config.getSqlSensibles()).containsExactly("DELETE", "UPDATE");
        assertThat(config.getFirestoreSensibles()).containsExactly("delete");
        assertThat(config.getCanarios()).hasSize(1);
        String canario = config.getCanarios().get(0);
        assertThat(canario).startsWith("OPSEC").matches("^[^\\s{}]{4,}$");
        assertThat(resultado.casos().get(0).traza().deTipo(TipoEvento.PASO_INICIO))
                .extracting(e -> String.valueOf(e.datos().get("valor")))
                .anyMatch(v -> v.startsWith(canario + "'"));
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
    void unCasoConRolIniciaSesionAntesDeSusPasos() throws Exception {
        Caso conRol =
                new Caso("A1", null, "admin", List.of(pulsar(1, "Avanzar")), null, null, null);

        ResultadoSuite resultado = ejecutar(PROYECTO_CON_LOGIN, List.of(), conRol);

        ResultadoCaso caso = resultado.casos().get(0);
        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(agentes).singleElement();
        CodecMensajes codec = new CodecMensajes();
        List<String> valores = new ArrayList<>();
        for (Mensaje mensaje : agentes.get(0).mensajesRecibidos()) {
            if (mensaje.getTipo() == TipoMensaje.EJECUTAR_PASO) {
                valores.add(codec.leerCuerpo(mensaje, PasoDto.class).getValor());
            }
        }
        assertThat(valores)
                .as("el agente recibe las credenciales reales")
                .containsExactly("Admin", "clave:" + CONTRASENA, null, null);

        List<Evento> eventos = caso.traza().eventos();
        assertThat(eventos)
                .extracting(Evento::tipo)
                .containsExactly(
                        TipoEvento.PASO_INICIO,
                        TipoEvento.PASO_FIN,
                        TipoEvento.PASO_INICIO,
                        TipoEvento.PASO_FIN,
                        TipoEvento.PASO_INICIO,
                        TipoEvento.PASO_FIN,
                        TipoEvento.SESION,
                        TipoEvento.PASO_INICIO,
                        TipoEvento.VENTANA_ABIERTA,
                        TipoEvento.PASO_FIN);
        assertThat(eventos.subList(0, 6))
                .allSatisfy(evento -> {
                    assertThat(evento.contexto().numeroPaso()).isZero();
                    assertThat(evento.contexto().rol()).isNull();
                    assertThat(evento.contexto().sesionActiva()).isFalse();
                });
        assertThat(eventos.get(0).datos()).containsEntry("valor", "Admin");
        assertThat(eventos.get(2).datos())
                .containsEntry("valor", "clave:" + EjecucionService.VALOR_OCULTO);
        assertThat(eventos)
                .as("la contraseña no queda en la traza")
                .noneSatisfy(evento -> assertThat(evento.datos().toString()).contains(CONTRASENA));

        Evento sesion = eventos.get(6);
        assertThat(sesion.datos())
                .containsEntry("activa", true)
                .containsEntry("rol", "admin")
                .containsEntry("usuario", "Admin");
        assertThat(eventos.subList(6, eventos.size()))
                .allSatisfy(evento -> {
                    assertThat(evento.contexto().rol()).isEqualTo("admin");
                    assertThat(evento.contexto().sesionActiva()).isTrue();
                });
        assertThat(eventos.get(7).contexto().numeroPaso()).isEqualTo(1);
    }

    @Test
    void siElLoginFallaLosPasosDelCasoNoSeEjecutan() throws Exception {
        Proyecto loginRoto =
                new Proyecto(
                        "clientes",
                        Path.of("no-existe", "app.jar"),
                        "app.Portada",
                        null,
                        null,
                        PROYECTO_CON_LOGIN.roles(),
                        List.of(llenar(1, "Usuario", "{usuario}"), pulsar(2, "Entrar")),
                        null,
                        null,
                        null);
        Caso conRol =
                new Caso("A1", null, "admin", List.of(pulsar(1, "Avanzar")), null, null, null);

        ResultadoSuite resultado = ejecutar(loginRoto, List.of(), conRol);

        ResultadoCaso caso = resultado.casos().get(0);
        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(caso.veredicto().motivo())
                .startsWith(
                        "No se pudo iniciar sesión con el rol admin: El paso 2 del login (PULSAR"
                                + " texto=Entrar) terminó con NO_ENCONTRADO");
        assertThat(caso.traza().deTipo(TipoEvento.SESION)).isEmpty();
        assertThat(caso.traza().deTipo(TipoEvento.PASO_INICIO))
                .as("solo los dos pasos de login")
                .hasSize(2)
                .allSatisfy(evento -> assertThat(evento.contexto().numeroPaso()).isZero());
    }

    @Test
    void unCasoConRolSinPasosDeLoginNoSeEjecuta() throws Exception {
        Proyecto sinLogin =
                new Proyecto(
                        "clientes",
                        Path.of("no-existe", "app.jar"),
                        "app.Portada",
                        null,
                        null,
                        PROYECTO_CON_LOGIN.roles(),
                        null,
                        null,
                        null,
                        null);
        Caso conRol =
                new Caso("A1", null, "admin", List.of(pulsar(1, "Avanzar")), null, null, null);

        ResultadoSuite resultado = ejecutar(sinLogin, List.of(), conRol);

        assertThat(resultado.casos().get(0).veredicto().estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(resultado.casos().get(0).veredicto().motivo())
                .contains("rol admin", "no declara pasos de login");
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

    @Test
    void notificaElAvanceEnOrdenInclusoSiUnPasoFalla() {
        List<ProgresoEjecucion> avisos = new ArrayList<>();
        Caso ok = caso("OK", pulsar(1, "Avanzar"));
        Caso falla = caso("FALLA", pulsar(1, "Romper"), pulsar(2, "Avanzar"));

        ResultadoSuite resultado = ejecutarConObservador(PROYECTO, avisos::add, ok, falla);

        assertThat(avisos)
                .extracting(ProgresoEjecucion::tipo)
                .containsExactly(
                        ProgresoEjecucion.Tipo.SUITE_INICIO,
                        ProgresoEjecucion.Tipo.CASO_INICIO,
                        ProgresoEjecucion.Tipo.PASO_INICIO,
                        ProgresoEjecucion.Tipo.PASO_FIN,
                        ProgresoEjecucion.Tipo.CASO_FIN,
                        ProgresoEjecucion.Tipo.CASO_INICIO,
                        ProgresoEjecucion.Tipo.PASO_INICIO,
                        ProgresoEjecucion.Tipo.PASO_FIN,
                        ProgresoEjecucion.Tipo.CASO_FIN,
                        ProgresoEjecucion.Tipo.SUITE_FIN);
        assertThat(avisos)
                .allSatisfy(aviso -> {
                    assertThat(aviso.nombreSuite()).isEqualTo("suite-prueba");
                    assertThat(aviso.totalCasos()).isEqualTo(2);
                    assertThat(aviso.login()).isFalse();
                });
        assertThat(avisos.get(0).casoId()).isNull();
        assertThat(avisos.get(1).casoId()).isEqualTo("OK");
        assertThat(avisos.get(3).numeroPaso()).isEqualTo(1);
        assertThat(avisos.get(3).codigo()).isEqualTo(CodigoResultado.OK);
        assertThat(avisos.get(7).codigo()).isEqualTo(CodigoResultado.ERROR);
        assertThat(avisos.get(7).totalPasos()).isEqualTo(2);
        assertThat(resultado.casos().get(1).veredicto().estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
    }

    @Test
    void notificaCadaPasoDeLoginSinExponerCredenciales() {
        List<ProgresoEjecucion> avisos = new ArrayList<>();
        Caso conRol =
                new Caso("A1", null, "admin", List.of(pulsar(1, "Avanzar")), null, null, null);

        ejecutarConObservador(PROYECTO_CON_LOGIN, avisos::add, conRol);

        assertThat(avisos.stream().filter(ProgresoEjecucion::login))
                .extracting(ProgresoEjecucion::numeroPaso)
                .containsExactly(1, 1, 2, 2, 3, 3);
        assertThat(avisos.stream().filter(ProgresoEjecucion::login))
                .allSatisfy(aviso -> assertThat(aviso.totalPasos()).isEqualTo(3));
        assertThat(avisos.get(8).login()).isFalse();
        assertThat(avisos.get(8).numeroPaso()).isEqualTo(1);
        assertThat(avisos.get(8).totalPasos()).isEqualTo(1);
        assertThat(avisos.toString()).doesNotContain(CONTRASENA, "Admin", "clave:");
    }

    @Test
    void siFallaElLoginNotificaSuFinSinAnunciarLosPasosDelCaso() {
        List<ProgresoEjecucion> avisos = new ArrayList<>();
        Proyecto proyecto =
                new Proyecto(
                        "clientes",
                        PROYECTO.jar(),
                        null,
                        null,
                        null,
                        PROYECTO_CON_LOGIN.roles(),
                        List.of(pulsar(1, "Romper")),
                        null,
                        null,
                        null);
        Caso conRol =
                new Caso("A1", null, "admin", List.of(pulsar(1, "Avanzar")), null, null, null);

        ResultadoSuite resultado = ejecutarConObservador(proyecto, avisos::add, conRol);

        assertThat(avisos).hasSize(6);
        assertThat(avisos.get(2).login()).isTrue();
        assertThat(avisos.get(3).codigo()).isEqualTo(CodigoResultado.ERROR);
        assertThat(avisos.get(4).tipo()).isEqualTo(ProgresoEjecucion.Tipo.CASO_FIN);
        assertThat(avisos.get(5).tipo()).isEqualTo(ProgresoEjecucion.Tipo.SUITE_FIN);
        assertThat(resultado.casos().get(0).veredicto().estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
    }

    @Test
    void notificaLosCasosNoEjecutablesSinInventarPasos() {
        List<ProgresoEjecucion> avisos = new ArrayList<>();
        Caso sinRol =
                new Caso(
                        "SIN-ROL",
                        null,
                        "desconocido",
                        List.of(pulsar(1, "Avanzar")),
                        null,
                        null,
                        null);

        ejecutarConObservador(PROYECTO, avisos::add, sinRol);

        assertThat(avisos)
                .extracting(ProgresoEjecucion::tipo)
                .containsExactly(
                        ProgresoEjecucion.Tipo.SUITE_INICIO, ProgresoEjecucion.Tipo.CASO_INICIO,
                        ProgresoEjecucion.Tipo.CASO_FIN, ProgresoEjecucion.Tipo.SUITE_FIN);
        assertThat(agentes).isEmpty();
    }

    @Test
    void notificaUnaSuiteVaciaSinAbrirSesiones() {
        List<ProgresoEjecucion> avisos = new ArrayList<>();

        ejecutarConObservador(PROYECTO, avisos::add);

        assertThat(avisos)
                .extracting(ProgresoEjecucion::tipo)
                .containsExactly(
                        ProgresoEjecucion.Tipo.SUITE_INICIO, ProgresoEjecucion.Tipo.SUITE_FIN);
        assertThat(avisos).allSatisfy(aviso -> assertThat(aviso.totalCasos()).isZero());
        assertThat(agentes).isEmpty();
    }

    @Test
    void unObservadorQueFallaNoInterrumpeLaSuiteYRecibeLosAvisosEnElMismoHilo() {
        Thread hilo = Thread.currentThread();
        List<ProgresoEjecucion> avisos = new ArrayList<>();
        ObservadorProgreso observador =
                aviso -> {
                    assertThat(Thread.currentThread()).isSameAs(hilo);
                    avisos.add(aviso);
                    throw new IllegalStateException("fallo de la presentación");
                };

        ResultadoSuite resultado =
                ejecutarConObservador(PROYECTO, observador, caso("OK", pulsar(1, "Avanzar")));

        assertThat(resultado.casos().get(0).veredicto().estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(avisos).hasSize(6);
        assertThat(avisos.get(5).tipo()).isEqualTo(ProgresoEjecucion.Tipo.SUITE_FIN);
    }

    @Test
    void evaluaCadaPrefijoDuranteLaEjecucionAntesDeEnviarElPasoSiguiente() {
        List<Integer> tamanos = new ArrayList<>();
        List<Long> comandos = new ArrayList<>();
        Oraculo observador =
                new Oraculo() {
                    @Override
                    public String id() {
                        return "PREFIJOS";
                    }

                    @Override
                    public Momento momento() {
                        return Momento.EVENTO;
                    }

                    @Override
                    public boolean aplica(Caso caso, Contexto contexto) {
                        return true;
                    }

                    @Override
                    public List<Hallazgo> evaluar(Traza traza, Contexto contexto) {
                        tamanos.add(traza.eventos().size());
                        comandos.add(
                                agentes.get(0).mensajesRecibidos().stream()
                                        .filter(mensaje ->
                                                mensaje.getTipo() == TipoMensaje.EJECUTAR_PASO)
                                        .count());
                        return List.of();
                    }
                };

        ResultadoSuite resultado =
                ejecutar(
                        List.of(observador),
                        caso("OK", pulsar(1, "Avanzar"), pulsar(2, "Avanzar")));

        assertThat(resultado.casos().get(0).veredicto().estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(tamanos).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(comandos).containsExactly(0L, 1L, 1L, 1L, 2L, 2L);
    }

    private ResultadoSuite ejecutarConObservador(
            Proyecto proyecto, ObservadorProgreso observador, Caso... casos) {
        EvaluacionService evaluacion =
                new EvaluacionService(
                        new RegistroExtensiones(List.of(), List.of(), List.of(), List.of()));
        try (SesionService sesiones = new SesionService(conAgenteFalso, ESPERA, ESPERA)) {
            Suite suite = new Suite("suite-prueba", "1", null, null, List.of(casos), null);
            return new EjecucionService(proyecto, sesiones, evaluacion, observador).ejecutar(suite);
        }
    }

    private ResultadoSuite ejecutar(List<Oraculo> oraculos, Caso... casos) {
        return ejecutar(PROYECTO, conAgenteFalso, oraculos, casos);
    }

    private ResultadoSuite ejecutar(Proyecto proyecto, List<Oraculo> oraculos, Caso... casos) {
        return ejecutar(proyecto, conAgenteFalso, oraculos, casos);
    }

    private ResultadoSuite ejecutar(ArranqueApp arranque, List<Oraculo> oraculos, Caso... casos) {
        return ejecutar(PROYECTO, arranque, oraculos, casos);
    }

    private ResultadoSuite ejecutar(
            Proyecto proyecto, ArranqueApp arranque, List<Oraculo> oraculos, Caso... casos) {
        EvaluacionService evaluacion =
                new EvaluacionService(
                        new RegistroExtensiones(oraculos, List.of(), List.of(), List.of()));
        try (SesionService sesiones = new SesionService(arranque, ESPERA, ESPERA)) {
            Suite suite = new Suite("suite-prueba", "1", null, null, List.of(casos), null);
            return new EjecucionService(proyecto, sesiones, evaluacion).ejecutar(suite);
        }
    }

    private static Caso caso(String id, Paso... pasos) {
        return new Caso(id, null, null, List.of(pasos), null, null, null);
    }

    private static Paso pulsar(int orden, String texto) {
        return new Paso(orden, Accion.PULSAR, Localizador.porTexto(texto), null, null, null);
    }

    private static Paso llenar(int orden, String texto, String valor) {
        return new Paso(orden, Accion.LLENAR, Localizador.porTexto(texto), valor, null, null);
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
