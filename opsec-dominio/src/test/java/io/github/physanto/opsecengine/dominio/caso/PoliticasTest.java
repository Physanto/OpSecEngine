package io.github.physanto.opsecengine.dominio.caso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.traza.ContextoEvento;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PoliticasTest {

    private static final Senal ERROR =
            new Senal(TipoEvento.DIALOGO, Map.of("tipoMensaje", "ERROR"));
    private static final Senal ENTRA = new Senal(TipoEvento.SESION, Map.of("activa", true));

    private static Evento evento(TipoEvento tipo, Map<String, Object> datos) {
        return new Evento(1, Instant.EPOCH, tipo, ContextoEvento.fueraDeCaso(), datos);
    }

    @Test
    void laSenalCoincideConTipoYTodosSusCampos() {
        Senal senal =
                new Senal(TipoEvento.DIALOGO, Map.of("tipoMensaje", "ERROR", "titulo", "Login"));

        assertThat(
                        senal.coincide(
                                evento(
                                        TipoEvento.DIALOGO,
                                        Map.of(
                                                "texto", "x",
                                                "tipoMensaje", "ERROR",
                                                "titulo", "Login"))))
                .isTrue();
        assertThat(senal.coincide(evento(TipoEvento.DIALOGO, Map.of("tipoMensaje", "ERROR"))))
                .isFalse();
        assertThat(senal.coincide(evento(TipoEvento.CONSOLA, Map.of("tipoMensaje", "ERROR"))))
                .isFalse();
        assertThat(senal.coincide(null)).isFalse();
    }

    @Test
    void laSenalComparaNumerosPorValorYNoConvierteTextos() {
        Senal filas = new Senal(TipoEvento.SQL, Map.of("filasAfectadas", 1));

        assertThat(filas.coincide(evento(TipoEvento.SQL, Map.of("filasAfectadas", 1L)))).isTrue();
        assertThat(filas.coincide(evento(TipoEvento.SQL, Map.of("filasAfectadas", 1.0)))).isTrue();
        assertThat(filas.coincide(evento(TipoEvento.SQL, Map.of("filasAfectadas", "1")))).isFalse();
        assertThat(ENTRA.coincide(evento(TipoEvento.SESION, Map.of("activa", "true")))).isFalse();
    }

    @Test
    void laSenalValidaSusCamposYEsInmutable() {
        assertThatThrownBy(() -> new Senal(TipoEvento.DIALOGO, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Senal(TipoEvento.DIALOGO, Map.of("opciones", List.of("a"))))
                .isInstanceOf(IllegalArgumentException.class);
        Map<String, Object> conNull = new HashMap<>();
        conNull.put("texto", null);
        assertThatThrownBy(() -> new Senal(TipoEvento.DIALOGO, conNull))
                .isInstanceOf(NullPointerException.class);

        Map<String, Object> campos = new HashMap<>(Map.of("tipoMensaje", "ERROR"));
        Senal senal = new Senal(TipoEvento.DIALOGO, campos);
        campos.put("titulo", "otro");
        assertThat(senal.campos()).containsOnlyKeys("tipoMensaje");
    }

    @Test
    void laPoliticaDeValidacionExigePasosOrdenadosYSenalesDistintas() {
        PoliticaValidacion politica =
                new PoliticaValidacion(3, 4, "-5", List.of(ENTRA), List.of(ERROR));
        assertThat(politica.valorInvalido()).isEqualTo("-5");

        assertThatThrownBy(() -> new PoliticaValidacion(0, 4, "x", List.of(ENTRA), List.of(ERROR)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaValidacion(4, 4, "x", List.of(ENTRA), List.of(ERROR)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaValidacion(3, 4, "x", List.of(), List.of(ERROR)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaValidacion(3, 4, "x", List.of(ERROR), List.of(ERROR)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaValidacion(3, 4, null, List.of(ENTRA), List.of(ERROR)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void elIntentoNecesitaDosEscriturasAntesDelEnvio() {
        assertThat(new IntentoLogin(4, 3, 5, "Admin", "x", true).debil()).isTrue();

        assertThatThrownBy(() -> new IntentoLogin(3, 3, 5, "Admin", "x", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntentoLogin(3, 4, 4, "Admin", "x", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntentoLogin(3, 4, 5, null, "x", false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void laPoliticaDeAutenticacionValidaUmbralesIntentosYSenales() {
        List<IntentoLogin> intentos =
                new ArrayList<>(
                        List.of(
                                new IntentoLogin(3, 4, 5, "Admin", "mala", false),
                                new IntentoLogin(7, 8, 9, "Admin", "12345", true)));
        Senal bloqueo = new Senal(TipoEvento.DIALOGO, Map.of("tipoMensaje", "ADVERTENCIA"));

        PoliticaAutenticacion politica =
                new PoliticaAutenticacion(intentos, 3, 30_000, List.of(ERROR), List.of(bloqueo));
        intentos.clear();
        assertThat(politica.intentos()).hasSize(2);

        List<IntentoLogin> solapados =
                List.of(
                        new IntentoLogin(3, 4, 6, "Admin", "a", false),
                        new IntentoLogin(5, 7, 8, "Admin", "b", false));
        assertThatThrownBy(() ->
                        new PoliticaAutenticacion(
                                solapados, 3, 1, List.of(ERROR), List.of(bloqueo)))
                .isInstanceOf(IllegalArgumentException.class);
        List<IntentoLogin> uno = List.of(new IntentoLogin(3, 4, 5, "Admin", "a", false));
        assertThatThrownBy(() ->
                        new PoliticaAutenticacion(uno, 0, 1, List.of(ERROR), List.of(bloqueo)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new PoliticaAutenticacion(uno, 3, 0, List.of(ERROR), List.of(bloqueo)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new PoliticaAutenticacion(uno, 3, 1, List.of(ERROR), List.of(ERROR)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new PoliticaAutenticacion(
                                List.of(), 3, 1, List.of(ERROR), List.of(bloqueo)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laComparacionNecesitaDosCasosDistintosYPasosPositivos() {
        assertThat(new ComparacionLogin("C01", 5, "C02", 5).casoContrasenaIncorrecta())
                .isEqualTo("C02");

        assertThatThrownBy(() -> new ComparacionLogin("C01", 5, "C01", 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComparacionLogin("C01", 0, "C02", 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComparacionLogin(" ", 5, "C02", 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComparacionLogin(null, 5, "C02", 5))
                .isInstanceOf(NullPointerException.class);
    }
}
