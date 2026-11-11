package io.github.physanto.opsecengine.practica.fallos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Pruebas de {@link Interruptores} y del catálogo {@link Fallo}. */
class InterruptoresTest {

    @AfterEach
    void limpiarPropiedad() {
        System.clearProperty(Interruptores.PROPIEDAD);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", ",", " , "})
    void sinValorTodosLosFallosQuedanActivos(String valor) {
        Interruptores interruptores = Interruptores.desdeTexto(valor);

        assertThat(interruptores.activos()).containsExactly(Fallo.values());
        assertThat(interruptores.corregidos()).isEmpty();
    }

    @Test
    void unaListaCorrigeSoloEsosFallos() {
        Interruptores interruptores = Interruptores.desdeTexto("F1,F4");

        assertThat(interruptores.corregidos()).containsExactly(Fallo.F1, Fallo.F4);
        assertThat(interruptores.activos())
                .containsExactly(Fallo.F2, Fallo.F3, Fallo.F5, Fallo.F6, Fallo.F7, Fallo.F8);
        assertThat(interruptores.estaActivo(Fallo.F1)).isFalse();
        assertThat(interruptores.estaActivo(Fallo.F2)).isTrue();
    }

    @Test
    void losIdsAdmitenMinusculasEspaciosYRepeticiones() {
        assertThat(Interruptores.desdeTexto(" f2 , F3,,f2 ").corregidos())
                .containsExactly(Fallo.F2, Fallo.F3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"todos", "TODOS", " Todos ", "F1,todos"})
    void todosCorrigeTodosLosFallos(String valor) {
        Interruptores interruptores = Interruptores.desdeTexto(valor);

        assertThat(interruptores.corregidos()).containsExactly(Fallo.values());
        assertThat(interruptores.activos()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"F9", "F1,X", "1", "todo"})
    void unIdDesconocidoEsUnError(String valor) {
        assertThatThrownBy(() -> Interruptores.desdeTexto(valor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(Interruptores.PROPIEDAD)
                .hasMessageContaining("F8");
    }

    @Test
    void leeLaPropiedadDelSistema() {
        assertThat(Interruptores.desdeSistema().corregidos()).isEmpty();

        System.setProperty(Interruptores.PROPIEDAD, "F5,F6");

        assertThat(Interruptores.desdeSistema().corregidos()).containsExactly(Fallo.F5, Fallo.F6);
    }

    @Test
    void lasFabricasEquivalenALosTextos() {
        assertThat(Interruptores.todosActivos().corregidos()).isEmpty();
        assertThat(Interruptores.todosCorregidos().activos()).isEmpty();
        assertThat(Interruptores.corrigiendo(Fallo.F3).corregidos()).containsExactly(Fallo.F3);
    }

    @Test
    void losConjuntosSonInmutables() {
        Interruptores interruptores = Interruptores.corrigiendo(Fallo.F1);

        assertThatThrownBy(() -> interruptores.corregidos().add(Fallo.F2))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> interruptores.activos().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void elTextoListaActivosYCorregidos() {
        assertThat(Interruptores.corrigiendo(Fallo.F1, Fallo.F4).toString())
                .isEqualTo("Fallos activos: F2, F3, F5, F6, F7, F8; corregidos: F1, F4");
        assertThat(Interruptores.todosActivos().toString()).endsWith("corregidos: ninguno");
    }

    @ParameterizedTest
    @EnumSource(Fallo.class)
    void cadaFalloTieneDescripcionYCwe(Fallo fallo) {
        assertThat(fallo.descripcion()).isNotBlank();
        assertThat(fallo.cwe()).matches("CWE-\\d+");
    }
}
