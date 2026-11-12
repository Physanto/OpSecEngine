package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del adaptador que no necesitan ventanas: funcionan también sin pantalla. Las acciones
 * sobre ventanas reales están en {@code AgenteIT}.
 */
class AdaptadorSwingTest {

    private final AdaptadorSwing adaptador = new AdaptadorSwing((tipo, datos) -> {});

    @Test
    void rechazaLosModosQueTodaviaNoEstanImplementados() {
        ResultadoPasoDto resultado =
                adaptador.ejecutar(new PasoDto("PULSAR", porTexto("Aceptar"), null, "ROBOT", 100));

        assertThat(resultado.getCodigo()).isEqualTo(CodigoResultado.ERROR);
        assertThat(resultado.getDetalle()).contains("ROBOT", "EVENTOS", "SEMANTICO");
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
}
