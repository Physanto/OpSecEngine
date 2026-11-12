package io.github.physanto.opsecengine.ataques;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CargadorPayloadsTest {

    private final CargadorPayloads cargador = new CargadorPayloads();

    @Test
    void cargaLosPayloadsVaciosIncluidos() throws Exception {
        List<Payload> vacios = cargador.cargarIncluido("vacios.yaml");

        assertThat(vacios)
                .extracting(Payload::id)
                .containsExactly(
                        "VACIO-CADENA", "VACIO-ESPACIOS", "VACIO-TABULACION", "VACIO-SALTO-LINEA");
        assertThat(vacios).extracting(Payload::texto).containsExactly("", "   ", "\t", "\n");
        assertThat(vacios)
                .allSatisfy(payload -> {
                    assertThat(payload.categoria()).isEqualTo(CategoriaPayload.VACIOS);
                    assertThat(payload.usaCanario()).isFalse();
                    assertThat(payload.sirvePara(TipoCampo.CONTRASENA)).isTrue();
                    assertThat(payload.oraculos()).contains("EXCEPCION-NO-CAPTURADA");
                });
    }

    @Test
    void cargaTodosLosArchivosIncluidos() throws Exception {
        List<Payload> todos = cargador.cargarIncluidos();
        assertThat(todos).extracting(Payload::id).doesNotHaveDuplicates();
        assertThat(todos.stream().map(Payload::categoria).collect(Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(CategoriaPayload.class));
        try (var archivos =
                Files.list(
                        Path.of(
                                "src",
                                "main",
                                "resources",
                                "io",
                                "github",
                                "physanto",
                                "opsecengine",
                                "ataques",
                                "payloads"))) {
            assertThat(CargadorPayloads.INCLUIDOS)
                    .containsExactlyInAnyOrderElementsOf(
                            archivos.filter(Files::isRegularFile)
                                    .map(p -> p.getFileName().toString())
                                    .filter(nombre -> nombre.endsWith(".yaml"))
                                    .toList());
        }
    }

    @Test
    void losPayloadsRastreablesConservanElCanarioYLosLimitesNoLoAlteran() throws Exception {
        Set<CategoriaPayload> rastreables =
                EnumSet.of(
                        CategoriaPayload.INYECCION_SQL,
                        CategoriaPayload.FIRESTORE,
                        CategoriaPayload.HTML_SWING,
                        CategoriaPayload.CADENAS_FORMATO,
                        CategoriaPayload.RUTAS_ARCHIVO,
                        CategoriaPayload.COMANDOS,
                        CategoriaPayload.FORMULAS_CSV,
                        CategoriaPayload.UNICODE);
        for (Payload payload : cargador.cargarIncluidos()) {
            if (rastreables.contains(payload.categoria())) {
                assertThat(payload.usaCanario()).as(payload.id()).isTrue();
                assertThat(payload.texto()).as(payload.id()).contains("{canario}");
            } else {
                assertThat(payload.usaCanario()).as(payload.id()).isFalse();
                assertThat(payload.texto()).as(payload.id()).doesNotContain("{canario}");
            }
        }
    }

    @Test
    void conservaLasLongitudesExactasYLosCaracteresEspeciales() throws Exception {
        assertThat(cargador.cargarIncluido("longitud.yaml"))
                .extracting(p -> p.texto().length())
                .containsExactly(255, 256, 10000, 1048576);
        List<Payload> unicode = cargador.cargarIncluido("unicode.yaml");
        assertThat(unicode.get(0).texto()).startsWith(new String(Character.toChars(0x1f642)));
        assertThat(unicode.get(1).texto()).isEqualTo("{canario}" + (char) 0);
        assertThat(unicode.get(2).texto())
                .isEqualTo(Character.toString(0x202e) + "{canario}" + Character.toString(0x202c));
        assertThat(unicode.get(3).texto()).isEqualTo("e" + (char) 0x0301 + "{canario}");
        assertThat(cargador.cargarIncluido("numericos.yaml"))
                .extracting(Payload::texto)
                .containsExactly("-1", "9223372036854775808", "1,5", "NaN", "000123");
    }

    @Test
    void incluyeCredencialesDebilesSinInventarUnaCategoria() throws Exception {
        List<Payload> credenciales = cargador.cargarIncluido("credenciales-debiles.yaml");
        assertThat(credenciales).extracting(Payload::texto).containsExactly("123456", "0000", "a");
        assertThat(credenciales)
                .allSatisfy(payload -> {
                    assertThat(payload.aplicaA()).containsExactly(TipoCampo.CONTRASENA);
                    assertThat(payload.oraculos())
                            .contains("CREDENCIALES-DEBILES", "CONTRASENA-EN-CONSOLA");
                    assertThat(payload.cwe()).isEqualTo("CWE-521");
                });
    }

    @Test
    void usaSoloOraculosYCwesDelCatalogoYConservaLosEjemplosSql() throws Exception {
        YAMLMapper mapper = new YAMLMapper();
        Set<String> oraculos = new java.util.HashSet<>();
        Set<String> cwes = new java.util.HashSet<>();
        mapper.readTree(Path.of("..", "contratos", "catalogos", "oraculos.yaml").toFile())
                .get("oraculos")
                .forEach(nodo -> oraculos.add(nodo.get("id").asText()));
        mapper.readTree(Path.of("..", "contratos", "catalogos", "cwe.yaml").toFile())
                .get("cwe")
                .forEach(nodo -> cwes.add(nodo.get("id").asText()));
        for (Payload payload : cargador.cargarIncluidos()) {
            assertThat(oraculos).as(payload.id()).containsAll(payload.oraculos());
            if (payload.cwe() != null) {
                assertThat(cwes).as(payload.id()).contains(payload.cwe());
            }
        }
        List<Payload> incluidos = cargador.cargarIncluido("inyeccion-sql.yaml");
        for (Payload ejemplo :
                cargador.leer(
                        Path.of("..", "contratos", "ejemplos", "ataques", "inyeccion-sql.yaml"))) {
            assertThat(incluidos)
                    .filteredOn(p -> p.id().equals(ejemplo.id()))
                    .singleElement()
                    .satisfies(payload -> {
                        assertThat(payload.texto()).isEqualTo(ejemplo.texto());
                        assertThat(payload.aplicaA()).isEqualTo(ejemplo.aplicaA());
                        assertThat(payload.oraculos()).isEqualTo(ejemplo.oraculos());
                        assertThat(payload.cwe()).isEqualTo(ejemplo.cwe());
                    });
        }
    }

    @Test
    void leeElEjemploDeLosContratos() throws Exception {
        List<Payload> sqli =
                cargador.leer(
                        Path.of("..", "contratos", "ejemplos", "ataques", "inyeccion-sql.yaml"));

        assertThat(sqli).hasSize(2);
        Payload tautologia = sqli.get(0);
        assertThat(tautologia.usaCanario()).isTrue();
        assertThat(tautologia.texto()).contains("{canario}");
        assertThat(tautologia.cwe()).isEqualTo("CWE-89");
        assertThat(tautologia.sirvePara(TipoCampo.BUSQUEDA)).isTrue();
        assertThat(tautologia.sirvePara(TipoCampo.CONTRASENA)).isFalse();
    }

    @Test
    void rechazaPayloadsQueNoCumplenElEsquema() {
        String yaml =
                """
                - id: sin-mayusculas
                  categoria: VACIOS
                  payload: ""
                - id: CANARIO-PERDIDO
                  categoria: INYECCION_SQL
                  payload: "' OR 1=1"
                  usa_canario: true
                - id: SIN-CATEGORIA
                  payload: x
                """;

        assertThatThrownBy(() -> cargador.leer(new StringReader(yaml), "propios.yaml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("propios.yaml no es un archivo de payloads válido:")
                .hasMessageContaining("/0/id: ")
                .hasMessageContaining("/1/payload: ")
                .hasMessageContaining("/2: no se encontró la propiedad requerida 'categoria'");
    }

    @Test
    void rechazaIdsRepetidos() {
        String yaml =
                """
                - {id: UNO, categoria: VACIOS, payload: ""}
                - {id: UNO, categoria: VACIOS, payload: " "}
                """;

        assertThatThrownBy(() -> cargador.leer(new StringReader(yaml), "repetidos.yaml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/1/id: el id UNO ya se usa en /0/id");
    }

    @Test
    void sinAplicaAElPayloadSirveParaCualquierCampo() throws Exception {
        List<Payload> payloads =
                cargador.leer(
                        new StringReader("- {id: X, categoria: LONGITUD, payload: aaaa}"), "x");

        assertThat(payloads.get(0).aplicaA()).containsExactly(TipoCampo.CUALQUIERA);
        assertThat(payloads.get(0).sirvePara(TipoCampo.FECHA)).isTrue();
    }
}
