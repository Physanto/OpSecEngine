package io.github.physanto.opsecengine.practica.gui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.practica.ContextoApp;
import io.github.physanto.opsecengine.practica.datos.BaseDatos;
import io.github.physanto.opsecengine.practica.datos.BasesDePrueba;
import io.github.physanto.opsecengine.practica.fallos.Interruptores;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Contrasta hilos, trabajo innecesario y recursos nativos en ambos estados de F11–F13. */
class RendimientoMuestraTest {
    private BaseDatos base;
    private RendimientoMuestra ventana;

    @BeforeEach
    void iniciar() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Requiere pantalla o Xvfb");
        base = BasesDePrueba.nueva();
    }

    @AfterEach
    void cerrar() throws Exception {
        if (ventana != null)
            enEdt(() -> {
                ventana.dispose();
                return null;
            });
        if (base != null) base.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void f11EjecutaLaCargaEnEdtSoloSiEstaActivo(boolean corregido) throws Exception {
        AtomicBoolean enEdt = new AtomicBoolean();
        CountDownLatch ejecutada = new CountDownLatch(1);
        crear(
                corregido,
                () -> {
                    enEdt.set(SwingUtilities.isEventDispatchThread());
                    ejecutada.countDown();
                });
        pulsar("botonProbarEdt");
        assertThat(ejecutada.await(3, TimeUnit.SECONDS)).isTrue();
        esperarFin("botonProbarEdt");
        assertThat(enEdt.get()).isEqualTo(!corregido);
    }

    @Test
    void f11CorregidoAtiendeElEdtMientrasLaCargaSiguePendiente() throws Exception {
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        crear(
                true,
                () -> {
                    inicio.countDown();
                    esperar(liberar);
                });
        try {
            pulsar("botonProbarEdt");
            assertThat(inicio.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(
                            enEdt(() ->
                                    componente(ventana, "botonProbarEdt", JButton.class)
                                            .isEnabled()))
                    .isFalse();
            assertThat(liberar.getCount()).isEqualTo(1);
        } finally {
            liberar.countDown();
        }
        esperarFin("botonProbarEdt");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void f12RetieneTresVentanasSoloEnVulnerableYElCierreLiberaTodas(boolean corregido)
            throws Exception {
        crear(corregido, () -> {});
        for (int i = 0; i < 3; i++) pulsar("botonProbarFugaVentanas");
        enEdt(() -> {
            assertThat(
                            Arrays.stream(ventana.getOwnedWindows())
                                    .filter(Window::isDisplayable)
                                    .count())
                    .isEqualTo(corregido ? 0 : 3);
            assertThat(componente(ventana, "resultadoVentanasRetenidas", JLabel.class).getText())
                    .isEqualTo("Ventanas retenidas: " + (corregido ? 0 : 3));
            ventana.dispose();
            assertThat(Arrays.stream(ventana.getOwnedWindows()).noneMatch(Window::isDisplayable))
                    .isTrue();
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void f13SoloLaVulnerableEjecutaElTrabajoLentoYSiempreFueraDeEdt(boolean corregido)
            throws Exception {
        AtomicInteger cargas = new AtomicInteger();
        AtomicBoolean enEdt = new AtomicBoolean();
        crear(
                corregido,
                () -> {
                    cargas.incrementAndGet();
                    enEdt.set(SwingUtilities.isEventDispatchThread());
                    try {
                        Thread.sleep(120);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                });
        long inicio = System.nanoTime();
        pulsar("botonProbarAccionLenta");
        esperarFin("botonProbarAccionLenta");
        assertThat(cargas.get()).isEqualTo(corregido ? 0 : 1);
        assertThat(enEdt.get()).isFalse();
        if (!corregido)
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio))
                    .isGreaterThanOrEqualTo(120);
        assertThat(enEdt(() -> componente(ventana, "resultadoRendimiento", JLabel.class).getText()))
                .isEqualTo("Acción terminada.");
    }

    @Test
    void cerrarCancelaLaCargaDeSegundoPlano() throws Exception {
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch interrumpida = new CountDownLatch(1);
        crear(
                true,
                () -> {
                    inicio.countDown();
                    try {
                        new CountDownLatch(1).await();
                    } catch (InterruptedException error) {
                        interrumpida.countDown();
                        Thread.currentThread().interrupt();
                    }
                });
        pulsar("botonProbarEdt");
        assertThat(inicio.await(3, TimeUnit.SECONDS)).isTrue();
        enEdt(() -> {
            ventana.dispose();
            return null;
        });
        assertThat(interrumpida.await(3, TimeUnit.SECONDS)).isTrue();
    }

    private void crear(boolean corregido, Runnable carga) throws Exception {
        ContextoApp contexto =
                ContextoApp.crear(
                        base,
                        corregido ? Interruptores.todosCorregidos() : Interruptores.todosActivos(),
                        System.out);
        ventana = enEdt(() -> new RendimientoMuestra(contexto, carga));
    }

    private void pulsar(String nombre) throws Exception {
        enEdt(() -> {
            componente(ventana, nombre, JButton.class).doClick();
            return null;
        });
    }

    private void esperarFin(String nombre) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!enEdt(() -> componente(ventana, nombre, JButton.class).isEnabled())) {
            assertThat(System.nanoTime()).as("El trabajo no termina").isLessThan(limite);
            Thread.sleep(10);
        }
    }

    private static void esperar(CountDownLatch senal) {
        try {
            senal.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static <T extends Component> T componente(
            Container raiz, String nombre, Class<T> tipo) {
        for (Component hijo : raiz.getComponents()) {
            if (nombre.equals(hijo.getName())) return tipo.cast(hijo);
            if (hijo instanceof Container) {
                T encontrado = componente((Container) hijo, nombre, tipo);
                if (encontrado != null) return encontrado;
            }
        }
        return null;
    }

    private static <T> T enEdt(Supplier<T> accion) throws Exception {
        AtomicReference<T> resultado = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> resultado.set(accion.get()));
        return resultado.get();
    }
}
