package io.github.physanto.opsecengine.practica.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.practica.datos.Rol;
import io.github.physanto.opsecengine.practica.fallos.Fallo;
import io.github.physanto.opsecengine.practica.fallos.Interruptores;
import org.junit.jupiter.api.Test;

/** Pruebas de {@link ControlAcceso} y de las sesiones que recibe. */
class ControlAccesoTest {

    private static final Sesion ADMIN = Sesion.iniciada("Admin", Rol.ADMINISTRADOR);
    private static final Sesion CLIENTE = Sesion.iniciada("Cliente", Rol.CLIENTE);

    @Test
    void soloElAdministradorAutenticadoAbreVentanasAdministrativas() {
        ControlAcceso control = new ControlAcceso(Interruptores.todosCorregidos());

        assertThat(control.permiteAdministracion(ADMIN)).isTrue();
        assertThat(control.permiteAdministracion(CLIENTE)).isFalse();
        assertThat(control.permiteAdministracion(Sesion.sinIniciar())).isFalse();
        assertThat(control.permiteAdministracion(Sesion.local())).isFalse();
    }

    @Test
    void conF2ActivoElModoLocalAbreElMenuSinSesion() {
        ControlAcceso control = new ControlAcceso(Interruptores.corrigiendo(Fallo.F3));

        assertThat(control.modoLocalSinSesion()).isTrue();
        assertThat(control.permiteAdministracion(Sesion.local())).isTrue();
        assertThat(control.permiteAdministracion(Sesion.sinIniciar())).isFalse();
        assertThat(control.permiteAdministracion(CLIENTE)).isFalse();
    }

    @Test
    void conF2CorregidoElModoLocalExigeSesion() {
        ControlAcceso control = new ControlAcceso(Interruptores.corrigiendo(Fallo.F2));

        assertThat(control.modoLocalSinSesion()).isFalse();
        assertThat(control.permiteAdministracion(Sesion.local())).isFalse();
    }

    @Test
    void elInterruptorF3DecideSiElMainAbreLaVentanaSinSesion() {
        assertThat(new ControlAcceso(Interruptores.todosActivos()).eliminarPersonaSinSesion())
                .isTrue();
        assertThat(
                        new ControlAcceso(Interruptores.corrigiendo(Fallo.F3))
                                .eliminarPersonaSinSesion())
                .isFalse();
    }

    @Test
    void lasSesionesDescribenSuEstado() {
        assertThat(Sesion.sinIniciar().estaIniciada()).isFalse();
        assertThat(Sesion.sinIniciar().usuario()).isEmpty();
        assertThat(Sesion.local().esModoLocal()).isTrue();
        assertThat(Sesion.local().estaIniciada()).isFalse();
        assertThat(Sesion.local().esAdministrador()).isFalse();
        assertThat(ADMIN.esModoLocal()).isFalse();
        assertThat(ADMIN.toString()).isEqualTo("Sesion[Admin, ADMINISTRADOR]");
        assertThat(Sesion.local().toString()).isEqualTo("Sesion[modo local]");
        assertThat(Sesion.sinIniciar().toString()).isEqualTo("Sesion[sin iniciar]");
    }
}
