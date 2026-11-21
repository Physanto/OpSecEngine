package io.github.physanto.opsecengine.nucleo.grabacion;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Criterio;
import io.github.physanto.opsecengine.dominio.caso.EstrategiaLocalizacion;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.nucleo.generacion.SuiteYaml;
import io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService;
import io.github.physanto.opsecengine.nucleo.sesion.ClienteAgente;
import io.github.physanto.opsecengine.protocolo.TipoMensaje;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.ModoInteraccionDto;
import io.github.physanto.opsecengine.soporte.AgenteFalso;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pruebas del servicio del inspector y la grabadora, sin pantalla ni agente real. */
class GrabacionServiceTest {

    private static final Duration ESPERA = Duration.ofSeconds(5);
    private static final String RUTA_USUARIO = "Inicio de sesión/JRootPane[0]/JTextField[0]";

    private static Map<String, String> criterio(String estrategia, String valor) {
        return Map.of("estrategia", estrategia, "valor", valor);
    }

    private static EventoDto accion(
            long secuencia,
            String accion,
            String componente,
            List<Map<String, String>> localizador,
            String valor,
            boolean sensible) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("accion", accion);
        datos.put("ventana", "Inicio de sesión");
        datos.put("componente", componente);
        datos.put("localizador", localizador);
        if (valor != null) {
            datos.put("valor", valor);
        }
        if (sensible) {
            datos.put("sensible", true);
        }
        return new EventoDto(secuencia, 0, "ACCION_GRABADA", datos);
    }

    private static List<EventoDto> grabacionDeLogin() {
        return List.of(
                new EventoDto(0, 0, "CONSOLA", Map.of("flujo", "SALIDA", "texto", "ruido")),
                accion(
                        1,
                        "LLENAR",
                        "javax.swing.JTextField",
                        List.of(criterio("ETIQUETA", "Usuario"), criterio("RUTA", RUTA_USUARIO)),
                        "Admin",
                        false),
                accion(
                        2,
                        "LLENAR",
                        "javax.swing.JPasswordField",
                        List.of(criterio("ETIQUETA", "Contraseña")),
                        "Admin#2026",
                        true),
                accion(
                        3,
                        "PULSAR",
                        "javax.swing.JButton",
                        List.of(
                                criterio("NOMBRE_COMPONENTE", "botonIngresar"),
                                criterio("TEXTO", "Iniciar Sesion"),
                                criterio("RUTA", "Inicio de sesión/JRootPane[0]/JButton[0]")),
                        null,
                        false));
    }

    @Test
    void unaGrabacionDeLoginProduceUnaSuiteConLosTresPasos() {
        ResultadoGrabacion resultado =
                GrabacionService.convertir(grabacionDeLogin(), "login-grabado", "LOGIN-01");

        Suite suite = resultado.suite();
        assertThat(suite.nombre()).isEqualTo("login-grabado");
        assertThat(suite.casos()).hasSize(1);
        Caso caso = suite.casos().get(0);
        assertThat(caso.id()).isEqualTo("LOGIN-01");
        assertThat(caso.pasos())
                .extracting(Paso::orden, Paso::accion, Paso::valor)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, Accion.LLENAR, "Admin"),
                        org.assertj.core.groups.Tuple.tuple(2, Accion.LLENAR, "Admin#2026"),
                        org.assertj.core.groups.Tuple.tuple(3, Accion.PULSAR, null));
        assertThat(caso.pasos().get(0).objetivo().criterios())
                .containsExactly(
                        new Criterio(EstrategiaLocalizacion.ETIQUETA, "Usuario"),
                        new Criterio(EstrategiaLocalizacion.TEXTO, "Usuario"));
        assertThat(caso.pasos().get(2).objetivo().criterios())
                .extracting(Criterio::estrategia)
                .containsExactly(
                        EstrategiaLocalizacion.NOMBRE_COMPONENTE,
                        EstrategiaLocalizacion.TEXTO,
                        EstrategiaLocalizacion.RUTA);
        assertThat(resultado.advertencias()).hasSize(1);
        assertThat(resultado.advertencias().get(0)).contains("Contraseña", "sensible");
    }

    @Test
    void laSuiteGrabadaSeEscribeComoYamlYSeVuelveACargarValida(@TempDir Path carpeta)
            throws Exception {
        Suite grabada =
                GrabacionService.convertir(grabacionDeLogin(), "login-grabado", "LOGIN-01").suite();
        Path archivo = carpeta.resolve("login.yaml");

        SuiteYaml.guardar(grabada, archivo);
        Proyecto proyecto =
                new Proyecto(
                        "p",
                        Path.of("app.jar"),
                        "app.Principal",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        Suite cargada = new ProyectoService().cargarSuite(archivo, proyecto);

        assertThat(Files.readString(archivo)).contains("llenar", "pulsar");
        Caso caso = cargada.casos().get(0);
        assertThat(caso.pasos())
                .extracting(Paso::accion, Paso::valor)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Accion.LLENAR, "Admin"),
                        org.assertj.core.groups.Tuple.tuple(Accion.LLENAR, "Admin#2026"),
                        org.assertj.core.groups.Tuple.tuple(Accion.PULSAR, null));
        assertThat(caso.pasos().get(2).objetivo())
                .isEqualTo(grabada.casos().get(0).pasos().get(2).objetivo());
    }

    @Test
    void marcarYSeleccionarSeConviertenYLosPasosQuedanNumeradosSinHuecos() {
        List<EventoDto> eventos =
                List.of(
                        accion(
                                0,
                                "MARCAR",
                                "javax.swing.JCheckBox",
                                List.of(criterio("TEXTO", "Recordarme")),
                                "true",
                                false),
                        accion(
                                1,
                                "SELECCIONAR",
                                "javax.swing.JComboBox",
                                List.of(criterio("ETIQUETA", "Rol")),
                                "Admin",
                                false));

        Suite suite = GrabacionService.convertir(eventos, "s", null).suite();

        Caso caso = suite.casos().get(0);
        assertThat(caso.id()).isEqualTo(GrabacionService.ID_CASO_POR_DEFECTO);
        assertThat(caso.pasos())
                .extracting(Paso::orden, Paso::accion, Paso::valor)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, Accion.MARCAR, null),
                        org.assertj.core.groups.Tuple.tuple(2, Accion.SELECCIONAR, "Admin"));
    }

    @Test
    void omiteYAvisaLoQueElFormatoNoPuedeExpresar() {
        List<EventoDto> eventos =
                List.of(
                        accion(
                                0,
                                "LLENAR",
                                "javax.swing.JTextField",
                                List.of(criterio("RUTA", RUTA_USUARIO)),
                                "x",
                                false),
                        accion(
                                1,
                                "MARCAR",
                                "javax.swing.JCheckBox",
                                List.of(criterio("TEXTO", "Recordarme")),
                                "false",
                                false),
                        accion(2, "PULSAR", "javax.swing.JButton", List.of(), null, false),
                        accion(
                                3,
                                "PULSAR",
                                "javax.swing.JButton",
                                List.of(criterio("TEXTO", "Aceptar")),
                                null,
                                false));

        ResultadoGrabacion resultado = GrabacionService.convertir(eventos, "s", "C");

        assertThat(resultado.suite().casos().get(0).pasos())
                .extracting(Paso::orden, Paso::accion)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, Accion.PULSAR));
        assertThat(resultado.advertencias()).hasSize(3);
        assertThat(resultado.advertencias().get(0)).contains("acción grabada 1", "etiqueta");
        assertThat(resultado.advertencias().get(1)).contains("acción grabada 2", "desmarcar");
        assertThat(resultado.advertencias().get(2)).contains("acción grabada 3", "localizar");
    }

    @Test
    void sinAccionesDevuelveUnCasoVacioYUnAviso() {
        ResultadoGrabacion resultado =
                GrabacionService.convertir(
                        List.of(new EventoDto(0, 0, "CONSOLA", Map.of("texto", "x"))), "s", "C");

        assertThat(resultado.suite().casos().get(0).pasos()).isEmpty();
        assertThat(resultado.advertencias()).containsExactly("No se grabó ninguna acción");
    }

    @Test
    void ignoraLasEstrategiasQueElMotorNoConoce() {
        List<EventoDto> eventos =
                List.of(
                        accion(
                                0,
                                "PULSAR",
                                "javax.swing.JButton",
                                List.of(criterio("INVENTADA", "x"), criterio("TEXTO", "Aceptar")),
                                null,
                                false));

        Suite suite = GrabacionService.convertir(eventos, "s", "C").suite();

        assertThat(suite.casos().get(0).pasos().get(0).objetivo().criterios())
                .containsExactly(new Criterio(EstrategiaLocalizacion.TEXTO, "Aceptar"));
    }

    @Test
    void lasInspeccionesSeLeenDeLosEventosDelInspector() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("ventana", "Inicio de sesión");
        datos.put("componente", "javax.swing.JButton");
        datos.put("ruta", "Inicio de sesión/JRootPane[0]/JButton[0]");
        datos.put("texto", "Cancelar");
        datos.put("localizador", List.of(criterio("TEXTO", "Cancelar")));

        List<Inspeccion> inspecciones =
                GrabacionService.inspecciones(
                        List.of(
                                new EventoDto(0, 0, "COMPONENTE_INSPECCIONADO", datos),
                                new EventoDto(1, 0, "CONSOLA", Map.of("texto", "x"))));

        assertThat(inspecciones).hasSize(1);
        Inspeccion inspeccion = inspecciones.get(0);
        assertThat(inspeccion.clase()).isEqualTo("javax.swing.JButton");
        assertThat(inspeccion.texto()).isEqualTo("Cancelar");
        assertThat(inspeccion.localizador().criterios())
                .containsExactly(new Criterio(EstrategiaLocalizacion.TEXTO, "Cancelar"));
    }

    @Test
    void pideLosModosAlAgenteEnOrdenYDescartaLosEventosPrevios() throws Exception {
        GrabacionService servicio = new GrabacionService();
        try (ClienteAgente cliente = new ClienteAgente();
                AgenteFalso agente = new AgenteFalso()) {
            agente.conectar(cliente.puerto(), cliente.token());
            cliente.esperarHola(ESPERA);

            servicio.iniciarGrabacion(cliente);
            ResultadoGrabacion resultado = servicio.terminarGrabacion(cliente, "s", "C");
            servicio.iniciarInspeccion(cliente);

            List<String> modos = new ArrayList<>();
            agente.mensajesRecibidos().stream()
                    .filter(m -> m.getTipo() == TipoMensaje.MODO_INTERACCION)
                    .forEach(m -> modos.add(m.getCuerpo().get("modo").asText()));
            assertThat(modos)
                    .containsExactly(
                            ModoInteraccionDto.GRABAR,
                            ModoInteraccionDto.NINGUNO,
                            ModoInteraccionDto.INSPECCIONAR);
            assertThat(resultado.advertencias()).containsExactly("No se grabó ninguna acción");
        }
    }
}
