package io.github.physanto.opsecengine.practica;

import io.github.physanto.opsecengine.practica.datos.BaseDatos;
import io.github.physanto.opsecengine.practica.datos.PersonaRepositorio;
import io.github.physanto.opsecengine.practica.datos.UsuarioRepositorio;
import io.github.physanto.opsecengine.practica.servicio.ControlAcceso;
import io.github.physanto.opsecengine.practica.servicio.LoginServicio;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Agrupa los objetos que comparten todas las ventanas: la base, los repositorios y los servicios.
 *
 * <p>Cada ventana lo recibe en su constructor, en lugar de usar variables estáticas. Es inmutable y
 * seguro entre hilos.
 */
public final class ContextoApp {

    private final BaseDatos base;
    private final PersonaRepositorio personas;
    private final LoginServicio login;
    private final ControlAcceso control;

    private ContextoApp(BaseDatos base, PrintStream consola) {
        this.base = base;
        this.personas = new PersonaRepositorio(base);
        this.login = new LoginServicio(new UsuarioRepositorio(base), consola);
        this.control = new ControlAcceso();
    }

    /**
     * Crea el contexto de la app en ejecución: base {@value BaseDatos#NOMBRE_APP} con los datos
     * semilla y consola {@code System.out}.
     *
     * @return el contexto listo para abrir la primera ventana
     * @throws SQLException si no se puede crear la base
     */
    public static ContextoApp iniciar() throws SQLException {
        return crear(BaseDatos.enMemoria(BaseDatos.NOMBRE_APP), System.out);
    }

    /**
     * Crea un contexto sobre una base ya abierta; lo usan las pruebas.
     *
     * @param base base abierta
     * @param consola salida donde se anotan los intentos de inicio de sesión
     * @return el contexto
     * @throws NullPointerException si algún argumento es {@code null}
     */
    public static ContextoApp crear(BaseDatos base, PrintStream consola) {
        return new ContextoApp(
                Objects.requireNonNull(base, "base"), Objects.requireNonNull(consola, "consola"));
    }

    /**
     * Devuelve la base de datos.
     *
     * @return base abierta
     */
    public BaseDatos base() {
        return base;
    }

    /**
     * Devuelve el repositorio de personas.
     *
     * @return repositorio
     */
    public PersonaRepositorio personas() {
        return personas;
    }

    /**
     * Devuelve el servicio de inicio de sesión.
     *
     * @return servicio
     */
    public LoginServicio login() {
        return login;
    }

    /**
     * Devuelve el control de acceso a las ventanas administrativas.
     *
     * @return control de acceso
     */
    public ControlAcceso control() {
        return control;
    }
}
