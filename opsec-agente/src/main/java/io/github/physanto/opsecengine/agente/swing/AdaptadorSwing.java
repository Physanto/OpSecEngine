package io.github.physanto.opsecengine.agente.swing;

import io.github.physanto.opsecengine.agente.api.AdaptadorToolkit;
import io.github.physanto.opsecengine.agente.api.EmisorEventos;
import io.github.physanto.opsecengine.agente.api.ResultadoBusqueda;
import io.github.physanto.opsecengine.agente.swing.BuscadorComponentes.Busqueda;
import io.github.physanto.opsecengine.agente.swing.EjecutorEdt.EstadoAccion;
import io.github.physanto.opsecengine.protocolo.dto.CapturaDto;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import io.github.physanto.opsecengine.protocolo.dto.NodoArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import java.awt.Component;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.text.JTextComponent;

/**
 * Implementación mínima de {@link AdaptadorToolkit} para Swing.
 *
 * <p><b>Alcance actual (demo).</b> Ejecuta en modo {@code SEMANTICO} las acciones:
 *
 * <ul>
 *   <li>{@code PULSAR}: {@code doClick()} sobre un botón.
 *   <li>{@code LLENAR}: {@code setText(valor)} sobre un campo de texto editable.
 *   <li>{@code ESPERAR}: sin objetivo, espera los milisegundos del valor; con objetivo, espera a
 *       que el componente o la ventana aparezca.
 * </ul>
 *
 * <p>Antes de actuar, espera hasta {@link PasoDto#getEsperaMaximaMs()} a que el componente aparezca
 * y sea alcanzable. Si la acción abre un diálogo modal, el paso termina con {@code OK} cuando el
 * diálogo aparece. Si el código de la aplicación lanza una excepción durante la acción, se emite un
 * evento {@code EXCEPCION} con {@code capturada=false}, como si la hubiera recibido el manejador
 * del EDT, y el paso termina con {@code OK}: la acción se hizo y el fallo es de la aplicación.
 *
 * <p>Los otros modos y acciones responden {@code ERROR} con un detalle que dice que no están
 * implementados. Las capturas lanzan {@link UnsupportedOperationException}.
 *
 * <p>Es seguro entre hilos; todo acceso a componentes ocurre en el EDT.
 */
public final class AdaptadorSwing implements AdaptadorToolkit {

    /** Modo de acción soportado. */
    public static final String MODO_SEMANTICO = "SEMANTICO";

    /** Tiempo máximo de las lecturas en el EDT, como la del árbol. */
    private static final long ESPERA_LECTURA_MS = 5_000;

    /** Intervalo entre intentos de encontrar un componente. */
    private static final long INTERVALO_BUSQUEDA_MS = 50;

    /** Líneas de pila que se envían como máximo en un evento {@code EXCEPCION}. */
    private static final int MAXIMO_LINEAS_PILA = 30;

    private final EmisorEventos emisor;

    /**
     * Crea el adaptador.
     *
     * @param emisor emisor de los eventos {@code EXCEPCION}; no puede ser null
     */
    public AdaptadorSwing(EmisorEventos emisor) {
        this.emisor = emisor;
    }

    /**
     * Devuelve el árbol de componentes de las ventanas visibles.
     *
     * @return una raíz por ventana visible; lista vacía si no hay ventanas o si el EDT no responde
     */
    @Override
    public List<NodoArbolDto> arbol() {
        try {
            return EjecutorEdt.leer(
                    () -> {
                        List<NodoArbolDto> raices = new ArrayList<>();
                        for (Window ventana : BuscadorComponentes.ventanasVisibles()) {
                            raices.add(DescriptorComponentes.describir(ventana, true));
                        }
                        return raices;
                    },
                    ESPERA_LECTURA_MS);
        } catch (TimeoutException | ExecutionException e) {
            return Collections.emptyList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }
    }

    /**
     * Busca un componente una sola vez, sin esperar a que aparezca.
     *
     * @param localizador localizador del componente
     * @return resultado de la búsqueda; {@code NO_ENCONTRADO} si el EDT no responde
     */
    @Override
    public ResultadoBusqueda resolver(LocalizadorDto localizador) {
        try {
            return EjecutorEdt.leer(
                            () -> BuscadorComponentes.buscar(localizador), ESPERA_LECTURA_MS)
                    .resultado();
        } catch (TimeoutException | ExecutionException e) {
            return ResultadoBusqueda.noEncontrado("No se pudo buscar en la interfaz: " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResultadoBusqueda.noEncontrado("Se interrumpió la búsqueda");
        }
    }

    /**
     * Ejecuta un paso y espera a que termine.
     *
     * @param paso paso a ejecutar
     * @return resultado del paso, con su duración; nunca null
     */
    @Override
    public ResultadoPasoDto ejecutar(PasoDto paso) {
        long inicio = System.nanoTime();
        Resultado resultado;
        try {
            resultado = ejecutarPaso(paso, inicio);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resultado = new Resultado(CodigoResultado.ERROR, "Se interrumpió el paso");
        } catch (RuntimeException e) {
            resultado = new Resultado(CodigoResultado.ERROR, "Falla del agente: " + e);
        }
        long duracion = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
        return new ResultadoPasoDto(resultado.codigo, resultado.detalle, duracion);
    }

    /**
     * Todavía no implementado.
     *
     * @param ventana título o clase de la ventana
     * @return nunca devuelve
     * @throws UnsupportedOperationException siempre
     */
    @Override
    public CapturaDto capturar(String ventana) {
        throw new UnsupportedOperationException("Las capturas todavía no están implementadas");
    }

    private Resultado ejecutarPaso(PasoDto paso, long inicio) throws InterruptedException {
        String accion = paso.getAccion();
        if (!MODO_SEMANTICO.equals(paso.getModo())) {
            return new Resultado(
                    CodigoResultado.ERROR,
                    "El modo "
                            + paso.getModo()
                            + " todavía no está implementado en el agente; use el modo "
                            + MODO_SEMANTICO);
        }
        long limite = inicio + TimeUnit.MILLISECONDS.toNanos(paso.getEsperaMaximaMs());
        switch (accion) {
            case "PULSAR":
            case "LLENAR":
                if (paso.getLocalizador() == null) {
                    return new Resultado(
                            CodigoResultado.ERROR, "La acción " + accion + " necesita un objetivo");
                }
                return actuar(paso, limite);
            case "ESPERAR":
                return esperar(paso, limite);
            default:
                return new Resultado(
                        CodigoResultado.ERROR,
                        "La acción " + accion + " todavía no está implementada en el agente");
        }
    }

    private Resultado actuar(PasoDto paso, long limite) throws InterruptedException {
        Busqueda busqueda = buscarHasta(paso.getLocalizador(), limite);
        if (busqueda == null) {
            return new Resultado(
                    CodigoResultado.TIEMPO_AGOTADO, "La interfaz no respondió a la búsqueda");
        }
        ResultadoBusqueda encontrado = busqueda.resultado();
        if (!encontrado.isEncontrado()) {
            return new Resultado(encontrado.getCodigo(), encontrado.getDetalle());
        }
        Component componente = busqueda.componente();
        Runnable accion;
        if ("PULSAR".equals(paso.getAccion())) {
            if (!(componente instanceof AbstractButton)) {
                return new Resultado(
                        CodigoResultado.ERROR,
                        "No se puede pulsar un " + componente.getClass().getName());
            }
            accion = ((AbstractButton) componente)::doClick;
        } else {
            if (!(componente instanceof JTextComponent)) {
                return new Resultado(
                        CodigoResultado.ERROR,
                        "No se puede llenar un " + componente.getClass().getName());
            }
            JTextComponent campo = (JTextComponent) componente;
            if (!campo.isEditable()) {
                return new Resultado(
                        CodigoResultado.NO_ALCANZABLE, "El campo de texto no es editable");
            }
            String valor = paso.getValor() == null ? "" : paso.getValor();
            accion = () -> campo.setText(valor);
        }
        return ejecutarAccion(accion, limite);
    }

    private Resultado ejecutarAccion(Runnable accion, long limite) throws InterruptedException {
        AtomicReference<String> hilo = new AtomicReference<>("AWT-EventQueue");
        EstadoAccion estado;
        try {
            estado =
                    EjecutorEdt.actuar(
                            () -> {
                                hilo.set(Thread.currentThread().getName());
                                accion.run();
                            },
                            restanteMs(limite));
        } catch (ExecutionException e) {
            emitirExcepcion(e.getCause() == null ? e : e.getCause(), hilo.get());
            estado = EstadoAccion.TERMINADA;
        }
        if (estado == EstadoAccion.TIEMPO_AGOTADO) {
            return new Resultado(
                    CodigoResultado.TIEMPO_AGOTADO,
                    "La acción no terminó dentro del tiempo máximo");
        }
        vaciarColaDeEventos();
        return new Resultado(CodigoResultado.OK, null);
    }

    private Resultado esperar(PasoDto paso, long limite) throws InterruptedException {
        if (paso.getLocalizador() == null) {
            long milisegundos = 0;
            if (paso.getValor() != null) {
                try {
                    milisegundos = Long.parseLong(paso.getValor().trim());
                } catch (NumberFormatException e) {
                    return new Resultado(
                            CodigoResultado.ERROR,
                            "El valor de ESPERAR no es un número de milisegundos: "
                                    + paso.getValor());
                }
            }
            Thread.sleep(Math.max(0, Math.min(milisegundos, restanteMs(limite))));
            return new Resultado(CodigoResultado.OK, null);
        }
        Busqueda busqueda = buscarHasta(paso.getLocalizador(), limite);
        if (busqueda != null && busqueda.resultado().isEncontrado()) {
            return new Resultado(CodigoResultado.OK, null);
        }
        return new Resultado(
                CodigoResultado.TIEMPO_AGOTADO,
                "No apareció ningún componente que cumpla "
                        + BuscadorComponentes.describir(paso.getLocalizador()));
    }

    /**
     * Busca el componente hasta encontrarlo alcanzable o hasta el límite; devuelve la última
     * búsqueda, o null si el EDT nunca respondió.
     */
    private static Busqueda buscarHasta(LocalizadorDto localizador, long limite)
            throws InterruptedException {
        Busqueda ultima = null;
        while (true) {
            try {
                ultima =
                        EjecutorEdt.leer(
                                () -> BuscadorComponentes.buscar(localizador),
                                Math.max(INTERVALO_BUSQUEDA_MS, restanteMs(limite)));
            } catch (TimeoutException e) {
                return ultima;
            } catch (ExecutionException e) {
                throw new IllegalStateException("Falló la búsqueda del componente", e.getCause());
            }
            if (ultima.resultado().isEncontrado() || System.nanoTime() >= limite) {
                return ultima;
            }
            Thread.sleep(INTERVALO_BUSQUEDA_MS);
        }
    }

    /**
     * Espera a que el EDT procese los eventos que generó la acción, como la apertura de ventanas,
     * para que sus eventos lleguen al motor antes que el resultado del paso.
     */
    private static void vaciarColaDeEventos() throws InterruptedException {
        try {
            EjecutorEdt.leer(() -> null, ESPERA_LECTURA_MS);
        } catch (TimeoutException | ExecutionException e) {
            // Si el EDT sigue ocupado, los eventos llegarán después del resultado
        }
    }

    private void emitirExcepcion(Throwable excepcion, String hilo) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("clase", excepcion.getClass().getName());
        datos.put("mensaje", excepcion.getMessage());
        datos.put("hilo", hilo);
        datos.put("capturada", false);
        List<String> pila = new ArrayList<>();
        for (StackTraceElement elemento : excepcion.getStackTrace()) {
            if (pila.size() == MAXIMO_LINEAS_PILA) {
                break;
            }
            pila.add(elemento.toString());
        }
        datos.put("pila", pila);
        emisor.emitir("EXCEPCION", datos);
    }

    private static long restanteMs(long limite) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(limite - System.nanoTime()));
    }

    /** Código y detalle de un paso, antes de agregar la duración. */
    private static final class Resultado {

        private final CodigoResultado codigo;
        private final String detalle;

        Resultado(CodigoResultado codigo, String detalle) {
            this.codigo = codigo;
            this.detalle = detalle;
        }
    }
}
