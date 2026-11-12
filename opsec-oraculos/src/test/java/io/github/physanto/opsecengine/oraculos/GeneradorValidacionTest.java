package io.github.physanto.opsecengine.oraculos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Enfoque;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.configuracion.Rol;
import io.github.physanto.opsecengine.dominio.extension.GeneradorCasos;
import io.github.physanto.opsecengine.dominio.mapa.MapaApp;
import io.github.physanto.opsecengine.dominio.mapa.Objetivo;
import io.github.physanto.opsecengine.oraculos.AdaptadorValidacion.Campo;
import io.github.physanto.opsecengine.oraculos.AdaptadorValidacion.Formulario;
import io.github.physanto.opsecengine.oraculos.AdaptadorValidacion.Payload;
import io.github.physanto.opsecengine.soporte.DatosEjemplo;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GeneradorValidacionTest {
    @Test
    void generaCasosParaLoginYBusquedaConElMapaYBibliotecaReales() throws Exception {
        List<Payload> payloads = biblioteca();
        List<Formulario> formularios = List.of(login(), busqueda());
        var generador = new GeneradorValidacion(new AdaptadorValidacion(formularios, payloads));
        List<Caso> casos =
                generador.generar(
                        DatosEjemplo.mapaClientes(),
                        Objetivo.todaLaApp(),
                        proyecto(Map.of(), List.of()));
        assertThat(casos).hasSize(17);
        assertThat(casos).extracting(Caso::id).doesNotHaveDuplicates();
        for (Caso caso : casos) {
            Map<?, ?> origen = (Map<?, ?>) caso.extensiones().get("validacion");
            Caso base =
                    formularios.stream()
                            .map(Formulario::base)
                            .filter(c -> c.id().equals(origen.get("casoBase")))
                            .findFirst()
                            .orElseThrow();
            int orden = (Integer) origen.get("ordenCampoBase");
            assertThat(caso.ataques()).isEmpty();
            assertThat(caso.pasos()).hasSameSizeAs(base.pasos());
            assertThat(caso.extensiones()).containsEntry("fixture", "conservar");
            assertThat(caso.oraculos()).contains("APP-TERMINADA");
            assertThat(
                            IntStream.range(0, base.pasos().size())
                                    .filter(i ->
                                            !base.pasos()
                                                    .get(i)
                                                    .valor()
                                                    .equals(caso.pasos().get(i).valor()))
                                    .count())
                    .isEqualTo(1);
            for (int i = 0; i < caso.pasos().size(); i++) {
                Paso paso = caso.pasos().get(i);
                Paso original = base.pasos().get(i);
                assertThat(paso.orden()).isEqualTo(i + 1);
                assertThat(paso.objetivo()).isEqualTo(original.objetivo());
                assertThat(paso.accion()).isEqualTo(original.accion());
                assertThat(paso.esperaMaxima()).isEqualTo(original.esperaMaxima());
                assertThat(paso.extensiones()).isEqualTo(original.extensiones());
                if (original.orden() != orden) {
                    assertThat(paso.valor()).isEqualTo(original.valor());
                }
            }
            Payload payload =
                    payloads.stream()
                            .filter(p -> p.id().equals(origen.get("payloadId")))
                            .findFirst()
                            .orElseThrow();
            String valor = caso.pasos().get(orden - 1).valor();
            assertThat(caso.oraculos()).containsAll(payload.oraculos());
            if (payload.usaCanario()) {
                assertThat(valor).isEqualTo(payload.texto().replace("{canario}", caso.canario()));
                assertThat(valor).doesNotContain("{canario}");
            } else {
                assertThat(valor).isEqualTo(payload.texto());
            }
        }
        assertThat(formularios.getFirst().base().pasos().get(2).valor()).isEqualTo("Cliente");
    }

    @Test
    void respetaVentanasCamposYBotonesDeEnvio() throws Exception {
        var generador =
                new GeneradorValidacion(
                        new AdaptadorValidacion(List.of(login(), busqueda()), biblioteca()));
        Proyecto proyecto = proyecto(Map.of(), List.of());
        MapaApp mapa = DatosEjemplo.mapaClientes();
        assertThat(generador.generar(mapa, new Objetivo(null, "InicioSesion", null), proyecto))
                .hasSize(11);
        assertThat(
                        generador.generar(
                                mapa,
                                new Objetivo(
                                        null,
                                        "InicioSesion",
                                        Localizador.porEtiqueta("Contraseña")),
                                proyecto))
                .hasSize(5);
        assertThat(
                        generador.generar(
                                mapa,
                                new Objetivo(
                                        null,
                                        "InicioSesion",
                                        Localizador.porTexto("Iniciar Sesion")),
                                proyecto))
                .hasSize(11);
        assertThat(
                        generador.generar(
                                mapa,
                                new Objetivo(null, "InicioSesion", Localizador.porTexto("Otro")),
                                proyecto))
                .isEmpty();
        assertThat(generador.generar(mapa, new Objetivo(null, "NO-EXISTE", null), proyecto))
                .isEmpty();
        assertThat(generador.generar(new MapaApp(null, null), Objetivo.todaLaApp(), proyecto))
                .isEmpty();
    }

    @Test
    void produceIdsCanariosYOrdenReproduciblesSinDependerDelOrdenDeEntrada() throws Exception {
        List<Payload> payloads = biblioteca();
        var a =
                new GeneradorValidacion(
                        new AdaptadorValidacion(List.of(login(), busqueda()), payloads));
        var b =
                new GeneradorValidacion(
                        new AdaptadorValidacion(List.of(busqueda(), login()), payloads.reversed()));
        MapaApp mapa = DatosEjemplo.mapaClientes();
        Proyecto proyecto = proyecto(Map.of(), List.of());
        List<Caso> casos = a.generar(mapa, Objetivo.todaLaApp(), proyecto);
        assertThat(b.generar(mapa, Objetivo.todaLaApp(), proyecto)).isEqualTo(casos);
        assertThat(casos.stream().map(Caso::canario).filter(java.util.Objects::nonNull).toList())
                .doesNotHaveDuplicates();
    }

    @Test
    void laInstanciaServiceLoaderRecibeElAdaptadorPorProyectoSinEstadoGlobal() throws Exception {
        GeneradorCasos generador =
                ServiceLoader.load(GeneradorCasos.class).stream()
                        .filter(p -> p.type() == GeneradorValidacion.class)
                        .findFirst()
                        .orElseThrow()
                        .get();
        assertThat(generador.enfoque()).isEqualTo(Enfoque.VALIDACION);
        var adaptador = new AdaptadorValidacion(List.of(login()), biblioteca());
        MapaApp mapa = DatosEjemplo.mapaClientes();
        assertThat(
                        generador.generar(
                                mapa,
                                Objetivo.todaLaApp(),
                                proyecto(Map.of("validacion", adaptador), List.of())))
                .hasSize(11);
        assertThat(generador.generar(mapa, Objetivo.todaLaApp(), proyecto(Map.of(), List.of())))
                .isEmpty();
        assertThat(
                        generador.generar(
                                mapa,
                                Objetivo.todaLaApp(),
                                proyecto(Map.of("validacion", Map.of()), List.of())))
                .isEmpty();
        assertThat(
                        generador.generar(
                                mapa,
                                Objetivo.todaLaApp(),
                                proyecto(Map.of("validacion", adaptador), List.of())))
                .hasSize(11);
    }

    @Test
    void conservaRolesConocidosYOmiteLosQueNoExistenEnElProyecto() throws Exception {
        Caso base = login().base();
        Caso conRol = new Caso(base.id(), null, "cliente", base.pasos(), null, null, null);
        var formulario = new Formulario("InicioSesion", conRol, 5, login().campos());
        var generador =
                new GeneradorValidacion(
                        new AdaptadorValidacion(List.of(formulario), List.of(vacio())));
        MapaApp mapa = DatosEjemplo.mapaClientes();
        assertThat(generador.generar(mapa, Objetivo.todaLaApp(), proyecto(Map.of(), List.of())))
                .isEmpty();
        assertThat(
                        generador.generar(
                                mapa,
                                Objetivo.todaLaApp(),
                                proyecto(
                                        Map.of(),
                                        List.of(new Rol("cliente", "Cliente", "Cliente#2026")))))
                .extracting(Caso::rol)
                .containsExactly("cliente", "cliente");
    }

    @Test
    void identificaElPasoPorOrdenCuandoHayLocalizadoresRepetidosYRenumbera() throws Exception {
        Localizador campo = Localizador.porEtiqueta("Usuario");
        Caso base =
                new Caso(
                        "REPETIDO",
                        null,
                        null,
                        List.of(
                                new Paso(10, Accion.LLENAR, campo, "navegacion", null, null),
                                new Paso(20, Accion.LLENAR, campo, "formulario", null, null),
                                new Paso(
                                        30,
                                        Accion.PULSAR,
                                        Localizador.porTexto("Enviar"),
                                        null,
                                        null,
                                        null)),
                        null,
                        null,
                        null);
        var generador =
                new GeneradorValidacion(
                        new AdaptadorValidacion(
                                List.of(
                                        new Formulario(
                                                "InicioSesion",
                                                base,
                                                30,
                                                List.of(new Campo(20, "TEXTO")))),
                                List.of(vacio())));
        Caso caso =
                generador
                        .generar(
                                DatosEjemplo.mapaClientes(),
                                Objetivo.todaLaApp(),
                                proyecto(Map.of(), List.of()))
                        .getFirst();
        assertThat(caso.pasos()).extracting(Paso::orden).containsExactly(1, 2, 3);
        assertThat(caso.pasos().getFirst().valor()).isEqualTo("navegacion");
        assertThat(caso.pasos().get(1).valor()).isEmpty();
    }

    @Test
    void rechazaConfiguracionesAmbiguasAntesDeGenerar() {
        Formulario login = login();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AdaptadorValidacion(List.of(login, login), List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AdaptadorValidacion(List.of(), List.of(vacio(), vacio())));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Payload("X", "sin marca", Set.of("TEXTO"), true, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Campo(1, "DESCONOCIDO"));
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new Formulario(
                                "InicioSesion", login.base(), 5, List.of(new Campo(1, "TEXTO"))));
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new Formulario(
                                "InicioSesion",
                                login.base(),
                                5,
                                List.of(new Campo(3, "TEXTO"), new Campo(3, "TEXTO"))));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Formulario("InicioSesion", login.base(), 3, login.campos()));
        Caso pendiente =
                new Caso(
                        "PENDIENTE",
                        null,
                        null,
                        login.base().pasos(),
                        List.of("ATAQUE"),
                        null,
                        null);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Formulario("InicioSesion", pendiente, 5, login.campos()));
    }

    @Test
    void losDatosParcialesSinCamposOPayloadsNoInventanCasos() throws Exception {
        var sinCampos = new Formulario("InicioSesion", login().base(), 5, List.of());
        MapaApp mapa = DatosEjemplo.mapaClientes();
        Proyecto proyecto = proyecto(Map.of(), List.of());
        assertThat(
                        new GeneradorValidacion(
                                        new AdaptadorValidacion(
                                                List.of(sinCampos), List.of(vacio())))
                                .generar(mapa, Objetivo.todaLaApp(), proyecto))
                .isEmpty();
        assertThat(
                        new GeneradorValidacion(
                                        new AdaptadorValidacion(List.of(login()), List.of()))
                                .generar(mapa, Objetivo.todaLaApp(), proyecto))
                .isEmpty();
    }

    private static List<Payload> biblioteca() throws Exception {
        YAMLMapper mapper = new YAMLMapper();
        List<Payload> payloads = new ArrayList<>();
        for (Path archivo :
                List.of(
                        Path.of("..", "contratos", "ejemplos", "ataques", "inyeccion-sql.yaml"),
                        Path.of(
                                "..",
                                "opsec-ataques",
                                "src",
                                "main",
                                "resources",
                                "io",
                                "github",
                                "physanto",
                                "opsecengine",
                                "ataques",
                                "payloads",
                                "vacios.yaml"))) {
            for (JsonNode nodo : mapper.readTree(archivo.toFile())) {
                Set<String> tipos = new java.util.HashSet<>();
                nodo.path("aplica_a").forEach(t -> tipos.add(t.asText()));
                List<String> oraculos = new ArrayList<>();
                nodo.path("oraculos").forEach(o -> oraculos.add(o.asText()));
                payloads.add(
                        new Payload(
                                nodo.get("id").asText(),
                                nodo.get("payload").asText(),
                                tipos,
                                nodo.path("usa_canario").asBoolean(false),
                                oraculos));
            }
        }
        return payloads;
    }

    private static Payload vacio() {
        return new Payload("VACIO", "", Set.of("CUALQUIERA"), false, List.of());
    }

    private static Formulario login() {
        Caso base =
                new Caso(
                        "LOGIN",
                        null,
                        null,
                        List.of(
                                pulsar(1, "Avanzar"),
                                pulsar(2, "Nube"),
                                llenar(3, "Usuario", "Cliente"),
                                llenar(4, "Contraseña", "Cliente#2026"),
                                pulsar(5, "Iniciar Sesion")),
                        null,
                        List.of("APP-TERMINADA"),
                        Map.of("fixture", "conservar"));
        return new Formulario(
                "InicioSesion",
                base,
                5,
                List.of(new Campo(3, "TEXTO"), new Campo(4, "CONTRASENA")));
    }

    private static Formulario busqueda() {
        List<Paso> pasos = new ArrayList<>(login().base().pasos());
        pasos.add(pulsar(6, "Buscar Persona"));
        pasos.add(llenar(7, "Uid", "persona-demo"));
        pasos.add(pulsar(8, "Buscar"));
        return new Formulario(
                "BuscarPersona",
                new Caso(
                        "BUSQUEDA",
                        null,
                        null,
                        pasos,
                        null,
                        List.of("APP-TERMINADA"),
                        Map.of("fixture", "conservar")),
                8,
                List.of(new Campo(7, "IDENTIFICADOR")));
    }

    private static Paso pulsar(int orden, String texto) {
        return new Paso(
                orden,
                Accion.PULSAR,
                Localizador.porTexto(texto),
                "",
                Duration.ofSeconds(2),
                Map.of("fixture", "paso"));
    }

    private static Paso llenar(int orden, String etiqueta, String valor) {
        return new Paso(
                orden,
                Accion.LLENAR,
                Localizador.porEtiqueta(etiqueta),
                valor,
                Duration.ofSeconds(2),
                Map.of("fixture", "paso"));
    }

    private static Proyecto proyecto(Map<String, Object> extensiones, List<Rol> roles) {
        return new Proyecto(
                "clientes",
                Path.of("app.jar"),
                null,
                null,
                null,
                roles,
                null,
                null,
                null,
                extensiones);
    }
}
