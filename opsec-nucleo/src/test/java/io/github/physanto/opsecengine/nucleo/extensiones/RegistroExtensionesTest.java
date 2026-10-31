package io.github.physanto.opsecengine.nucleo.extensiones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.extension.ProveedorDatos;
import io.github.physanto.opsecengine.dominio.extension.Reportero;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class RegistroExtensionesTest {

    @Test
    void descubreLasExtensionesDeclaradasEnMetaInfServices() {
        RegistroExtensiones registro = RegistroExtensiones.descubrir();

        assertThat(registro.oraculo(OraculoDePrueba.ID)).containsInstanceOf(OraculoDePrueba.class);
        assertThat(registro.tiposDatos()).contains(ProveedorDatosDePrueba.TIPO);
        assertThat(registro.oraculo("NO-EXISTE")).isEmpty();
    }

    @Test
    void creaUnProveedorDeDatosNuevoEnCadaLlamada() {
        RegistroExtensiones registro = RegistroExtensiones.descubrir();

        ProveedorDatos primero = registro.nuevoProveedorDatos(ProveedorDatosDePrueba.TIPO).get();
        ProveedorDatos segundo = registro.nuevoProveedorDatos(ProveedorDatosDePrueba.TIPO).get();

        assertThat(primero).isNotSameAs(segundo);
        assertThat(registro.nuevoProveedorDatos("mysql")).isEmpty();
    }

    @Test
    void rechazaDosOraculosConElMismoId() {
        List<OraculoDePrueba> oraculos = List.of(new OraculoDePrueba(), new OraculoDePrueba());

        assertThatThrownBy(() -> new RegistroExtensiones(oraculos, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hay dos oráculos con el id 'PRUEBA-NUCLEO'")
                .hasMessageContaining(OraculoDePrueba.class.getName());
    }

    @Test
    void rechazaDosReporterosConElMismoFormato() {
        List<Reportero> reporteros = List.of(reportero("json"), reportero("json"));

        assertThatThrownBy(
                        () -> new RegistroExtensiones(List.of(), List.of(), reporteros, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hay dos reporteros con el formato 'json'");
    }

    @Test
    void rechazaDosProveedoresDeDatosDelMismoTipo() {
        List<Supplier<ProveedorDatos>> fabricas =
                List.of(ProveedorDatosDePrueba::new, ProveedorDatosDePrueba::new);

        assertThatThrownBy(() -> new RegistroExtensiones(List.of(), List.of(), List.of(), fabricas))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hay dos proveedores de datos con el tipo 'prueba-nucleo'");
    }

    @Test
    void conservaElOrdenEnQueRecibeLasExtensiones() {
        OraculoDePrueba a = new OraculoDePrueba("A");
        OraculoDePrueba b = new OraculoDePrueba("B");

        RegistroExtensiones registro =
                new RegistroExtensiones(List.of(b, a), List.of(), List.of(), List.of());

        assertThat(registro.oraculos()).containsExactly(b, a);
    }

    private static Reportero reportero(String formato) {
        return new Reportero() {
            @Override
            public String formato() {
                return formato;
            }

            @Override
            public void escribir(ResultadoSuite resultado, Path destino) {}
        };
    }
}
