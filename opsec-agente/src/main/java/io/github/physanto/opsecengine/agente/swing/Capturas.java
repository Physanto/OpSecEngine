package io.github.physanto.opsecengine.agente.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Toma capturas pintando los componentes en una imagen, sin {@code java.awt.Robot}: no depende de
 * lo que se vea en la pantalla, así que funciona con ventanas tapadas o fuera de la pantalla. La
 * imagen no incluye la barra de título ni los bordes que dibuja el sistema operativo.
 *
 * <p>Se usa exclusivamente desde el EDT, salvo {@link #codificarPng(BufferedImage)}, que no toca
 * componentes.
 */
final class Capturas {

    private Capturas() {}

    /**
     * Elige la ventana que se captura.
     *
     * <p>Con un nombre, la primera ventana visible cuyo título (sin espacios a los lados) o cuyo
     * nombre de clase, completo o simple, sea ese. Sin nombre, la ventana activa si se muestra; si
     * no hay, el último diálogo modal visible y, si tampoco hay, la última ventana visible.
     *
     * @param nombre título o clase de la ventana; null o vacío para la ventana activa
     * @return la ventana, o null si ninguna coincide
     */
    static Window elegirVentana(String nombre) {
        List<Window> visibles = BuscadorComponentes.ventanasVisibles();
        if (nombre != null && !nombre.trim().isEmpty()) {
            String buscado = nombre.trim();
            for (Window ventana : visibles) {
                String titulo = DescriptorComponentes.texto(ventana);
                Class<?> clase = ventana.getClass();
                if ((titulo != null && buscado.equals(titulo.trim()))
                        || buscado.equals(clase.getName())
                        || buscado.equals(clase.getSimpleName())) {
                    return ventana;
                }
            }
            return null;
        }
        Window activa = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (activa != null && activa.isShowing()) {
            return activa;
        }
        for (int i = visibles.size() - 1; i >= 0; i--) {
            Window ventana = visibles.get(i);
            if (ventana instanceof Dialog && ((Dialog) ventana).isModal()) {
                return ventana;
            }
        }
        return visibles.isEmpty() ? null : visibles.get(visibles.size() - 1);
    }

    /**
     * Nombre con que se informa la ventana capturada: su título o, si no tiene, su clase.
     *
     * @param ventana ventana capturada
     * @return el nombre; nunca null
     */
    static String nombre(Window ventana) {
        String titulo = DescriptorComponentes.texto(ventana);
        return titulo == null || titulo.trim().isEmpty() ? ventana.getClass().getName() : titulo;
    }

    /**
     * Pinta el componente y sus hijos sobre un fondo blanco, con {@code printAll}.
     *
     * @param componente componente o ventana con tamaño
     * @return la imagen, del tamaño del componente
     * @throws IllegalStateException si el componente no tiene tamaño
     */
    static BufferedImage pintar(Component componente) {
        int ancho = componente.getWidth();
        int alto = componente.getHeight();
        if (ancho <= 0 || alto <= 0) {
            throw new IllegalStateException("El componente no tiene tamaño: " + ancho + "x" + alto);
        }
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D grafico = imagen.createGraphics();
        try {
            grafico.setColor(Color.WHITE);
            grafico.fillRect(0, 0, ancho, alto);
            componente.printAll(grafico);
        } finally {
            grafico.dispose();
        }
        return imagen;
    }

    /**
     * Codifica la imagen en PNG y luego en Base64.
     *
     * @param imagen imagen
     * @return el PNG en Base64
     * @throws IllegalStateException si no se puede codificar
     */
    static String codificarPng(BufferedImage imagen) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(imagen, "png", salida)) {
                throw new IllegalStateException("No hay un codificador PNG disponible");
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo codificar la captura en PNG", e);
        }
        return Base64.getEncoder().encodeToString(salida.toByteArray());
    }
}
