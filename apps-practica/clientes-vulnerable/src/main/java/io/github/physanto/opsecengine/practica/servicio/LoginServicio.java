package io.github.physanto.opsecengine.practica.servicio;

import io.github.physanto.opsecengine.practica.datos.Usuario;
import io.github.physanto.opsecengine.practica.datos.UsuarioRepositorio;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/**
 * Valida las credenciales del formulario de inicio de sesión contra la tabla {@code usuario}.
 *
 * <p>Cada intento se anota en la consola, como haría una app de estudiantes. Es seguro entre hilos
 * si la consola recibida lo es.
 */
public final class LoginServicio {

    /** Mensaje cuando el usuario o la contraseña están vacíos. */
    public static final String MENSAJE_CAMPOS_VACIOS = "Escriba el usuario y la contraseña.";

    /** Mensaje genérico cuando las credenciales no son válidas. */
    public static final String MENSAJE_GENERICO = "Usuario o contraseña incorrectos.";

    private final UsuarioRepositorio usuarios;
    private final PrintStream consola;

    /**
     * Crea el servicio.
     *
     * @param usuarios repositorio de usuarios
     * @param consola salida donde se anotan los intentos, normalmente {@code System.out}
     * @throws NullPointerException si algún argumento es {@code null}
     */
    public LoginServicio(UsuarioRepositorio usuarios, PrintStream consola) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.consola = Objects.requireNonNull(consola, "consola");
    }

    /**
     * Intenta iniciar sesión con las credenciales escritas en el formulario.
     *
     * @param usuario texto del campo Usuario; {@code null} cuenta como vacío
     * @param contrasena texto del campo Contraseña; {@code null} cuenta como vacío
     * @return la sesión iniciada o el mensaje que se debe mostrar
     * @throws SQLException si falla la consulta a la base
     */
    public ResultadoLogin iniciarSesion(String usuario, String contrasena) throws SQLException {
        consola.println("Intento de inicio de sesión del usuario " + usuario);
        if (usuario == null || usuario.isEmpty() || contrasena == null || contrasena.isEmpty()) {
            return ResultadoLogin.fallo(MENSAJE_CAMPOS_VACIOS);
        }
        Optional<Usuario> valido = usuarios.buscarPorCredenciales(usuario, contrasena);
        if (valido.isPresent()) {
            return ResultadoLogin.exito(Sesion.iniciada(valido.get().nombre(), valido.get().rol()));
        }
        return ResultadoLogin.fallo(MENSAJE_GENERICO);
    }
}
