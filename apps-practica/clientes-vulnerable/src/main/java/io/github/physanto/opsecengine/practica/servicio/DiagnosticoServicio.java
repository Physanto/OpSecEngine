package io.github.physanto.opsecengine.practica.servicio;

import io.github.physanto.opsecengine.practica.fallos.Fallo;
import io.github.physanto.opsecengine.practica.fallos.Interruptores;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.Objects;

/** Fallos de diagnóstico del nivel 1. Es inmutable y seguro entre hilos. */
public final class DiagnosticoServicio {

    private final Interruptores interruptores;

    /**
     * Crea el servicio con los fallos seleccionados.
     *
     * @param interruptores configuración de fallos
     * @throws NullPointerException si los interruptores son null
     */
    public DiagnosticoServicio(Interruptores interruptores) {
        this.interruptores = Objects.requireNonNull(interruptores, "interruptores");
    }

    /**
     * Construye el mensaje de un error real de consulta para el diálogo del login.
     *
     * @param error excepción de la base de datos
     * @return mensaje genérico con F7 corregido; pila completa con F7 activo
     * @throws NullPointerException si el error es null
     */
    public String mensajeConsulta(SQLException error) {
        Objects.requireNonNull(error, "error");
        if (!interruptores.estaActivo(Fallo.F7)) {
            return "No se pudo consultar la base de datos.";
        }
        StringWriter texto = new StringWriter();
        error.printStackTrace(new PrintWriter(texto));
        return texto.toString();
    }

    /**
     * Ejecuta la acción de diagnóstico del botón Probar excepción.
     *
     * @throws IllegalStateException si F8 está activo; con F8 corregido termina normalmente
     */
    public void probarExcepcion() {
        if (interruptores.estaActivo(Fallo.F8)) {
            throw new IllegalStateException("F8: excepción de prueba no capturada");
        }
    }
}
