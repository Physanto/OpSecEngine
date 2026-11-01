package io.github.physanto.opsecengine.soporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Pruebas de {@link CargadorTrazas} con las trazas de ejemplo y con líneas inválidas. */
class CargadorTrazasTest {

    private final CargadorTrazas cargador = new CargadorTrazas();

    @ParameterizedTest
    @EnumSource(TrazaEjemplo.class)
    void cadaTrazaDeEjemploSeCargaEnOrdenYConContexto(TrazaEjemplo ejemplo) throws IOException {
        List<Traza> trazas = cargador.cargarEjemplo(ejemplo);

        assertThat(trazas).isNotEmpty();
        long anterior = -1;
        for (Traza traza : trazas) {
            assertThat(traza.eventos()).isNotEmpty();
            for (Evento evento : traza.eventos()) {
                assertThat(evento.secuencia()).isGreaterThan(anterior);
                assertThat(evento.contexto().casoId()).isEqualTo(traza.casoId());
                anterior = evento.secuencia();
            }
            assertThat(traza.eventos())
                    .extracting(Evento::instante)
                    .isSortedAccordingTo(Comparable::compareTo);
        }
    }

    @Test
    void lasTrazasDeEjemploTienenLosCasosEsperados() throws IOException {
        assertThat(cargador.cargarEjemplo(TrazaEjemplo.C01_C02_MENSAJES))
                .extracting(Traza::casoId)
                .containsExactly("C01", "C02");
        assertThat(cargador.cargarEjemploUnico(TrazaEjemplo.N3_LOCAL_SIN_LOGIN).casoId())
                .isEqualTo("N3");
        assertThat(cargador.cargarEjemploUnico(TrazaEjemplo.A1_ADMIN_ELIMINA).casoId())
                .isEqualTo("A1");
        assertThat(cargador.cargarEjemploUnico(TrazaEjemplo.C13_SQLI_LOGIN).casoId())
                .isEqualTo("C13");
        assertThat(cargador.cargarEjemploUnico(TrazaEjemplo.CASO_LIMPIO).casoId())
                .isEqualTo("LIMPIO");
        assertThatThrownBy(() -> cargador.cargarEjemploUnico(TrazaEjemplo.C01_C02_MENSAJES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laTrazaN3BorraSinSesionYConfirmaElEfecto() throws IOException {
        Traza traza = cargador.cargarEjemploUnico(TrazaEjemplo.N3_LOCAL_SIN_LOGIN);

        Evento sql = traza.primero(TipoEvento.SQL).orElseThrow();
        assertThat(sql.texto("operacion")).hasValue("DELETE");
        assertThat(sql.texto("tipoSentencia")).hasValue("STATEMENT");
        assertThat(traza.deTipo(TipoEvento.SESION)).isEmpty();
        assertThat(traza.eventos()).noneMatch(evento -> evento.contexto().sesionActiva());
        assertThat(traza.deTipo(TipoEvento.DIALOGO))
                .extracting(evento -> evento.texto("texto").orElseThrow())
                .containsExactly("Persona eliminada");
        assertThat(traza.deTipo(TipoEvento.ESTADO))
                .extracting(evento -> evento.booleano("existe").orElseThrow())
                .containsExactly(true, false);
    }

    @Test
    void laTrazaA1BorraConLaSesionDeAdmin() throws IOException {
        Traza traza = cargador.cargarEjemploUnico(TrazaEjemplo.A1_ADMIN_ELIMINA);

        Evento sesion = traza.primero(TipoEvento.SESION).orElseThrow();
        Evento borrado =
                traza.deTipo(TipoEvento.SQL).stream()
                        .filter(evento -> evento.texto("operacion").orElse("").equals("DELETE"))
                        .findFirst()
                        .orElseThrow();
        assertThat(sesion.texto("rol")).hasValue("admin");
        assertThat(borrado.secuencia()).isGreaterThan(sesion.secuencia());
        assertThat(borrado.contexto().sesionActiva()).isTrue();
        assertThat(borrado.contexto().rol()).isEqualTo("admin");
    }

    @Test
    void laTrazaC13LlevaElCanarioHastaUnStatement() throws IOException {
        Traza traza = cargador.cargarEjemploUnico(TrazaEjemplo.C13_SQLI_LOGIN);

        assertThat(traza.deTipo(TipoEvento.PASO_INICIO))
                .anyMatch(evento -> evento.texto("valor").orElse("").contains("K7Q2"));
        assertThat(traza.deTipo(TipoEvento.SQL))
                .anyMatch(evento ->
                        evento.texto("sql").orElse("").contains("K7Q2")
                                && evento.texto("tipoSentencia").orElse("").equals("STATEMENT"));
    }

    @Test
    void lasTrazasC01YC02MuestranMensajesDistintos() throws IOException {
        List<String> mensajes =
                cargador.cargarEjemplo(TrazaEjemplo.C01_C02_MENSAJES).stream()
                        .map(traza -> traza.primero(TipoEvento.DIALOGO).orElseThrow())
                        .map(evento -> evento.texto("texto").orElseThrow())
                        .toList();

        assertThat(mensajes).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    void leeUnArchivoEIgnoraLineasEnBlanco(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("traza.jsonl");
        Files.writeString(
                archivo,
                linea(0, "X1") + "\n\n" + linea(1, "X1") + "\n" + linea(5, "X2") + "\n",
                StandardCharsets.UTF_8);

        List<Traza> trazas = cargador.leer(archivo);

        assertThat(trazas).extracting(Traza::casoId).containsExactly("X1", "X2");
        assertThat(trazas.get(0).eventos()).hasSize(2);
        assertThat(cargador.leer(new StringReader(""), "vacio")).isEmpty();
    }

    @Test
    void rechazaLineasInvalidasIndicandoLaLinea() {
        assertThatThrownBy(() -> leer(linea(0, "X1") + "\n{no es json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("prueba:2:");
        assertThatThrownBy(() -> leer(linea(3, "X1") + "\n" + linea(3, "X1")))
                .hasMessageContaining("prueba:2:")
                .hasMessageContaining("secuencia");
        assertThatThrownBy(() -> leer(linea(0, "X1").replace("\"SQL\"", "\"RED\"")))
                .hasMessageContaining("tipo de evento desconocido: RED");
        assertThatThrownBy(() -> leer(linea(0, "X1").replace("\"casoId\":\"X1\",", "")))
                .hasMessageContaining("casoId");
        assertThatThrownBy(() -> leer(linea(0, "X1").replace("2026-10-30T10:00:00Z", "ayer")))
                .hasMessageStartingWith("prueba:1:");
    }

    private List<Traza> leer(String contenido) throws IOException {
        return cargador.leer(new StringReader(contenido), "prueba");
    }

    private static String linea(long secuencia, String casoId) {
        return "{\"secuencia\":"
                + secuencia
                + ",\"instante\":\"2026-10-30T10:00:00Z\",\"tipo\":\"SQL\",\"contexto\":{\"casoId\":\""
                + casoId
                + "\",\"numeroPaso\":1,\"sesionActiva\":false},\"datos\":{\"sql\":\"SELECT 1\","
                + "\"tipoSentencia\":\"PREPARED\"}}";
    }
}
