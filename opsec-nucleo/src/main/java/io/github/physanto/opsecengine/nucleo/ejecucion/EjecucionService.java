package io.github.physanto.opsecengine.nucleo.ejecucion;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.ConfigEjecucion;
import io.github.physanto.opsecengine.dominio.caso.Criterio;
import io.github.physanto.opsecengine.dominio.caso.EstrategiaLocalizacion;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.configuracion.Rol;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.EvaluacionCaso;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Manifiesto;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.ContextoEvento;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.nucleo.VersionMotor;
import io.github.physanto.opsecengine.nucleo.evaluacion.EvaluacionService;
import io.github.physanto.opsecengine.nucleo.sesion.ClienteAgente;
import io.github.physanto.opsecengine.nucleo.sesion.SesionService;
import io.github.physanto.opsecengine.nucleo.sesion.TraductorProtocolo;
import io.github.physanto.opsecengine.protocolo.dto.CapturaDto;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ejecuta los casos de una suite contra la aplicación probada y arma sus trazas.
 *
 * <p>Por cada caso abre una sesión nueva con {@link SesionService}, así que cada caso empieza con
 * la aplicación recién arrancada. Envía los pasos al agente en orden y registra en la traza, con la
 * secuencia y el contexto que pone el motor:
 *
 * <ul>
 *   <li>un evento {@code PASO_INICIO} antes de cada paso, con la acción, el objetivo escrito como
 *       texto, el valor y el modo;
 *   <li>los eventos que el agente emite durante el paso, en orden de llegada;
 *   <li>un evento {@code PASO_FIN} con el código del resultado, su detalle y la duración.
 * </ul>
 *
 * <p>Si el caso tiene rol, antes de sus pasos se ejecutan los pasos de login del proyecto ({@link
 * Proyecto#pasosLogin()}) con las variables {@code {usuario}} y {@code {contrasena}} reemplazadas
 * por las credenciales del rol. Sus eventos llevan el número de paso 0, sin rol y con {@code
 * sesionActiva=false}; en sus {@code PASO_INICIO} la contraseña se escribe como {@value
 * #VALOR_OCULTO}, para que no llegue a los informes. Si todos terminan con {@code OK}, el motor
 * registra un evento {@code SESION} con {@code activa=true}, el rol y el usuario, y desde ahí los
 * eventos llevan el rol y {@code sesionActiva=true}. Si un paso de login falla, o el proyecto no
 * declara pasos de login o el rol, los pasos del caso no se ejecutan y el caso queda {@code
 * NO_CONCLUYENTE} con el motivo.
 *
 * <p>Las esperas por condición usan {@code ESPERAR} con un localizador objetivo: el agente espera a
 * que aparezca. Cada intento usa {@link Paso#esperaMaxima()} o, si no se define, {@link
 * ConfigEjecucion#esperaMaximaPaso()}. El cliente añade su margen de comunicación al plazo. Los
 * constructores originales no reintentan. Los que reciben {@code reintentosEspera} repiten
 * únicamente {@code ESPERAR} cuando el agente responde {@code TIEMPO_AGOTADO}, en la misma sesión y
 * hasta esa cantidad de repeticiones adicionales. No se reintentan errores de comunicación ni otras
 * acciones: una acción que agota el plazo puede seguir ejecutándose. Una interrupción impide el
 * siguiente reintento. Cada intento conserva sus propios {@code PASO_INICIO}, eventos del agente y
 * {@code PASO_FIN}, y sus avisos de progreso, con el mismo número de paso. El último resultado
 * decide si se continúa; todos los eventos se evalúan para no ocultar hallazgos. Esto no modifica
 * {@link ConfigEjecucion#reintentos()}, el número de intentos del caso ni su marca de inestable,
 * reservados para la v0.3.
 *
 * <p>Los pasos {@code CAPTURAR} no se envían como {@code EJECUTAR_PASO}: el motor pide la imagen
 * con el mensaje {@code CAPTURAR}, la guarda como {@code capturas/<caso>-<paso>.png} en la carpeta
 * de resultados de {@link SesionService#carpetaResultados()} y registra un evento {@code CAPTURA}
 * con la ruta relativa, antes del {@code PASO_FIN}. La ventana se toma del primer criterio del
 * objetivo que sea {@code TITULO_VENTANA}, {@code CLASE_VENTANA}, {@code TEXTO} o {@code ETIQUETA};
 * sin objetivo se captura la ventana activa. Si el agente no encuentra la ventana, no hay carpeta
 * de resultados o la imagen no se puede guardar, el paso termina con {@code ERROR}. Las capturas de
 * los pasos de login se guardan como {@code capturas/<caso>-login-<paso>.png}.
 *
 * <p>Si el último intento de un paso no termina con {@code OK} (el agente responde {@code ERROR},
 * {@code NO_ENCONTRADO}, {@code NO_ALCANZABLE} o {@code TIEMPO_AGOTADO}, o no responde), los pasos
 * siguientes no se ejecutan y el motivo pasa a {@link EvaluacionService}: el caso queda {@code
 * NO_CONCLUYENTE} con ese motivo, salvo que un oráculo encuentre un fallo. Lo mismo pasa si la
 * sesión no se puede abrir. Al final se evalúan los oráculos de suite y se arma el manifiesto.
 *
 * <p>Ejemplo:
 *
 * <pre>{@code
 * RegistroExtensiones registro = RegistroExtensiones.descubrir();
 * try (SesionService sesiones = new SesionService(new LanzadorProceso(jarAgente, resultados))) {
 *     EjecucionService ejecucion =
 *             new EjecucionService(proyecto, sesiones, new EvaluacionService(registro));
 *     ResultadoSuite resultado = ejecucion.ejecutar(suite);
 *     new InformeService(registro).generar(resultado, resultados, List.of("json"));
 * }
 * }</pre>
 *
 * <p>El constructor con {@link ObservadorProgreso} permite recibir el inicio y fin de suite, caso y
 * paso, incluidos los pasos de login. Las notificaciones son síncronas, no contienen valores ni
 * credenciales y los fallos del observador se registran sin detener la ejecución. El constructor
 * original usa {@link ObservadorProgreso#VACIO}.
 *
 * <p>Ejecuta un caso a la vez; no es seguro entre hilos.
 *
 * <p>Lo que falta y su versión:
 *
 * <ul>
 *   <li>v0.1: preparar y restaurar los datos con {@code ProveedorDatos} y consultar el estado
 *       final; inferir la sesión activa cuando el caso inicia sesión con sus propios pasos.
 *   <li>v0.2: expandir los ataques del caso, un intento por payload (hoy un caso con ataques queda
 *       {@code NO_CONCLUYENTE} sin ejecutarse).
 *   <li>v0.3: reintentos de casos y marca de inestables; casos en paralelo según {@code
 *       paralelismo}; manifiesto completo para re-ejecutar exactamente (hoy el hash de la suite se
 *       calcula sobre su contenido traducido, no sobre el archivo).
 * </ul>
 */
public final class EjecucionService {

    /**
     * Valor del manifiesto cuando no se puede calcular el hash del JAR. Los hashes calculados se
     * escriben como {@code sha256:<hex>}.
     */
    public static final String HASH_NO_DISPONIBLE = "no-disponible";

    /** Carpeta, dentro de la de resultados, donde se guardan las capturas. */
    public static final String CARPETA_CAPTURAS = "capturas";

    /** Texto que reemplaza la contraseña del rol en los {@code PASO_INICIO} del login. */
    public static final String VALOR_OCULTO = "****";

    /** Variable de los pasos de login que se reemplaza por el usuario del rol. */
    private static final String VARIABLE_USUARIO = "{usuario}";

    /** Variable de los pasos de login que se reemplaza por la contraseña del rol. */
    private static final String VARIABLE_CONTRASENA = "{contrasena}";

    private static final Logger REGISTRO = Logger.getLogger(EjecucionService.class.getName());
    private static final String PREFIJO_HASH = "sha256:";

    /** Estrategias cuyo valor se usa como título o clase de la ventana que se captura. */
    private static final Set<EstrategiaLocalizacion> ESTRATEGIAS_VENTANA =
            Set.of(
                    EstrategiaLocalizacion.TITULO_VENTANA,
                    EstrategiaLocalizacion.CLASE_VENTANA,
                    EstrategiaLocalizacion.TEXTO,
                    EstrategiaLocalizacion.ETIQUETA);

    private final Proyecto proyecto;
    private final SesionService sesiones;
    private final EvaluacionService evaluacion;
    private final ObservadorProgreso observador;
    private final int reintentosEspera;

    /**
     * Crea el servicio.
     *
     * @param proyecto proyecto con la aplicación probada; no puede ser null
     * @param sesiones servicio que abre una sesión por caso; no puede ser null y no debe tener una
     *     sesión abierta
     * @param evaluacion servicio que aplica los oráculos; no puede ser null
     * @throws NullPointerException si algún parámetro es null
     */
    public EjecucionService(
            Proyecto proyecto, SesionService sesiones, EvaluacionService evaluacion) {
        this(proyecto, sesiones, evaluacion, ObservadorProgreso.VACIO);
    }

    /**
     * Crea el servicio con un observador de progreso síncrono.
     *
     * @param proyecto proyecto con la aplicación probada; no puede ser null
     * @param sesiones servicio que abre una sesión por caso; no puede ser null ni tener sesión
     *     abierta
     * @param evaluacion servicio que aplica los oráculos; no puede ser null
     * @param observador receptor de las notificaciones; no puede ser null; para omitirlas use
     *     {@link ObservadorProgreso#VACIO}
     * @throws NullPointerException si algún parámetro es null
     */
    public EjecucionService(
            Proyecto proyecto,
            SesionService sesiones,
            EvaluacionService evaluacion,
            ObservadorProgreso observador) {
        this(proyecto, sesiones, evaluacion, observador, 0);
    }

    /**
     * Crea el servicio con reintentos de espera y sin notificaciones de progreso.
     *
     * @param proyecto proyecto con la aplicación probada; no puede ser null
     * @param sesiones servicio que abre una sesión por caso; no puede ser null ni tener sesión
     *     abierta
     * @param evaluacion servicio que aplica los oráculos; no puede ser null
     * @param reintentosEspera repeticiones adicionales de un {@code ESPERAR} que responde {@code
     *     TIEMPO_AGOTADO}; cero o más
     * @throws NullPointerException si algún servicio o el proyecto es null
     * @throws IllegalArgumentException si {@code reintentosEspera} es negativo
     */
    public EjecucionService(
            Proyecto proyecto,
            SesionService sesiones,
            EvaluacionService evaluacion,
            int reintentosEspera) {
        this(proyecto, sesiones, evaluacion, ObservadorProgreso.VACIO, reintentosEspera);
    }

    /**
     * Crea el servicio con observador y reintentos de espera. No es seguro entre hilos.
     *
     * @param proyecto proyecto con la aplicación probada; no puede ser null
     * @param sesiones servicio que abre una sesión por caso; no puede ser null ni tener sesión
     *     abierta
     * @param evaluacion servicio que aplica los oráculos; no puede ser null
     * @param observador receptor síncrono de las notificaciones; no puede ser null
     * @param reintentosEspera repeticiones adicionales de {@code ESPERAR} tras recibir {@code
     *     TIEMPO_AGOTADO}; cero o más; no se repiten acciones con efectos ni errores de conexión
     * @throws NullPointerException si algún parámetro de referencia es null
     * @throws IllegalArgumentException si {@code reintentosEspera} es negativo
     */
    public EjecucionService(
            Proyecto proyecto,
            SesionService sesiones,
            EvaluacionService evaluacion,
            ObservadorProgreso observador,
            int reintentosEspera) {
        if (reintentosEspera < 0) {
            throw new IllegalArgumentException("Los reintentos de espera no pueden ser negativos");
        }
        this.reintentosEspera = reintentosEspera;
        this.observador = Objects.requireNonNull(observador, "El observador no puede ser null");
        this.proyecto = Objects.requireNonNull(proyecto, "El proyecto no puede ser null");
        this.sesiones = Objects.requireNonNull(sesiones, "El servicio de sesiones es null");
        this.evaluacion = Objects.requireNonNull(evaluacion, "El servicio de evaluación es null");
    }

    /**
     * Ejecuta todos los casos de una suite, en orden.
     *
     * @param suite suite a ejecutar; no puede ser null
     * @return el resultado, con un resultado por caso, los hallazgos de suite y el manifiesto
     * @throws NullPointerException si {@code suite} es null
     */
    public ResultadoSuite ejecutar(Suite suite) {
        Objects.requireNonNull(suite, "La suite no puede ser null");
        Instant inicio = Instant.now();
        notificar(suite, null, ProgresoEjecucion.Tipo.SUITE_INICIO, 0, false, null);
        List<ResultadoCaso> resultados = new ArrayList<>();
        List<Traza> trazas = new ArrayList<>();
        for (Caso caso : suite.casos()) {
            ResultadoCaso resultado = ejecutarCaso(suite, caso);
            resultados.add(resultado);
            trazas.add(resultado.traza());
        }
        List<Hallazgo> hallazgosSuite =
                evaluacion.evaluarSuite(Contexto.deSuite(proyecto, suite, trazas));
        Instant fin = Instant.now();
        ResultadoSuite resultado =
                new ResultadoSuite(
                        suite.nombre(),
                        manifiesto(suite, inicio),
                        resultados,
                        hallazgosSuite,
                        inicio,
                        fin);
        notificar(suite, null, ProgresoEjecucion.Tipo.SUITE_FIN, 0, false, null);
        return resultado;
    }

    private ResultadoCaso ejecutarCaso(Suite suite, Caso caso) {
        long comienzo = System.nanoTime();
        notificar(suite, caso, ProgresoEjecucion.Tipo.CASO_INICIO, 0, false, null);
        TrazaEnCurso traza =
                new TrazaEnCurso(
                        suite,
                        caso,
                        evaluacion.iniciarCaso(Contexto.deCaso(proyecto, suite, caso)));
        String motivo;
        Optional<Rol> rol = caso.rol() == null ? Optional.empty() : proyecto.buscarRol(caso.rol());
        if (caso.rol() != null && rol.isEmpty()) {
            motivo = "El proyecto no declara el rol " + caso.rol() + "; el caso no se ejecutó";
        } else if (caso.rol() != null && proyecto.pasosLogin().isEmpty()) {
            motivo =
                    "El caso usa el rol "
                            + caso.rol()
                            + ", pero el proyecto no declara pasos de login; el caso no se"
                            + " ejecutó";
        } else if (!caso.ataques().isEmpty()) {
            motivo = "Los ataques todavía no se expanden (v0.2); el caso no se ejecutó";
        } else {
            motivo = ejecutarPasos(caso, rol.orElse(null), suite.config(), traza);
        }
        Traza completa = traza.terminar();
        Veredicto veredicto = traza.evaluacion.terminar(motivo);
        ResultadoCaso resultado =
                new ResultadoCaso(
                        caso.id(),
                        veredicto,
                        completa,
                        Duration.ofNanos(System.nanoTime() - comienzo),
                        1,
                        false);
        notificar(suite, caso, ProgresoEjecucion.Tipo.CASO_FIN, 0, false, null);
        return resultado;
    }

    /**
     * Ejecuta los pasos en una sesión nueva, después del login si hay rol; devuelve por qué no
     * terminó, o null si terminó.
     */
    private String ejecutarPasos(Caso caso, Rol rol, ConfigEjecucion config, TrazaEnCurso traza) {
        ClienteAgente cliente;
        try {
            cliente = sesiones.abrir(proyecto, config.oculto());
        } catch (IOException | RuntimeException e) {
            REGISTRO.log(Level.WARNING, "No se pudo abrir la sesión del caso " + caso.id(), e);
            return "No se pudo abrir la sesión: " + e.getMessage();
        }
        int ultimoPaso = 0;
        try {
            if (rol != null) {
                String fallo = iniciarSesion(cliente, rol, config, traza);
                if (fallo != null) {
                    return fallo;
                }
            }
            for (Paso paso : caso.pasos()) {
                ultimoPaso = paso.orden();
                String fallo =
                        ejecutarPaso(
                                cliente,
                                paso,
                                paso.orden(),
                                "El paso " + paso.orden(),
                                paso.valor(),
                                config,
                                traza);
                if (fallo != null) {
                    return fallo;
                }
            }
            return null;
        } finally {
            sesiones.cerrar();
            traza.agente(cliente.tomarEventos(), ultimoPaso);
        }
    }

    /**
     * Ejecuta los pasos de login del proyecto con las credenciales del rol y, si todos terminan con
     * {@code OK}, marca la sesión como activa; devuelve por qué falló, o null si se inició sesión.
     */
    private String iniciarSesion(
            ClienteAgente cliente, Rol rol, ConfigEjecucion config, TrazaEnCurso traza) {
        for (Paso plantilla : proyecto.pasosLogin()) {
            Paso paso =
                    new Paso(
                            plantilla.orden(),
                            plantilla.accion(),
                            plantilla.objetivo(),
                            reemplazar(plantilla.valor(), rol.usuario(), rol.contrasena()),
                            plantilla.esperaMaxima(),
                            plantilla.extensiones());
            String fallo =
                    ejecutarPaso(
                            cliente,
                            paso,
                            0,
                            "El paso " + plantilla.orden() + " del login",
                            reemplazar(plantilla.valor(), rol.usuario(), VALOR_OCULTO),
                            config,
                            traza);
            if (fallo != null) {
                return "No se pudo iniciar sesión con el rol " + rol.nombre() + ": " + fallo;
            }
        }
        traza.iniciarSesion();
        Map<String, Object> sesion = new LinkedHashMap<>();
        sesion.put("activa", true);
        sesion.put("rol", rol.nombre());
        sesion.put("usuario", rol.usuario());
        sesion.put("criterio", "pasos de login del proyecto terminados con OK");
        traza.motor(TipoEvento.SESION, 0, sesion);
        return null;
    }

    /** Reemplaza las variables de un valor de login; null se mantiene. */
    private static String reemplazar(String valor, String usuario, String contrasena) {
        return valor == null
                ? null
                : valor.replace(VARIABLE_USUARIO, usuario).replace(VARIABLE_CONTRASENA, contrasena);
    }

    /**
     * Ejecuta un paso; devuelve por qué falló, o null si terminó con {@code OK}.
     *
     * @param numero número de paso de los eventos: el orden del paso en el caso, o 0 en el login
     * @param nombre cómo se nombra el paso en el motivo de un fallo
     * @param valorTraza valor que se registra en el {@code PASO_INICIO}
     */
    private String ejecutarPaso(
            ClienteAgente cliente,
            Paso paso,
            int numero,
            String nombre,
            String valorTraza,
            ConfigEjecucion config,
            TrazaEnCurso traza) {
        int restantes = reintentosEspera;
        ResultadoPasoDto resultado;
        while (true) {
            resultado = ejecutarIntento(cliente, paso, numero, valorTraza, config, traza);
            if (paso.accion() != Accion.ESPERAR
                    || resultado.getCodigo() != CodigoResultado.TIEMPO_AGOTADO
                    || restantes == 0
                    || Thread.currentThread().isInterrupted()) {
                break;
            }
            restantes--;
        }
        if (resultado.getCodigo() == CodigoResultado.OK) {
            return null;
        }
        String objetivo =
                paso.objetivo() == null ? null : TraductorProtocolo.describir(paso.objetivo());
        return nombre
                + " ("
                + paso.accion()
                + (objetivo == null ? "" : " " + objetivo)
                + ") terminó con "
                + resultado.getCodigo()
                + (resultado.getDetalle() == null ? "" : ": " + resultado.getDetalle());
    }

    /** Ejecuta un intento y conserva todos sus eventos, incluso si termina por tiempo agotado. */
    private ResultadoPasoDto ejecutarIntento(
            ClienteAgente cliente,
            Paso paso,
            int numero,
            String valorTraza,
            ConfigEjecucion config,
            TrazaEnCurso traza) {
        PasoDto dto = TraductorProtocolo.aPasoDto(paso, config);
        String objetivo =
                paso.objetivo() == null ? null : TraductorProtocolo.describir(paso.objetivo());
        Map<String, Object> inicio = new LinkedHashMap<>();
        inicio.put("accion", dto.getAccion());
        poner(inicio, "objetivo", objetivo);
        poner(inicio, "valor", valorTraza);
        inicio.put("modo", dto.getModo());
        traza.motor(TipoEvento.PASO_INICIO, numero, inicio);
        notificar(
                traza.suite,
                traza.caso,
                ProgresoEjecucion.Tipo.PASO_INICIO,
                paso.orden(),
                numero == 0,
                null);

        long comienzo = System.nanoTime();
        ResultadoPasoDto resultado;
        Map<String, Object> captura = null;
        try {
            if (paso.accion() == Accion.CAPTURAR) {
                captura =
                        capturar(
                                cliente,
                                traza.caso.id(),
                                paso,
                                numero == 0 ? "login-" + paso.orden() : String.valueOf(numero));
                resultado = new ResultadoPasoDto(CodigoResultado.OK, null, milisDesde(comienzo));
            } else {
                resultado = cliente.enviarPaso(dto);
            }
        } catch (IOException e) {
            resultado =
                    new ResultadoPasoDto(
                            CodigoResultado.ERROR, e.getMessage(), milisDesde(comienzo));
        }
        traza.agente(cliente.tomarEventos(), numero);
        if (captura != null) {
            traza.motor(TipoEvento.CAPTURA, numero, captura);
        }

        Map<String, Object> fin = new LinkedHashMap<>();
        fin.put("codigo", resultado.getCodigo().name());
        poner(fin, "detalle", resultado.getDetalle());
        fin.put("duracionMs", resultado.getDuracionMs());
        traza.motor(TipoEvento.PASO_FIN, numero, fin);
        notificar(
                traza.suite,
                traza.caso,
                ProgresoEjecucion.Tipo.PASO_FIN,
                paso.orden(),
                numero == 0,
                resultado.getCodigo());

        return resultado;
    }

    /**
     * Pide la captura al agente y la guarda en la carpeta de resultados.
     *
     * @return los datos del evento {@code CAPTURA}
     * @throws IOException si el agente no la toma, no hay carpeta o no se puede guardar
     */
    private Map<String, Object> capturar(
            ClienteAgente cliente, String idCaso, Paso paso, String sufijo) throws IOException {
        Optional<Path> carpeta = sesiones.carpetaResultados();
        if (carpeta.isEmpty()) {
            throw new IOException("No hay carpeta de resultados donde guardar la captura");
        }
        CapturaDto captura = cliente.pedirCaptura(ventanaACapturar(paso));
        if (!"png".equalsIgnoreCase(captura.getFormato())) {
            throw new IOException("El agente envió una captura en formato " + captura.getFormato());
        }
        byte[] imagen;
        try {
            imagen = Base64.getDecoder().decode(captura.getBase64());
        } catch (IllegalArgumentException e) {
            throw new IOException("El agente envió una captura que no está en Base64", e);
        }
        String nombre = nombreArchivo(idCaso) + "-" + sufijo + ".png";
        Path capturas = carpeta.get().resolve(CARPETA_CAPTURAS);
        Files.createDirectories(capturas);
        Files.write(capturas.resolve(nombre), imagen);
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("archivo", CARPETA_CAPTURAS + "/" + nombre);
        datos.put("formato", "PNG");
        return datos;
    }

    /** Título o clase de la ventana que pide el paso, o null para la ventana activa. */
    private static String ventanaACapturar(Paso paso) {
        if (paso.objetivo() == null) {
            return null;
        }
        for (Criterio criterio : paso.objetivo().criterios()) {
            if (ESTRATEGIAS_VENTANA.contains(criterio.estrategia())) {
                return criterio.valor();
            }
        }
        return null;
    }

    /** Id del caso con solo letras ASCII, dígitos, punto, guion y guion bajo. */
    private static String nombreArchivo(String idCaso) {
        return idCaso.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static long milisDesde(long comienzo) {
        return Duration.ofNanos(System.nanoTime() - comienzo).toMillis();
    }

    private Manifiesto manifiesto(Suite suite, Instant fecha) {
        return new Manifiesto(
                hashJar(proyecto.jar()),
                VersionMotor.motor(),
                VersionMotor.java(),
                VersionMotor.sistemaOperativo(),
                suite.config().semilla(),
                hashSuite(suite),
                fecha);
    }

    private static String hashJar(Path jar) {
        if (!Files.isRegularFile(jar)) {
            return HASH_NO_DISPONIBLE;
        }
        MessageDigest digest = sha256();
        try (InputStream flujo = new DigestInputStream(Files.newInputStream(jar), digest)) {
            flujo.transferTo(OutputStream.nullOutputStream());
        } catch (IOException e) {
            REGISTRO.log(Level.WARNING, "No se pudo calcular el hash de " + jar, e);
            return HASH_NO_DISPONIBLE;
        }
        return PREFIJO_HASH + HexFormat.of().formatHex(digest.digest());
    }

    /** Hash del contenido traducido de la suite, con un orden estable. */
    private static String hashSuite(Suite suite) {
        StringBuilder texto =
                new StringBuilder(suite.nombre()).append('\n').append(suite.version()).append('\n');
        for (Caso caso : suite.casos()) {
            texto.append(caso.id()).append('|').append(caso.rol()).append('\n');
            for (Paso paso : caso.pasos()) {
                texto.append(paso.orden()).append('|').append(paso.accion()).append('|');
                if (paso.objetivo() != null) {
                    for (Criterio criterio : paso.objetivo().criterios()) {
                        texto.append(criterio.estrategia()).append('=').append(criterio.valor());
                        texto.append(';');
                    }
                }
                texto.append('|').append(paso.valor()).append('|').append(paso.esperaMaxima());
                texto.append('\n');
            }
        }
        return PREFIJO_HASH
                + HexFormat.of()
                        .formatHex(
                                sha256().digest(texto.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-256", e);
        }
    }

    private void notificar(
            Suite suite,
            Caso caso,
            ProgresoEjecucion.Tipo tipo,
            int numeroPaso,
            boolean login,
            CodigoResultado codigo) {
        ProgresoEjecucion progreso =
                new ProgresoEjecucion(
                        tipo,
                        suite.nombre(),
                        suite.casos().size(),
                        caso == null ? null : caso.id(),
                        numeroPaso,
                        caso == null
                                ? 0
                                : (login ? proyecto.pasosLogin().size() : caso.pasos().size()),
                        login,
                        codigo);
        try {
            observador.actualizar(progreso);
        } catch (RuntimeException e) {
            REGISTRO.log(Level.WARNING, "Falló el observador de progreso", e);
        }
    }

    private static void poner(Map<String, Object> datos, String clave, Object valor) {
        if (valor != null) {
            datos.put(clave, valor);
        }
    }

    /** Eventos de la traza de un caso, numerados en orden de llegada. */
    private static final class TrazaEnCurso {

        private final Suite suite;
        private final Caso caso;
        private final List<Evento> eventos = new ArrayList<>();
        private final EvaluacionCaso evaluacion;
        private boolean sesionActiva;

        TrazaEnCurso(Suite suite, Caso caso, EvaluacionCaso evaluacion) {
            this.suite = suite;
            this.caso = caso;
            this.evaluacion = evaluacion;
        }

        void motor(TipoEvento tipo, int numeroPaso, Map<String, Object> datos) {
            registrar(new Evento(eventos.size(), Instant.now(), tipo, contexto(numeroPaso), datos));
        }

        void agente(List<EventoDto> recibidos, int numeroPaso) {
            for (EventoDto dto : recibidos) {
                Evento evento;
                try {
                    evento = TraductorProtocolo.aEvento(dto, eventos.size(), contexto(numeroPaso));
                } catch (IllegalArgumentException e) {
                    REGISTRO.log(Level.WARNING, "Se descartó un evento del agente: " + dto, e);
                    continue;
                }
                registrar(evento);
            }
        }

        private void registrar(Evento evento) {
            evaluacion.registrarEvento(evento);
            eventos.add(evento);
        }

        /** Desde aquí los eventos llevan el rol del caso y {@code sesionActiva=true}. */
        void iniciarSesion() {
            sesionActiva = true;
        }

        Traza terminar() {
            return new Traza(caso.id(), eventos);
        }

        private ContextoEvento contexto(int numeroPaso) {
            return new ContextoEvento(
                    caso.id(), numeroPaso, sesionActiva ? caso.rol() : null, sesionActiva);
        }
    }
}
