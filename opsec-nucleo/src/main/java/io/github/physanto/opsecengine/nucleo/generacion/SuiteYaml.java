package io.github.physanto.opsecengine.nucleo.generacion;

import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.ConfigEjecucion;
import io.github.physanto.opsecengine.dominio.caso.Criterio;
import io.github.physanto.opsecengine.dominio.caso.EstrategiaLocalizacion;
import io.github.physanto.opsecengine.dominio.caso.IntentoLogin;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.PoliticaAutenticacion;
import io.github.physanto.opsecengine.dominio.caso.PoliticaValidacion;
import io.github.physanto.opsecengine.dominio.caso.Senal;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Escribe una {@link Suite} como YAML con el formato de {@code
 * contratos/esquemas/suite.schema.json}. Es la operación inversa de la lectura de {@code
 * ProyectoService}: leer lo escrito devuelve una suite equivalente. No tiene estado; es seguro
 * entre hilos.
 *
 * <p>Cada paso del dominio se escribe como un paso del YAML. Los pasos {@code LLENAR} y {@code
 * SELECCIONAR} solo pueden localizar el campo con un texto simple (etiqueta o texto iguales),
 * porque el formato usa ese texto como clave.
 */
public final class SuiteYaml {

    private static final YAMLMapper MAPPER =
            YAMLMapper.builder()
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                    .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                    .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
                    .build();

    private SuiteYaml() {}

    /**
     * Lista los pasos de la suite que el formato YAML no puede expresar, para saberlo antes de
     * escribirla. {@link #escribir(Suite)} falla con el primero.
     *
     * @param suite suite a revisar; no puede ser null
     * @return un mensaje en español por cada paso que no se puede expresar, con su caso y su
     *     número; vacío si la suite se puede escribir completa
     * @throws NullPointerException si {@code suite} es null
     */
    public static List<String> noExpresables(Suite suite) {
        Objects.requireNonNull(suite, "Suite null");
        List<String> problemas = new ArrayList<>();
        for (Caso caso : suite.casos()) {
            for (Paso paso : caso.pasos()) {
                try {
                    paso(caso.id(), paso);
                } catch (IllegalArgumentException e) {
                    problemas.add(e.getMessage());
                }
            }
        }
        return problemas;
    }

    /**
     * Convierte la suite a texto YAML.
     *
     * @param suite suite a escribir; no puede ser null
     * @return el YAML
     * @throws IllegalArgumentException si un paso no se puede expresar en el formato
     * @throws NullPointerException si {@code suite} es null
     */
    public static String escribir(Suite suite) {
        try {
            return MAPPER.writeValueAsString(aMapa(Objects.requireNonNull(suite, "Suite null")));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir la suite", e);
        }
    }

    /**
     * Escribe la suite en un archivo UTF-8, reemplazándolo si existe.
     *
     * @param suite suite a escribir; no puede ser null
     * @param destino archivo de destino; su carpeta debe existir; no puede ser null
     * @throws IOException si no se puede escribir
     * @throws IllegalArgumentException si un paso no se puede expresar en el formato
     * @throws NullPointerException si algún parámetro es null
     */
    public static void guardar(Suite suite, Path destino) throws IOException {
        String yaml = escribir(suite);
        try (Writer escritor = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            escritor.write(yaml);
        }
    }

    private static Map<String, Object> aMapa(Suite suite) {
        Map<String, Object> raiz = new LinkedHashMap<>();
        raiz.put("suite", suite.nombre());
        if (suite.version() != null) {
            raiz.put("version", suite.version());
        }
        if (!suite.enfoques().isEmpty()) {
            raiz.put("enfoques", suite.enfoques().stream().sorted().map(Enum::name).toList());
        }
        raiz.put("config", config(suite.config()));
        List<Object> casos = new ArrayList<>();
        for (Caso caso : suite.casos()) {
            casos.add(caso(caso));
        }
        raiz.put("casos", casos);
        if (suite.enumeracionUsuarios() != null) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("casoUsuarioInexistente", suite.enumeracionUsuarios().casoUsuarioInexistente());
            e.put("pasoUsuarioInexistente", suite.enumeracionUsuarios().pasoUsuarioInexistente());
            e.put(
                    "casoContrasenaIncorrecta",
                    suite.enumeracionUsuarios().casoContrasenaIncorrecta());
            e.put(
                    "pasoContrasenaIncorrecta",
                    suite.enumeracionUsuarios().pasoContrasenaIncorrecta());
            raiz.put("enumeracionUsuarios", e);
        }
        if (!suite.extensiones().isEmpty()) {
            raiz.put("extensiones", suite.extensiones());
        }
        return raiz;
    }

    private static Map<String, Object> config(ConfigEjecucion c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("modo", c.modo().name());
        m.put("oculto", c.oculto());
        m.put("esperaMaximaPasoMs", c.esperaMaximaPaso().toMillis());
        m.put("reintentos", c.reintentos());
        m.put("semilla", c.semilla());
        m.put("paralelismo", c.paralelismo());
        return m;
    }

    private static Map<String, Object> caso(Caso caso) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", caso.id());
        if (caso.descripcion() != null) {
            m.put("descripcion", caso.descripcion());
        }
        if (caso.rol() != null) {
            m.put("rol", caso.rol());
        }
        List<Object> pasos = new ArrayList<>();
        for (Paso paso : caso.pasos()) {
            pasos.add(paso(caso.id(), paso));
        }
        m.put("pasos", pasos);
        if (!caso.ataques().isEmpty()) {
            m.put("ataques", caso.ataques());
        }
        if (!caso.oraculos().isEmpty()) {
            m.put("oraculos", caso.oraculos());
        }
        if (caso.canario() != null) {
            m.put("canario", caso.canario());
        }
        if (caso.validacion() != null) {
            m.put("validacion", validacion(caso.validacion()));
        }
        if (caso.autenticacion() != null) {
            m.put("autenticacion", autenticacion(caso.autenticacion()));
        }
        if (!caso.extensiones().isEmpty()) {
            m.put("extensiones", caso.extensiones());
        }
        return m;
    }

    private static Map<String, Object> paso(String casoId, Paso paso) {
        Map<String, Object> m = new LinkedHashMap<>();
        String clave = paso.accion().name().toLowerCase(Locale.ROOT);
        switch (paso.accion()) {
            case LLENAR, SELECCIONAR -> {
                Map<String, Object> campo = new LinkedHashMap<>();
                campo.put(textoSimple(casoId, paso), paso.valor() == null ? "" : paso.valor());
                m.put(clave, campo);
            }
            case ESPERAR, CAPTURAR -> {
                if (paso.objetivo() != null) {
                    m.put(clave, localizador(paso.objetivo()));
                } else if (paso.accion()
                        == io.github.physanto.opsecengine.dominio.caso.Accion.CAPTURAR) {
                    m.put(clave, null);
                } else if (paso.valor() != null) {
                    m.put(clave, Long.parseLong(paso.valor().trim()));
                } else {
                    throw new IllegalArgumentException(
                            "El paso "
                                    + paso.orden()
                                    + " del caso "
                                    + casoId
                                    + " espera sin localizador ni milisegundos");
                }
            }
            default -> m.put(clave, localizador(paso.objetivo()));
        }
        if (paso.esperaMaxima() != null) {
            m.put("esperaMaximaMs", paso.esperaMaxima().toMillis());
        }
        if (!paso.extensiones().isEmpty()) {
            m.put("extensiones", paso.extensiones());
        }
        return m;
    }

    /** Texto que localiza un campo: etiqueta y texto iguales; si no, no se puede expresar. */
    private static String textoSimple(String casoId, Paso paso) {
        String texto = textoSimple(paso.objetivo());
        if (texto == null) {
            throw new IllegalArgumentException(
                    "El paso "
                            + paso.orden()
                            + " del caso "
                            + casoId
                            + " localiza el campo con "
                            + paso.objetivo()
                            + ", que el formato de suites no puede expresar en un llenar o"
                            + " seleccionar");
        }
        return texto;
    }

    private static String textoSimple(Localizador localizador) {
        List<Criterio> c = localizador.criterios();
        if (c.size() == 2
                && c.get(0).estrategia() == EstrategiaLocalizacion.ETIQUETA
                && c.get(1).estrategia() == EstrategiaLocalizacion.TEXTO
                && c.get(0).valor().equals(c.get(1).valor())) {
            return c.get(0).valor();
        }
        return null;
    }

    private static Object localizador(Localizador localizador) {
        String simple = textoSimple(localizador);
        if (simple != null) {
            return simple;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (Criterio criterio : localizador.criterios()) {
            m.put(claveEstrategia(criterio.estrategia()), criterio.valor());
        }
        return m;
    }

    /** {@code NOMBRE_COMPONENTE} se escribe {@code nombreComponente}. */
    private static String claveEstrategia(EstrategiaLocalizacion estrategia) {
        String[] partes = estrategia.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder clave = new StringBuilder(partes[0]);
        for (int i = 1; i < partes.length; i++) {
            clave.append(Character.toUpperCase(partes[i].charAt(0))).append(partes[i].substring(1));
        }
        return clave.toString();
    }

    private static Map<String, Object> validacion(PoliticaValidacion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pasoEntrada", v.pasoEntrada());
        m.put("pasoEnvio", v.pasoEnvio());
        m.put("valorInvalido", v.valorInvalido());
        m.put("aceptacion", senales(v.aceptacion()));
        m.put("rechazo", senales(v.rechazo()));
        return m;
    }

    private static Map<String, Object> autenticacion(PoliticaAutenticacion a) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Object> intentos = new ArrayList<>();
        for (IntentoLogin i : a.intentos()) {
            Map<String, Object> intento = new LinkedHashMap<>();
            intento.put("pasoUsuario", i.pasoUsuario());
            intento.put("pasoContrasena", i.pasoContrasena());
            intento.put("pasoEnvio", i.pasoEnvio());
            intento.put("usuario", i.usuario());
            intento.put("contrasena", i.contrasena());
            if (i.debil()) {
                intento.put("debil", true);
            }
            intentos.add(intento);
        }
        m.put("intentos", intentos);
        m.put("fallosPermitidos", a.fallosPermitidos());
        m.put("retardoMinimoMs", a.retardoMinimoMs());
        m.put("rechazo", senales(a.rechazo()));
        m.put("bloqueo", senales(a.bloqueo()));
        return m;
    }

    private static List<Object> senales(List<Senal> senales) {
        List<Object> lista = new ArrayList<>();
        for (Senal senal : senales) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tipo", senal.tipo().name());
            Map<String, Object> campos = new LinkedHashMap<>();
            senal.campos().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                    .forEach(e -> campos.put(e.getKey(), e.getValue()));
            m.put("campos", campos);
            lista.add(m);
        }
        return lista;
    }
}
