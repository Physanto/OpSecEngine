package io.github.physanto.opsecengine.agente.swing;

import io.github.physanto.opsecengine.agente.api.EmisorEventos;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.ModoInteraccionDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Atiende los mensajes {@code MODO_INTERACCION}: mantiene a lo sumo un modo activo, el inspector o
 * la grabadora, y cambia entre ellos.
 *
 * <p>Los cambios se hacen en el EDT. Sus métodos están sincronizados y se pueden llamar desde el
 * hilo del servidor del protocolo.
 */
public final class ControlInteraccion {

    private final Inspector inspector;
    private final Grabadora grabadora;

    /**
     * Crea el control con el inspector y la grabadora sin activar.
     *
     * @param emisor por donde salen los eventos de ambos modos; no puede ser null
     * @param errores recibe los problemas que no impiden seguir; no puede ser null
     */
    public ControlInteraccion(EmisorEventos emisor, Consumer<String> errores) {
        this.inspector = new Inspector(emisor, errores);
        this.grabadora = new Grabadora(emisor, errores);
    }

    /**
     * Activa un modo y desactiva el otro.
     *
     * @param modo uno de {@link ModoInteraccionDto#INSPECCIONAR}, {@link ModoInteraccionDto#GRABAR}
     *     o {@link ModoInteraccionDto#NINGUNO}
     * @return {@code OK} si el modo ya está vigente; {@code ERROR} con la causa en el detalle si no
     *     hay entorno gráfico o el cambio falló
     */
    public synchronized ResultadoPasoDto activar(String modo) {
        long comienzo = System.nanoTime();
        if (!ModoInteraccionDto.NINGUNO.equals(modo) && GraphicsEnvironment.isHeadless()) {
            return new ResultadoPasoDto(
                    CodigoResultado.ERROR,
                    "Sin entorno gráfico: no se puede inspeccionar ni grabar",
                    0);
        }
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                cambiar(modo);
            } else {
                SwingUtilities.invokeAndWait(() -> cambiar(modo));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ResultadoPasoDto(
                    CodigoResultado.ERROR, "Se interrumpió el cambio de modo", 0);
        } catch (InvocationTargetException | RuntimeException e) {
            Throwable causa = e instanceof InvocationTargetException ? e.getCause() : e;
            return new ResultadoPasoDto(
                    CodigoResultado.ERROR, "No se pudo activar el modo " + modo + ": " + causa, 0);
        }
        return new ResultadoPasoDto(
                CodigoResultado.OK, null, (System.nanoTime() - comienzo) / 1_000_000);
    }

    /** Desactiva el modo activo, si hay alguno; no lanza excepciones. */
    public synchronized void detener() {
        try {
            activar(ModoInteraccionDto.NINGUNO);
        } catch (RuntimeException e) {
            // Se está cerrando la sesión: no hay nada más que hacer.
        }
    }

    private void cambiar(String modo) {
        if (ModoInteraccionDto.INSPECCIONAR.equals(modo)) {
            grabadora.detener();
            inspector.iniciar();
        } else if (ModoInteraccionDto.GRABAR.equals(modo)) {
            inspector.detener();
            grabadora.iniciar();
        } else {
            inspector.detener();
            grabadora.detener();
        }
    }
}
