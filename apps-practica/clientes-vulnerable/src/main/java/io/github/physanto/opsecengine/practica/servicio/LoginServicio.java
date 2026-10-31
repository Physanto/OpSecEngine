package io.github.physanto.opsecengine.practica.servicio;

import io.github.physanto.opsecengine.practica.datos.Rol;
import io.github.physanto.opsecengine.practica.datos.Usuario;
import io.github.physanto.opsecengine.practica.datos.UsuarioRepositorio;
import io.github.physanto.opsecengine.practica.fallos.Fallo;
import io.github.physanto.opsecengine.practica.fallos.Interruptores;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/**
 * Valida las credenciales del formulario de inicio de sesión.
 *
 * <p>Cada intento se anota en la consola, como haría una app de estudiantes. Según los {@link
 * Interruptores}, el servicio incluye los fallos F1 (cuenta de soporte embebida), F5 (mensajes
 * distintos) y F6 (contraseña en la consola); el fallo F4 lo aporta el {@link UsuarioRepositorio}.
 * Es seguro entre hilos si la consola recibida lo es.
 */
public final class LoginServicio {

    /** Mensaje cuando el usuario o la contraseña están vacíos. */
    public static final String MENSAJE_CAMPOS_VACIOS = "Escriba el usuario y la contraseña.";

    /** Mensaje genérico cuando las credenciales no son válidas. */
    public static final String MENSAJE_GENERICO = "Usuario o contraseña incorrectos.";

    /** Mensaje cuando el usuario no existe (solo con el fallo F5 activo). */
    public static final String MENSAJE_USUARIO_INEXISTENTE = "El usuario no existe.";

    /** Mensaje cuando la contraseña no coincide (solo con el fallo F5 activo). */
    public static final String MENSAJE_CONTRASENA_INCORRECTA = "La contraseña es incorrecta.";

    // FALLO F1: credenciales de una cuenta de soporte escritas en el código
    private static final String USUARIO_SOPORTE = "soporte";
    private static final String CONTRASENA_SOPORTE = "soporte123";

    private final UsuarioRepositorio usuarios;
    private final Interruptores interruptores;
    private final PrintStream consola;

    /**
     * Crea el servicio.
     *
     * @param usuarios repositorio de usuarios
     * @param interruptores fallos activos y corregidos
     * @param consola salida donde se anotan los intentos, normalmente {@code System.out}
     * @throws NullPointerException si algún argumento es {@code null}
     */
    public LoginServicio(
            UsuarioRepositorio usuarios, Interruptores interruptores, PrintStream consola) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.interruptores = Objects.requireNonNull(interruptores, "interruptores");
        this.consola = Objects.requireNonNull(consola, "consola");
    }

    /**
     * Intenta iniciar sesión con las credenciales escritas en el formulario.
     *
     * @param usuario texto del campo Usuario; {@code null} cuenta como vacío
     * @param contrasena texto del campo Contraseña; {@code null} cuenta como vacío
     * @return la sesión iniciada o el mensaje que se debe mostrar
     * @throws SQLException si falla la consulta a la base, por ejemplo por un texto inyectado mal
     *     formado con el fallo F4 activo
     */
    public ResultadoLogin iniciarSesion(String usuario, String contrasena) throws SQLException {
        if (interruptores.estaActivo(Fallo.F6)) {
            // FALLO F6: la contraseña queda en la consola
            consola.println(
                    "Intento de inicio de sesión: usuario="
                            + usuario
                            + ", contraseña="
                            + contrasena);
        } else {
            consola.println("Intento de inicio de sesión del usuario " + usuario);
        }
        if (usuario == null || usuario.isEmpty() || contrasena == null || contrasena.isEmpty()) {
            return ResultadoLogin.fallo(MENSAJE_CAMPOS_VACIOS);
        }
        if (interruptores.estaActivo(Fallo.F1)
                && USUARIO_SOPORTE.equals(usuario)
                && CONTRASENA_SOPORTE.equals(contrasena)) {
            return ResultadoLogin.exito(Sesion.iniciada(USUARIO_SOPORTE, Rol.ADMINISTRADOR));
        }
        Optional<Usuario> valido = usuarios.buscarPorCredenciales(usuario, contrasena);
        if (valido.isPresent()) {
            return ResultadoLogin.exito(Sesion.iniciada(valido.get().nombre(), valido.get().rol()));
        }
        if (!interruptores.estaActivo(Fallo.F5)) {
            return ResultadoLogin.fallo(MENSAJE_GENERICO);
        }
        // FALLO F5: el mensaje revela si el usuario existe
        return ResultadoLogin.fallo(
                usuarios.buscarPorNombre(usuario).isPresent()
                        ? MENSAJE_CONTRASENA_INCORRECTA
                        : MENSAJE_USUARIO_INEXISTENTE);
    }
}
