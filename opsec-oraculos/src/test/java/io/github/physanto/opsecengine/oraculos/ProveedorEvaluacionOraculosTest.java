package io.github.physanto.opsecengine.oraculos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.extension.ProveedorEvaluacion;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.ContextoEvento;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProveedorEvaluacionOraculosTest {
    @TempDir Path carpeta;
    private final ProveedorEvaluacion proveedor = new ProveedorEvaluacionOraculos();

    @Test
    void descubreElProveedorYNoCargaReglasSiNoHayRuta() throws Exception {
        assertThat(
                        ServiceLoader.load(ProveedorEvaluacion.class).stream()
                                .map(p -> p.get().getClass().getName()))
                .containsExactly(ProveedorEvaluacionOraculos.class.getName());
        var evaluador = proveedor.crear(List.of(new OraculoExcepcionNoCapturada()), null);
        var sesion = evaluador.iniciarCaso(contexto());
        assertThat(sesion.registrarEvento(evento()).estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(sesion.terminar(null).hallazgos()).hasSize(1);
    }

    @Test
    void creaEvaluadoresIndependientesYCopiaLosOraculos() throws Exception {
        List<Oraculo> lista = new ArrayList<>();
        lista.add(new OraculoExcepcionNoCapturada());
        var primero = proveedor.crear(lista, carpeta);
        lista.clear();
        var segundo = proveedor.crear(lista, carpeta);
        assertThat(primero).isNotSameAs(segundo);
        var sesion = primero.iniciarCaso(contexto());
        assertThat(sesion.registrarEvento(evento()).hallazgos()).hasSize(1);
        assertThat(segundo.iniciarCaso(contexto()).registrarEvento(evento()).hallazgos()).isEmpty();
    }

    @Test
    void cargaLosTresMomentosEnOrdenDeArchivo() throws Exception {
        escribir("b.yaml", regla("REGLA-CASO", "CASO"));
        escribir("a.yaml", regla("REGLA-EVENTO", "EVENTO"));
        escribir("c.yaml", regla("REGLA-SUITE", "SUITE"));
        var evaluador = proveedor.crear(List.of(), carpeta);
        var sesion = evaluador.iniciarCaso(contexto());
        assertThat(sesion.registrarEvento(evento()).hallazgos())
                .extracting(Hallazgo::oraculoId)
                .containsExactly("REGLA-EVENTO");
        assertThat(sesion.terminar(null).hallazgos())
                .extracting(Hallazgo::oraculoId)
                .containsExactly("REGLA-EVENTO", "REGLA-CASO");
        Contexto c = contexto();
        assertThat(
                        evaluador
                                .evaluarSuite(
                                        Contexto.deSuite(
                                                c.proyecto(), c.suite(), List.of(sesion.traza())))
                                .hallazgos())
                .extracting(Hallazgo::oraculoId)
                .containsExactly("REGLA-SUITE");
    }

    @Test
    void rechazaCarpetasAusentesYArchivosEnLugarDeCarpetas() throws Exception {
        assertThatThrownBy(() -> proveedor.crear(List.of(), carpeta.resolve("ausente")))
                .isInstanceOf(IOException.class);
        Path archivo = escribir("archivo.yaml", regla("REGLA", "CASO"));
        assertThatThrownBy(() -> proveedor.crear(List.of(), archivo))
                .isInstanceOf(IOException.class);
    }

    @Test
    void rechazaDuplicadosInclusoEntreJavaYYaml() throws Exception {
        escribir("a.yaml", regla("REGLA", "CASO"));
        Oraculo java =
                new Oraculo() {
                    @Override
                    public String id() {
                        return "REGLA";
                    }

                    @Override
                    public Momento momento() {
                        return Momento.CASO;
                    }

                    @Override
                    public boolean aplica(Caso caso, Contexto contexto) {
                        return true;
                    }

                    @Override
                    public List<Hallazgo> evaluar(Traza traza, Contexto contexto) {
                        return List.of();
                    }
                };
        assertThatThrownBy(() -> proveedor.crear(List.of(java), carpeta))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REGLA");
        escribir("b.yaml", regla("REGLA", "CASO"));
        assertThatThrownBy(() -> proveedor.crear(List.of(), carpeta))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REGLA");
    }

    @Test
    void rechazaReglasInvalidasEIdsReservados() throws Exception {
        Path archivo = escribir("a.yaml", "contenido: invalido\n");
        assertThatThrownBy(() -> proveedor.crear(List.of(), carpeta))
                .isInstanceOf(IllegalArgumentException.class);
        Files.writeString(archivo, regla("SIN-SESION", "CASO"), StandardCharsets.UTF_8);
        assertThatThrownBy(() -> proveedor.crear(List.of(), carpeta))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIN-SESION");
    }

    private Path escribir(String nombre, String contenido) throws IOException {
        return Files.writeString(carpeta.resolve(nombre), contenido, StandardCharsets.UTF_8);
    }

    private static String regla(String id, String momento) {
        return "- id: "
                + id
                + "\n  momento: "
                + momento
                + "\n  si: {tipo: EXCEPCION}\n  entonces: {titulo: Prueba, cwe: CWE-209, confianza: INFO}\n";
    }

    private static Evento evento() {
        return new Evento(
                0,
                Instant.EPOCH,
                TipoEvento.EXCEPCION,
                new ContextoEvento("X", 0, null, false),
                Map.of("capturada", false));
    }

    private static Contexto contexto() {
        Proyecto proyecto =
                new Proyecto(
                        "p", Path.of("app.jar"), null, null, null, null, null, null, null, null);
        Caso caso = new Caso("X", null, null, null, null, null, null);
        return Contexto.deCaso(
                proyecto, new Suite("s", null, null, null, List.of(caso), null), caso);
    }
}
