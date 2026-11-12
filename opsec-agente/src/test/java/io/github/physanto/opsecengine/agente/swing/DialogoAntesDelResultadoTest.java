package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Comprueba que el diálogo que abre una acción se informa antes del resultado del paso, aunque el
 * evento {@code WINDOW_OPENED} todavía no se haya despachado. El observador no se registra en AWT:
 * el único camino para que se emita el {@code DIALOGO} es el que usa el adaptador al cerrar el
 * paso.
 */
class DialogoAntesDelResultadoTest {

    private static final String TEXTO_BOTON = "Abrir dialogo de prueba";

    private final List<String> eventos = Collections.synchronizedList(new ArrayList<>());
    private JFrame ventana;

    @AfterEach
    void cerrarVentanas() throws InterruptedException, InvocationTargetException {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            for (Window abierta : Window.getWindows()) {
                abierta.dispose();
            }
        });
    }

    @Test
    void elDialogoModalSeInformaAntesDelResultado() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "necesita pantalla");
        ObservadorVentanas observador =
                new ObservadorVentanas((tipo, datos) -> eventos.add(tipo), mensaje -> {});
        AdaptadorSwing adaptador = new AdaptadorSwing((tipo, datos) -> {}, observador);
        SwingUtilities.invokeAndWait(() -> {
            ventana = new JFrame("Ventana de prueba del dialogo");
            JButton boton = new JButton(TEXTO_BOTON);
            boton.addActionListener(e ->
                    JOptionPane.showMessageDialog(
                            ventana,
                            "El usuario no existe.",
                            "Iniciar sesión",
                            JOptionPane.ERROR_MESSAGE));
            ventana.add(boton);
            ventana.pack();
            ventana.setVisible(true);
        });
        eventos.clear();

        ResultadoPasoDto resultado =
                adaptador.ejecutar(
                        new PasoDto("PULSAR", porTexto(TEXTO_BOTON), null, "EVENTOS", 5000));

        assertThat(resultado.getCodigo()).isEqualTo(CodigoResultado.OK);
        assertThat(eventos).contains("DIALOGO");
    }

    private static LocalizadorDto porTexto(String texto) {
        return new LocalizadorDto(Collections.singletonList(new CriterioDto("TEXTO", texto)));
    }
}
