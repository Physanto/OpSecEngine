package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Comprueba la grabadora con el cursor y el teclado reales del sistema, que llegan a AWT como los
 * de una persona. Se omite sin pantalla o si el sistema no activa la ventana ni deja controlar la
 * entrada.
 */
class GrabadoraRobotTest {

    private static LocalizadorDto etiqueta(String texto) {
        return new LocalizadorDto(List.of(new CriterioDto("ETIQUETA", texto)));
    }

    private static LocalizadorDto nombre(String texto) {
        return new LocalizadorDto(List.of(new CriterioDto("NOMBRE_COMPONENTE", texto)));
    }

    @Test
    void grabaLoQueHaceElTecladoYElRatonReales() throws Exception {
        VentanaLoginPrueba login = VentanaLoginPrueba.abrir();
        Grabadora grabadora = new Grabadora(login.emisor(), mensaje -> {});
        AdaptadorSwing adaptador = new AdaptadorSwing((tipo, datos) -> {});
        try {
            SwingUtilities.invokeAndWait(() -> login.ventana.setAlwaysOnTop(true));
            long limite = System.nanoTime() + 5_000_000_000L;
            while (!login.ventana.isActive() && System.nanoTime() < limite) {
                Thread.sleep(50);
            }
            Assumptions.assumeTrue(login.ventana.isActive(), "El sistema no activó la ventana");
            enEdt(() -> {
                grabadora.iniciar();
                return null;
            });

            ResultadoPasoDto lleno =
                    adaptador.ejecutar(
                            new PasoDto("LLENAR", etiqueta("Usuario"), "Admin", "ROBOT", 3000));
            Assumptions.assumeFalse(
                    lleno.getCodigo() == CodigoResultado.ERROR
                            && lleno.getDetalle().contains("plataforma"),
                    "La plataforma no permite controlar la entrada");
            ResultadoPasoDto pulsado =
                    adaptador.ejecutar(
                            new PasoDto("PULSAR", nombre("botonIngresar"), null, "ROBOT", 3000));
            Thread.sleep(300);
            VentanaLoginPrueba.vaciarEdt();

            assertThat(lleno.getCodigo()).isEqualTo(CodigoResultado.OK);
            assertThat(pulsado.getCodigo()).isEqualTo(CodigoResultado.OK);
            assertThat(login.pulsaciones).hasValue(1);
            assertThat(login.eventos)
                    .extracting(e -> e.get("accion") + ":" + e.get("valor"))
                    .containsExactly("LLENAR:Admin", "PULSAR:null");
            Map<String, Object> pulsar = login.eventos.get(1);
            assertThat(((List<?>) pulsar.get("localizador")).get(0))
                    .isEqualTo(Map.of("estrategia", "NOMBRE_COMPONENTE", "valor", "botonIngresar"));
        } finally {
            enEdt(() -> {
                grabadora.detener();
                return null;
            });
            login.cerrar();
        }
    }

    private static <T> T enEdt(Callable<T> tarea) throws Exception {
        return VentanaLoginPrueba.enEdt(tarea);
    }
}
