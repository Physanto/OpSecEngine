package io.github.physanto.opsecengine.dominio.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextoTest {

    private static final Proyecto PROYECTO =
            new Proyecto(
                    "clientes", Path.of("app.jar"), null, null, null, null, null, null, null, null);
    private static final Suite SUITE = new Suite("seguridad", null, null, null, null, null);
    private static final Caso CASO = new Caso("N3", null, null, null, null, null, null);

    @Test
    void deCasoLlevaCasoSinTrazasDeSuite() {
        Contexto contexto = Contexto.deCaso(PROYECTO, SUITE, CASO);

        assertThat(contexto.caso()).isEqualTo(CASO);
        assertThat(contexto.trazasSuite()).isEmpty();
    }

    @Test
    void deSuiteLlevaTrazasSinCaso() {
        Contexto contexto = Contexto.deSuite(PROYECTO, SUITE, List.of(new Traza("N3", null)));

        assertThat(contexto.caso()).isNull();
        assertThat(contexto.trazasSuite()).hasSize(1);
    }

    @Test
    void rechazaCasoConTrazasDeSuite() {
        List<Traza> trazas = List.of(new Traza("N3", null));

        assertThatThrownBy(() -> new Contexto(PROYECTO, SUITE, CASO, trazas))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Contexto.deCaso(PROYECTO, SUITE, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Contexto.deSuite(null, SUITE, null))
                .isInstanceOf(NullPointerException.class);
    }
}
