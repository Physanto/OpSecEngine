package io.github.physanto.opsecengine.dominio.configuracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProyectoTest {

    private static final Path JAR = Path.of("dist", "MiApp.jar");
    private static final Rol ADMIN = new Rol("admin", "Admin", "12345");
    private static final Rol CLIENTE = new Rol("cliente", "Cliente", "12345");

    private static Proyecto conRoles(List<Rol> roles) {
        return new Proyecto("clientes", JAR, null, null, null, roles, null, null, null, null);
    }

    @Test
    void usaValoresPorDefecto() {
        Proyecto proyecto = conRoles(null);

        assertThat(proyecto.datos()).isEqualTo(EntornoDatos.ninguno());
        assertThat(proyecto.sensibles()).isEqualTo(OperacionesSensibles.porDefecto());
        assertThat(proyecto.argumentosJvm()).isEmpty();
        assertThat(proyecto.roles()).isEmpty();
        assertThat(proyecto.pasosLogin()).isEmpty();
        assertThat(proyecto.politica()).isEmpty();
        assertThat(proyecto.extensiones()).isEmpty();
        assertThat(proyecto.javaApp()).isNull();
    }

    @Test
    void conservaLaCarpetaDeJavaDeLaApp() {
        Path jdk8 = Path.of("jdk8");

        Proyecto proyecto =
                new Proyecto("clientes", JAR, null, null, jdk8, null, null, null, null, null, null);

        assertThat(proyecto.javaApp()).isEqualTo(jdk8);
    }

    @Test
    void buscarRolEncuentraPorNombre() {
        Proyecto proyecto = conRoles(List.of(CLIENTE, ADMIN));

        assertThat(proyecto.buscarRol("admin")).contains(ADMIN);
        assertThat(proyecto.buscarRol("invitado")).isEmpty();
        assertThatThrownBy(() -> proyecto.buscarRol(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void exigeNombresDeRolUnicos() {
        Rol otroAdmin = new Rol("admin", "Root", "otra");

        assertThatThrownBy(() -> conRoles(List.of(ADMIN, CLIENTE, otroAdmin)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("admin");
    }

    @Test
    void validaNombreJarYClasePrincipal() {
        assertThatThrownBy(() ->
                        new Proyecto(null, JAR, null, null, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> new Proyecto("", JAR, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new Proyecto(
                                "clientes", null, null, null, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("JAR");
        assertThatThrownBy(() ->
                        new Proyecto(
                                "clientes", JAR, " ", null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laPoliticaSeCopiaEnProfundidad() {
        List<String> autorizados = new ArrayList<>(List.of("admin"));
        Map<String, List<String>> politica = new HashMap<>();
        politica.put("Eliminar Persona", autorizados);
        Proyecto proyecto =
                new Proyecto("clientes", JAR, null, null, null, null, null, null, politica, null);

        autorizados.add("cliente");
        politica.put("Editar Persona", List.of("admin"));

        assertThat(proyecto.politica()).containsOnlyKeys("Eliminar Persona");
        assertThat(proyecto.politica().get("Eliminar Persona")).containsExactly("admin");
        assertThatThrownBy(() -> proyecto.politica().get("Eliminar Persona").add("cliente"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void laPoliticaRechazaOperacionesSinRoles() {
        Map<String, List<String>> politica = new HashMap<>();
        politica.put("Eliminar Persona", null);

        assertThatThrownBy(() ->
                        new Proyecto(
                                "clientes",
                                JAR,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                politica,
                                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Eliminar Persona");
    }

    @Test
    void esInmutable() {
        List<Rol> roles = new ArrayList<>(List.of(ADMIN));
        List<String> argumentos = new ArrayList<>(List.of("-Xmx512m"));
        Proyecto proyecto =
                new Proyecto(
                        "clientes", JAR, null, argumentos, null, roles, null, null, null, null);

        roles.add(CLIENTE);
        argumentos.clear();

        assertThat(proyecto.roles()).containsExactly(ADMIN);
        assertThat(proyecto.argumentosJvm()).containsExactly("-Xmx512m");
        assertThatThrownBy(() -> proyecto.roles().add(CLIENTE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void operacionesSensiblesPorDefecto() {
        OperacionesSensibles sensibles = OperacionesSensibles.porDefecto();

        assertThat(sensibles.sql()).containsExactlyInAnyOrder("DELETE", "UPDATE", "INSERT");
        assertThat(sensibles.firestore()).containsExactlyInAnyOrder("delete", "set", "update");
        assertThat(new OperacionesSensibles(null, null).sql()).isEmpty();
        assertThatThrownBy(() -> sensibles.sql().add("SELECT"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void entornoDatosValidaYCopiaSusParametros() {
        Map<String, String> parametros = new HashMap<>(Map.of("puerto", "8080"));
        EntornoDatos entorno = new EntornoDatos(EntornoDatos.FIREBASE_EMULADOR, parametros);

        parametros.put("host", "localhost");

        assertThat(entorno.parametros()).containsOnlyKeys("puerto");
        assertThat(new EntornoDatos(EntornoDatos.H2, null).parametros()).isEmpty();
        assertThat(EntornoDatos.ninguno().tipo()).isEqualTo("ninguno");
        assertThatThrownBy(() -> new EntornoDatos(null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EntornoDatos(" ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
