package io.github.physanto.opsecengine.dominio.mapa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MapaAppTest {

    private static final Ventana PORTADA = new Ventana("portada", "Bienvenido", "app.Portada");
    private static final Ventana LOGIN = new Ventana("login", "Iniciar sesión", "app.Login");
    private static final Ventana MENU_ADMIN = new Ventana("menu-admin", null, "app.MenuAdmin");
    private static final Ventana MENU_CLIENTE = new Ventana("menu-cliente", null, "app.Menu");
    private static final Ventana ELIMINAR = new Ventana("eliminar", "Eliminar", "app.Eliminar");

    private static final String ADMIN = MapaApp.contextoRol("admin");
    private static final String CLIENTE = MapaApp.contextoRol("cliente");

    private static Transicion pulsar(Ventana origen, Ventana destino, String contexto) {
        return new Transicion(
                origen.id(), destino.id(), Accion.PULSAR, Localizador.porTexto("x"), contexto);
    }

    private static final MapaApp MAPA =
            new MapaApp(
                    Set.of(PORTADA, LOGIN, MENU_ADMIN, MENU_CLIENTE, ELIMINAR),
                    List.of(
                            pulsar(PORTADA, LOGIN, MapaApp.SIN_SESION),
                            pulsar(PORTADA, LOGIN, ADMIN),
                            pulsar(LOGIN, MENU_ADMIN, ADMIN),
                            pulsar(MENU_ADMIN, ELIMINAR, ADMIN),
                            pulsar(ELIMINAR, MENU_ADMIN, ADMIN),
                            pulsar(PORTADA, LOGIN, CLIENTE),
                            pulsar(LOGIN, MENU_CLIENTE, CLIENTE)));

    @Test
    void alcanzablesDesdeSigueSoloElContextoPedido() {
        assertThat(MAPA.alcanzablesDesde("portada", ADMIN))
                .containsExactly(PORTADA, LOGIN, MENU_ADMIN, ELIMINAR);
        assertThat(MAPA.alcanzablesDesde("portada", CLIENTE))
                .containsExactly(PORTADA, LOGIN, MENU_CLIENTE);
        assertThat(MAPA.alcanzablesDesde("portada", MapaApp.SIN_SESION))
                .containsExactly(PORTADA, LOGIN);
    }

    @Test
    void alcanzablesDesdeIncluyeLaPartidaYToleraCiclos() {
        assertThat(MAPA.alcanzablesDesde("eliminar", ADMIN)).containsExactly(ELIMINAR, MENU_ADMIN);
        assertThat(MAPA.alcanzablesDesde("eliminar", CLIENTE)).containsExactly(ELIMINAR);
    }

    @Test
    void alcanzablesDesdeUnaVentanaDesconocidaEsVacio() {
        assertThat(MAPA.alcanzablesDesde("no-existe", ADMIN)).isEmpty();
        assertThat(MAPA.alcanzablesDesde("portada", "rol:invitado")).containsExactly(PORTADA);
        assertThatThrownBy(() -> MAPA.alcanzablesDesde(null, ADMIN))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void contextosEnOrdenDeAparicion() {
        assertThat(MAPA.contextos()).containsExactly(MapaApp.SIN_SESION, ADMIN, CLIENTE);
        assertThat(new MapaApp(null, null).contextos()).isEmpty();
    }

    @Test
    void construyeLosContextosDocumentados() {
        assertThat(ADMIN).isEqualTo("rol:admin");
        assertThat(MapaApp.contextoEntrada("app.MenuAdmin")).isEqualTo("entrada:app.MenuAdmin");
        assertThat(MapaApp.SIN_SESION).isEqualTo("sin-sesion");
    }

    @Test
    void validaLaEstructuraDelMapa() {
        Ventana otraPortada = new Ventana("portada", "Otra", "app.Otra");

        assertThatThrownBy(() -> new MapaApp(Set.of(PORTADA, otraPortada), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("portada");
        assertThatThrownBy(
                        () ->
                                new MapaApp(
                                        Set.of(PORTADA),
                                        List.of(pulsar(PORTADA, LOGIN, MapaApp.SIN_SESION))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transicionYObjetivoValidanSusDatos() {
        assertThat(new Transicion("a", "b", Accion.ESPERAR, null, MapaApp.SIN_SESION).control())
                .isNull();
        assertThatThrownBy(() -> new Transicion("a", "b", Accion.PULSAR, null, ADMIN))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new Transicion(
                                        "a", "b", Accion.PULSAR, Localizador.porTexto("x"), " "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(Objetivo.todaLaApp().esTodaLaApp()).isTrue();
        assertThat(new Objetivo(null, "eliminar", Localizador.porTexto("Eliminar")).esTodaLaApp())
                .isFalse();
        assertThatThrownBy(() -> new Objetivo(null, null, Localizador.porTexto("Eliminar")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
