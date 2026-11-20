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
import java.awt.AWTException;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.text.JTextComponent;

/**
 * Implementación mínima de {@link AdaptadorToolkit} para Swing.
 *
 * <p>Ejecuta en los modos {@code SEMANTICO} y {@code EVENTOS} las acciones:
 *
 * <ul>
 *   <li>{@code PULSAR}: {@code doClick()} o eventos de ratón sobre un botón.
 *   <li>{@code LLENAR}: {@code setText(valor)} o eventos de teclado sobre un campo editable.
 *   <li>{@code SELECCIONAR}: elige la opción cuyo texto es el valor en un combo, una lista, una
 *       tabla o un árbol; espera a que la opción aparezca y, si no aparece, termina con {@code
 *       NO_ENCONTRADO}.
 *   <li>{@code MARCAR}: deja una casilla, un botón de opción o un botón de alternancia en el estado
 *       del valor ({@code "true"} o {@code "false"}; sin valor, {@code "true"}). Si ya está en ese
 *       estado no lo toca; si no, lo pulsa. Termina con {@code ERROR} si después de pulsarlo sigue
 *       en el otro estado, por ejemplo al desmarcar un botón de opción de un grupo.
 *   <li>{@code IR_A}: elige las pestañas que contienen al objetivo, restaura y trae al frente su
 *       ventana y le da el foco. Con {@code TEXTO} encuentra también una pestaña por su título, y
 *       un objetivo oculto solo por estar en otra pestaña cuenta como alcanzable.
 *   <li>{@code CERRAR_VENTANA}: envía {@code WINDOW_CLOSING} a la ventana del objetivo, igual en
 *       los dos modos. La aplicación decide qué hacer (ocultarla, destruirla, pedir confirmación o
 *       terminar); el paso termina con {@code OK} aunque la ventana siga abierta.
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
 * <p>Cuando una acción sobre un componente termina con {@code OK}, emite un evento {@code TIEMPO}
 * con {@code medida=RESPUESTA_ACCION} y la duración de la acción en el EDT, sin contar la espera a
 * que el componente aparezca. No lo emite para {@code ESPERAR} ni en modo {@code ROBOT}.
 *
 * <p>Las capturas se toman con {@link #capturar(String)}, que pinta la ventana en una imagen PNG.
 * La acción {@code CAPTURAR} dentro de un paso responde {@code ERROR}: la imagen no cabe en el
 * resultado del paso, así que el motor la pide con el mensaje {@code CAPTURAR}.
 *
 * <p>El modo {@code ROBOT} mueve el cursor real y escribe con el teclado real ({@link
 * AccionesRobot}). Solo admite {@code PULSAR}, {@code LLENAR} y {@code ESPERAR}; las demás acciones
 * responden {@code ERROR}, igual que sin pantalla o en modo oculto, porque necesita una ventana
 * visible y accesible al cursor.
 *
 * <p>Es seguro entre hilos; todo acceso a componentes ocurre en el EDT.
 */
public final class AdaptadorSwing implements AdaptadorToolkit {

    /** Modo de acción soportado. */
    public static final String MODO_SEMANTICO = "SEMANTICO";

    /** Modo de eventos sintéticos de ratón y teclado, sin depender del foco nativo. */
    public static final String MODO_EVENTOS = "EVENTOS";

    /** Modo con el cursor y el teclado reales; necesita pantalla visible. */
    public static final String MODO_ROBOT = "ROBOT";

    /** Medida del evento {@code TIEMPO} con la duración de una acción. */
    public static final String MEDIDA_RESPUESTA_ACCION = "RESPUESTA_ACCION";

    /** Veces que el modo ROBOT escribe un texto antes de darlo por bueno. */
    private static final int INTENTOS_ROBOT = 3;

    /** Tiempo máximo de las lecturas en el EDT, como la del árbol. */
    private static final long ESPERA_LECTURA_MS = 5_000;

    /** Intervalo entre intentos de encontrar un componente. */
    private static final long INTERVALO_BUSQUEDA_MS = 50;

    private final EmisorEventos emisor;
    private final ObservadorVentanas observador;
    private AccionesRobot robot;

    /**
     * Crea el adaptador sin observador de ventanas.
     *
     * @param emisor emisor de los eventos {@code EXCEPCION}; no puede ser null
     */
    public AdaptadorSwing(EmisorEventos emisor) {
        this(emisor, null);
    }

    /**
     * Crea el adaptador. Después de cada acción le pide al observador que informe las ventanas que
     * ya se ven, para que sus eventos lleguen al motor antes que el resultado del paso.
     *
     * @param emisor emisor de los eventos {@code EXCEPCION}; no puede ser null
     * @param observador observador de ventanas instalado, o null si no hay
     */
    public AdaptadorSwing(EmisorEventos emisor, ObservadorVentanas observador) {
        this.emisor = emisor;
        this.observador = observador;
    }

    /**
     * Devuelve el árbol de todas las ventanas visibles, incluidos diálogos y menús emergentes. La
     * lectura de rutas, alcance y acciones de los nodos ocurre íntegramente en el EDT.
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
     * Toma una captura pintando la ventana en una imagen, sin {@code java.awt.Robot}.
     *
     * <p>Con un nombre, captura la primera ventana visible con ese título o esa clase (completa o
     * simple). Sin nombre, la ventana activa; si no hay, el último diálogo modal visible o la
     * última ventana visible. La imagen no incluye la barra de título del sistema operativo.
     *
     * @param ventana título o clase de la ventana; null para la ventana activa
     * @return la captura en PNG, con el título de la ventana o, si no tiene, su clase; null si
     *     ninguna ventana visible coincide
     * @throws IllegalStateException si la interfaz no responde a tiempo o la imagen no se puede
     *     pintar o codificar
     */
    @Override
    public CapturaDto capturar(String ventana) {
        Object[] pintada;
        try {
            pintada =
                    EjecutorEdt.leer(
                            () -> {
                                Window elegida = Capturas.elegirVentana(ventana);
                                if (elegida == null) {
                                    return null;
                                }
                                return new Object[] {
                                    Capturas.nombre(elegida), Capturas.pintar(elegida)
                                };
                            },
                            ESPERA_LECTURA_MS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("La interfaz no respondió a la captura", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("No se pudo pintar la ventana", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la captura", e);
        }
        if (pintada == null) {
            return null;
        }
        return new CapturaDto(
                (String) pintada[0], "png", Capturas.codificarPng((BufferedImage) pintada[1]));
    }

    private Resultado ejecutarPaso(PasoDto paso, long inicio) throws InterruptedException {
        String accion = paso.getAccion();
        if (accion == null) {
            return new Resultado(CodigoResultado.ERROR, "El paso no tiene acción");
        }
        boolean esRobot = MODO_ROBOT.equals(paso.getModo());
        if (!esRobot
                && !MODO_SEMANTICO.equals(paso.getModo())
                && !MODO_EVENTOS.equals(paso.getModo())) {
            return new Resultado(
                    CodigoResultado.ERROR,
                    "El modo "
                            + paso.getModo()
                            + " no existe en el agente; use "
                            + MODO_SEMANTICO
                            + ", "
                            + MODO_EVENTOS
                            + " o "
                            + MODO_ROBOT);
        }
        long limite = inicio + TimeUnit.MILLISECONDS.toNanos(paso.getEsperaMaximaMs());
        if (esRobot && !"ESPERAR".equals(accion)) {
            return ejecutarRobot(paso, limite);
        }
        switch (accion) {
            case "MARCAR":
                if (estadoDeseado(paso.getValor()) == null) {
                    return new Resultado(
                            CodigoResultado.ERROR,
                            "El valor de MARCAR debe ser true o false: " + paso.getValor());
                }
            // fall through
            case "PULSAR":
            case "LLENAR":
            case "SELECCIONAR":
            case "IR_A":
            case "CERRAR_VENTANA":
                if (paso.getLocalizador() == null) {
                    return new Resultado(
                            CodigoResultado.ERROR, "La acción " + accion + " necesita un objetivo");
                }
                return actuar(paso, limite);
            case "ESPERAR":
                return esperar(paso, limite);
            case "CAPTURAR":
                return new Resultado(
                        CodigoResultado.ERROR,
                        "La acción CAPTURAR no se ejecuta como paso; el motor debe pedir la"
                                + " imagen con el mensaje CAPTURAR");
            default:
                return new Resultado(
                        CodigoResultado.ERROR,
                        "La acción " + accion + " todavía no está implementada en el agente");
        }
    }

    /** Pulsa o llena con el cursor y el teclado reales; no usa el EDT para actuar. */
    private Resultado ejecutarRobot(PasoDto paso, long limite) throws InterruptedException {
        String accion = paso.getAccion();
        if (!"PULSAR".equals(accion) && !"LLENAR".equals(accion)) {
            return new Resultado(
                    CodigoResultado.ERROR, "La acción " + accion + " no está soportada en ROBOT");
        }
        if (paso.getLocalizador() == null) {
            return new Resultado(
                    CodigoResultado.ERROR, "La acción " + accion + " necesita un objetivo");
        }
        if (GraphicsEnvironment.isHeadless()) {
            return new Resultado(
                    CodigoResultado.ERROR, "El modo ROBOT necesita una pantalla visible");
        }
        if (SupresorFoco.modoOcultoActivo()) {
            return new Resultado(
                    CodigoResultado.ERROR,
                    "El modo ROBOT necesita una pantalla visible y no funciona con el modo oculto");
        }
        Busqueda busqueda =
                buscarHasta(() -> BuscadorComponentes.buscar(paso.getLocalizador()), limite);
        if (busqueda == null) {
            return new Resultado(
                    CodigoResultado.TIEMPO_AGOTADO, "La interfaz no respondió a la búsqueda");
        }
        if (!busqueda.resultado().isEncontrado()) {
            return new Resultado(
                    busqueda.resultado().getCodigo(), busqueda.resultado().getDetalle());
        }
        Component componente = busqueda.componente();
        if ("PULSAR".equals(accion) && !(componente instanceof AbstractButton)) {
            return noAdmite("pulsar", componente);
        }
        if ("LLENAR".equals(accion)) {
            if (!(componente instanceof JTextComponent)) {
                return noAdmite("llenar", componente);
            }
            if (!((JTextComponent) componente).isEditable()) {
                return new Resultado(
                        CodigoResultado.NO_ALCANZABLE, "El campo de texto no es editable");
            }
        }
        Point centro;
        try {
            centro = EjecutorEdt.leer(() -> centroEnPantalla(componente), ESPERA_LECTURA_MS);
        } catch (TimeoutException | ExecutionException e) {
            return new Resultado(CodigoResultado.TIEMPO_AGOTADO, "No se pudo ubicar el componente");
        }
        if (centro == null) {
            return new Resultado(
                    CodigoResultado.NO_ALCANZABLE, "El componente no está visible en la pantalla");
        }
        try {
            if (robot == null) {
                robot = new AccionesRobot();
            }
            if ("PULSAR".equals(accion)) {
                robot.pulsar(centro.x, centro.y);
            } else {
                llenarConRobot(componente, centro, paso.getValor());
            }
        } catch (AWTException | IllegalStateException | SecurityException e) {
            return new Resultado(
                    CodigoResultado.ERROR, "La plataforma no permite el modo ROBOT: " + e);
        }
        vaciarColaDeEventos();
        return new Resultado(CodigoResultado.OK, null);
    }

    /**
     * Escribe con el teclado real y comprueba el texto; el sistema operativo a veces pierde una
     * tecla, así que repite hasta {@value #INTENTOS_ROBOT} veces si el campo no quedó con el texto
     * pedido. Si tras los intentos sigue distinto, se da por bueno: la aplicación pudo filtrar o
     * transformar lo escrito.
     */
    private void llenarConRobot(Component componente, Point centro, String valor)
            throws InterruptedException {
        String esperado = valor == null ? "" : valor;
        for (int intento = 1; intento <= INTENTOS_ROBOT; intento++) {
            robot.llenar(centro.x, centro.y, esperado);
            try {
                String actual =
                        EjecutorEdt.leer(
                                () -> ((JTextComponent) componente).getText(), ESPERA_LECTURA_MS);
                if (esperado.equals(actual)) {
                    return;
                }
            } catch (TimeoutException | ExecutionException e) {
                return;
            }
        }
    }

    /** Centro del componente en coordenadas de pantalla; null si no se ve. Solo en el EDT. */
    private static Point centroEnPantalla(Component componente) {
        if (!componente.isShowing() || componente.getWidth() <= 0 || componente.getHeight() <= 0) {
            return null;
        }
        Point origen = componente.getLocationOnScreen();
        return new Point(
                origen.x + componente.getWidth() / 2, origen.y + componente.getHeight() / 2);
    }

    private Resultado actuar(PasoDto paso, long limite) throws InterruptedException {
        Busqueda busqueda = buscarHasta(() -> localizarObjetivo(paso), limite);
        if (busqueda == null) {
            return new Resultado(
                    CodigoResultado.TIEMPO_AGOTADO, "La interfaz no respondió a la búsqueda");
        }
        ResultadoBusqueda encontrado = busqueda.resultado();
        if (!encontrado.isEncontrado()) {
            return new Resultado(encontrado.getCodigo(), encontrado.getDetalle());
        }
        AtomicReference<Resultado> rechazo = new AtomicReference<>();
        long inicio = System.nanoTime();
        Resultado resultado = ejecutarAccion(() -> rechazo.set(actuarEnEdt(paso)), limite);
        if (rechazo.get() != null) {
            return rechazo.get();
        }
        if (resultado.codigo == CodigoResultado.OK) {
            emitirTiempoRespuesta(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));
        }
        return resultado;
    }

    /**
     * Emite el evento {@code TIEMPO} con medida {@code RESPUESTA_ACCION}: lo que tardó la acción en
     * el EDT, sin contar la espera a que el componente apareciera.
     */
    private void emitirTiempoRespuesta(long duracionMs) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("medida", MEDIDA_RESPUESTA_ACCION);
        datos.put("duracionMs", duracionMs);
        emisor.emitir("TIEMPO", datos);
    }

    /**
     * Busca el objetivo del paso en el EDT. {@code IR_A} atraviesa pestañas; {@code SELECCIONAR}
     * además exige que el componente tenga la opción pedida.
     */
    private static Busqueda localizarObjetivo(PasoDto paso) {
        if ("IR_A".equals(paso.getAccion())) {
            return BuscadorComponentes.buscar(paso.getLocalizador(), true);
        }
        Busqueda busqueda = BuscadorComponentes.buscar(paso.getLocalizador());
        Component componente = busqueda.componente();
        if ("SELECCIONAR".equals(paso.getAccion())
                && busqueda.resultado().isEncontrado()
                && Seleccion.admite(componente)
                && Seleccion.buscar(componente, paso.getValor()) == null) {
            return new Busqueda(
                    componente,
                    ResultadoBusqueda.noEncontrado(
                            "La opción \""
                                    + paso.getValor()
                                    + "\" no está en el "
                                    + componente.getClass().getName()
                                    + " que cumple "
                                    + BuscadorComponentes.describir(paso.getLocalizador())));
        }
        return busqueda;
    }

    /** Revalida el objetivo y actúa en el EDT, sin leer propiedades Swing desde el servidor. */
    private Resultado actuarEnEdt(PasoDto paso) {
        Busqueda busqueda = localizarObjetivo(paso);
        if (!busqueda.resultado().isEncontrado()) {
            return new Resultado(
                    busqueda.resultado().getCodigo(), busqueda.resultado().getDetalle());
        }
        Component componente = busqueda.componente();
        boolean eventos = MODO_EVENTOS.equals(paso.getModo());
        switch (paso.getAccion()) {
            case "PULSAR":
                return pulsar(componente, eventos);
            case "LLENAR":
                return llenar(componente, paso.getValor(), eventos);
            case "SELECCIONAR":
                return seleccionar(componente, paso.getValor(), eventos);
            case "MARCAR":
                return marcar(componente, estadoDeseado(paso.getValor()), eventos);
            case "IR_A":
                return Navegacion.irA(componente, eventos)
                        ? null
                        : new Resultado(
                                CodigoResultado.NO_ALCANZABLE,
                                "El objetivo no quedó visible después de ir a él");
            default:
                Navegacion.cerrarVentana(componente);
                return null;
        }
    }

    private static Resultado pulsar(Component componente, boolean eventos) {
        if (!(componente instanceof AbstractButton)) {
            return noAdmite("pulsar", componente);
        }
        if (eventos) {
            AccionesEventos.pulsar((AbstractButton) componente);
        } else {
            ((AbstractButton) componente).doClick();
        }
        return null;
    }

    private static Resultado llenar(Component componente, String valor, boolean eventos) {
        if (!(componente instanceof JTextComponent)) {
            return noAdmite("llenar", componente);
        }
        JTextComponent campo = (JTextComponent) componente;
        if (!campo.isEditable()) {
            return new Resultado(CodigoResultado.NO_ALCANZABLE, "El campo de texto no es editable");
        }
        String texto = valor == null ? "" : valor;
        if (eventos) {
            AccionesEventos.llenar(campo, texto);
        } else {
            campo.setText(texto);
        }
        return null;
    }

    private static Resultado seleccionar(Component componente, String valor, boolean eventos) {
        if (!Seleccion.admite(componente)) {
            return noAdmite("seleccionar en", componente);
        }
        Seleccion.Opcion opcion = Seleccion.buscar(componente, valor);
        if (opcion == null) {
            return new Resultado(
                    CodigoResultado.NO_ENCONTRADO, "La opción \"" + valor + "\" ya no está");
        }
        if (!Seleccion.seleccionar(opcion, eventos)) {
            return new Resultado(
                    CodigoResultado.ERROR,
                    "La opción \""
                            + valor
                            + "\" no quedó seleccionada; la aplicación pudo rechazar el cambio");
        }
        return null;
    }

    private static Resultado marcar(Component componente, boolean deseado, boolean eventos) {
        if (!(componente instanceof JToggleButton
                || componente instanceof JCheckBoxMenuItem
                || componente instanceof JRadioButtonMenuItem)) {
            return noAdmite("marcar", componente);
        }
        AbstractButton boton = (AbstractButton) componente;
        if (boton.isSelected() == deseado) {
            return null;
        }
        if (eventos) {
            AccionesEventos.pulsar(boton);
        } else {
            boton.doClick();
        }
        if (boton.isSelected() != deseado) {
            return new Resultado(
                    CodigoResultado.ERROR,
                    "El botón sigue "
                            + (deseado ? "desmarcado" : "marcado")
                            + " después de pulsarlo");
        }
        return null;
    }

    private static Resultado noAdmite(String verbo, Component componente) {
        return new Resultado(
                CodigoResultado.ERROR,
                "No se puede " + verbo + " un " + componente.getClass().getName());
    }

    /** Estado pedido por el valor de {@code MARCAR}; null si el valor no es válido. */
    private static Boolean estadoDeseado(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return Boolean.TRUE;
        }
        String normalizado = valor.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalizado)) {
            return Boolean.TRUE;
        }
        if ("false".equals(normalizado)) {
            return Boolean.FALSE;
        }
        return null;
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
        LocalizadorDto localizador = paso.getLocalizador();
        Busqueda busqueda = buscarHasta(() -> BuscadorComponentes.buscar(localizador), limite);
        if (busqueda != null && busqueda.resultado().isEncontrado()) {
            return new Resultado(CodigoResultado.OK, null);
        }
        return new Resultado(
                CodigoResultado.TIEMPO_AGOTADO,
                "No apareció ningún componente que cumpla "
                        + BuscadorComponentes.describir(paso.getLocalizador()));
    }

    /**
     * Repite la búsqueda en el EDT hasta encontrar el objetivo alcanzable o hasta el límite;
     * devuelve la última búsqueda, o null si el EDT nunca respondió.
     */
    private static Busqueda buscarHasta(Callable<Busqueda> busqueda, long limite)
            throws InterruptedException {
        Busqueda ultima = null;
        while (true) {
            try {
                ultima =
                        EjecutorEdt.leer(
                                busqueda, Math.max(INTERVALO_BUSQUEDA_MS, restanteMs(limite)));
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
    private void vaciarColaDeEventos() throws InterruptedException {
        try {
            EjecutorEdt.leer(
                    () -> {
                        if (observador != null) {
                            observador.informarVisibles();
                        }
                        return null;
                    },
                    ESPERA_LECTURA_MS);
        } catch (TimeoutException | ExecutionException e) {
            // Si el EDT sigue ocupado, los eventos llegarán después del resultado
        }
    }

    private void emitirExcepcion(Throwable excepcion, String hilo) {
        emisor.emitir("EXCEPCION", ObservadorExcepciones.datos(excepcion, hilo, false));
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
