package io.github.physanto.opsecengine.verificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pruebas de los ejemplos de {@code contratos/ejemplos/}: cada archivo cumple su esquema y las
 * referencias entre archivos (roles, oráculos, ataques y CWE) existen.
 */
class EjemplosTest {

    static Stream<Path> ejemplos() {
        return Contratos.archivos(Contratos.EJEMPLOS).stream();
    }

    @ParameterizedTest
    @MethodSource("ejemplos")
    void cadaEjemploCumpleSuEsquema(Path archivo) {
        String esquema = esquemaDe(archivo);

        for (JsonNode documento : documentos(archivo)) {
            assertThat(Contratos.errores(esquema, documento))
                    .as("errores de %s contra %s", archivo, esquema)
                    .isEmpty();
        }
    }

    @Test
    void cadaEsquemaTieneAlMenosUnEjemplo() {
        Set<String> usados = ejemplos().map(EjemplosTest::esquemaDe).collect(Collectors.toSet());

        assertThat(usados)
                .containsExactlyInAnyOrder(
                        Contratos.PROYECTO,
                        Contratos.SUITE,
                        Contratos.PAYLOAD,
                        Contratos.ORACULO_REGLA,
                        Contratos.TRAZA_EVENTO);
    }

    @Test
    void laSuiteDeLoginTieneLosCasosDeSec001() {
        JsonNode suite =
                Contratos.leerYaml(Contratos.EJEMPLOS.resolve("suites").resolve("login.yaml"));

        assertThat(textos(suite.path("casos"), "id"))
                .containsExactly("C01", "C02", "C04", "C13", "C15");
    }

    @Test
    void elProyectoTieneLosRolesClienteYAdmin() {
        JsonNode proyecto = proyecto();

        assertThat(campos(proyecto.path("roles"))).containsExactly("cliente", "admin");
        for (JsonNode roles : proyecto.path("politica")) {
            roles.forEach(rol -> assertThat(campos(proyecto.path("roles"))).contains(rol.asText()));
        }
    }

    @Test
    void lasReferenciasDeLasSuitesExisten() {
        Set<String> roles = Set.copyOf(campos(proyecto().path("roles")));
        Set<String> oraculos = new HashSet<>(Catalogos.idsDeOraculos());
        oraculos.addAll(idsDeEjemplos("oraculos"));
        Set<String> ataques = Set.copyOf(idsDeEjemplos("ataques"));

        for (Path archivo : Contratos.archivos(Contratos.EJEMPLOS.resolve("suites"))) {
            JsonNode casos = Contratos.leerYaml(archivo).path("casos");
            assertThat(textos(casos, "id"))
                    .as("ids de casos en %s", archivo)
                    .doesNotHaveDuplicates();
            for (JsonNode caso : casos) {
                if (caso.has("rol")) {
                    assertThat(roles).contains(caso.path("rol").asText());
                }
                caso.path("oraculos").forEach(id -> assertThat(oraculos).contains(id.asText()));
                caso.path("ataques").forEach(id -> assertThat(ataques).contains(id.asText()));
            }
        }
    }

    @Test
    void lasReglasYLosPayloadsUsanCweDelCatalogoYNoReusanIdsReservados() {
        Set<String> cwe = Set.copyOf(Catalogos.idsDeCwe());
        List<JsonNode> entradas = new ArrayList<>();
        entradasDeEjemplos("oraculos").forEach(regla -> entradas.add(regla.path("entonces")));
        entradas.addAll(entradasDeEjemplos("ataques"));

        for (JsonNode entrada : entradas) {
            if (entrada.has("cwe")) {
                assertThat(cwe).contains(entrada.path("cwe").asText());
            }
        }
        assertThat(idsDeEjemplos("oraculos"))
                .doesNotContainAnyElementsOf(Catalogos.idsDeOraculos());
    }

    @Test
    void lasTrazasSiguenElCatalogoDeEventos() {
        Map<String, JsonNode> catalogo = new TreeMap<>();
        Catalogos.eventos().forEach(evento -> catalogo.put(evento.path("tipo").asText(), evento));

        for (Path archivo : ejemplos().filter(EjemplosTest::esTraza).collect(Collectors.toList())) {
            long anterior = -1;
            for (JsonNode evento : Contratos.leerJsonLineas(archivo)) {
                long secuencia = evento.path("secuencia").asLong();
                assertThat(secuencia)
                        .as("secuencia creciente en %s", archivo)
                        .isGreaterThan(anterior);
                anterior = secuencia;

                JsonNode campos = catalogo.get(evento.path("tipo").asText()).path("campos");
                List<String> conocidos = textos(campos, "nombre");
                List<String> obligatorios = new ArrayList<>();
                campos.forEach(
                        campo -> {
                            if (campo.path("obligatorio").asBoolean()) {
                                obligatorios.add(campo.path("nombre").asText());
                            }
                        });
                List<String> presentes = campos(evento.path("datos"));
                assertThat(presentes)
                        .as("datos del evento %s en %s", secuencia, archivo)
                        .containsAll(obligatorios);
                assertThat(conocidos)
                        .as("datos del evento %s en %s", secuencia, archivo)
                        .containsAll(presentes);
            }
        }
    }

    private static String esquemaDe(Path archivo) {
        Path relativa = Contratos.EJEMPLOS.relativize(archivo);
        String nombre = relativa.getFileName().toString();
        String carpeta = relativa.getNameCount() > 1 ? relativa.getName(0).toString() : "";
        if (relativa.getNameCount() == 1 && nombre.equals("proyecto.yaml")) {
            return Contratos.PROYECTO;
        }
        if (carpeta.equals("suites") && nombre.endsWith(".yaml")) {
            return Contratos.SUITE;
        }
        if (carpeta.equals("ataques") && nombre.endsWith(".yaml")) {
            return Contratos.PAYLOAD;
        }
        if (carpeta.equals("oraculos") && nombre.endsWith(".yaml")) {
            return Contratos.ORACULO_REGLA;
        }
        if (esTraza(archivo)) {
            return Contratos.TRAZA_EVENTO;
        }
        return fail("El ejemplo %s no corresponde a ningún esquema", relativa);
    }

    private static boolean esTraza(Path archivo) {
        Path relativa = Contratos.EJEMPLOS.relativize(archivo);
        return relativa.getName(0).toString().equals("resultados")
                && relativa.getFileName().toString().equals("traza.jsonl");
    }

    private static List<JsonNode> documentos(Path archivo) {
        return esTraza(archivo)
                ? Contratos.leerJsonLineas(archivo)
                : List.of(Contratos.leerYaml(archivo));
    }

    private static JsonNode proyecto() {
        return Contratos.leerYaml(Contratos.EJEMPLOS.resolve("proyecto.yaml"));
    }

    private static List<JsonNode> entradasDeEjemplos(String carpeta) {
        List<JsonNode> entradas = new ArrayList<>();
        for (Path archivo : Contratos.archivos(Contratos.EJEMPLOS.resolve(carpeta))) {
            Contratos.leerYaml(archivo).forEach(entradas::add);
        }
        return entradas;
    }

    private static List<String> idsDeEjemplos(String carpeta) {
        return entradasDeEjemplos(carpeta).stream()
                .map(entrada -> entrada.path("id").asText())
                .collect(Collectors.toList());
    }

    private static List<String> textos(JsonNode arreglo, String campo) {
        List<String> textos = new ArrayList<>();
        arreglo.forEach(elemento -> textos.add(elemento.path(campo).asText()));
        return textos;
    }

    private static List<String> campos(JsonNode objeto) {
        List<String> nombres = new ArrayList<>();
        objeto.fieldNames().forEachRemaining(nombres::add);
        return nombres;
    }
}
