package io.github.physanto.opsecengine.agente.swing;

import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.AbstractButton;
import javax.swing.text.JTextComponent;

/**
 * Envía eventos sintéticos a los componentes Swing. Se usa exclusivamente desde el EDT.
 *
 * <p>Los eventos de teclado se redistribuyen al campo elegido para que no dependan del foco nativo
 * ni terminen en otra ventana. Los escuchadores y los enlaces de teclado del componente reciben los
 * eventos; no se cambia el documento con {@code setText}.
 */
final class AccionesEventos {

    private AccionesEventos() {}

    /** Envía un clic izquierdo completo al centro del botón. */
    static void pulsar(AbstractButton boton) {
        raton(boton, MouseEvent.MOUSE_ENTERED, 0, MouseEvent.NOBUTTON, 0);
        raton(boton, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1, 1);
        raton(boton, MouseEvent.MOUSE_RELEASED, 0, MouseEvent.BUTTON1, 1);
        raton(boton, MouseEvent.MOUSE_CLICKED, 0, MouseEvent.BUTTON1, 1);
        raton(boton, MouseEvent.MOUSE_EXITED, 0, MouseEvent.NOBUTTON, 0);
    }

    /** Selecciona el contenido anterior, lo borra con Retroceso y escribe el nuevo texto. */
    static void llenar(JTextComponent campo, String valor) {
        campo.selectAll();
        tecla(campo, KeyEvent.KEY_PRESSED, KeyEvent.VK_BACK_SPACE, KeyEvent.CHAR_UNDEFINED);
        tecla(campo, KeyEvent.KEY_RELEASED, KeyEvent.VK_BACK_SPACE, KeyEvent.CHAR_UNDEFINED);
        for (int i = 0; i < valor.length(); i++) {
            char caracter = valor.charAt(i);
            int codigo = KeyEvent.getExtendedKeyCodeForChar(caracter);
            tecla(campo, KeyEvent.KEY_PRESSED, codigo, caracter);
            tecla(campo, KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, caracter);
            tecla(campo, KeyEvent.KEY_RELEASED, codigo, caracter);
        }
    }

    private static void raton(
            AbstractButton boton, int tipo, int modificadores, int tecla, int clics) {
        boton.dispatchEvent(
                new MouseEvent(
                        boton,
                        tipo,
                        System.currentTimeMillis(),
                        modificadores,
                        boton.getWidth() / 2,
                        boton.getHeight() / 2,
                        0,
                        0,
                        clics,
                        false,
                        tecla));
    }

    private static void tecla(JTextComponent campo, int tipo, int codigo, char caracter) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .redispatchEvent(
                        campo,
                        new KeyEvent(campo, tipo, System.currentTimeMillis(), 0, codigo, caracter));
    }
}
