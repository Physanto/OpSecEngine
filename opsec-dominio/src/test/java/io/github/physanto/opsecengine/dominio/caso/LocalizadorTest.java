package io.github.physanto.opsecengine.dominio.caso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalizadorTest {

    @Test
    void porTextoCreaUnCriterioDeTexto() {
        assertThat(Localizador.porTexto("Eliminar").criterios())
                .containsExactly(new Criterio(EstrategiaLocalizacion.TEXTO, "Eliminar"));
    }

    @Test
    void porEtiquetaCreaUnCriterioDeEtiqueta() {
        assertThat(Localizador.porEtiqueta("Usuario").criterios())
                .containsExactly(new Criterio(EstrategiaLocalizacion.ETIQUETA, "Usuario"));
    }

    @Test
    void porClaseVentanaCreaUnCriterioDeClaseVentana() {
        assertThat(Localizador.porClaseVentana("GUI_Local.Portada").criterios())
                .containsExactly(
                        new Criterio(EstrategiaLocalizacion.CLASE_VENTANA, "GUI_Local.Portada"));
    }

    @Test
    void lasFabricasRechazanValoresInvalidos() {
        assertThatThrownBy(() -> Localizador.porTexto(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Localizador.porEtiqueta("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conservaElOrdenDePrioridad() {
        Criterio primero = new Criterio(EstrategiaLocalizacion.NOMBRE_COMPONENTE, "btnEliminar");
        Criterio segundo = new Criterio(EstrategiaLocalizacion.TEXTO, "Eliminar");

        assertThat(new Localizador(List.of(primero, segundo)).criterios())
                .containsExactly(primero, segundo);
    }

    @Test
    void exigeAlMenosUnCriterio() {
        assertThatThrownBy(() -> new Localizador(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Localizador(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void esInmutable() {
        List<Criterio> criterios = new ArrayList<>();
        criterios.add(new Criterio(EstrategiaLocalizacion.TEXTO, "Aceptar"));
        Localizador localizador = new Localizador(criterios);

        criterios.add(new Criterio(EstrategiaLocalizacion.TIPO, "JButton"));

        assertThat(localizador.criterios()).hasSize(1);
        assertThatThrownBy(() -> localizador.criterios().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void criterioValidaEstrategiaYValor() {
        assertThatThrownBy(() -> new Criterio(null, "Aceptar"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("estrategia");
        assertThatThrownBy(() -> new Criterio(EstrategiaLocalizacion.TEXTO, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Criterio(EstrategiaLocalizacion.TEXTO, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
