package io.github.physanto.opsecengine.practica.gui;

import io.github.physanto.opsecengine.practica.ContextoApp;
import io.github.physanto.opsecengine.practica.servicio.Sesion;
import java.awt.Component;
import java.sql.SQLException;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/** Pasos de una ventana a otra; las ventanas administrativas verifican el rol antes de abrirse. */
final class Navegacion {

    static final String MENSAJE_ACCESO_DENEGADO =
            "Acceso denegado: esta opción requiere una sesión de administrador.";

    private Navegacion() {}

    /** Muestra la siguiente ventana y cierra la actual (que puede ser {@code null}). */
    static void cambiar(JFrame actual, JFrame siguiente) {
        siguiente.setVisible(true);
        if (actual != null) {
            actual.dispose();
        }
    }

    /** Vuelve al menú que corresponde a la sesión, o a la portada si no hay sesión. */
    static void abrirMenu(JFrame actual, ContextoApp contexto, Sesion sesion) {
        if (sesion.esAdministrador() || sesion.esModoLocal()) {
            abrirMenuAdministrador(actual, contexto, sesion);
        } else if (sesion.estaIniciada()) {
            cambiar(actual, new MenuCliente(contexto, sesion));
        } else {
            cambiar(actual, new Portada(contexto));
        }
    }

    /** Abre el menú del administrador si la sesión lo permite. */
    static void abrirMenuAdministrador(JFrame actual, ContextoApp contexto, Sesion sesion) {
        if (permitido(actual, contexto, sesion)) {
            cambiar(actual, new MenuAdministrador(contexto, sesion));
        }
    }

    /** Abre la ventana de eliminación si la sesión lo permite. */
    static void abrirEliminarPersona(JFrame actual, ContextoApp contexto, Sesion sesion) {
        if (permitido(actual, contexto, sesion)) {
            cambiar(actual, new EliminarPersona(contexto, sesion));
        }
    }

    /**
     * Crea el contexto de la app al arrancar; si falla, escribe el motivo y termina la JVM con
     * código 2 (fallo desconocido en la propiedad) o 1 (base de datos).
     */
    static ContextoApp iniciarContexto() {
        try {
            return ContextoApp.iniciar();
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
        } catch (SQLException e) {
            System.err.println("No se pudo crear la base de datos: " + e.getMessage());
            System.exit(1);
        }
        throw new IllegalStateException("La JVM no terminó");
    }

    private static boolean permitido(Component padre, ContextoApp contexto, Sesion sesion) {
        if (contexto.control().permiteAdministracion(sesion)) {
            return true;
        }
        JOptionPane.showMessageDialog(
                padre, MENSAJE_ACCESO_DENEGADO, "Acceso denegado", JOptionPane.WARNING_MESSAGE);
        return false;
    }
}
