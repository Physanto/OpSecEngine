package io.github.physanto.opsecengine.protocolo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Intercambios reales por un socket local entre un hilo "motor" y un hilo "agente". */
@Timeout(30)
class CanalMensajesTest {

    private static final Duration ESPERA = Duration.ofSeconds(5);
    private static final String TOKEN = "token-de-sesion";

    private final CodecMensajes codec = new CodecMensajes();
    private ExecutorService hilos;
    private ServerSocket servidor;

    @BeforeEach
    void abrir() throws IOException {
        hilos = Executors.newCachedThreadPool();
        servidor = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    }

    @AfterEach
    void cerrar() throws Exception {
        hilos.shutdownNow();
        hilos.awaitTermination(5, TimeUnit.SECONDS);
        servidor.close();
    }

    private Socket conectar() throws IOException {
        return new Socket(InetAddress.getLoopbackAddress(), servidor.getLocalPort());
    }

    private <T> Future<T> enHilo(Callable<T> tarea) {
        return hilos.submit(tarea);
    }

    private Mensaje recibirObligatorio(CanalMensajes canal) throws IOException {
        Optional<Mensaje> mensaje = canal.recibir(ESPERA);
        assertThat(mensaje).as("mensaje recibido a tiempo").isPresent();
        return mensaje.get();
    }

    @Test
    void saludoComandoRespuestaYEventos() throws Exception {
        Future<String> agente =
                enHilo(
                        () -> {
                            try (CanalMensajes canal = new CanalMensajes(conectar(), codec)) {
                                canal.enviar(
                                        codec.crear(
                                                TipoMensaje.HOLA,
                                                new HolaDto(TOKEN, "0.0.1", "1.8.0", "Linux")));
                                Mensaje comando = recibirObligatorio(canal);
                                PasoDto paso = codec.leerCuerpo(comando, PasoDto.class);
                                canal.enviar(
                                        codec.crear(
                                                TipoMensaje.EVENTO,
                                                new EventoDto(1, 0, "VENTANA_ABIERTA", Map.of())));
                                canal.enviar(
                                        codec.responder(
                                                comando,
                                                TipoMensaje.RESULTADO_PASO,
                                                new ResultadoPasoDto(
                                                        CodigoResultado.OK, paso.getValor(), 5)));
                                return recibirObligatorio(canal).getTipo().name();
                            }
                        });

        try (CanalMensajes motor = new CanalMensajes(servidor.accept(), codec)) {
            Mensaje hola = recibirObligatorio(motor);
            assertThat(hola.getTipo()).isEqualTo(TipoMensaje.HOLA);
            String token = codec.leerCuerpo(hola, HolaDto.class).getToken();
            assertThat(
                            MessageDigest.isEqual(
                                    token.getBytes(StandardCharsets.UTF_8),
                                    TOKEN.getBytes(StandardCharsets.UTF_8)))
                    .isTrue();

            Mensaje comando = codec.crear(TipoMensaje.EJECUTAR_PASO, Muestras.PASO);
            motor.enviar(comando);

            Mensaje evento = recibirObligatorio(motor);
            Mensaje respuesta = recibirObligatorio(motor);
            assertThat(evento.getTipo()).isEqualTo(TipoMensaje.EVENTO);
            assertThat(respuesta.getCorrelacion()).isEqualTo(comando.getId());
            assertThat(codec.leerCuerpo(respuesta, ResultadoPasoDto.class).getDetalle())
                    .isEqualTo(Muestras.PASO.getValor());

            motor.enviar(codec.crear(TipoMensaje.CERRAR, null));
        }
        assertThat(agente.get(10, TimeUnit.SECONDS)).isEqualTo("CERRAR");
    }

    @Test
    void recibirDevuelveVacioSiSeAgotaElTiempo() throws Exception {
        try (Socket cliente = conectar();
                CanalMensajes motor = new CanalMensajes(servidor.accept(), codec)) {
            long inicio = System.nanoTime();

            assertThat(motor.recibir(Duration.ofMillis(200))).isEmpty();
            assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(ESPERA);
        }
    }

    @Test
    void conservaLaLineaParcialEntreEsperasAgotadas() throws Exception {
        byte[] linea = codec.codificarLinea(codec.crear(TipoMensaje.EJECUTAR_PASO, Muestras.PASO));
        int mitad = linea.length / 2;
        try (Socket cliente = conectar();
                CanalMensajes motor = new CanalMensajes(servidor.accept(), codec)) {
            OutputStream salida = cliente.getOutputStream();
            salida.write(linea, 0, mitad);
            salida.flush();

            assertThat(motor.recibir(Duration.ofMillis(200))).isEmpty();

            salida.write(linea, mitad, linea.length - mitad);
            salida.flush();
            Mensaje mensaje = recibirObligatorio(motor);

            assertThat(codec.leerCuerpo(mensaje, PasoDto.class)).isEqualTo(Muestras.PASO);
        }
    }

    @Test
    void unaLineaInvalidaNoRompeElCanal() throws Exception {
        try (Socket cliente = conectar();
                CanalMensajes motor = new CanalMensajes(servidor.accept(), codec)) {
            OutputStream salida = cliente.getOutputStream();
            salida.write("esto no es json\n\n".getBytes(StandardCharsets.UTF_8));
            salida.write(codec.codificarLinea(codec.crear(TipoMensaje.CERRAR, null)));
            salida.flush();

            assertThatThrownBy(() -> motor.recibir(ESPERA)).isInstanceOf(ExcepcionProtocolo.class);
            assertThat(recibirObligatorio(motor).getTipo()).isEqualTo(TipoMensaje.CERRAR);
        }
    }

    @Test
    void enviarEsSeguroEntreHilos() throws Exception {
        int hilosEmisores = 4;
        int porHilo = 50;
        try (Socket cliente = conectar();
                CanalMensajes agente = new CanalMensajes(cliente, codec);
                CanalMensajes motor = new CanalMensajes(servidor.accept(), codec)) {
            for (int h = 0; h < hilosEmisores; h++) {
                int base = h * porHilo;
                enHilo(
                        () -> {
                            for (int i = 0; i < porHilo; i++) {
                                agente.enviar(
                                        codec.crear(
                                                TipoMensaje.EVENTO,
                                                new EventoDto(
                                                        base + i,
                                                        0,
                                                        "CONSOLA",
                                                        Map.of("texto", "x".repeat(2_000)))));
                            }
                            return null;
                        });
            }

            Set<Long> secuencias = new HashSet<>();
            for (int i = 0; i < hilosEmisores * porHilo; i++) {
                Mensaje mensaje = recibirObligatorio(motor);
                secuencias.add(codec.leerCuerpo(mensaje, EventoDto.class).getSecuencia());
            }

            assertThat(secuencias).hasSize(hilosEmisores * porHilo);
        }
    }

    @Test
    void detectaElCierreDelOtroExtremo() throws Exception {
        Socket cliente = conectar();
        try (CanalMensajes motor = new CanalMensajes(servidor.accept(), codec)) {
            cliente.close();

            assertThatThrownBy(() -> motor.recibir(ESPERA)).isInstanceOf(EOFException.class);
        }
    }

    @Test
    void validaElTiempoMaximo() throws Exception {
        try (Socket cliente = conectar();
                CanalMensajes motor = new CanalMensajes(servidor.accept(), codec)) {
            assertThatThrownBy(() -> motor.recibir(Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
