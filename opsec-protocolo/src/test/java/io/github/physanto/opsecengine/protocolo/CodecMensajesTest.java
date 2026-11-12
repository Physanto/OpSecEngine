package io.github.physanto.opsecengine.protocolo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.physanto.opsecengine.protocolo.dto.ArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.ConfigGanchosDto;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import io.github.physanto.opsecengine.protocolo.dto.NodoArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CodecMensajesTest {

    private final CodecMensajes codec = new CodecMensajes();

    @Test
    void hayUnCuerpoDeMuestraPorCadaTipo() {
        assertThat(Muestras.CUERPOS).containsOnlyKeys(TipoMensaje.values());
    }

    @ParameterizedTest
    @EnumSource(TipoMensaje.class)
    void cadaMensajeHaceIdaYVuelta(TipoMensaje tipo) throws Exception {
        Object cuerpo = Muestras.CUERPOS.get(tipo);
        Mensaje mensaje = codec.crear(tipo, cuerpo);

        String linea = codec.serializar(mensaje);
        Mensaje leido = codec.deserializar(linea);

        assertThat(linea).doesNotContain("\n", "\r");
        assertThat(leido).isEqualTo(mensaje);
        assertThat(leido.getVersion()).isEqualTo(Mensaje.VERSION_PROTOCOLO);
        if (cuerpo == null) {
            assertThat(leido.getCuerpo()).isEqualTo(JsonNodeFactory.instance.objectNode());
        } else {
            assertThat(codec.leerCuerpo(leido, Muestras.CLASES.get(tipo))).isEqualTo(cuerpo);
        }
    }

    @Test
    void codificarLineaTerminaEnSaltoDeLineaYUsaUtf8() throws Exception {
        byte[] bytes = codec.codificarLinea(codec.crear(TipoMensaje.EJECUTAR_PASO, Muestras.PASO));
        String texto = new String(bytes, StandardCharsets.UTF_8);

        assertThat(texto).endsWith("\n");
        assertThat(texto.indexOf('\n')).isEqualTo(texto.length() - 1);
        assertThat(texto).contains("ñandú");
    }

    @Test
    void responderUsaElIdDelOriginalComoCorrelacion() {
        Mensaje comando = codec.crear(TipoMensaje.EJECUTAR_PASO, Muestras.PASO);
        Mensaje respuesta =
                codec.responder(comando, TipoMensaje.RESULTADO_PASO, Muestras.RESULTADO);

        assertThat(respuesta.getCorrelacion()).isEqualTo(comando.getId());
        assertThat(respuesta.getId()).isNotEqualTo(comando.getId());
        assertThat(comando.getCorrelacion()).isNull();
    }

    @Test
    void ignoraCamposDesconocidosEnElSobreYEnElCuerpo() throws Exception {
        String json =
                "{\"version\":1,\"tipo\":\"HOLA\",\"id\":\"m1\",\"prioridad\":\"alta\","
                        + "\"cuerpo\":{\"token\":\"t\",\"versionAgente\":\"9.0\","
                        + "\"versionJava\":\"21\",\"sistemaOperativo\":\"macOS\","
                        + "\"capacidades\":[\"javafx\"],\"extra\":{\"a\":1}}}";

        Mensaje mensaje = codec.deserializar(json);

        assertThat(codec.leerCuerpo(mensaje, HolaDto.class))
                .isEqualTo(new HolaDto("t", "9.0", "21", "macOS"));
    }

    @Test
    void aceptaVersionesFuturasEnElSobre() throws Exception {
        Mensaje mensaje = codec.deserializar("{\"version\":2,\"tipo\":\"CERRAR\",\"id\":\"m1\"}");

        assertThat(mensaje.getVersion()).isEqualTo(2);
        assertThat(mensaje.getCuerpo().isEmpty()).isTrue();
    }

    @Test
    void omiteLosCamposNull() throws Exception {
        String linea =
                codec.serializar(
                        codec.crear(
                                TipoMensaje.EJECUTAR_PASO,
                                new PasoDto("ESPERAR", null, null, "EVENTOS", 500)));

        assertThat(linea).doesNotContain("correlacion", "localizador", "valor", "null");
    }

    @Test
    void rechazaMensajesInvalidos() {
        assertThatThrownBy(() -> codec.deserializar("no es json"))
                .isInstanceOf(ExcepcionProtocolo.class);
        assertThatThrownBy(() -> codec.deserializar("{\"version\":1,\"tipo\":\"HOLA\"}"))
                .isInstanceOf(ExcepcionProtocolo.class)
                .hasMessageContaining("id");
        assertThatThrownBy(
                        () -> codec.deserializar("{\"version\":1,\"tipo\":\"NUEVO\",\"id\":\"m\"}"))
                .isInstanceOf(ExcepcionProtocolo.class);
        assertThatThrownBy(() ->
                        codec.deserializar(
                                "{\"version\":1,\"tipo\":\"CERRAR\",\"id\":\"m\","
                                        + "\"cuerpo\":[1,2]}"))
                .isInstanceOf(ExcepcionProtocolo.class);
    }

    @Test
    void rechazaCuerposQueNoCorrespondenAlDto() throws Exception {
        Mensaje sinToken =
                codec.deserializar("{\"version\":1,\"tipo\":\"HOLA\",\"id\":\"m\",\"cuerpo\":{}}");

        assertThatThrownBy(() -> codec.leerCuerpo(sinToken, HolaDto.class))
                .isInstanceOf(ExcepcionProtocolo.class)
                .hasMessageContaining("HolaDto");
    }

    @Test
    void elCuerpoDelMensajeNoSePuedeModificarDesdeFuera() {
        Mensaje mensaje = codec.crear(TipoMensaje.ERROR, null);

        ((com.fasterxml.jackson.databind.node.ObjectNode) mensaje.getCuerpo()).put("x", 1);

        assertThat(mensaje.getCuerpo().has("x")).isFalse();
    }

    @Test
    void losDtoSonInmutablesYDescartanValoresNull() {
        List<String> canarios = new ArrayList<>(List.of("C1"));
        ConfigGanchosDto config = new ConfigGanchosDto(null, null, canarios, null);
        Map<String, Object> datos = new HashMap<>();
        datos.put("sql", "SELECT 1");
        datos.put("parametros", null);
        EventoDto evento = new EventoDto(1, 0, "SQL", datos);

        canarios.add("C2");

        assertThat(config.getCanarios()).containsExactly("C1");
        assertThat(config.getSqlSensibles()).isEmpty();
        assertThat(evento.getDatos()).containsOnlyKeys("sql");
        assertThatThrownBy(() -> config.getCanarios().add("C3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void arbolConservaRutaAlcanzabilidadYAccionesEnLaIdaYVuelta() throws Exception {
        NodoArbolDto nodo =
                new NodoArbolDto(
                        "javax.swing.JButton",
                        "Aceptar",
                        "boton",
                        null,
                        true,
                        true,
                        false,
                        null,
                        "Inicio/JButton[0]",
                        true,
                        List.of("PULSAR"));
        ArbolDto arbol = new ArbolDto(List.of(nodo));
        Mensaje mensaje =
                codec.deserializar(codec.serializar(codec.crear(TipoMensaje.ARBOL, arbol)));
        assertThat(codec.leerCuerpo(mensaje, ArbolDto.class)).isEqualTo(arbol);
        assertThat(mensaje.getCuerpo().path("ventanas").get(0).path("ruta").asText())
                .isEqualTo("Inicio/JButton[0]");
    }

    @Test
    void arbolAntiguoUsaValoresPorDefectoEIgnoraCamposDesconocidosEnLosHijos() throws Exception {
        String json =
                "{\"version\":1,\"tipo\":\"ARBOL\",\"id\":\"a\",\"cuerpo\":{\"ventanas\":["
                        + "{\"tipo\":\"javax.swing.JFrame\",\"hijos\":[{\"tipo\":\"javax.swing.JButton\","
                        + "\"futuro\":123}]}]}}";
        NodoArbolDto ventana =
                codec.leerCuerpo(codec.deserializar(json), ArbolDto.class).getVentanas().get(0);
        for (NodoArbolDto nodo : List.of(ventana, ventana.getHijos().get(0))) {
            assertThat(nodo.getRuta()).isNull();
            assertThat(nodo.isAlcanzable()).isFalse();
            assertThat(nodo.getAcciones()).isEmpty();
            assertThat(nodo)
                    .isEqualTo(
                            new NodoArbolDto(
                                    nodo.getTipo(),
                                    null,
                                    null,
                                    null,
                                    false,
                                    false,
                                    false,
                                    nodo.getHijos()));
        }
    }

    @Test
    void lasAccionesDelNodoSonUnaCopiaInmutable() {
        List<String> acciones = new ArrayList<>(List.of("PULSAR"));
        NodoArbolDto nodo =
                new NodoArbolDto(
                        "Boton",
                        null,
                        null,
                        null,
                        true,
                        true,
                        false,
                        null,
                        "Inicio/Boton[0]",
                        true,
                        acciones);
        acciones.add("IR_A");
        assertThat(nodo.getAcciones()).containsExactly("PULSAR");
        assertThatThrownBy(() -> nodo.getAcciones().add("MARCAR"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() ->
                        new NodoArbolDto(
                                "Boton",
                                null,
                                null,
                                null,
                                true,
                                true,
                                false,
                                null,
                                null,
                                false,
                                java.util.Arrays.asList("PULSAR", null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void holaNoMuestraElTokenEnToString() {
        assertThat(Muestras.HOLA.toString()).doesNotContain("token-secreto").contains("1.8.0_402");
    }
}
