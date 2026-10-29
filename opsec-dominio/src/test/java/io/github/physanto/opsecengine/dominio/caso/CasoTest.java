package io.github.physanto.opsecengine.dominio.caso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CasoTest {

    private static Paso pulsar(int orden, String texto) {
        return new Paso(orden, Accion.PULSAR, Localizador.porTexto(texto), null, null, null);
    }

    @Test
    void lasColeccionesNullQuedanVacias() {
        Caso caso = new Caso("SEC-001", null, null, null, null, null, null);

        assertThat(caso.pasos()).isEmpty();
        assertThat(caso.ataques()).isEmpty();
        assertThat(caso.oraculos()).isEmpty();
        assertThat(caso.extensiones()).isEmpty();
    }

    @Test
    void rolNullSignificaSinSesion() {
        assertThat(new Caso("N3", null, null, null, null, null, null).sinSesion()).isTrue();
        assertThat(new Caso("A1", null, "admin", null, null, null, null).sinSesion()).isFalse();
    }

    @Test
    void validaIdYRol() {
        assertThatThrownBy(() -> new Caso(null, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");
        assertThatThrownBy(() -> new Caso(" ", null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Caso("A1", null, "", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void esInmutable() {
        List<Paso> pasos = new ArrayList<>(List.of(pulsar(1, "Avanzar")));
        List<String> ataques = new ArrayList<>(List.of("sqli-basico"));
        Caso caso = new Caso("SEC-001", "Login", null, pasos, ataques, List.of(), null);

        pasos.add(pulsar(2, "Nube"));
        ataques.clear();

        assertThat(caso.pasos()).hasSize(1);
        assertThat(caso.ataques()).containsExactly("sqli-basico");
        assertThatThrownBy(() -> caso.pasos().add(pulsar(3, "Salir")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rechazaElementosNull() {
        List<String> oraculos = new ArrayList<>();
        oraculos.add(null);

        assertThatThrownBy(() -> new Caso("C1", null, null, null, null, oraculos, null))
                .isInstanceOf(NullPointerException.class);
    }
}
