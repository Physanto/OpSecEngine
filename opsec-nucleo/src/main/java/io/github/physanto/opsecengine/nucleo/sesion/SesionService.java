package io.github.physanto.opsecengine.nucleo.sesion;

import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abre y cierra sesiones: la aplicación probada corriendo con el agente conectado al motor.
 *
 * <p>{@link #abrir(Proyecto)} crea un {@link ClienteAgente}, arranca la aplicación con su puerto y
 * su token y espera el {@code HOLA}. {@link #cerrar()} envía {@code CERRAR}, espera a que el agente
 * corte la conexión y detiene la aplicación si sigue viva. Si la aplicación no arranca o el agente
 * no saluda a tiempo, {@code abrir} deja todo cerrado antes de lanzar la excepción.
 *
 * <p>Ejemplo:
 *
 * <pre>{@code
 * try (SesionService sesiones = new SesionService(new LanzadorProceso(jarAgente, resultados))) {
 *     ClienteAgente cliente = sesiones.abrir(proyecto);
 *     cliente.enviarPaso(paso);
 *     sesiones.cerrar();
 * }
 * }</pre>
 *
 * <p>Maneja una sesión a la vez; sus métodos están sincronizados. Las sesiones en paralelo llegan
 * en la v0.3, con una instancia por sesión.
 */
public final class SesionService implements AutoCloseable {

    /** Tiempo máximo por defecto para que el agente salude después de arrancar la aplicación. */
    public static final Duration ESPERA_HOLA_POR_DEFECTO = Duration.ofSeconds(30);

    private static final Logger REGISTRO = Logger.getLogger(SesionService.class.getName());

    private final ArranqueApp arranque;
    private final Duration esperaHola;
    private final Duration tiempoMaximoRespuesta;
    private ClienteAgente cliente;
    private Closeable detencion;
    private HolaDto saludo;

    /**
     * Crea el servicio con las esperas por defecto.
     *
     * @param arranque cómo se arranca la aplicación, normalmente un {@link LanzadorProceso}; no
     *     puede ser null
     * @throws NullPointerException si {@code arranque} es null
     */
    public SesionService(ArranqueApp arranque) {
        this(arranque, ESPERA_HOLA_POR_DEFECTO, ClienteAgente.TIEMPO_MAXIMO_POR_DEFECTO);
    }

    /**
     * Crea el servicio.
     *
     * @param arranque cómo se arranca la aplicación; no puede ser null
     * @param esperaHola tiempo máximo para que el agente salude; debe ser positivo
     * @param tiempoMaximoRespuesta tiempo máximo para cada respuesta del agente; debe ser positivo
     * @throws NullPointerException si algún parámetro es null
     * @throws IllegalArgumentException si alguna espera no es positiva
     */
    public SesionService(
            ArranqueApp arranque, Duration esperaHola, Duration tiempoMaximoRespuesta) {
        this.arranque = Objects.requireNonNull(arranque, "El arranque no puede ser null");
        this.esperaHola = positiva(esperaHola, "La espera del saludo");
        this.tiempoMaximoRespuesta = positiva(tiempoMaximoRespuesta, "El tiempo máximo");
    }

    /**
     * Devuelve la carpeta de resultados del arranque, donde se guardan las capturas.
     *
     * @return la carpeta, o vacío si el arranque no tiene una
     */
    public Optional<Path> carpetaResultados() {
        return arranque.carpetaResultados();
    }

    /**
     * Arranca la aplicación del proyecto y espera el saludo de su agente.
     *
     * @param proyecto proyecto con la aplicación; no puede ser null
     * @return el cliente conectado al agente, listo para enviar comandos
     * @throws IOException si la aplicación no arranca o el agente no saluda a tiempo
     * @throws IllegalStateException si ya hay una sesión abierta
     * @throws NullPointerException si {@code proyecto} es null
     */
    public synchronized ClienteAgente abrir(Proyecto proyecto) throws IOException {
        return abrir(proyecto, false);
    }

    /**
     * Abre una sesión con el modo oculto solicitado para las ventanas de la aplicación.
     *
     * @param proyecto proyecto con la aplicación; no puede ser null
     * @param oculto si se deben ocultar las ventanas y evitar el foco
     * @return cliente conectado después del saludo
     * @throws IOException si falla el arranque o no llega el saludo
     * @throws IllegalStateException si ya existe una sesión abierta
     * @throws NullPointerException si proyecto es null
     */
    public synchronized ClienteAgente abrir(Proyecto proyecto, boolean oculto) throws IOException {
        Objects.requireNonNull(proyecto, "El proyecto no puede ser null");
        if (cliente != null) {
            throw new IllegalStateException(
                    "Ya hay una sesión abierta; ciérrela antes de abrir otra");
        }
        ClienteAgente nuevo = new ClienteAgente(tiempoMaximoRespuesta);
        Closeable nuevaDetencion = null;
        try {
            nuevaDetencion = arranque.arrancar(proyecto, nuevo.puerto(), nuevo.token(), oculto);
            saludo = nuevo.esperarHola(esperaHola);
        } catch (IOException | RuntimeException e) {
            nuevo.close();
            detener(nuevaDetencion);
            throw e;
        }
        cliente = nuevo;
        detencion = nuevaDetencion;
        REGISTRO.fine(() -> "Sesión abierta con el agente " + saludo.getVersionAgente());
        return nuevo;
    }

    /**
     * Indica si hay una sesión abierta.
     *
     * @return true entre {@link #abrir(Proyecto)} y {@link #cerrar()}
     */
    public synchronized boolean abierta() {
        return cliente != null;
    }

    /**
     * Devuelve el cliente de la sesión abierta.
     *
     * @return el cliente conectado al agente
     * @throws IllegalStateException si no hay una sesión abierta
     */
    public synchronized ClienteAgente cliente() {
        if (cliente == null) {
            throw new IllegalStateException("No hay una sesión abierta; llame antes a abrir");
        }
        return cliente;
    }

    /**
     * Devuelve el saludo del agente de la sesión abierta, con su versión y la de Java.
     *
     * @return el saludo recibido
     * @throws IllegalStateException si no hay una sesión abierta
     */
    public synchronized HolaDto saludo() {
        cliente();
        return saludo;
    }

    /**
     * Cierra la sesión abierta: envía {@code CERRAR}, espera al agente y detiene la aplicación. Los
     * errores al cerrar se registran y no se lanzan. Sin sesión abierta no hace nada.
     */
    public synchronized void cerrar() {
        if (cliente == null) {
            return;
        }
        try {
            if (!cliente.desconectado() && !cliente.cerrar()) {
                REGISTRO.warning("El agente no cerró la conexión a tiempo después de CERRAR");
            }
        } catch (IOException e) {
            REGISTRO.log(Level.WARNING, "No se pudo enviar CERRAR al agente", e);
        } finally {
            cliente.close();
            detener(detencion);
            cliente = null;
            detencion = null;
            saludo = null;
        }
    }

    /** Igual que {@link #cerrar()}. */
    @Override
    public void close() {
        cerrar();
    }

    private static void detener(Closeable detencion) {
        if (detencion == null) {
            return;
        }
        try {
            detencion.close();
        } catch (IOException e) {
            REGISTRO.log(Level.WARNING, "No se pudo detener la aplicación probada", e);
        }
    }

    private static Duration positiva(Duration espera, String descripcion) {
        Objects.requireNonNull(espera, descripcion + " no puede ser null");
        if (espera.isZero() || espera.isNegative()) {
            throw new IllegalArgumentException(descripcion + " debe ser mayor que cero: " + espera);
        }
        return espera;
    }
}
