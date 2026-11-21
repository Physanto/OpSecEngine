package io.github.physanto.opsecengine.verificacion;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.ataques.CargadorPayloads;
import io.github.physanto.opsecengine.ataques.Payload;
import io.github.physanto.opsecengine.ataques.TipoCampo;
import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Criterio;
import io.github.physanto.opsecengine.dominio.caso.Enfoque;
import io.github.physanto.opsecengine.dominio.caso.EstrategiaLocalizacion;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.nucleo.generacion.GeneracionService;
import io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService;
import io.github.physanto.opsecengine.oraculos.AdaptadorValidacion;
import io.github.physanto.opsecengine.oraculos.GeneradorValidacion;
import io.github.physanto.opsecengine.soporte.DatosEjemplo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Aceptación de la generación de casos (#26): con la biblioteca de payloads real, el mapa de la app
 * de práctica y el flujo conocido de su inicio de sesión, {@link GeneracionService} produce los
 * casos de validación con el generador real, descubierto por {@code ServiceLoader}.
 *
 * <p>También documenta cómo se une todo, que es lo que debe hacer quien llame (la CLI o una
 * integración): el núcleo no depende de {@code opsec-oraculos} ni de {@code opsec-ataques}, así que
 * quien conoce los tres módulos convierte la biblioteca en un {@link AdaptadorValidacion} y lo
 * entrega al proyecto en {@code extensiones.validacion}.
 */
class GeneracionValidacionTest {

    private static final String VENTANA = "InicioSesion";
    private static final int PASO_USUARIO = 3;
    private static final int PASO_CONTRASENA = 4;

    @Test
    void generaCasosDeValidacionParaElInicioDeSesionDeLaAppDePractica(@TempDir Path carpeta)
            throws Exception {
        List<Payload> biblioteca = new CargadorPayloads().cargarIncluidos();
        Proyecto proyecto = proyectoConAdaptador(biblioteca);

        Suite suite =
                new GeneracionService(RegistroExtensiones.descubrir(), new ProyectoService())
                        .generar(
                                proyecto,
                                DatosEjemplo.mapaClientes(),
                                Set.of(Enfoque.VALIDACION),
                                List.of());

        long esperados =
                biblioteca.stream().filter(p -> p.sirvePara(TipoCampo.TEXTO)).count()
                        + biblioteca.stream()
                                .filter(p -> p.sirvePara(TipoCampo.CONTRASENA))
                                .count();
        assertThat(suite.enfoques()).containsExactly(Enfoque.VALIDACION);
        assertThat(suite.casos()).hasSize((int) esperados).isNotEmpty();
        assertThat(suite.casos().stream().map(Caso::id).collect(Collectors.toSet()))
                .as("ids únicos")
                .hasSize(suite.casos().size())
                .allMatch(id -> id.startsWith("VAL-"));
        assertThat(biblioteca.stream().filter(Payload::usaCanario).count()).isPositive();
        Set<Integer> camposVariados = new HashSet<>();
        for (Caso caso : suite.casos()) {
            assertThat(caso.pasos()).hasSize(5);
            int variado = camposDistintosDeLaBase(caso);
            camposVariados.add(variado);
            if (caso.canario() != null) {
                assertThat(caso.pasos().get(variado - 1).valor()).contains(caso.canario());
            }
        }
        assertThat(camposVariados).containsExactlyInAnyOrder(PASO_USUARIO, PASO_CONTRASENA);
        assertThat(suite.casos().stream().flatMap(c -> c.oraculos().stream()))
                .as("los payloads aportan sus oráculos")
                .contains("SQLI-CANARIO");

        Path archivo = carpeta.resolve("validacion.yaml");
        Suite guardada =
                new GeneracionService(RegistroExtensiones.descubrir(), new ProyectoService())
                        .guardar(suite, proyecto, archivo);
        assertThat(Files.readString(archivo)).contains("llenar", "VAL-");
        assertThat(guardada.casos()).hasSameSizeAs(suite.casos());
    }

    @Test
    void sinAdaptadorElGeneradorRealNoProduceCasos() throws Exception {
        Proyecto sinAdaptador = proyecto(Map.of());

        Suite suite =
                new GeneracionService(RegistroExtensiones.descubrir(), new ProyectoService())
                        .generar(
                                sinAdaptador,
                                DatosEjemplo.mapaClientes(),
                                Set.of(Enfoque.VALIDACION),
                                List.of());

        assertThat(suite.casos()).isEmpty();
        assertThat(GeneradorValidacion.EXTENSION).isEqualTo("validacion");
    }

    /** Une la biblioteca de payloads con el flujo conocido del inicio de sesión. */
    private static Proyecto proyectoConAdaptador(List<Payload> biblioteca) {
        List<AdaptadorValidacion.Payload> payloads =
                biblioteca.stream()
                        .map(p ->
                                new AdaptadorValidacion.Payload(
                                        p.id(),
                                        p.texto(),
                                        p.aplicaA().stream()
                                                .map(TipoCampo::name)
                                                .collect(Collectors.toSet()),
                                        p.usaCanario(),
                                        p.oraculos()))
                        .toList();
        AdaptadorValidacion.Formulario login =
                new AdaptadorValidacion.Formulario(
                        VENTANA,
                        baseDeLogin(),
                        5,
                        List.of(
                                new AdaptadorValidacion.Campo(PASO_USUARIO, "TEXTO"),
                                new AdaptadorValidacion.Campo(PASO_CONTRASENA, "CONTRASENA")));
        return proyecto(
                Map.of(
                        GeneradorValidacion.EXTENSION,
                        new AdaptadorValidacion(List.of(login), payloads)));
    }

    private static Caso baseDeLogin() {
        return new Caso(
                "LOGIN-BASE",
                "Inicio de sesión con datos válidos",
                null,
                List.of(
                        new Paso(
                                1,
                                Accion.PULSAR,
                                Localizador.porTexto("Avanzar"),
                                null,
                                null,
                                null),
                        new Paso(2, Accion.PULSAR, Localizador.porTexto("Nube"), null, null, null),
                        new Paso(3, Accion.LLENAR, campo("Usuario"), "Admin", null, null),
                        new Paso(4, Accion.LLENAR, campo("Contraseña"), "Admin#2026", null, null),
                        new Paso(
                                5,
                                Accion.PULSAR,
                                Localizador.porTexto("Iniciar Sesion"),
                                null,
                                null,
                                null)),
                null,
                null,
                null);
    }

    /** Un campo se localiza como lo hace el formato de suites: etiqueta y texto iguales. */
    private static Localizador campo(String etiqueta) {
        return new Localizador(
                List.of(
                        new Criterio(EstrategiaLocalizacion.ETIQUETA, etiqueta),
                        new Criterio(EstrategiaLocalizacion.TEXTO, etiqueta)));
    }

    private static Proyecto proyecto(Map<String, Object> extensiones) {
        return new Proyecto(
                "clientes",
                Path.of("app.jar"),
                "app.Portada",
                null,
                null,
                null,
                null,
                null,
                null,
                extensiones);
    }

    /** Orden del único paso que el caso cambió respecto al flujo base. */
    private static int camposDistintosDeLaBase(Caso caso) {
        Caso base = baseDeLogin();
        int distinto = 0;
        for (int i = 0; i < base.pasos().size(); i++) {
            Paso original = base.pasos().get(i);
            Paso generado = caso.pasos().get(i);
            assertThat(generado.accion()).isEqualTo(original.accion());
            assertThat(generado.objetivo()).isEqualTo(original.objetivo());
            if (!java.util.Objects.equals(original.valor(), generado.valor())) {
                assertThat(distinto).as("solo cambia un campo por caso").isZero();
                distinto = original.orden();
            }
        }
        assertThat(distinto).isPositive();
        return distinto;
    }
}
