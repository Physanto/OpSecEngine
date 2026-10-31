package io.github.physanto.opsecengine.verificacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.ModoAccion;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pruebas de los catálogos de {@code contratos/catalogos/}: que se lean sin errores y que coincidan
 * con el dominio, con el protocolo y entre sí.
 */
class CatalogosTest {

    private static final Set<String> TIPOS_DE_CAMPO =
            Set.of("texto", "entero", "numero", "booleano", "lista", "objeto");

    private static final Set<String> ORIGENES = Set.of("agente", "motor");

    static Stream<Path> catalogos() {
        return Contratos.archivos(Contratos.CATALOGOS).stream();
    }

    @ParameterizedTest
    @MethodSource("catalogos")
    void cadaCatalogoSeLeeYTieneVersionYEntradas(Path archivo) {
        JsonNode catalogo = Contratos.leerYaml(archivo);
        List<String> listas = new ArrayList<>();
        catalogo.fieldNames().forEachRemaining(listas::add);
        listas.remove("version");

        assertThat(catalogo.path("version").asInt()).as("versión de %s", archivo).isEqualTo(1);
        assertThat(listas).as("listas de %s", archivo).hasSize(1);
        assertThat(catalogo.path(listas.get(0)).isArray()).isTrue();
        assertThat(catalogo.path(listas.get(0))).as("entradas de %s", archivo).isNotEmpty();
    }

    @Test
    void existenLosCuatroCatalogosDelContrato() {
        assertThat(catalogos().map(archivo -> archivo.getFileName().toString()))
                .containsExactlyInAnyOrder(
                        "eventos.yaml", "oraculos.yaml", "codigos-resultado.yaml", "cwe.yaml");
    }

    @Test
    void cadaTipoEventoTieneEntradaEnElCatalogoYViceversa() {
        List<String> catalogo = Catalogos.textos(Catalogos.eventos(), "tipo");
        List<String> dominio = nombres(TipoEvento.values());

        assertThat(catalogo).as("tipos del catálogo de eventos").doesNotHaveDuplicates();
        assertThat(catalogo)
                .as("tipos del dominio sin entrada en el catálogo")
                .containsAll(dominio);
        assertThat(dominio)
                .as("tipos del catálogo que no existen en el dominio")
                .containsAll(catalogo);
    }

    @Test
    void losCamposDeCadaEventoEstanBienDescritos() {
        for (JsonNode evento : Catalogos.eventos()) {
            String tipo = evento.path("tipo").asText();
            assertThat(evento.path("descripcion").asText())
                    .as("descripción de %s", tipo)
                    .isNotBlank();
            evento.path("origen").forEach(origen -> assertThat(ORIGENES).contains(origen.asText()));
            assertThat(evento.path("origen")).as("origen de %s", tipo).isNotEmpty();

            List<String> nombres = new ArrayList<>();
            for (JsonNode campo : evento.path("campos")) {
                String nombre = campo.path("nombre").asText();
                nombres.add(nombre);
                assertThat(nombre)
                        .as("nombre de un campo de %s", tipo)
                        .matches("[a-z][A-Za-z0-9]*");
                assertThat(TIPOS_DE_CAMPO)
                        .as("tipo de %s.%s", tipo, nombre)
                        .contains(campo.path("tipo").asText());
                assertThat(campo.path("obligatorio").isBoolean())
                        .as("obligatorio de %s.%s", tipo, nombre)
                        .isTrue();
                assertThat(campo.path("descripcion").asText())
                        .as("descripción de %s.%s", tipo, nombre)
                        .isNotBlank();
                if (campo.has("valores")) {
                    assertThat(campo.path("tipo").asText()).isEqualTo("texto");
                    assertThat(campo.path("valores")).isNotEmpty();
                }
            }
            assertThat(nombres).as("campos de %s", tipo).isNotEmpty().doesNotHaveDuplicates();
        }
    }

    @Test
    void losValoresEnumeradosDeLosEventosCoincidenConElDominioYElProtocolo() {
        assertThat(valores("PASO_INICIO", "accion")).isEqualTo(nombres(Accion.values()));
        assertThat(valores("PASO_INICIO", "modo")).isEqualTo(nombres(ModoAccion.values()));
        assertThat(valores("PASO_FIN", "codigo")).isEqualTo(nombres(CodigoResultado.values()));
    }

    @Test
    void losCodigosDeResultadoSonLosDelProtocolo() {
        List<JsonNode> codigos = Catalogos.entradas("codigos-resultado.yaml", "codigos");

        assertThat(Catalogos.textos(codigos, "codigo"))
                .isEqualTo(nombres(CodigoResultado.values()));
        codigos.forEach(codigo -> assertThat(codigo.path("significado").asText()).isNotBlank());
    }

    @Test
    void losOraculosReservadosEstanCompletosYUsanMomentosYCweValidos() {
        List<JsonNode> oraculos = Catalogos.entradas("oraculos.yaml", "oraculos");
        Set<String> cwe = Set.copyOf(Catalogos.idsDeCwe());

        assertThat(Catalogos.idsDeOraculos())
                .containsExactly(
                        "SIN-SESION",
                        "AUTORIZACION-DIFERENCIAL",
                        "SQLI-CANARIO",
                        "FIRESTORE-RUTA-CANARIO",
                        "ENUMERACION-USUARIOS",
                        "SIN-BLOQUEO",
                        "CREDENCIALES-DEBILES",
                        "CONTRASENA-EN-CAMPO-VISIBLE",
                        "CONTRASENA-EN-CONSOLA",
                        "TRAZA-EN-DIALOGO",
                        "JDBC-SIN-CIFRADO",
                        "EXCEPCION-NO-CAPTURADA",
                        "APP-TERMINADA",
                        "INTERFAZ-CONGELADA");
        for (JsonNode oraculo : oraculos) {
            String id = oraculo.path("id").asText();
            assertThat(nombres(Momento.values()))
                    .as("momento de %s", id)
                    .contains(oraculo.path("momento").asText());
            assertThat(oraculo.has("cwe")).as("%s declara su CWE, aunque sea null", id).isTrue();
            if (!oraculo.path("cwe").isNull()) {
                assertThat(cwe).as("CWE de %s", id).contains(oraculo.path("cwe").asText());
            }
            assertThat(oraculo.path("descripcion").asText())
                    .as("descripción de %s", id)
                    .isNotBlank();
        }
    }

    @Test
    void elCatalogoDeCweTieneLasDebilidadesUsadasYSusTextos() {
        List<JsonNode> cwe = Catalogos.entradas("cwe.yaml", "cwe");

        assertThat(Catalogos.idsDeCwe())
                .doesNotHaveDuplicates()
                .allMatch(id -> id.matches("CWE-[0-9]+"))
                .contains(
                        "CWE-89", "CWE-204", "CWE-209", "CWE-306", "CWE-307", "CWE-319", "CWE-521",
                        "CWE-532", "CWE-798", "CWE-862");
        for (JsonNode entrada : cwe) {
            String id = entrada.path("id").asText();
            for (String campo : List.of("nombre", "explicacion", "recomendacion")) {
                assertThat(Optional.ofNullable(entrada.get(campo)).map(JsonNode::asText))
                        .as("%s de %s", campo, id)
                        .hasValueSatisfying(texto -> assertThat(texto).isNotBlank());
            }
        }
    }

    private static List<String> valores(String tipo, String campo) {
        for (JsonNode evento : Catalogos.eventos()) {
            if (evento.path("tipo").asText().equals(tipo)) {
                for (JsonNode descripcion : evento.path("campos")) {
                    if (descripcion.path("nombre").asText().equals(campo)) {
                        List<String> valores = new ArrayList<>();
                        descripcion.path("valores").forEach(valor -> valores.add(valor.asText()));
                        return valores;
                    }
                }
            }
        }
        throw new AssertionError("No existe el campo " + campo + " en el evento " + tipo);
    }

    private static List<String> nombres(Enum<?>[] valores) {
        return Arrays.stream(valores).map(Enum::name).collect(Collectors.toList());
    }
}
