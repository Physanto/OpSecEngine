package io.github.physanto.opsecengine.dominio.configuracion;

import io.github.physanto.opsecengine.dominio.caso.Paso;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Proyecto de pruebas: todo lo específico de la aplicación probada, tal como se declara en {@code
 * proyecto.yaml}.
 *
 * <p>El record solo valida su estructura. Las comprobaciones que cruzan datos, como que la política
 * nombre roles existentes, le corresponden al servicio que carga el proyecto.
 *
 * <p>Es inmutable y seguro entre hilos. Los valores de {@code extensiones} se copian de forma
 * superficial: quien los crea debe usar valores inmutables.
 *
 * @param nombre nombre del proyecto; no puede ser null ni estar en blanco
 * @param jar ruta del JAR de la aplicación probada, tal como aparece en la configuración; si es
 *     relativa, se resuelve respecto a la carpeta del proyecto; no puede ser null
 * @param clasePrincipal nombre completo de la clase con el método {@code main}; puede ser null para
 *     leerla del manifiesto del JAR
 * @param argumentosJvm argumentos adicionales para la JVM de la aplicación probada; si es null
 *     queda vacía, y no admite elementos null
 * @param datos entorno de datos de prueba; si es null se usa {@link EntornoDatos#ninguno()}
 * @param roles roles con sus credenciales de prueba, con nombres únicos; si es null queda vacía, y
 *     no admite elementos null
 * @param pasosLogin pasos que inician sesión con un rol; sus valores pueden usar las variables
 *     {@code {usuario}} y {@code {contrasena}}; si es null queda vacía, y no admite elementos null
 * @param sensibles operaciones que se consideran sensibles; si es null se usa {@link
 *     OperacionesSensibles#porDefecto()}
 * @param politica roles autorizados para cada operación, con el nombre de la operación como clave;
 *     si es null queda vacía, y no admite claves, valores ni elementos null
 * @param extensiones campos adicionales para cambios aditivos del contrato; si es null queda vacío,
 *     y no admite claves ni valores null
 */
public record Proyecto(
        String nombre,
        Path jar,
        String clasePrincipal,
        List<String> argumentosJvm,
        EntornoDatos datos,
        List<Rol> roles,
        List<Paso> pasosLogin,
        OperacionesSensibles sensibles,
        Map<String, List<String>> politica,
        Map<String, Object> extensiones) {

    /**
     * Crea un proyecto con copias inmutables de sus colecciones y valida su estructura.
     *
     * @throws NullPointerException si {@code nombre} o {@code jar} son null, o si alguna colección
     *     contiene elementos, claves o valores null
     * @throws IllegalArgumentException si {@code nombre} está en blanco, si {@code clasePrincipal}
     *     no es null y está en blanco, o si hay dos roles con el mismo nombre
     */
    public Proyecto {
        Objects.requireNonNull(nombre, "El nombre del proyecto no puede ser null");
        if (nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del proyecto no puede estar en blanco");
        }
        Objects.requireNonNull(
                jar, "La ruta del JAR del proyecto " + nombre + " no puede ser null");
        if (clasePrincipal != null && clasePrincipal.isBlank()) {
            throw new IllegalArgumentException(
                    "La clase principal no puede estar en blanco; use null para leerla del"
                            + " manifiesto");
        }
        argumentosJvm = argumentosJvm == null ? List.of() : List.copyOf(argumentosJvm);
        datos = datos == null ? EntornoDatos.ninguno() : datos;
        roles = roles == null ? List.of() : List.copyOf(roles);
        pasosLogin = pasosLogin == null ? List.of() : List.copyOf(pasosLogin);
        sensibles = sensibles == null ? OperacionesSensibles.porDefecto() : sensibles;
        politica = copiarPolitica(politica);
        extensiones = extensiones == null ? Map.of() : Map.copyOf(extensiones);

        Set<String> nombres = new HashSet<>();
        for (Rol rol : roles) {
            if (!nombres.add(rol.nombre())) {
                throw new IllegalArgumentException(
                        "El proyecto "
                                + nombre
                                + " tiene roles repetidos con el nombre "
                                + rol.nombre());
            }
        }
    }

    /**
     * Busca un rol por su nombre.
     *
     * @param nombreRol nombre exacto del rol; no puede ser null
     * @return el rol con ese nombre, o vacío si el proyecto no lo declara
     * @throws NullPointerException si {@code nombreRol} es null
     */
    public Optional<Rol> buscarRol(String nombreRol) {
        Objects.requireNonNull(nombreRol, "El nombre del rol buscado no puede ser null");
        return roles.stream().filter(rol -> rol.nombre().equals(nombreRol)).findFirst();
    }

    private static Map<String, List<String>> copiarPolitica(Map<String, List<String>> politica) {
        if (politica == null) {
            return Map.of();
        }
        Map<String, List<String>> copia = new HashMap<>();
        politica.forEach((operacion, rolesAutorizados) ->
                copia.put(
                        Objects.requireNonNull(operacion, "La política no admite operaciones null"),
                        List.copyOf(
                                Objects.requireNonNull(
                                        rolesAutorizados,
                                        "La operación "
                                                + operacion
                                                + " de la política no tiene roles"))));
        return Map.copyOf(copia);
    }
}
