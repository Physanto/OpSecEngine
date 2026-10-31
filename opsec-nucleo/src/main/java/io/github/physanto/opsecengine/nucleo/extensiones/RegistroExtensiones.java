package io.github.physanto.opsecengine.nucleo.extensiones;

import io.github.physanto.opsecengine.dominio.caso.Enfoque;
import io.github.physanto.opsecengine.dominio.extension.GeneradorCasos;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.extension.ProveedorDatos;
import io.github.physanto.opsecengine.dominio.extension.Reportero;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Extensiones disponibles para el motor: oráculos, generadores de casos, reporteros y proveedores
 * de datos.
 *
 * <p>{@link #descubrir()} las busca con {@link ServiceLoader} en los archivos {@code
 * META-INF/services/} del classpath, así que un plugin solo necesita estar en el classpath para
 * aparecer. Cada extensión se identifica por una clave que no puede repetirse: el id del oráculo,
 * el enfoque del generador, el formato del reportero y el tipo del proveedor de datos. Si dos
 * extensiones usan la misma clave, el registro no se crea y el error nombra las dos clases.
 *
 * <p>Oráculos, generadores y reporteros son sin estado, así que se guarda una instancia de cada
 * uno. Los proveedores de datos tienen estado, así que {@link #nuevoProveedorDatos(String)} crea
 * una instancia nueva en cada llamada.
 *
 * <p>Ejemplo:
 *
 * <pre>{@code
 * RegistroExtensiones registro = RegistroExtensiones.descubrir();
 * Optional<Reportero> json = registro.reportero("json");
 * }</pre>
 *
 * <p>Es inmutable y seguro entre hilos.
 */
public final class RegistroExtensiones {

    private final Map<String, Oraculo> oraculos;
    private final Map<Enfoque, GeneradorCasos> generadores;
    private final Map<String, Reportero> reporteros;
    private final Map<String, Supplier<ProveedorDatos>> proveedores;

    /**
     * Crea un registro con extensiones dadas, sin buscar en el classpath; sirve sobre todo para
     * pruebas.
     *
     * @param oraculos oráculos con ids únicos; no puede ser null ni contener null
     * @param generadores generadores con enfoques únicos; no puede ser null ni contener null
     * @param reporteros reporteros con formatos únicos; no puede ser null ni contener null
     * @param proveedores fábricas de proveedores de datos con tipos únicos; cada fábrica debe crear
     *     una instancia nueva en cada llamada; no puede ser null ni contener null
     * @throws NullPointerException si alguna colección o alguno de sus elementos es null
     * @throws IllegalStateException si dos extensiones del mismo tipo usan la misma clave
     */
    public RegistroExtensiones(
            Collection<? extends Oraculo> oraculos,
            Collection<? extends GeneradorCasos> generadores,
            Collection<? extends Reportero> reporteros,
            Collection<? extends Supplier<? extends ProveedorDatos>> proveedores) {
        this.oraculos = indexar(oraculos, "oráculos", "id", Oraculo::id);
        this.generadores =
                indexar(generadores, "generadores de casos", "enfoque", GeneradorCasos::enfoque);
        this.reporteros = indexar(reporteros, "reporteros", "formato", Reportero::formato);
        this.proveedores = indexarProveedores(proveedores);
    }

    /**
     * Busca las extensiones con el cargador de clases de contexto del hilo actual.
     *
     * @return el registro con todas las extensiones encontradas
     * @throws IllegalStateException si una extensión no se puede cargar o si dos usan la misma
     *     clave
     */
    public static RegistroExtensiones descubrir() {
        return descubrir(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Busca las extensiones con un cargador de clases, por ejemplo el de la carpeta {@code
     * plugins/}.
     *
     * @param cargador cargador de clases donde buscar; no puede ser null
     * @return el registro con todas las extensiones encontradas
     * @throws NullPointerException si {@code cargador} es null
     * @throws IllegalStateException si una extensión no se puede cargar o si dos usan la misma
     *     clave
     */
    public static RegistroExtensiones descubrir(ClassLoader cargador) {
        Objects.requireNonNull(cargador, "El cargador de clases no puede ser null");
        return new RegistroExtensiones(
                instancias(Oraculo.class, cargador),
                instancias(GeneradorCasos.class, cargador),
                instancias(Reportero.class, cargador),
                fabricas(ProveedorDatos.class, cargador));
    }

    /**
     * Devuelve los oráculos registrados, en el orden en que se encontraron.
     *
     * @return lista inmutable de oráculos
     */
    public List<Oraculo> oraculos() {
        return List.copyOf(oraculos.values());
    }

    /**
     * Busca un oráculo por su id.
     *
     * @param id id del oráculo, por ejemplo {@code "SIN-SESION"}; no puede ser null
     * @return el oráculo, o vacío si no está registrado
     */
    public Optional<Oraculo> oraculo(String id) {
        return Optional.ofNullable(oraculos.get(Objects.requireNonNull(id, "El id es null")));
    }

    /**
     * Devuelve los generadores de casos registrados, en el orden en que se encontraron.
     *
     * @return lista inmutable de generadores
     */
    public List<GeneradorCasos> generadores() {
        return List.copyOf(generadores.values());
    }

    /**
     * Busca el generador de un enfoque.
     *
     * @param enfoque enfoque de prueba; no puede ser null
     * @return el generador, o vacío si no hay uno para ese enfoque
     */
    public Optional<GeneradorCasos> generador(Enfoque enfoque) {
        return Optional.ofNullable(
                generadores.get(Objects.requireNonNull(enfoque, "El enfoque es null")));
    }

    /**
     * Devuelve los reporteros registrados, en el orden en que se encontraron.
     *
     * @return lista inmutable de reporteros
     */
    public List<Reportero> reporteros() {
        return List.copyOf(reporteros.values());
    }

    /**
     * Busca el reportero de un formato.
     *
     * @param formato formato del informe, por ejemplo {@code "json"}; no puede ser null
     * @return el reportero, o vacío si no hay uno para ese formato
     */
    public Optional<Reportero> reportero(String formato) {
        return Optional.ofNullable(
                reporteros.get(Objects.requireNonNull(formato, "El formato es null")));
    }

    /**
     * Devuelve los tipos de entorno de datos que tienen proveedor.
     *
     * @return conjunto inmutable de tipos, en el orden en que se encontraron
     */
    public Set<String> tiposDatos() {
        return Collections.unmodifiableSet(proveedores.keySet());
    }

    /**
     * Crea un proveedor de datos nuevo para un tipo de entorno.
     *
     * @param tipo tipo del entorno, por ejemplo {@code "mysql"}; no puede ser null
     * @return una instancia nueva del proveedor, o vacío si no hay uno para ese tipo
     * @throws IllegalStateException si el proveedor no se puede crear
     */
    public Optional<ProveedorDatos> nuevoProveedorDatos(String tipo) {
        Supplier<ProveedorDatos> fabrica =
                proveedores.get(Objects.requireNonNull(tipo, "El tipo es null"));
        return fabrica == null ? Optional.empty() : Optional.of(crear(fabrica));
    }

    private static <E, K> Map<K, E> indexar(
            Collection<? extends E> extensiones,
            String descripcion,
            String nombreClave,
            Function<? super E, K> clave) {
        Objects.requireNonNull(
                extensiones, "La colección de " + descripcion + " no puede ser null");
        Map<K, E> porClave = new LinkedHashMap<>();
        for (E extension : extensiones) {
            Objects.requireNonNull(extension, "La colección de " + descripcion + " contiene null");
            K k = exigirClave(clave.apply(extension), extension, nombreClave);
            E anterior = porClave.putIfAbsent(k, extension);
            if (anterior != null) {
                throw duplicada(descripcion, nombreClave, k, anterior, extension);
            }
        }
        return Collections.unmodifiableMap(porClave);
    }

    private static Map<String, Supplier<ProveedorDatos>> indexarProveedores(
            Collection<? extends Supplier<? extends ProveedorDatos>> fabricas) {
        Objects.requireNonNull(fabricas, "La colección de proveedores de datos no puede ser null");
        Map<String, Supplier<ProveedorDatos>> porTipo = new LinkedHashMap<>();
        Map<String, ProveedorDatos> muestras = new LinkedHashMap<>();
        for (Supplier<? extends ProveedorDatos> fabrica : fabricas) {
            Objects.requireNonNull(fabrica, "La colección de proveedores de datos contiene null");
            ProveedorDatos muestra = crear(fabrica);
            String tipo = exigirClave(muestra.tipo(), muestra, "tipo");
            ProveedorDatos anterior = muestras.putIfAbsent(tipo, muestra);
            if (anterior != null) {
                throw duplicada("proveedores de datos", "tipo", tipo, anterior, muestra);
            }
            porTipo.put(tipo, fabrica::get);
        }
        return Collections.unmodifiableMap(porTipo);
    }

    private static <K> K exigirClave(K clave, Object extension, String nombreClave) {
        if (clave == null) {
            throw new IllegalStateException(
                    "La extensión "
                            + extension.getClass().getName()
                            + " devuelve null como "
                            + nombreClave
                            + "; corrija el plugin");
        }
        return clave;
    }

    private static IllegalStateException duplicada(
            String descripcion, String nombreClave, Object clave, Object primera, Object segunda) {
        return new IllegalStateException(
                "Hay dos "
                        + descripcion
                        + " con el "
                        + nombreClave
                        + " '"
                        + clave
                        + "': "
                        + primera.getClass().getName()
                        + " y "
                        + segunda.getClass().getName()
                        + ". Quite uno de los dos plugins o cambie su "
                        + nombreClave
                        + ".");
    }

    private static ProveedorDatos crear(Supplier<? extends ProveedorDatos> fabrica) {
        ProveedorDatos proveedor;
        try {
            proveedor = fabrica.get();
        } catch (ServiceConfigurationError e) {
            throw new IllegalStateException(
                    "No se pudo crear un proveedor de datos: " + e.getMessage(), e);
        }
        if (proveedor == null) {
            throw new IllegalStateException("Una fábrica de proveedores de datos devolvió null");
        }
        return proveedor;
    }

    private static <S> List<S> instancias(Class<S> servicio, ClassLoader cargador) {
        List<S> encontradas = new ArrayList<>();
        try {
            for (S instancia : ServiceLoader.load(servicio, cargador)) {
                encontradas.add(instancia);
            }
        } catch (ServiceConfigurationError e) {
            throw errorDeCarga(servicio, e);
        }
        return encontradas;
    }

    private static <S> List<Supplier<S>> fabricas(Class<S> servicio, ClassLoader cargador) {
        try {
            return ServiceLoader.load(servicio, cargador).stream()
                    .<Supplier<S>>map(proveedor -> proveedor)
                    .toList();
        } catch (ServiceConfigurationError e) {
            throw errorDeCarga(servicio, e);
        }
    }

    private static IllegalStateException errorDeCarga(
            Class<?> servicio, ServiceConfigurationError e) {
        return new IllegalStateException(
                "No se pudo cargar una extensión "
                        + servicio.getSimpleName()
                        + " declarada en META-INF/services/"
                        + servicio.getName()
                        + ": "
                        + e.getMessage(),
                e);
    }
}
