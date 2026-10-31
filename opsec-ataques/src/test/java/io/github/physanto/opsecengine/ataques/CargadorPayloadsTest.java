package io.github.physanto.opsecengine.ataques;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
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
                .allSatisfy(
                        payload -> {
                            assertThat(payload.categoria()).isEqualTo(CategoriaPayload.VACIOS);
                            assertThat(payload.usaCanario()).isFalse();
                            assertThat(payload.sirvePara(TipoCampo.CONTRASENA)).isTrue();
                            assertThat(payload.oraculos()).contains("EXCEPCION-NO-CAPTURADA");
                        });
    }

    @Test
    void cargaTodosLosArchivosIncluidos() throws Exception {
        assertThat(cargador.cargarIncluidos()).hasSize(4);
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
