package io.github.physanto.opsecengine.nucleo.exploracion;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.configuracion.EntornoDatos;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.configuracion.Rol;
import io.github.physanto.opsecengine.dominio.mapa.MapaApp;
import io.github.physanto.opsecengine.dominio.mapa.Transicion;
import io.github.physanto.opsecengine.dominio.mapa.Ventana;
import io.github.physanto.opsecengine.nucleo.sesion.ArranqueApp;
import io.github.physanto.opsecengine.nucleo.sesion.SesionService;
import io.github.physanto.opsecengine.protocolo.dto.ArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.NodoArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import io.github.physanto.opsecengine.soporte.AgenteFalso;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pruebas de {@link ExploracionService} contra una aplicación simulada de cuatro ventanas y un
 * diálogo, que responde por el protocolo con un agente falso.
 *
 * <p>La aplicación simulada: {@code Portada} (Avanzar, Ayuda, Eliminar cuenta) lleva a {@code
 * EscogerModo} (Local, Nube); {@code Local} abre {@code MenuLocal} y {@code Nube} abre {@code
 * InicioSesion}. {@code Ayuda} abre un diálogo sin cerrar la portada.
 */
class ExploracionServiceTest {

    private static final Duration ESPERA = Duration.ofSeconds(5);

    /** Ventana simulada: clase, título y botones. */
    private record Def(String clase, String titulo, List<String> botones) {}

    private static final Map<String, Def> VENTANAS =
            Map.of(
                    "Portada",
                    new Def(
                            "App.Portada",
                            "Portada",
                            List.of("Avanzar", "Ayuda", "Eliminar cuenta")),
                    "EscogerModo",
                    new Def("App.EscogerModo", "Modo", List.of("Local", "Nube")),
                    "MenuLocal",
                    new Def("App.MenuLocal", "Menú local", List.of()),
                    "InicioSesion",
                    new Def("App.InicioSesion", "Iniciar sesión", List.of()),
                    "Ayuda",
                    new Def("javax.swing.JDialog", "Ayuda", List.of()));

    /** Qué ventanas quedan visibles al pulsar un botón de una ventana. */
    private static final Map<String, List<String>> SALTOS =
            Map.of(
                    "Portada|Avanzar", List.of("EscogerModo"),
                    "Portada|Ayuda", List.of("Portada", "Ayuda"),
                    "EscogerModo|Local", List.of("MenuLocal"),
                    "EscogerModo|Nube", List.of("InicioSesion"));

    @TempDir Path carpeta;

    private final List<AgenteFalso> agentes = new ArrayList<>();
    private final List<String> pulsados = new CopyOnWriteArrayList<>();
    private final List<String> avisos = new CopyOnWriteArrayList<>();

    @AfterEach
    void cerrar() {
        agentes.forEach(AgenteFalso::close);
    }

    @Test
    void encuentraLasVentanasConocidasYSusTransiciones() {
        MapaApp mapa = explorar(proyecto(List.of(), false), configuracion(4));

        assertThat(ids(mapa))
                .containsExactlyInAnyOrder(
                        "Portada", "EscogerModo", "MenuLocal", "InicioSesion", "JDialog-Ayuda");
        assertThat(destinos(mapa, "Portada"))
                .containsExactlyInAnyOrder("EscogerModo", "JDialog-Ayuda");
        assertThat(destinos(mapa, "EscogerModo"))
                .containsExactlyInAnyOrder("MenuLocal", "InicioSesion");
        assertThat(mapa.contextos()).containsExactly(MapaApp.SIN_SESION);
        assertThat(mapa.alcanzablesDesde("Portada", MapaApp.SIN_SESION))
                .extracting(Ventana::id)
                .contains("MenuLocal");
        assertThat(avisos).anyMatch(a -> a.contains("Ventana encontrada: MenuLocal"));
    }

    @Test
    void nuncaPulsaLosControlesDestructivos() {
        explorar(proyecto(List.of(), false), configuracion(4));

        assertThat(pulsados).isNotEmpty().noneMatch(p -> p.contains("Eliminar cuenta"));
    }

    @Test
    void sinTextosDestructivosPulsaTodo() {
        ConfigExploracion sinReglas =
                new ConfigExploracion(
                        4, Duration.ofMinutes(1), Set.of(), true, Duration.ofMillis(100));

        explorar(proyecto(List.of(), false), sinReglas);

        assertThat(pulsados).anyMatch(p -> p.contains("Eliminar cuenta"));
    }

    @Test
    void respetaLaProfundidadMaxima() {
        MapaApp mapa = explorar(proyecto(List.of(), false), configuracion(1));

        assertThat(ids(mapa)).contains("Portada", "EscogerModo").doesNotContain("MenuLocal");
    }

    @Test
    void paraPorTiempoSinLanzarExcepcion() {
        ConfigExploracion corta =
                new ConfigExploracion(
                        4, Duration.ofNanos(1), Set.of(), true, Duration.ofMillis(100));

        MapaApp mapa = explorar(proyecto(List.of(), false), corta);

        assertThat(ids(mapa)).contains("Portada").doesNotContain("MenuLocal");
    }

    @Test
    void exploraUnContextoPorRolDespuesDeIniciarSesion() {
        Rol admin = new Rol("admin", "Admin", "x");

        MapaApp mapa = explorar(proyecto(List.of(admin), false), configuracion(4));

        assertThat(mapa.contextos()).contains(MapaApp.SIN_SESION, "rol:admin");
        assertThat(
                        mapa.transiciones().stream()
                                .filter(t -> t.contexto().equals("rol:admin"))
                                .map(Transicion::origen))
                .contains("EscogerModo");
    }

    @Test
    void siElLoginFallaOmiteElRolYLoDice() {
        Rol admin = new Rol("admin", "Admin", "x");

        MapaApp mapa = explorar(proyecto(List.of(admin), true), configuracion(4));

        assertThat(mapa.contextos()).containsExactly(MapaApp.SIN_SESION);
        assertThat(avisos).anyMatch(a -> a.contains("rol:admin omitido"));
    }

    @Test
    void elMapaSeGuardaYSeLeeSinPerderNada() throws Exception {
        MapaApp mapa = explorar(proyecto(List.of(), false), configuracion(4));
        Path archivo = carpeta.resolve(MapaJson.ARCHIVO);

        MapaJson.guardar(mapa, archivo);

        assertThat(MapaJson.leer(archivo)).isEqualTo(mapa);
    }

    @Test
    void normalizaTextosSinTildesNiMayusculas() {
        assertThat(ExploracionService.normalizar("  Cerrar Sesión ")).isEqualTo("cerrar sesion");
        assertThat(ExploracionService.normalizar(null)).isEmpty();
    }

    // ---- Aplicación simulada ----

    private MapaApp explorar(Proyecto proyecto, ConfigExploracion config) {
        ArranqueApp arranque =
                (p, puerto, token) -> {
                    nuevaApp(puerto, token, proyecto.pasosLogin().isEmpty());
                    return () -> {};
                };
        try (SesionService sesiones = new SesionService(arranque, ESPERA, ESPERA)) {
            return new ExploracionService(proyecto, sesiones, avisos::add).explorar(config);
        }
    }

    /** Crea una instancia nueva de la aplicación simulada, con su propio estado. */
    private void nuevaApp(int puerto, String token, boolean sinLogin) throws java.io.IOException {
        AgenteFalso agente = new AgenteFalso();
        agentes.add(agente);
        List<String> visibles = new ArrayList<>(List.of("Portada"));
        agente.conArbol(arbol(visibles));
        agente.respuestaPorDefecto(new ResultadoPasoDto(CodigoResultado.OK, null, 1));
        agente.cuandoPaso(paso ->
                        paso.getLocalizador() != null
                                && "Inexistente"
                                        .equals(
                                                paso.getLocalizador()
                                                        .getCriterios()
                                                        .get(0)
                                                        .getValor()))
                .responder(new ResultadoPasoDto(CodigoResultado.NO_ENCONTRADO, "no está", 1));
        agente.cuandoPaso(
                        paso -> {
                            CriterioDto criterio = paso.getLocalizador().getCriterios().get(0);
                            String boton =
                                    "RUTA".equals(criterio.getEstrategia())
                                            ? textoDe(criterio.getValor())
                                            : criterio.getValor();
                            pulsados.add(paso.getAccion() + ":" + boton);
                            String ventana = visibles.get(visibles.size() - 1);
                            if (ventana.equals("Ayuda")) {
                                ventana = "Portada";
                            }
                            List<String> destino = SALTOS.get(ventana + "|" + boton);
                            if (destino != null) {
                                visibles.clear();
                                visibles.addAll(destino);
                                agente.conArbol(arbol(visibles));
                            }
                            return false;
                        })
                .responder(new ResultadoPasoDto(CodigoResultado.OK, null, 1));
        agente.conectar(puerto, token);
    }

    /**
     * Texto del botón cuya ruta es {@code Titulo/JButton[i]}, buscado en las ventanas simuladas.
     */
    private static String textoDe(String ruta) {
        String titulo = ruta.substring(0, ruta.indexOf("/"));
        int indice = Integer.parseInt(ruta.substring(ruta.indexOf('[') + 1, ruta.indexOf(']')));
        return VENTANAS.values().stream()
                .filter(d -> d.titulo().equals(titulo))
                .findFirst()
                .map(d -> d.botones().get(indice))
                .orElse("?");
    }

    private static ArbolDto arbol(List<String> visibles) {
        List<NodoArbolDto> raices = new ArrayList<>();
        for (String id : visibles) {
            Def def = VENTANAS.get(id);
            List<NodoArbolDto> botones = new ArrayList<>();
            for (int i = 0; i < def.botones().size(); i++) {
                botones.add(
                        new NodoArbolDto(
                                "javax.swing.JButton",
                                def.botones().get(i),
                                null,
                                null,
                                true,
                                true,
                                false,
                                List.of(),
                                def.titulo() + "/JButton[" + i + "]",
                                true,
                                List.of("PULSAR")));
            }
            raices.add(
                    new NodoArbolDto(
                            def.clase(),
                            def.titulo(),
                            null,
                            null,
                            true,
                            true,
                            false,
                            botones,
                            def.titulo(),
                            true,
                            List.of()));
        }
        return new ArbolDto(raices);
    }

    private Proyecto proyecto(List<Rol> roles, boolean loginFalla) {
        List<Paso> login =
                roles.isEmpty()
                        ? List.of()
                        : List.of(
                                new Paso(
                                        1,
                                        Accion.PULSAR,
                                        Localizador.porTexto(
                                                loginFalla ? "Inexistente" : "Avanzar"),
                                        null,
                                        null,
                                        null));
        return new Proyecto(
                "simulada",
                Path.of("app.jar"),
                null,
                List.of(),
                null,
                EntornoDatos.ninguno(),
                roles,
                login,
                null,
                Map.of(),
                Map.of());
    }

    private static ConfigExploracion configuracion(int profundidad) {
        return new ConfigExploracion(
                profundidad,
                Duration.ofMinutes(1),
                ConfigExploracion.DESTRUCTIVOS_POR_DEFECTO,
                true,
                Duration.ofMillis(100));
    }

    private static Set<String> ids(MapaApp mapa) {
        return mapa.ventanas().stream().map(Ventana::id).collect(Collectors.toSet());
    }

    private static Set<String> destinos(MapaApp mapa, String origen) {
        return mapa.transiciones().stream()
                .filter(t -> t.origen().equals(origen))
                .map(Transicion::destino)
                .collect(Collectors.toSet());
    }
}
