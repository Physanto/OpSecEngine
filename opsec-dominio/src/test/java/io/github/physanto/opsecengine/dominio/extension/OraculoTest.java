package io.github.physanto.opsecengine.dominio.extension;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.util.List;
import org.junit.jupiter.api.Test;

class OraculoTest {

    private static Oraculo devuelve(List<Hallazgo> hallazgos) {
        return new Oraculo() {
            @Override
            public String id() {
                return "PRUEBA";
            }

            @Override
            public Momento momento() {
                return Momento.CASO;
            }

            @Override
            public boolean aplica(Caso caso, Contexto ctx) {
                return true;
            }

            @Override
            public List<Hallazgo> evaluar(Traza traza, Contexto ctx) {
                return hallazgos;
            }
        };
    }

    private static Hallazgo hallazgo(Confianza confianza) {
        return new Hallazgo("PRUEBA", null, confianza, "t", "e", List.of(1L));
    }

    @Test
    void sinHallazgosElResultadoPorDefectoCumple() {
        Veredicto veredicto = devuelve(List.of()).evaluarResultado(null, null);

        assertThat(veredicto.estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(veredicto.hallazgos()).isEmpty();
    }

    @Test
    void unHallazgoQueNoEsInfoHaceQueNoCumpla() {
        Hallazgo probable = hallazgo(Confianza.PROBABLE);

        Veredicto veredicto = devuelve(List.of(probable)).evaluarResultado(null, null);

        assertThat(veredicto.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(veredicto.hallazgos()).containsExactly(probable);
    }

    @Test
    void losHallazgosInfoSeConservanSinCambiarElCumple() {
        Hallazgo info = hallazgo(Confianza.INFO);

        Veredicto veredicto = devuelve(List.of(info)).evaluarResultado(null, null);

        assertThat(veredicto.estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(veredicto.hallazgos()).containsExactly(info);
    }
}
