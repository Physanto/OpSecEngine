package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pruebas de la navegación entre pestañas en los dos modos, incluso sin pantalla. */
class NavegacionTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void eligeLasPestanasQueContienenAlDestinoDeAfueraHaciaAdentro(boolean eventos)
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane externas = new JTabbedPane();
            JTabbedPane internas = new JTabbedPane();
            JButton destino = new JButton("Detalle");
            JPanel avanzado = new JPanel();
            avanzado.add(destino);
            internas.addTab("Básico", new JPanel());
            internas.addTab("Avanzado", avanzado);
            externas.addTab("General", new JPanel());
            externas.addTab("Opciones", internas);
            externas.setSize(300, 200);
            externas.doLayout();
            internas.doLayout();

            Navegacion.irA(destino, eventos);

            assertThat(externas.getSelectedIndex()).isEqualTo(1);
            assertThat(internas.getSelectedIndex()).isEqualTo(1);
        });
    }
}
