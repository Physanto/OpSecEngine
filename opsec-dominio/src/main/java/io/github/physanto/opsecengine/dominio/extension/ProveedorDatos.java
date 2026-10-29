package io.github.physanto.opsecengine.dominio.extension;

import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import java.util.Optional;

/**
 * Prepara, restaura y consulta los datos de prueba que usa la aplicación probada.
 *
 * <p><b>Cuándo se llama.</b> El núcleo elige el proveedor cuyo {@link #tipo()} coincide con el del
 * entorno de datos del proyecto y sigue este ciclo: {@link #preparar(Proyecto)} una vez antes del
 * primer caso, {@link #restaurar()} antes de cada caso, {@link #consultar(String)} después de un
 * caso para confirmar sus efectos y {@link #cerrar()} al terminar.
 *
 * <p><b>Errores.</b> Si no puede preparar o restaurar el entorno, lanza una excepción no comprobada
 * (por ejemplo {@link IllegalStateException}) con un mensaje claro; el núcleo marca los casos
 * afectados como {@code NO_CONCLUYENTE}. {@link #consultar(String)} no debe lanzar excepciones
 * porque el dato buscado no exista.
 *
 * <p><b>Hilos.</b> A diferencia de las demás extensiones, un proveedor de datos tiene estado: el
 * núcleo crea una instancia por sesión y la usa desde un solo hilo a la vez, así que no necesita
 * ser seguro entre hilos.
 *
 * <p><b>Registro.</b> Con {@link java.util.ServiceLoader}, declarando la implementación en {@code
 * META-INF/services/io.github.physanto.opsecengine.dominio.extension.ProveedorDatos}. Necesita un
 * constructor público sin parámetros.
 */
public interface ProveedorDatos {

    /**
     * Devuelve el tipo de entorno que atiende el proveedor.
     *
     * @return tipo del entorno, por ejemplo {@code "firebase-emulador"} o {@code "mysql"} (ver
     *     {@link io.github.physanto.opsecengine.dominio.configuracion.EntornoDatos})
     */
    String tipo();

    /**
     * Prepara el entorno de datos: lo arranca si hace falta y carga los datos iniciales.
     *
     * @param proyecto proyecto de pruebas, con los parámetros del entorno; nunca es null
     * @throws IllegalStateException si no puede preparar el entorno
     */
    void preparar(Proyecto proyecto);

    /**
     * Devuelve los datos al estado inicial, para que cada caso empiece igual.
     *
     * @throws IllegalStateException si no puede restaurar el entorno
     */
    void restaurar();

    /**
     * Consulta el estado actual de los datos, por ejemplo para confirmar que un registro se borró.
     *
     * @param consulta consulta en el lenguaje propio del tipo: SQL en {@code mysql} y {@code h2},
     *     ruta de documento en {@code firebase-emulador}; nunca es null
     * @return el resultado de la consulta, o vacío si no hay datos
     */
    Optional<Object> consultar(String consulta);

    /**
     * Libera los recursos del entorno, como conexiones o procesos. Llamarlo más de una vez no tiene
     * efecto.
     */
    void cerrar();
}
