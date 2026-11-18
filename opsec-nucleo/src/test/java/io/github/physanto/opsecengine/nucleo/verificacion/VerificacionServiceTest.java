package io.github.physanto.opsecengine.nucleo.verificacion;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.nucleo.extensiones.ProveedorDatosDePrueba;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService;
import io.github.physanto.opsecengine.nucleo.sesion.ArranqueApp;
import io.github.physanto.opsecengine.nucleo.verificacion.Comprobacion.Estado;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import io.github.physanto.opsecengine.soporte.AgenteFalso;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pruebas de {@link VerificacionService} con un agente falso que responde por guion. */
class VerificacionServiceTest {

    private static final String SUITE =
            """
            suite: humo
            version: "1"
            enfoques: [FUNCIONAL]
            config:
              modo: SEMANTICO
              esperaMaximaPasoMs: 1000
            casos:
              - id: C-EXISTE
                descripcion: El primer localizador existe.
                pasos:
                  - pulsar: Existe
              - id: C-FALTA
                descripcion: El primer localizador no aparece al abrir.
                pasos:
                  - pulsar: Falta
              - id: C-ADMIN
                descripcion: Caso con rol.
                rol: admin
                pasos:
                  - pulsar: Existe
            """;

    @TempDir Path carpeta;

    private final List<AgenteFalso> agentes = new ArrayList<>();
    private final List<String> pasosRecibidos = new ArrayList<>();

    @AfterEach
    void cerrarAgentes() {
        agentes.forEach(AgenteFalso::close);
    }

    @Test
    void conTodoCorrectoSoloAvisaDelLocalizadorQueFalta() throws Exception {
        Path proyecto = escribirProyecto("");

        ResultadoVerificacion resultado = verificar(proyecto, conAgente());

        assertThat(resultado.listo()).isTrue();
        assertThat(estado(resultado, "Conexión")).isEqualTo(Estado.OK);
        assertThat(estado(resultado, "Localizador humo/C-EXISTE")).isEqualTo(Estado.OK);
        assertThat(estado(resultado, "Localizador humo/C-ADMIN")).isEqualTo(Estado.OK);
        assertThat(estado(resultado, "Localizador humo/C-FALTA")).isEqualTo(Estado.AVISO);
        assertThat(pasosRecibidos).contains("PULSAR:Avanzar", "ESPERAR:Existe", "ESPERAR:Falta");
    }

    @Test
    void sinElJarDeLaAplicacionFallaYNoAbreSesiones() throws Exception {
        Path proyecto = escribirProyecto("");
        Files.delete(carpeta.resolve("app.jar"));

        ResultadoVerificacion resultado = verificar(proyecto, conAgente());

        assertThat(resultado.listo()).isFalse();
        assertThat(resultado.fallo())
                .get()
                .extracting(Comprobacion::nombre)
                .isEqualTo("Aplicación");
        assertThat(agentes).isEmpty();
    }

    @Test
    void sinElJarDelAgenteFalla() throws Exception {
        Path proyecto = escribirProyecto("");

        ResultadoVerificacion resultado =
                new VerificacionService(
                                new ProyectoService(), registro(), (jar, registros) -> conAgente())
                        .verificar(proyecto, carpeta.resolve("no-existe.jar"), c -> {});

        assertThat(resultado.fallo()).get().extracting(Comprobacion::nombre).isEqualTo("Agente");
    }

    @Test
    void unAgenteQueNoSaludaEsUnFalloDeConexionYSeDetiene() throws Exception {
        Path proyecto = escribirProyecto("");
        ArranqueApp sinAgente = (p, puerto, token) -> () -> {};

        ResultadoVerificacion resultado =
                new VerificacionService(
                                new ProyectoService(),
                                registro(),
                                (jar, registros) -> sinAgente,
                                Duration.ofMillis(300))
                        .verificar(proyecto, carpeta.resolve("agente.jar"), c -> {});

        assertThat(resultado.fallo()).get().extracting(Comprobacion::nombre).isEqualTo("Conexión");
        assertThat(resultado.comprobaciones())
                .noneMatch(c -> c.nombre().startsWith("Localizador") || c.nombre().equals("Datos"));
    }

    @Test
    void unProyectoInvalidoFallaSinTocarLaAplicacion() throws Exception {
        Path proyecto = carpeta.resolve("proyecto.yaml");
        Files.writeString(proyecto, "esto: no es un proyecto\n", StandardCharsets.UTF_8);

        ResultadoVerificacion resultado = verificar(proyecto, conAgente());

        assertThat(resultado.fallo()).get().extracting(Comprobacion::nombre).isEqualTo("Proyecto");
        assertThat(agentes).isEmpty();
    }

    @Test
    void unTipoDeDatosSinProveedorEsUnAviso() throws Exception {
        Path proyecto = escribirProyecto("datos:\n  tipo: mysql\n");

        ResultadoVerificacion resultado = verificar(proyecto, conAgente());

        assertThat(resultado.listo()).isTrue();
        assertThat(estado(resultado, "Datos")).isEqualTo(Estado.AVISO);
    }

    @Test
    void unTipoDeDatosConProveedorSePrepara() throws Exception {
        Path proyecto = escribirProyecto("datos:\n  tipo: " + ProveedorDatosDePrueba.TIPO + "\n");

        ResultadoVerificacion resultado = verificar(proyecto, conAgente());

        assertThat(estado(resultado, "Datos")).isEqualTo(Estado.OK);
    }

    @Test
    void siElLoginFallaLosLocalizadoresDelRolSonAvisos() throws Exception {
        Path proyecto = escribirProyecto("");
        ArranqueApp arranque = conAgente(true);

        ResultadoVerificacion resultado =
                new VerificacionService(
                                new ProyectoService(), registro(), (jar, registros) -> arranque)
                        .verificar(proyecto, carpeta.resolve("agente.jar"), c -> {});

        assertThat(resultado.listo()).isTrue();
        assertThat(estado(resultado, "Localizador humo/C-ADMIN")).isEqualTo(Estado.AVISO);
        assertThat(estado(resultado, "Localizador humo/C-EXISTE")).isEqualTo(Estado.OK);
    }

    @Test
    void avisaDeCadaComprobacionApenasTermina() throws Exception {
        Path proyecto = escribirProyecto("");
        List<String> avisos = new ArrayList<>();

        new VerificacionService(new ProyectoService(), registro(), (jar, r) -> conAgente())
                .verificar(proyecto, carpeta.resolve("agente.jar"), c -> avisos.add(c.nombre()));

        assertThat(avisos).startsWith("Proyecto", "Suite humo", "Aplicación", "Agente", "Conexión");
    }

    private ResultadoVerificacion verificar(Path proyecto, ArranqueApp arranque)
            throws IOException {
        Files.writeString(carpeta.resolve("agente.jar"), "agente", StandardCharsets.UTF_8);
        return new VerificacionService(
                        new ProyectoService(), registro(), (jar, registros) -> arranque)
                .verificar(proyecto, carpeta.resolve("agente.jar"), c -> {});
    }

    private static RegistroExtensiones registro() {
        List<Supplier<ProveedorDatosDePrueba>> proveedores = List.of(ProveedorDatosDePrueba::new);
        return new RegistroExtensiones(List.of(), List.of(), List.of(), proveedores);
    }

    private ArranqueApp conAgente() {
        return conAgente(false);
    }

    /** Cada sesión arranca un agente falso nuevo; {@code PULSAR} falla si se pide. */
    private ArranqueApp conAgente(boolean loginFalla) {
        return (proyecto, puerto, token) -> {
            AgenteFalso agente = new AgenteFalso();
            agentes.add(agente);
            agente.respuestaPorDefecto(new ResultadoPasoDto(CodigoResultado.OK, null, 1));
            agente.cuandoPaso(
                            paso -> {
                                pasosRecibidos.add(
                                        paso.getAccion()
                                                + ":"
                                                + (paso.getLocalizador() == null
                                                        ? ""
                                                        : paso.getLocalizador()
                                                                .getCriterios()
                                                                .get(0)
                                                                .getValor()));
                                return "ESPERAR".equals(paso.getAccion())
                                        && paso.getLocalizador()
                                                .getCriterios()
                                                .get(0)
                                                .getValor()
                                                .equals("Falta");
                            })
                    .responder(new ResultadoPasoDto(CodigoResultado.TIEMPO_AGOTADO, "no", 1));
            if (loginFalla) {
                agente.cuandoPaso(paso -> "PULSAR".equals(paso.getAccion()))
                        .responder(new ResultadoPasoDto(CodigoResultado.NO_ENCONTRADO, "no", 1));
            }
            agente.conectar(puerto, token);
            return () -> {};
        };
    }

    private Path escribirProyecto(String extra) throws IOException {
        Files.writeString(carpeta.resolve("app.jar"), "app", StandardCharsets.UTF_8);
        Files.writeString(carpeta.resolve("agente.jar"), "agente", StandardCharsets.UTF_8);
        Files.writeString(
                carpeta.resolve("proyecto.yaml"),
                """
                nombre: humo
                app:
                  jar: app.jar
                roles:
                  admin: {usuario: Admin, contrasena: "x"}
                login:
                  pasos:
                    - pulsar: Avanzar
                """
                        + extra,
                StandardCharsets.UTF_8);
        Files.createDirectories(carpeta.resolve("suites"));
        Files.writeString(
                carpeta.resolve("suites").resolve("humo.yaml"), SUITE, StandardCharsets.UTF_8);
        return carpeta.resolve("proyecto.yaml");
    }

    private static Estado estado(ResultadoVerificacion resultado, String nombre) {
        return resultado.comprobaciones().stream()
                .filter(c -> c.nombre().equals(nombre))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Falta la comprobación " + nombre))
                .estado();
    }
}
