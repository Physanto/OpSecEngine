package io.github.physanto.opsecengine.nucleo.grabacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.nucleo.ejecucion.EjecucionService;
import io.github.physanto.opsecengine.nucleo.evaluacion.EvaluacionService;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.nucleo.generacion.SuiteYaml;
import io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService;
import io.github.physanto.opsecengine.nucleo.sesion.ClienteAgente;
import io.github.physanto.opsecengine.nucleo.sesion.LanzadorProceso;
import io.github.physanto.opsecengine.nucleo.sesion.SesionService;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Prueba de aceptación del inspector y la grabadora (#17): graba un inicio de sesión en la app de
 * práctica real con el agente real, lo convierte en una suite, la valida con el cargador y la
 * reproduce en una aplicación nueva.
 *
 * <p>La ejecuta failsafe; se omite sin pantalla. Las acciones del usuario se simulan con pasos en
 * modo {@code EVENTOS}, que llegan a AWT como eventos de ratón y teclado, sin mover el cursor real.
 */
class GrabacionServiceIT {

    private static final Duration ESPERA = Duration.ofSeconds(30);

    private static LocalizadorDto texto(String estrategia, String valor) {
        return new LocalizadorDto(List.of(new CriterioDto(estrategia, valor)));
    }

    private static void ejecutar(
            ClienteAgente cliente, String accion, LocalizadorDto objetivo, String valor)
            throws Exception {
        ResultadoPasoDto resultado =
                cliente.enviarPaso(new PasoDto(accion, objetivo, valor, "EVENTOS", 10_000));
        assertThat(resultado.getCodigo()).as("%s %s", accion, valor).isEqualTo(CodigoResultado.OK);
    }

    @Test
    void grabaUnLoginLoConvierteEnSuiteValidaYLaReproduce() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "necesita pantalla");
        Path jarAgente = Path.of(System.getProperty("opsec.agente.jar"));
        Path proyectoYaml = Path.of("..", "demo", "sec-001", "proyecto.yaml").normalize();
        ProyectoService proyectos = new ProyectoService();
        Proyecto proyecto = proyectos.cargarProyecto(proyectoYaml);
        assertThat(proyecto.jar())
                .as("Construir también la app de práctica con -am")
                .isRegularFile();
        Path registros =
                Files.createDirectories(
                        Path.of("target", "registros-it", "grabacion-" + System.nanoTime()));
        GrabacionService servicio = new GrabacionService();

        // 1. Se graba un inicio de sesión en la app real.
        ResultadoGrabacion grabado;
        try (SesionService sesiones =
                new SesionService(new LanzadorProceso(jarAgente, registros), ESPERA, ESPERA)) {
            ClienteAgente cliente = sesiones.abrir(proyecto, false);
            servicio.iniciarGrabacion(cliente);
            ejecutar(cliente, "PULSAR", texto("TEXTO", "Avanzar"), null);
            ejecutar(cliente, "PULSAR", texto("TEXTO", "Nube"), null);
            ejecutar(cliente, "LLENAR", texto("ETIQUETA", "Usuario"), "Admin");
            ejecutar(cliente, "LLENAR", texto("ETIQUETA", "Contraseña"), "Admin#2026");
            ejecutar(cliente, "PULSAR", texto("TEXTO", "Iniciar Sesion"), null);
            grabado = servicio.terminarGrabacion(cliente, "login-grabado", "LOGIN-GRABADO");
        }

        // 2. La grabación es una suite con los pasos en orden.
        List<Paso> pasos = grabado.suite().casos().get(0).pasos();
        assertThat(pasos)
                .extracting(Paso::accion, Paso::valor)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Accion.PULSAR, null),
                        org.assertj.core.groups.Tuple.tuple(Accion.PULSAR, null),
                        org.assertj.core.groups.Tuple.tuple(Accion.LLENAR, "Admin"),
                        org.assertj.core.groups.Tuple.tuple(Accion.LLENAR, "Admin#2026"),
                        org.assertj.core.groups.Tuple.tuple(Accion.PULSAR, null));
        assertThat(grabado.advertencias()).hasSize(1);

        // 3. Se escribe, es válida para el cargador y se reproduce sin fallos de pasos.
        Path archivo = registros.resolve("login-grabado.yaml");
        SuiteYaml.guardar(grabado.suite(), archivo);
        Suite cargada = proyectos.cargarSuite(archivo, proyecto);
        assertThat(cargada.casos().get(0).pasos()).hasSize(5);

        ResultadoSuite reproduccion;
        try (SesionService sesiones =
                new SesionService(new LanzadorProceso(jarAgente, registros), ESPERA, ESPERA)) {
            reproduccion =
                    new EjecucionService(
                                    proyecto,
                                    sesiones,
                                    new EvaluacionService(RegistroExtensiones.descubrir()))
                            .ejecutar(cargada);
        }
        ResultadoCaso caso = reproduccion.casos().get(0);
        List<Object> codigos =
                caso.traza().eventos().stream()
                        .filter(e -> e.tipo() == TipoEvento.PASO_FIN)
                        .map((Evento e) -> e.datos().get("codigo"))
                        .toList();
        assertThat(codigos).hasSize(5).containsOnly("OK");
    }
}
