package io.github.physanto.opsecengine.dominio.configuracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RolTest {

    @Test
    void toStringNoMuestraLaContrasena() {
        Rol rol = new Rol("admin", "Admin", "secreta-12345");

        assertThat(rol.toString()).contains("admin", "Admin").doesNotContain("secreta-12345");
    }

    @Test
    void laContrasenaSigueDisponibleEnSuAccesor() {
        assertThat(new Rol("admin", "Admin", "12345").contrasena()).isEqualTo("12345");
    }

    @Test
    void validaSusDatos() {
        assertThatThrownBy(() -> new Rol(null, "Admin", "12345"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Rol(" ", "Admin", "12345"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Rol("admin", null, "12345"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Rol("admin", "Admin", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("contraseña");
    }
}
