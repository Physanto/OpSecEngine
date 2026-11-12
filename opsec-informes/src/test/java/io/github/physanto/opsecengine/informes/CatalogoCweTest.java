package io.github.physanto.opsecengine.informes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.github.physanto.opsecengine.informes.CatalogoCwe.DescripcionCwe;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de {@link CatalogoCwe}: el recurso es el catálogo oficial del contrato, cubre todos los
 * CWE de los oráculos y se lee de forma estricta.
 */
class CatalogoCweTest {

    @Test
    void todoCweDelCatalogoDeOraculosTieneExplicacionYRecomendacion() throws Exception {
        Set<String> deOraculos = cwesDelCatalogoDeOraculos();

        assertThat(deOraculos).isNotEmpty();
        for (String cwe : deOraculos) {
            assertThat(CatalogoCwe.oficial().buscar(cwe))
                    .as("explicación de " + cwe)
                    .hasValueSatisfying(d -> {
                        assertThat(d.nombre()).isNotBlank();
                        assertThat(d.explicacion()).isNotBlank();
                        assertThat(d.recomendacion()).isNotBlank();
                    });
        }
    }

    @Test
    void elRecursoEsElCatalogoDelContratoSinCopiasAMano() throws Exception {
        Path contrato = Path.of("..", "contratos", "catalogos", "cwe.yaml");
        String recurso;
        try (InputStream flujo = CatalogoCwe.class.getResourceAsStream(CatalogoCwe.RECURSO)) {
            assertThat(flujo).as("recurso " + CatalogoCwe.RECURSO).isNotNull();
            recurso = new String(flujo.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(recurso).isEqualTo(Files.readString(contrato, StandardCharsets.UTF_8));
        assertThat(CatalogoCwe.oficial().entradas()).hasSize(contarEntradas(recurso));
    }

    @Test
    void buscaSinImportarMayusculasNiEspacios() {
        DescripcionCwe sinSesion = CatalogoCwe.oficial().buscar("CWE-306").orElseThrow();

        assertThat(CatalogoCwe.oficial().buscar(" cwe-306 ")).contains(sinSesion);
        assertThat(sinSesion.id()).isEqualTo("CWE-306");
        assertThat(sinSesion.enlace()).isEqualTo("https://cwe.mitre.org/data/definitions/306.html");
    }

    @Test
    void unCweDesconocidoONuloNoTieneDescripcion() {
        assertThat(CatalogoCwe.oficial().buscar("CWE-99999")).isEmpty();
        assertThat(CatalogoCwe.oficial().buscar(null)).isEmpty();
    }

    @Test
    void lasExplicacionesNoArrastranSaltosDeLineaDelYaml() {
        DescripcionCwe inyeccion = CatalogoCwe.oficial().buscar("CWE-89").orElseThrow();

        assertThat(inyeccion.nombre()).isEqualTo("Inyección SQL");
        assertThat(inyeccion.explicacion()).doesNotContain("\n").startsWith("La app arma");
    }

    @Test
    void rechazaUnCatalogoSinLista() {
        assertThatThrownBy(() -> leer("version: 1\n"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("'cwe'");
    }

    @Test
    void rechazaUnaEntradaIncompleta() {
        assertThatThrownBy(() -> leer("cwe:\n  - id: CWE-1\n    nombre: X\n    explicacion: Y\n"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("'recomendacion'");
    }

    @Test
    void elMapaDeEntradasEsInmutable() {
        assertThatThrownBy(() -> CatalogoCwe.oficial().entradas().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static CatalogoCwe leer(String yaml) throws IOException {
        return CatalogoCwe.leer(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    /** CWE que declaran los oráculos del catálogo oficial ({@code oraculos.yaml}). */
    private static Set<String> cwesDelCatalogoDeOraculos() throws IOException {
        try (InputStream flujo = CatalogoCwe.class.getResourceAsStream("catalogos/oraculos.yaml")) {
            assertThat(flujo).as("recurso de prueba catalogos/oraculos.yaml").isNotNull();
            JsonNode raiz = new YAMLMapper().readTree(flujo);
            Set<String> cwes = new TreeSet<>();
            Matcher m = Pattern.compile("CWE-[0-9]+").matcher(raiz.toString());
            while (m.find()) {
                cwes.add(m.group());
            }
            return cwes;
        }
    }

    private static int contarEntradas(String yaml) {
        return (int) yaml.lines().filter(l -> l.startsWith("  - id: CWE-")).count();
    }
}
