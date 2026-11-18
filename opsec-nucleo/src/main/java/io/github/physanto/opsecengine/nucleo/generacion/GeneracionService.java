package io.github.physanto.opsecengine.nucleo.generacion;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.ConfigEjecucion;
import io.github.physanto.opsecengine.dominio.caso.Enfoque;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.extension.GeneradorCasos;
import io.github.physanto.opsecengine.dominio.mapa.MapaApp;
import io.github.physanto.opsecengine.dominio.mapa.Objetivo;
import io.github.physanto.opsecengine.dominio.mapa.Transicion;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.nucleo.proyecto.ExcepcionConfiguracion;
import io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Produce una {@link Suite} con los casos que proponen los generadores registrados para cada
 * enfoque pedido.
 *
 * <p>Por cada enfoque se usa el {@link GeneradorCasos} registrado para él y se le pasa cada
 * objetivo. Los casos se unen, se renombran los ids repetidos con un sufijo {@code -2}, {@code -3}
 * y se ordenan de forma estable por enfoque y id. Si un enfoque no tiene generador, se falla con un
 * mensaje que lo nombra, en vez de omitirlo en silencio.
 *
 * <p>Sin objetivos se derivan del mapa: uno por cada campo que el mapa sabe llenar (transiciones
 * {@code LLENAR}); si no hay ninguno, un objetivo sobre toda la aplicación.
 *
 * <p>{@link #generar} no toca el disco. {@link #guardar} escribe la suite como YAML y la vuelve a
 * leer con {@link ProyectoService}, de modo que lo guardado cumple el esquema.
 *
 * <p>Es seguro entre hilos si lo son los generadores registrados, que por contrato no tienen
 * estado.
 */
public final class GeneracionService {

    private final RegistroExtensiones extensiones;
    private final ProyectoService proyectos;

    /**
     * Crea el servicio.
     *
     * @param extensiones registro donde se buscan los generadores; no puede ser null
     * @param proyectos servicio que valida la suite guardada; no puede ser null
     * @throws NullPointerException si algún parámetro es null
     */
    public GeneracionService(RegistroExtensiones extensiones, ProyectoService proyectos) {
        this.extensiones = Objects.requireNonNull(extensiones, "El registro es null");
        this.proyectos = Objects.requireNonNull(proyectos, "El servicio de proyectos es null");
    }

    /**
     * Genera una suite.
     *
     * @param proyecto proyecto de pruebas; no puede ser null
     * @param mapa mapa de la aplicación; no puede ser null
     * @param enfoques enfoques pedidos; no puede ser null ni estar vacío
     * @param objetivos qué probar; si está vacío se derivan del mapa
     * @return la suite, con la configuración por defecto; puede no tener casos si los generadores
     *     no encuentran nada que probar
     * @throws IllegalArgumentException si no hay enfoques o alguno no tiene generador registrado
     * @throws NullPointerException si algún parámetro es null
     */
    public Suite generar(
            Proyecto proyecto, MapaApp mapa, Set<Enfoque> enfoques, List<Objetivo> objetivos) {
        Objects.requireNonNull(proyecto, "El proyecto es null");
        Objects.requireNonNull(mapa, "El mapa es null");
        Objects.requireNonNull(enfoques, "Los enfoques son null");
        Objects.requireNonNull(objetivos, "Los objetivos son null");
        if (enfoques.isEmpty()) {
            throw new IllegalArgumentException("Hay que pedir al menos un enfoque");
        }
        List<Objetivo> aProbar = objetivos.isEmpty() ? derivarObjetivos(mapa) : objetivos;
        Map<Enfoque, List<Caso>> porEnfoque = new LinkedHashMap<>();
        for (Enfoque enfoque : EnumSet.copyOf(enfoques)) {
            GeneradorCasos generador =
                    extensiones
                            .generador(enfoque)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "No hay un generador de casos registrado para"
                                                    + " el enfoque "
                                                    + enfoque));
            List<Caso> casos = new ArrayList<>();
            for (Objetivo objetivo : aProbar) {
                casos.addAll(generador.generar(mapa, objetivo, proyecto));
            }
            porEnfoque.put(enfoque, casos);
        }
        List<Caso> unidos = unir(porEnfoque);
        String nombre =
                "generada-"
                        + EnumSet.copyOf(enfoques).stream()
                                .map(e -> e.name().toLowerCase(java.util.Locale.ROOT))
                                .collect(Collectors.joining("-"));
        return new Suite(
                nombre,
                "1",
                EnumSet.copyOf(enfoques),
                ConfigEjecucion.porDefecto(),
                unidos,
                null,
                Map.of());
    }

    /**
     * Escribe la suite como YAML y la valida volviéndola a leer.
     *
     * @param suite suite a guardar; debe tener al menos un caso
     * @param proyecto proyecto con el que se valida (por ejemplo, sus roles); no puede ser null
     * @param destino archivo de destino; su carpeta debe existir
     * @return la suite leída del archivo, ya validada contra el esquema
     * @throws IOException si no se puede escribir o leer
     * @throws ExcepcionConfiguracion si lo escrito no cumple el esquema o el proyecto; en ese caso
     *     el archivo se borra
     * @throws IllegalStateException si la suite no tiene casos
     * @throws IllegalArgumentException si un paso no se puede expresar en el formato de suites
     * @throws NullPointerException si algún parámetro es null
     */
    public Suite guardar(Suite suite, Proyecto proyecto, Path destino)
            throws IOException, ExcepcionConfiguracion {
        Objects.requireNonNull(suite, "La suite es null");
        Objects.requireNonNull(proyecto, "El proyecto es null");
        Objects.requireNonNull(destino, "El destino es null");
        if (suite.casos().isEmpty()) {
            throw new IllegalStateException("La suite no tiene casos que guardar");
        }
        SuiteYaml.guardar(suite, destino);
        try {
            return proyectos.cargarSuite(destino, proyecto);
        } catch (ExcepcionConfiguracion | IOException | RuntimeException e) {
            Files.deleteIfExists(destino);
            throw e;
        }
    }

    /** Un objetivo por cada campo que el mapa sabe llenar; si no hay ninguno, toda la app. */
    static List<Objetivo> derivarObjetivos(MapaApp mapa) {
        List<Objetivo> objetivos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (Transicion t : mapa.transiciones()) {
            if (t.accion() == Accion.LLENAR && vistos.add(t.origen() + "|" + t.control())) {
                objetivos.add(
                        new Objetivo(
                                "Campo " + t.control() + " de " + t.origen(),
                                t.origen(),
                                t.control()));
            }
        }
        return objetivos.isEmpty() ? List.of(Objetivo.todaLaApp()) : objetivos;
    }

    /** Une los casos, renombra los ids repetidos y ordena por enfoque y id. */
    private static List<Caso> unir(Map<Enfoque, List<Caso>> porEnfoque) {
        List<Caso> unidos = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Map.Entry<Enfoque, List<Caso>> entrada : porEnfoque.entrySet()) {
            List<Caso> ordenados =
                    entrada.getValue().stream().sorted(Comparator.comparing(Caso::id)).toList();
            for (Caso caso : ordenados) {
                unidos.add(conIdUnico(caso, ids));
            }
        }
        return unidos;
    }

    private static Caso conIdUnico(Caso caso, Set<String> ids) {
        String id = caso.id();
        int sufijo = 2;
        while (!ids.add(id)) {
            id = caso.id() + "-" + sufijo++;
        }
        if (id.equals(caso.id())) {
            return caso;
        }
        return new Caso(
                id,
                caso.descripcion(),
                caso.rol(),
                caso.pasos(),
                caso.ataques(),
                caso.oraculos(),
                caso.canario(),
                caso.validacion(),
                caso.autenticacion(),
                caso.extensiones());
    }
}
