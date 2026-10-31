package io.github.physanto.opsecengine.nucleo.ejecucion;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.ConfigEjecucion;
import io.github.physanto.opsecengine.dominio.caso.Criterio;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * <p>Si un paso no termina con {@code OK} (el agente responde {@code ERROR}, {@code NO_ENCONTRADO},
 * {@code NO_ALCANZABLE} o {@code TIEMPO_AGOTADO}, o no responde), los pasos siguientes no se
 * ejecutan y el motivo pasa a {@link EvaluacionService}: el caso queda {@code NO_CONCLUYENTE} con
 * ese motivo, salvo que un oráculo encuentre un fallo. Lo mismo pasa si la sesión no se puede
 * abrir. Al final se evalúan los oráculos de suite y se arma el manifiesto.
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
 * <p>Ejecuta un caso a la vez; no es seguro entre hilos.
 *
 * <p>Lo que falta y su versión:
 *
 * <ul>
 *   <li>v0.1: iniciar sesión con el rol del caso usando los pasos de login del proyecto (hoy un
 *       caso con rol queda {@code NO_CONCLUYENTE} sin ejecutarse); preparar y restaurar los datos
 *       con {@code ProveedorDatos} y consultar el estado final; inferir la sesión activa en el
 *       contexto de los eventos; eventos de progreso para la presentación.
 *   <li>v0.2: expandir los ataques del caso, un intento por payload (hoy un caso con ataques queda
 *       {@code NO_CONCLUYENTE} sin ejecutarse).
 *   <li>v0.3: reintentos y marca de inestables; casos en paralelo según {@code paralelismo};
 *       manifiesto completo para re-ejecutar exactamente (hoy el hash de la suite se calcula sobre
 *       su contenido traducido, no sobre el archivo).
 * </ul>
 */
public final class EjecucionService {

    /** Valor del manifiesto cuando no se puede calcular el hash del JAR. */
    public static final String HASH_NO_DISPONIBLE = "no-disponible";

    private static final Logger REGISTRO = Logger.getLogger(EjecucionService.class.getName());

    private final Proyecto proyecto;
    private final SesionService sesiones;
    private final EvaluacionService evaluacion;

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
        return new ResultadoSuite(
                suite.nombre(), manifiesto(suite, inicio), resultados, hallazgosSuite, inicio, fin);
    }

    private ResultadoCaso ejecutarCaso(Suite suite, Caso caso) {
        long comienzo = System.nanoTime();
        TrazaEnCurso traza = new TrazaEnCurso(caso);
        String motivo;
        if (caso.rol() != null) {
            motivo =
                    "El inicio de sesión con el rol "
                            + caso.rol()
                            + " todavía no está implementado (v0.1); el caso no se ejecutó";
        } else if (!caso.ataques().isEmpty()) {
            motivo = "Los ataques todavía no se expanden (v0.2); el caso no se ejecutó";
        } else {
            motivo = ejecutarPasos(caso, suite.config(), traza);
        }
        Traza completa = traza.terminar();
        Veredicto veredicto =
                evaluacion.evaluarCaso(completa, Contexto.deCaso(proyecto, suite, caso), motivo);
        return new ResultadoCaso(
                caso.id(),
                veredicto,
                completa,
                Duration.ofNanos(System.nanoTime() - comienzo),
                1,
                false);
    }

    /** Ejecuta los pasos en una sesión nueva; devuelve por qué no terminó, o null si terminó. */
    private String ejecutarPasos(Caso caso, ConfigEjecucion config, TrazaEnCurso traza) {
        ClienteAgente cliente;
        try {
            cliente = sesiones.abrir(proyecto);
        } catch (IOException | RuntimeException e) {
            REGISTRO.log(Level.WARNING, "No se pudo abrir la sesión del caso " + caso.id(), e);
            return "No se pudo abrir la sesión: " + e.getMessage();
        }
        int ultimoPaso = 0;
        try {
            for (Paso paso : caso.pasos()) {
                ultimoPaso = paso.orden();
                String fallo = ejecutarPaso(cliente, paso, config, traza);
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

    /** Ejecuta un paso; devuelve por qué falló, o null si terminó con {@code OK}. */
    private String ejecutarPaso(
            ClienteAgente cliente, Paso paso, ConfigEjecucion config, TrazaEnCurso traza) {
        PasoDto dto = TraductorProtocolo.aPasoDto(paso, config);
        String objetivo =
                paso.objetivo() == null ? null : TraductorProtocolo.describir(paso.objetivo());
        Map<String, Object> inicio = new LinkedHashMap<>();
        inicio.put("accion", dto.getAccion());
        poner(inicio, "objetivo", objetivo);
        poner(inicio, "valor", dto.getValor());
        inicio.put("modo", dto.getModo());
        traza.motor(TipoEvento.PASO_INICIO, paso.orden(), inicio);

        long comienzo = System.nanoTime();
        ResultadoPasoDto resultado;
        try {
            resultado = cliente.enviarPaso(dto);
        } catch (IOException e) {
            long duracion = Duration.ofNanos(System.nanoTime() - comienzo).toMillis();
            resultado = new ResultadoPasoDto(CodigoResultado.ERROR, e.getMessage(), duracion);
        }
        traza.agente(cliente.tomarEventos(), paso.orden());

        Map<String, Object> fin = new LinkedHashMap<>();
        fin.put("codigo", resultado.getCodigo().name());
        poner(fin, "detalle", resultado.getDetalle());
        fin.put("duracionMs", resultado.getDuracionMs());
        traza.motor(TipoEvento.PASO_FIN, paso.orden(), fin);

        if (resultado.getCodigo() == CodigoResultado.OK) {
            return null;
        }
        return "El paso "
                + paso.orden()
                + " ("
                + paso.accion()
                + (objetivo == null ? "" : " " + objetivo)
                + ") terminó con "
                + resultado.getCodigo()
                + (resultado.getDetalle() == null ? "" : ": " + resultado.getDetalle());
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
        return HexFormat.of().formatHex(digest.digest());
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
        return HexFormat.of()
                .formatHex(sha256().digest(texto.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-256", e);
        }
    }

    private static void poner(Map<String, Object> datos, String clave, Object valor) {
        if (valor != null) {
            datos.put(clave, valor);
        }
    }

    /** Eventos de la traza de un caso, numerados en orden de llegada. */
    private static final class TrazaEnCurso {

        private final Caso caso;
        private final List<Evento> eventos = new ArrayList<>();

        TrazaEnCurso(Caso caso) {
            this.caso = caso;
        }

        void motor(TipoEvento tipo, int numeroPaso, Map<String, Object> datos) {
            eventos.add(
                    new Evento(eventos.size(), Instant.now(), tipo, contexto(numeroPaso), datos));
        }

        void agente(List<EventoDto> recibidos, int numeroPaso) {
            for (EventoDto dto : recibidos) {
                try {
                    eventos.add(
                            TraductorProtocolo.aEvento(dto, eventos.size(), contexto(numeroPaso)));
                } catch (IllegalArgumentException e) {
                    REGISTRO.log(Level.WARNING, "Se descartó un evento del agente: " + dto, e);
                }
            }
        }

        Traza terminar() {
            return new Traza(caso.id(), eventos);
        }

        private ContextoEvento contexto(int numeroPaso) {
            return new ContextoEvento(caso.id(), numeroPaso, caso.rol(), false);
        }
    }
}
