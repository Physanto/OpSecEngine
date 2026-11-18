package io.github.physanto.opsecengine.nucleo.exploracion;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.ConfigEjecucion;
import io.github.physanto.opsecengine.dominio.caso.Criterio;
import io.github.physanto.opsecengine.dominio.caso.EstrategiaLocalizacion;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.configuracion.Rol;
import io.github.physanto.opsecengine.dominio.mapa.MapaApp;
import io.github.physanto.opsecengine.dominio.mapa.Transicion;
import io.github.physanto.opsecengine.dominio.mapa.Ventana;
import io.github.physanto.opsecengine.nucleo.sesion.ClienteAgente;
import io.github.physanto.opsecengine.nucleo.sesion.InicioSesion;
import io.github.physanto.opsecengine.nucleo.sesion.SesionService;
import io.github.physanto.opsecengine.nucleo.sesion.TraductorProtocolo;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.NodoArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Explora la aplicación y construye su {@link MapaApp}: ventanas como nodos y acciones como
 * aristas, por contexto.
 *
 * <p><b>Algoritmo.</b> Hay un contexto {@link MapaApp#SIN_SESION} y uno por rol del proyecto
 * ({@link MapaApp#contextoRol(String)}). En cada contexto se recorre en anchura: se abre la
 * aplicación, se inicia sesión si hay rol, se repite el camino hasta una ventana y se leen sus
 * controles alcanzables con acción {@code PULSAR} o {@code IR_A}. Cada control se prueba en una
 * sesión nueva (login, camino y acción), así el estado es siempre limpio y el resultado es
 * determinista. Si tras la acción aparece una ventana nueva, o la ventana activa cambia, se agrega
 * una {@link Transicion} con el control localizado por su ruta. Una ventana se expande una sola vez
 * por contexto.
 *
 * <p><b>Seguridad.</b> Nunca se pulsa un control cuyo texto coincida (sin tildes ni mayúsculas, por
 * inclusión) con {@link ConfigExploracion#textosDestructivos()} o con una operación de la política
 * del proyecto.
 *
 * <p><b>Límites.</b> Se detiene por profundidad, por tiempo o cuando no aparecen ventanas nuevas.
 * Un contexto cuyo login falla se omite y se informa por el progreso. Los puntos de entrada
 * alternativos ({@code entrada:<clase>}) no se exploran todavía.
 *
 * <p>No es seguro entre hilos: usa una sola {@link SesionService}, una sesión a la vez.
 */
public final class ExploracionService {

    private static final Logger REGISTRO = Logger.getLogger(ExploracionService.class.getName());
    private static final Duration ESPERA_PASO = Duration.ofSeconds(5);
    private static final Duration ESPERA_VENTANA_INICIAL = Duration.ofSeconds(10);
    private static final long INTERVALO_MS = 100;

    private final Proyecto proyecto;
    private final SesionService sesiones;
    private final Consumer<String> progreso;

    /**
     * Crea el servicio sin notificaciones de progreso.
     *
     * @param proyecto proyecto con la aplicación, los roles y el login; no puede ser null
     * @param sesiones servicio que abre y cierra las sesiones; no puede ser null
     * @throws NullPointerException si algún parámetro es null
     */
    public ExploracionService(Proyecto proyecto, SesionService sesiones) {
        this(proyecto, sesiones, mensaje -> {});
    }

    /**
     * Crea el servicio.
     *
     * @param proyecto proyecto con la aplicación, los roles y el login; no puede ser null
     * @param sesiones servicio que abre y cierra las sesiones; no puede ser null
     * @param progreso recibe un mensaje por cada ventana encontrada y cada contexto omitido, en el
     *     hilo que explora; no puede ser null
     * @throws NullPointerException si algún parámetro es null
     */
    public ExploracionService(
            Proyecto proyecto, SesionService sesiones, Consumer<String> progreso) {
        this.proyecto = Objects.requireNonNull(proyecto, "El proyecto no puede ser null");
        this.sesiones = Objects.requireNonNull(sesiones, "Las sesiones no pueden ser null");
        this.progreso = Objects.requireNonNull(progreso, "El progreso no puede ser null");
    }

    /**
     * Explora la aplicación en todos los contextos.
     *
     * @param config límites y reglas de seguridad; no puede ser null
     * @return el mapa encontrado; vacío si la aplicación no abre ninguna ventana
     * @throws NullPointerException si {@code config} es null
     */
    public MapaApp explorar(ConfigExploracion config) {
        Objects.requireNonNull(config, "La configuración no puede ser null");
        long limite = System.nanoTime() + config.tiempoMaximo().toNanos();
        Map<String, Ventana> ventanas = new LinkedHashMap<>();
        List<Transicion> transiciones = new ArrayList<>();
        explorarContexto(null, MapaApp.SIN_SESION, config, limite, ventanas, transiciones);
        for (Rol rol : proyecto.roles()) {
            explorarContexto(
                    rol, MapaApp.contextoRol(rol.nombre()), config, limite, ventanas, transiciones);
        }
        return new MapaApp(new HashSet<>(ventanas.values()), transiciones);
    }

    private void explorarContexto(
            Rol rol,
            String contexto,
            ConfigExploracion config,
            long limite,
            Map<String, Ventana> ventanas,
            List<Transicion> transiciones) {
        Observacion inicial = observar(rol, List.of(), null, config);
        if (inicial == null) {
            progreso.accept("Contexto " + contexto + " omitido: no se pudo abrir o iniciar sesión");
            return;
        }
        Set<String> expandidas = new HashSet<>();
        Deque<Pendiente> cola = new ArrayDeque<>();
        for (NodoArbolDto raiz : inicial.antes()) {
            String id = idVentana(raiz);
            registrar(ventanas, id, raiz, contexto);
            if (expandidas.add(id)) {
                cola.add(new Pendiente(List.of(), id, 0));
            }
        }
        while (!cola.isEmpty() && System.nanoTime() < limite) {
            Pendiente pendiente = cola.poll();
            if (pendiente.profundidad() >= config.profundidadMaxima()) {
                continue;
            }
            Observacion estado = observar(rol, pendiente.camino(), null, config);
            NodoArbolDto raiz = estado == null ? null : buscar(estado.antes(), pendiente.ventana());
            if (raiz == null) {
                continue;
            }
            for (Tramo tramo : candidatos(raiz, config)) {
                if (System.nanoTime() >= limite) {
                    break;
                }
                Observacion resultado = observar(rol, pendiente.camino(), tramo, config);
                if (resultado == null) {
                    continue;
                }
                NodoArbolDto destino = destino(resultado, pendiente.ventana());
                if (destino == null) {
                    continue;
                }
                String idDestino = idVentana(destino);
                registrar(ventanas, idDestino, destino, contexto);
                transiciones.add(
                        new Transicion(
                                pendiente.ventana(),
                                idDestino,
                                tramo.accion(),
                                tramo.control(),
                                contexto));
                if (expandidas.add(idDestino)) {
                    List<Tramo> camino = new ArrayList<>(pendiente.camino());
                    camino.add(tramo);
                    cola.add(new Pendiente(camino, idDestino, pendiente.profundidad() + 1));
                }
            }
        }
    }

    /**
     * Abre la aplicación, inicia sesión si hay rol, repite el camino y, si se pide, ejecuta una
     * acción más y espera a que cambien las ventanas. Devuelve null si algo falla.
     */
    private Observacion observar(Rol rol, List<Tramo> camino, Tramo accion, ConfigExploracion c) {
        ConfigEjecucion config = configuracionDePasos(c);
        try {
            ClienteAgente cliente = sesiones.abrir(proyecto, c.oculto());
            if (rol != null) {
                String problema = InicioSesion.iniciar(proyecto, rol, cliente, config);
                if (problema != null) {
                    REGISTRO.log(Level.WARNING, "Exploración: {0}", problema);
                    return null;
                }
            }
            for (Tramo tramo : camino) {
                if (!enviar(cliente, tramo, config)) {
                    return null;
                }
            }
            List<NodoArbolDto> antes = esperarVentanas(cliente);
            if (antes.isEmpty()) {
                return null;
            }
            if (accion == null) {
                return new Observacion(antes, antes);
            }
            if (!enviar(cliente, accion, config)) {
                return null;
            }
            return new Observacion(antes, esperarCambio(cliente, antes, c));
        } catch (IOException | RuntimeException e) {
            REGISTRO.log(Level.WARNING, "Exploración: falló una sesión", e);
            return null;
        } finally {
            sesiones.cerrar();
        }
    }

    /**
     * Pide el árbol hasta que haya alguna ventana visible, porque la aplicación tarda en abrirla.
     */
    private static List<NodoArbolDto> esperarVentanas(ClienteAgente cliente) throws IOException {
        long limite = System.nanoTime() + ESPERA_VENTANA_INICIAL.toNanos();
        List<NodoArbolDto> actual = cliente.pedirArbol().getVentanas();
        while (actual.isEmpty() && System.nanoTime() < limite) {
            try {
                Thread.sleep(INTERVALO_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return actual;
            }
            actual = cliente.pedirArbol().getVentanas();
        }
        return actual;
    }

    private static boolean enviar(ClienteAgente cliente, Tramo tramo, ConfigEjecucion config)
            throws IOException {
        Paso paso = new Paso(1, tramo.accion(), tramo.control(), null, ESPERA_PASO, null);
        ResultadoPasoDto resultado = cliente.enviarPaso(TraductorProtocolo.aPasoDto(paso, config));
        return resultado.getCodigo() == CodigoResultado.OK;
    }

    /** Pide el árbol hasta que cambie el conjunto de ventanas o venza la espera. */
    private static List<NodoArbolDto> esperarCambio(
            ClienteAgente cliente, List<NodoArbolDto> antes, ConfigExploracion c)
            throws IOException {
        List<String> ids = ids(antes);
        long limite = System.nanoTime() + c.esperaTransicion().toNanos();
        List<NodoArbolDto> actual = cliente.pedirArbol().getVentanas();
        while (ids(actual).equals(ids) && System.nanoTime() < limite) {
            try {
                Thread.sleep(INTERVALO_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return actual;
            }
            actual = cliente.pedirArbol().getVentanas();
        }
        return actual;
    }

    /**
     * Ventana a la que lleva la acción: la última ventana nueva; o, si la de origen se cerró, la
     * última ventana visible. Null si nada cambió.
     */
    private static NodoArbolDto destino(Observacion resultado, String origen) {
        Set<String> antes = new HashSet<>(ids(resultado.antes()));
        NodoArbolDto nueva = null;
        for (NodoArbolDto raiz : resultado.despues()) {
            if (!antes.contains(idVentana(raiz))) {
                nueva = raiz;
            }
        }
        if (nueva != null) {
            return nueva;
        }
        List<String> despues = ids(resultado.despues());
        if (!despues.isEmpty() && !despues.contains(origen)) {
            return resultado.despues().get(resultado.despues().size() - 1);
        }
        return null;
    }

    /** Controles alcanzables que se pueden pulsar, sin los destructivos, en orden del árbol. */
    private List<Tramo> candidatos(NodoArbolDto raiz, ConfigExploracion config) {
        Set<String> prohibidos = new LinkedHashSet<>();
        for (String texto : config.textosDestructivos()) {
            prohibidos.add(normalizar(texto));
        }
        for (String operacion : proyecto.politica().keySet()) {
            prohibidos.add(normalizar(operacion));
        }
        List<Tramo> tramos = new ArrayList<>();
        Set<String> rutas = new HashSet<>();
        recorrer(raiz, prohibidos, rutas, tramos);
        return tramos;
    }

    private static void recorrer(
            NodoArbolDto nodo, Set<String> prohibidos, Set<String> rutas, List<Tramo> tramos) {
        Accion accion = accionDe(nodo);
        if (accion != null
                && nodo.getRuta() != null
                && !esProhibido(nodo, prohibidos)
                && rutas.add(nodo.getRuta())) {
            tramos.add(
                    new Tramo(
                            accion,
                            new Localizador(
                                    List.of(
                                            new Criterio(
                                                    EstrategiaLocalizacion.RUTA,
                                                    nodo.getRuta())))));
        }
        for (NodoArbolDto hijo : nodo.getHijos()) {
            recorrer(hijo, prohibidos, rutas, tramos);
        }
    }

    private static Accion accionDe(NodoArbolDto nodo) {
        if (!nodo.isAlcanzable()) {
            return null;
        }
        if (nodo.getAcciones().contains(Accion.PULSAR.name())) {
            return Accion.PULSAR;
        }
        return nodo.getAcciones().contains(Accion.IR_A.name()) ? Accion.IR_A : null;
    }

    private static boolean esProhibido(NodoArbolDto nodo, Set<String> prohibidos) {
        for (String texto : new String[] {nodo.getTexto(), nodo.getNombreAccesible()}) {
            String normalizado = normalizar(texto);
            if (normalizado.isEmpty()) {
                continue;
            }
            for (String prohibido : prohibidos) {
                if (!prohibido.isEmpty() && normalizado.contains(prohibido)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Minúsculas, sin tildes y con los espacios recortados; null se trata como vacío. */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    /**
     * Id de una ventana: el nombre simple de su clase; si es una clase genérica de Swing o AWT, se
     * le agrega el título para distinguir diálogos distintos.
     */
    static String idVentana(NodoArbolDto raiz) {
        String clase = raiz.getTipo();
        String simple = clase.substring(clase.lastIndexOf('.') + 1);
        if (clase.startsWith("javax.swing.") || clase.startsWith("java.awt.")) {
            String titulo = raiz.getTexto() == null ? "" : raiz.getTexto();
            String limpio =
                    Normalizer.normalize(titulo, Normalizer.Form.NFD)
                            .replaceAll("[^A-Za-z0-9]+", "");
            return limpio.isEmpty() ? simple : simple + "-" + limpio;
        }
        return simple;
    }

    private void registrar(
            Map<String, Ventana> ventanas, String id, NodoArbolDto raiz, String contexto) {
        Ventana nueva = new Ventana(id, raiz.getTexto(), raiz.getTipo());
        if (ventanas.putIfAbsent(id, nueva) == null) {
            progreso.accept("Ventana encontrada: " + id + " (" + contexto + ")");
        }
    }

    private static NodoArbolDto buscar(List<NodoArbolDto> raices, String id) {
        for (NodoArbolDto raiz : raices) {
            if (idVentana(raiz).equals(id)) {
                return raiz;
            }
        }
        return null;
    }

    private static List<String> ids(List<NodoArbolDto> raices) {
        List<String> ids = new ArrayList<>();
        for (NodoArbolDto raiz : raices) {
            ids.add(idVentana(raiz));
        }
        return Collections.unmodifiableList(ids);
    }

    private static ConfigEjecucion configuracionDePasos(ConfigExploracion c) {
        ConfigEjecucion base = ConfigEjecucion.porDefecto();
        return new ConfigEjecucion(
                base.modo(), c.oculto(), ESPERA_PASO, base.reintentos(), base.semilla(), 1);
    }

    /** Una acción sobre un control, para repetir un camino. */
    private record Tramo(Accion accion, Localizador control) {}

    /** Una ventana por expandir y el camino de acciones que lleva hasta ella. */
    private record Pendiente(List<Tramo> camino, String ventana, int profundidad) {}

    /** Ventanas antes y después de una acción; iguales si no hubo acción. */
    private record Observacion(List<NodoArbolDto> antes, List<NodoArbolDto> despues) {}
}
