package io.github.physanto.opsecengine.agente.swing;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Acciones con el cursor y el teclado reales del sistema, mediante {@link Robot}.
 *
 * <p>Es el modo de confirmación: mueve el cursor real y necesita una pantalla visible y la ventana
 * por encima de las demás. Los métodos se deben llamar fuera del EDT, porque esperan a que la cola
 * de eventos quede libre. Una instancia no es segura entre hilos; el adaptador la usa desde el hilo
 * del servidor del protocolo.
 *
 * <p>Las letras sin tilde, los dígitos y el espacio se escriben tecla a tecla. Cualquier otro
 * carácter (tildes, ñ, símbolos, emojis) se pega desde el portapapeles, porque no existe una tecla
 * portable para él; esa escritura reemplaza el contenido del portapapeles del usuario.
 */
final class AccionesRobot {

    /** Pausa entre pulsaciones para que el sistema operativo las procese en orden. */
    private static final int PAUSA_MS = 40;

    private final Robot robot;

    /**
     * Crea el controlador del cursor y el teclado.
     *
     * @throws AWTException si la plataforma no permite controlar la entrada
     * @throws java.awt.HeadlessException si no hay pantalla
     */
    AccionesRobot() throws AWTException {
        this.robot = new Robot();
        this.robot.setAutoDelay(PAUSA_MS);
    }

    /**
     * Mueve el cursor a unas coordenadas de pantalla y da un clic izquierdo.
     *
     * @param x coordenada horizontal en la pantalla
     * @param y coordenada vertical en la pantalla
     */
    void pulsar(int x, int y) {
        robot.mouseMove(x, y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
    }

    /**
     * Da un clic en el campo, borra su contenido con Ctrl+A y Suprimir, y escribe el texto.
     *
     * @param x coordenada horizontal del campo en la pantalla
     * @param y coordenada vertical del campo en la pantalla
     * @param texto texto a escribir; null se trata como vacío
     * @throws IllegalStateException si hace falta el portapapeles y no está disponible
     */
    void llenar(int x, int y, String texto) {
        pulsar(x, y);
        robot.waitForIdle();
        combinacion(KeyEvent.VK_CONTROL, KeyEvent.VK_A);
        tecla(KeyEvent.VK_DELETE);
        robot.waitForIdle();
        String contenido = texto == null ? "" : texto;
        for (int i = 0; i < contenido.length(); ) {
            int punto = contenido.codePointAt(i);
            i += Character.charCount(punto);
            escribir(punto);
        }
        robot.waitForIdle();
    }

    /** Indica si el carácter se puede escribir con teclas ordinarias en cualquier distribución. */
    static boolean esTecleable(int punto) {
        return punto == ' '
                || (punto >= 'a' && punto <= 'z')
                || (punto >= 'A' && punto <= 'Z')
                || (punto >= '0' && punto <= '9');
    }

    private void escribir(int punto) {
        if (!esTecleable(punto)) {
            pegar(new String(Character.toChars(punto)));
            return;
        }
        boolean mayuscula = punto >= 'A' && punto <= 'Z';
        int codigo = KeyEvent.getExtendedKeyCodeForChar(punto);
        if (mayuscula) {
            robot.keyPress(KeyEvent.VK_SHIFT);
        }
        tecla(codigo);
        if (mayuscula) {
            robot.keyRelease(KeyEvent.VK_SHIFT);
        }
        robot.waitForIdle();
    }

    private void pegar(String texto) {
        Clipboard portapapeles;
        try {
            portapapeles = Toolkit.getDefaultToolkit().getSystemClipboard();
            portapapeles.setContents(new StringSelection(texto), null);
        } catch (IllegalStateException | SecurityException e) {
            throw new IllegalStateException(
                    "El portapapeles no está disponible para escribir \"" + texto + "\"", e);
        }
        combinacion(KeyEvent.VK_CONTROL, KeyEvent.VK_V);
        robot.waitForIdle();
    }

    private void tecla(int codigo) {
        robot.keyPress(codigo);
        robot.keyRelease(codigo);
    }

    private void combinacion(int modificador, int codigo) {
        robot.keyPress(modificador);
        tecla(codigo);
        robot.keyRelease(modificador);
    }
}
