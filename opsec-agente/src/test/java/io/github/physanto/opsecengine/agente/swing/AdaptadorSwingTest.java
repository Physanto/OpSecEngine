package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del adaptador que no necesitan ventanas: funcionan también sin pantalla. Las acciones
 * sobre ventanas reales están en {@code AgenteIT}.
 */
class AdaptadorSwingTest {

    private final AdaptadorSwing adaptador = new AdaptadorSwing((tipo, datos) -> {});

    @Test
    void rechazaLosModosQueNoExisten() {
        ResultadoPasoDto resultado =
                adaptador.ejecutar(new PasoDto("PULSAR", porTexto("Aceptar"), null, "TURBO", 100));

        assertThat(resultado.getCodigo()).isEqualTo(CodigoResultado.ERROR);
        assertThat(resultado.getDetalle()).contains("TURBO", "ROBOT", "EVENTOS", "SEMANTICO");
    }

    @Test
    void robotRechazaLasAccionesQueNoSoporta() {
        ResultadoPasoDto resultado =
                adaptador.ejecutar(
                        new PasoDto("SELECCIONAR", porTexto("Rol"), "Admin", "ROBOT", 100));

        assertThat(resultado.getCodigo()).isEqualTo(CodigoResultado.ERROR);
        assertThat(resultado.getDetalle()).contains("no está soportada en ROBOT");
    }

    @Test
    void robotExplicaQueNecesitaPantallaSiElEntornoNoLaTiene() {
        Assumptions.assumeTrue(GraphicsEnvironment.isHeadless());

        ResultadoPasoDto resultado =
                adaptador.ejecutar(new PasoDto("PULSAR", porTexto("Aceptar"), null, "ROBOT", 100));

        assertThat(resultado.getCodigo()).isEqualTo(CodigoResultado.ERROR);
        assertThat(resultado.getDetalle()).contains("pantalla visible");
    }

    @Test
    void robotPulsaYEscribeConElCursorYElTecladoReales() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicInteger pulsaciones = new AtomicInteger();
        JTextField campo = new JTextField("viejo");
        JButton boton = new JButton("Aceptar");
        boton.addActionListener(e -> pulsaciones.incrementAndGet());
        JFrame[] ventana = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            campo.setName("campo");
            boton.setName("boton");
            JPanel panel = new JPanel();
            panel.add(campo);
            panel.add(boton);
            ventana[0] = new JFrame("Prueba ROBOT");
            ventana[0].add(panel);
            ventana[0].setSize(300, 120);
            ventana[0].setLocation(100, 100);
            ventana[0].setAlwaysOnTop(true);
            ventana[0].setVisible(true);
        });
        try {
            long limite = System.nanoTime() + 5_000_000_000L;
            while (!ventana[0].isActive() && System.nanoTime() < limite) {
                Thread.sleep(50);
            }
            Assumptions.assumeTrue(ventana[0].isActive(), "El sistema no activó la ventana");
            ResultadoPasoDto lleno =
                    adaptador.ejecutar(
                            new PasoDto(
                                    "LLENAR",
                                    porNombre("campo"),
                                    "Contraseña Ñ 123",
                                    "ROBOT",
                                    3000));
            Assumptions.assumeFalse(
                    lleno.getCodigo() == CodigoResultado.ERROR
                            && lleno.getDetalle().contains("plataforma"),
                    "La plataforma no permite controlar la entrada");
            ResultadoPasoDto pulsado =
                    adaptador.ejecutar(
                            new PasoDto("PULSAR", porNombre("boton"), null, "ROBOT", 3000));

            assertThat(lleno.getCodigo()).isEqualTo(CodigoResultado.OK);
            assertThat(pulsado.getCodigo()).isEqualTo(CodigoResultado.OK);
            assertThat(campo.getText()).isEqualTo("Contraseña Ñ 123");
            assertThat(pulsaciones).hasValue(1);
        } finally {
            SwingUtilities.invokeAndWait(ventana[0]::dispose);
        }
    }

    @Test
    void laAccionCapturarSePideConElMensajeCapturar() {
        ResultadoPasoDto resultado =
                adaptador.ejecutar(new PasoDto("CAPTURAR", null, null, "SEMANTICO", 100));

        assertThat(resultado.getCodigo()).isEqualTo(CodigoResultado.ERROR);
        assertThat(resultado.getDetalle()).contains("mensaje CAPTURAR");
    }

    @Test
    void capturarDevuelveNullSiNingunaVentanaCoincide() {
        assertThat(adaptador.capturar("Inexistente")).isNull();
    }

    @Test
    void marcarRechazaUnValorQueNoEsBooleano() {
        ResultadoPasoDto resultado =
                adaptador.ejecutar(
                        new PasoDto("MARCAR", porTexto("Recordar"), "quizas", "SEMANTICO", 100));

        assertThat(resultado.getCodigo()).isEqualTo(CodigoResultado.ERROR);
        assertThat(resultado.getDetalle()).contains("true o false", "quizas");
    }

    @Test
    void lasAccionesSobreComponentesExigenUnObjetivo() {
        for (String accion :
                List.of("PULSAR", "LLENAR", "SELECCIONAR", "MARCAR", "IR_A", "CERRAR_VENTANA")) {
            ResultadoPasoDto resultado =
                    adaptador.ejecutar(new PasoDto(accion, null, null, "EVENTOS", 100));

            assertThat(resultado.getCodigo()).isEqualTo(CodigoResultado.ERROR);
            assertThat(resultado.getDetalle()).contains("necesita un objetivo");
        }
    }

    @Test
    void eventosConservaElResultadoSiElComponenteNoExiste() {
        ResultadoPasoDto resultado =
                adaptador.ejecutar(
                        new PasoDto("PULSAR", porTexto("Inexistente"), null, "EVENTOS", 100));

        assertThat(resultado.getCodigo()).isEqualTo(CodigoResultado.NO_ENCONTRADO);
    }

    @Test
    void esperarSinObjetivoEsperaLosMilisegundosDelValor() {
        ResultadoPasoDto resultado =
                adaptador.ejecutar(new PasoDto("ESPERAR", null, "50", "SEMANTICO", 1000));

        assertThat(resultado.getCodigo()).isEqualTo(CodigoResultado.OK);
        assertThat(resultado.getDuracionMs()).isGreaterThanOrEqualTo(50);
    }

    @Test
    void pulsarAlgoQueNoExisteTerminaEnNoEncontradoAlVencerLaEspera() {
        ResultadoPasoDto resultado =
                adaptador.ejecutar(
                        new PasoDto("PULSAR", porTexto("No existe"), null, "SEMANTICO", 300));

        assertThat(resultado.getCodigo()).isEqualTo(CodigoResultado.NO_ENCONTRADO);
        assertThat(resultado.getDetalle()).contains("TEXTO=No existe");
        assertThat(resultado.getDuracionMs()).isGreaterThanOrEqualTo(300);
    }

    @Test
    void esperarUnaVentanaQueNoApareceAgotaElTiempo() {
        LocalizadorDto ventana =
                new LocalizadorDto(List.of(new CriterioDto("TITULO_VENTANA", "Inexistente")));

        ResultadoPasoDto resultado =
                adaptador.ejecutar(new PasoDto("ESPERAR", ventana, null, "SEMANTICO", 200));

        assertThat(resultado.getCodigo()).isEqualTo(CodigoResultado.TIEMPO_AGOTADO);
    }

    private static LocalizadorDto porTexto(String texto) {
        return new LocalizadorDto(List.of(new CriterioDto("TEXTO", texto)));
    }

    private static LocalizadorDto porNombre(String nombre) {
        return new LocalizadorDto(List.of(new CriterioDto("NOMBRE_COMPONENTE", nombre)));
    }
}
