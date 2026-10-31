package io.github.physanto.opsecengine.verificacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Enfoque;
import io.github.physanto.opsecengine.dominio.caso.EstrategiaLocalizacion;
import io.github.physanto.opsecengine.dominio.caso.ModoAccion;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pruebas de los esquemas de {@code contratos/esquemas/}: que sean JSON Schema válidos, que sus
 * valores enumerados coincidan con el dominio y que rechacen los errores típicos.
 */
class EsquemasTest {

    static Stream<Path> esquemas() {
        return Contratos.archivos(Contratos.ESQUEMAS).stream();
    }

    @ParameterizedTest
    @MethodSource("esquemas")
    void cadaEsquemaCumpleElMetaesquemaYTieneSuId(Path archivo) {
        JsonNode esquema = Contratos.leerJson(archivo);

        assertThat(Contratos.describir(Contratos.metaesquema().validate(esquema)))
                .as("errores de %s contra el metaesquema", archivo)
                .isEmpty();
        assertThat(esquema.path("$schema").asText())
                .isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(esquema.path("$id").asText())
                .isEqualTo(Contratos.BASE_ID + archivo.getFileName());
    }

    @Test
    void existenLosCincoEsquemasDelContrato() {
        assertThat(esquemas().map(archivo -> archivo.getFileName().toString()))
                .containsExactlyInAnyOrder(
                        Contratos.PROYECTO,
                        Contratos.SUITE,
                        Contratos.PAYLOAD,
                        Contratos.ORACULO_REGLA,
                        Contratos.TRAZA_EVENTO);
    }

    @Test
    void losValoresEnumeradosCoincidenConElDominio() {
        JsonNode suite = leerEsquema(Contratos.SUITE);
        JsonNode regla = leerEsquema(Contratos.ORACULO_REGLA).at("/$defs/regla/properties");
        JsonNode traza = leerEsquema(Contratos.TRAZA_EVENTO);

        assertThat(textos(suite.at("/properties/enfoques/items/enum")))
                .isEqualTo(nombres(Enfoque.values(), Enum::name));
        assertThat(textos(suite.at("/$defs/config/properties/modo/enum")))
                .isEqualTo(nombres(ModoAccion.values(), Enum::name));
        assertThat(textos(traza.at("/$defs/tipoEvento/enum")))
                .isEqualTo(nombres(TipoEvento.values(), Enum::name));
        assertThat(textos(regla.at("/momento/enum")))
                .isEqualTo(nombres(Momento.values(), Enum::name));
        assertThat(textos(regla.at("/entonces/properties/confianza/enum")))
                .isEqualTo(nombres(Confianza.values(), Enum::name));
    }

    @Test
    void lasAccionesDeUnPasoSonLasDelDominio() {
        JsonNode paso = leerEsquema(Contratos.SUITE).at("/$defs/paso");
        List<String> acciones =
                nombres(Accion.values(), accion -> accion.name().toLowerCase(Locale.ROOT));
        List<String> requeridas = new ArrayList<>();
        paso.path("oneOf").forEach(opcion -> requeridas.addAll(textos(opcion.path("required"))));
        List<String> opcionales =
                nombresDeCampos(paso.path("properties")).stream()
                        .filter(clave -> !acciones.contains(clave))
                        .collect(Collectors.toList());

        assertThat(requeridas).isEqualTo(acciones);
        assertThat(nombresDeCampos(paso.path("properties"))).containsAll(acciones);
        assertThat(opcionales).containsExactly("esperaMaximaMs", "extensiones");
    }

    @Test
    void losCriteriosDeUnLocalizadorSonLasEstrategiasDelDominio() {
        JsonNode objeto = leerEsquema(Contratos.SUITE).at("/$defs/localizador/oneOf/1/properties");

        assertThat(nombresDeCampos(objeto))
                .isEqualTo(nombres(EstrategiaLocalizacion.values(), Contratos::camelCase));
    }

    @Test
    void unaSuiteAceptaExtensionesEnSuRaizSusCasosYSusPasos() {
        JsonNode suite =
                Contratos.yaml(
                        """
                        suite: minima
                        extensiones: {equipo: qa}
                        casos:
                          - id: X1
                            extensiones: {prioridad: 1}
                            pasos:
                              - pulsar: Avanzar
                                extensiones: {nota: libre}
                              - esperar: 500
                              - capturar:
                        """);

        assertThat(Contratos.errores(Contratos.SUITE, suite)).isEmpty();
    }

    @Test
    void unPasoConDosAccionesNoEsValido() {
        JsonNode suite =
                Contratos.yaml(
                        """
                        suite: invalida
                        casos:
                          - id: X1
                            pasos:
                              - pulsar: Avanzar
                                marcar: Recordarme
                        """);

        assertThat(Contratos.errores(Contratos.SUITE, suite)).isNotEmpty();
    }

    @Test
    void unPasoSinAccionNoEsValido() {
        JsonNode suite =
                Contratos.yaml(
                        """
                        suite: invalida
                        casos:
                          - id: X1
                            pasos:
                              - esperaMaximaMs: 100
                        """);

        assertThat(Contratos.errores(Contratos.SUITE, suite)).isNotEmpty();
    }

    @Test
    void unaPropiedadDesconocidaNoEsValida() {
        JsonNode suite =
                Contratos.yaml(
                        """
                        suite: invalida
                        casos:
                          - id: X1
                            orakulos: [SIN-SESION]
                            pasos:
                              - pulsar: Avanzar
                        """);

        assertThat(Contratos.errores(Contratos.SUITE, suite)).isNotEmpty();
    }

    @Test
    void unLocalizadorSinCriteriosOConCriterioDesconocidoNoEsValido() {
        JsonNode vacio =
                Contratos.yaml(
                        """
                        suite: invalida
                        casos:
                          - id: X1
                            pasos:
                              - pulsar: {}
                        """);
        JsonNode desconocido =
                Contratos.yaml(
                        """
                        suite: invalida
                        casos:
                          - id: X1
                            pasos:
                              - pulsar: {color: rojo}
                        """);

        assertThat(Contratos.errores(Contratos.SUITE, vacio)).isNotEmpty();
        assertThat(Contratos.errores(Contratos.SUITE, desconocido)).isNotEmpty();
    }

    @Test
    void unProyectoSinAppNoEsValido() {
        JsonNode proyecto =
                Contratos.yaml(
                        """
                        roles:
                          admin: {usuario: Admin, contrasena: "12345"}
                        """);

        assertThat(Contratos.errores(Contratos.PROYECTO, proyecto)).isNotEmpty();
    }

    @Test
    void unPayloadConCanarioDebeIncluirLaVariableCanario() {
        JsonNode sinVariable =
                Contratos.yaml(
                        """
                        - id: SQLI-X
                          categoria: INYECCION_SQL
                          payload: "' OR 1=1 --"
                          usa_canario: true
                        """);
        JsonNode vacio =
                Contratos.yaml(
                        """
                        - id: VACIO
                          categoria: VACIOS
                          payload: ""
                        """);

        assertThat(Contratos.errores(Contratos.PAYLOAD, sinVariable)).isNotEmpty();
        assertThat(Contratos.errores(Contratos.PAYLOAD, vacio)).isEmpty();
    }

    @Test
    void unaReglaConIgualANecesitaCampo() {
        JsonNode regla =
                Contratos.yaml(
                        """
                        - id: REGLA-X
                          momento: EVENTO
                          si: {tipo: CONSOLA, igual_a: ERROR}
                          entonces: {titulo: Salida de error, confianza: INFO}
                        """);

        assertThat(Contratos.errores(Contratos.ORACULO_REGLA, regla)).isNotEmpty();
    }

    @Test
    void unEventoConInstanteOTipoInvalidoNoEsValido() {
        JsonNode instante =
                Contratos.yaml(
                        """
                        {secuencia: 0, instante: "30/10/2026 15:30", tipo: SQL,
                         contexto: {numeroPaso: 0, sesionActiva: false}}
                        """);
        JsonNode tipo =
                Contratos.yaml(
                        """
                        {secuencia: 0, instante: "2026-10-30T15:30:00Z", tipo: RED,
                         contexto: {numeroPaso: 0, sesionActiva: false}}
                        """);

        assertThat(Contratos.errores(Contratos.TRAZA_EVENTO, instante)).isNotEmpty();
        assertThat(Contratos.errores(Contratos.TRAZA_EVENTO, tipo)).isNotEmpty();
    }

    private static JsonNode leerEsquema(String archivo) {
        return Contratos.leerJson(Contratos.ESQUEMAS.resolve(archivo));
    }

    private static List<String> textos(JsonNode arreglo) {
        assertThat(arreglo.isArray()).as("se esperaba un arreglo").isTrue();
        List<String> textos = new ArrayList<>();
        arreglo.forEach(valor -> textos.add(valor.asText()));
        return textos;
    }

    private static List<String> nombresDeCampos(JsonNode objeto) {
        assertThat(objeto.isObject()).as("se esperaba un objeto").isTrue();
        List<String> nombres = new ArrayList<>();
        objeto.fieldNames().forEachRemaining(nombres::add);
        return nombres;
    }

    private static <E extends Enum<E>> List<String> nombres(
            E[] valores, Function<E, String> nombre) {
        return Arrays.stream(valores).map(nombre).collect(Collectors.toList());
    }
}
