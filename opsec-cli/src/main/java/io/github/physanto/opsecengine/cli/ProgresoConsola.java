package io.github.physanto.opsecengine.cli;

import io.github.physanto.opsecengine.nucleo.ejecucion.ObservadorProgreso;
import io.github.physanto.opsecengine.nucleo.ejecucion.ProgresoEjecucion;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * Muestra en la consola el avance de una ejecución: suite, caso y paso.
 *
 * <p>Escribe una línea por cada transición que interesa al usuario, sin códigos de color ni
 * reescritura de líneas, para que se lea igual en cualquier terminal y en los registros de una CI:
 *
 * <pre>
 * Ejecutando la suite login (2 casos)
 *   [1/2] Caso LOGIN-ADMIN (4 pasos)
 *         Inicio de sesión, paso 1/3: OK
 *         Paso 1/4: OK
 *         Paso 2/4: NO_ENCONTRADO
 *   [2/2] Caso LOGIN-CLIENTE (4 pasos)
 *         ...
 * Suite login terminada
 * </pre>
 *
 * <p>El inicio de cada paso no se muestra (solo su fin, con el código de resultado), para no
 * duplicar líneas. Un reintento de {@code ESPERAR} aparece como otra línea del mismo paso. Las
 * notificaciones del núcleo no llevan valores ni credenciales, así que esta salida tampoco.
 *
 * <p>Cada línea se vacía al momento para que el avance se vea mientras la aplicación corre. No es
 * segura entre hilos; el núcleo la llama desde un solo hilo y en orden.
 */
final class ProgresoConsola implements ObservadorProgreso {

    private final PrintWriter destino;
    private int casoActual;

    /**
     * Crea el observador.
     *
     * @param destino donde se escribe el avance, normalmente la salida de error; no puede ser null
     */
    ProgresoConsola(PrintWriter destino) {
        this.destino = Objects.requireNonNull(destino, "El destino no puede ser null");
    }

    /**
     * Escribe la línea que corresponde a una notificación, si corresponde alguna.
     *
     * @param progreso avance de suite, caso o paso; nunca null
     */
    @Override
    public void actualizar(ProgresoEjecucion progreso) {
        switch (progreso.tipo()) {
            case SUITE_INICIO -> {
                casoActual = 0;
                linea(
                        "Ejecutando la suite "
                                + progreso.nombreSuite()
                                + " ("
                                + contar(progreso.totalCasos(), "caso", "casos")
                                + ")");
            }
            case CASO_INICIO -> {
                casoActual++;
                linea(
                        "  ["
                                + casoActual
                                + "/"
                                + progreso.totalCasos()
                                + "] Caso "
                                + progreso.casoId()
                                + (progreso.totalPasos() > 0
                                        ? " ("
                                                + contar(progreso.totalPasos(), "paso", "pasos")
                                                + ")"
                                        : ""));
            }
            case PASO_FIN ->
                    linea(
                            "        "
                                    + (progreso.login() ? "Inicio de sesión, paso " : "Paso ")
                                    + progreso.numeroPaso()
                                    + "/"
                                    + progreso.totalPasos()
                                    + ": "
                                    + (progreso.codigo() == null ? "?" : progreso.codigo().name()));
            case SUITE_FIN -> linea("Suite " + progreso.nombreSuite() + " terminada");
            case PASO_INICIO, CASO_FIN -> {
                // No se muestran: el fin del paso ya dice cómo terminó y el veredicto del caso
                // aparece en el resumen de la suite.
            }
        }
    }

    private void linea(String texto) {
        destino.println(texto);
        destino.flush();
    }

    private static String contar(int cantidad, String singular, String plural) {
        return cantidad + " " + (cantidad == 1 ? singular : plural);
    }
}
