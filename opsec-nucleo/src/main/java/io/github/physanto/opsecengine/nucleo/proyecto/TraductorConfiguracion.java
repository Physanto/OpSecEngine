package io.github.physanto.opsecengine.nucleo.proyecto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.ConfigEjecucion;
import io.github.physanto.opsecengine.dominio.caso.Criterio;
import io.github.physanto.opsecengine.dominio.caso.Enfoque;
import io.github.physanto.opsecengine.dominio.caso.EstrategiaLocalizacion;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.ModoAccion;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.EntornoDatos;
import io.github.physanto.opsecengine.dominio.configuracion.OperacionesSensibles;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.configuracion.Rol;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Traduce documentos de configuración ya validados contra su esquema a objetos del dominio.
 *
 * <p>Hace las comprobaciones que el esquema no puede expresar, como que un caso use un rol del
 * proyecto o que los ids de los casos no se repitan, y acumula sus errores en vez de detenerse en
 * el primero. Una instancia sirve para un solo documento y no es segura entre hilos.
 */
final class TraductorConfiguracion {

    private static final String CLAVE_ESPERA = "esperaMaximaMs";
    private static final String CLAVE_EXTENSIONES = "extensiones";

    private final ObjectMapper mapper;
    private final List<ErrorConfiguracion> errores = new ArrayList<>();

    TraductorConfiguracion(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Devuelve los errores acumulados.
     *
     * @return lista de errores, vacía si la traducción fue correcta
     */
    List<ErrorConfiguracion> errores() {
        return errores;
    }

    /**
     * Traduce un {@code proyecto.yaml}.
     *
     * @param raiz documento validado contra el esquema del proyecto
     * @param carpeta carpeta del archivo, contra la que se resuelven las rutas relativas
     * @return el proyecto, o null si hubo errores
     */
    Proyecto proyecto(JsonNode raiz, Path carpeta) {
        JsonNode app = raiz.get("app");
        String nombre =
                raiz.has("nombre")
                        ? raiz.get("nombre").asText()
                        : String.valueOf(carpeta.getFileName());
        List<Rol> roles = new ArrayList<>();
        for (Map.Entry<String, JsonNode> rol : raiz.path("roles").properties()) {
            roles.add(
                    new Rol(
                            rol.getKey(),
                            rol.getValue().get("usuario").asText(),
                            rol.getValue().get("contrasena").asText()));
        }
        Set<String> nombresRoles = new LinkedHashSet<>();
        roles.forEach(rol -> nombresRoles.add(rol.nombre()));
        Map<String, List<String>> politica = politica(raiz.path("politica"), nombresRoles);
        List<Paso> login = pasos(raiz.path("login").path("pasos"), "/login/pasos");
        if (!errores.isEmpty()) {
            return null;
        }
        try {
            return new Proyecto(
                    nombre,
                    carpeta.resolve(app.get("jar").asText()).normalize(),
                    app.has("principal") ? app.get("principal").asText() : null,
                    textos(app.path("argumentosJvm")),
                    datos(raiz.get("datos")),
                    roles,
                    login,
                    sensibles(raiz.get("sensibles")),
                    politica,
                    extensiones(raiz, ""));
        } catch (IllegalArgumentException | NullPointerException e) {
            errores.add(new ErrorConfiguracion("", e.getMessage()));
            return null;
        }
    }

    /**
     * Traduce una suite.
     *
     * @param raiz documento validado contra el esquema de las suites
     * @param proyecto proyecto al que pertenece la suite, para comprobar sus roles
     * @return la suite, o null si hubo errores
     */
    Suite suite(JsonNode raiz, Proyecto proyecto) {
        List<Caso> casos = new ArrayList<>();
        Map<String, String> rutasPorId = new HashMap<>();
        JsonNode nodosCasos = raiz.get("casos");
        for (int i = 0; i < nodosCasos.size(); i++) {
            String ruta = "/casos/" + i;
            JsonNode nodo = nodosCasos.get(i);
            String id = nodo.get("id").asText();
            String anterior = rutasPorId.putIfAbsent(id, ruta + "/id");
            if (anterior != null) {
                errores.add(
                        new ErrorConfiguracion(
                                ruta + "/id", "el id " + id + " ya se usa en " + anterior));
            }
            Caso caso = caso(nodo, ruta, proyecto);
            if (caso != null) {
                casos.add(caso);
            }
        }
        if (!errores.isEmpty()) {
            return null;
        }
        Set<Enfoque> enfoques = EnumSet.noneOf(Enfoque.class);
        raiz.path("enfoques").forEach(enfoque -> enfoques.add(Enfoque.valueOf(enfoque.asText())));
        try {
            return new Suite(
                    raiz.get("suite").asText(),
                    raiz.has("version") ? raiz.get("version").asText() : null,
                    enfoques,
                    config(raiz.path("config")),
                    casos,
                    extensiones(raiz, ""));
        } catch (IllegalArgumentException | NullPointerException e) {
            errores.add(new ErrorConfiguracion("", e.getMessage()));
            return null;
        }
    }

    private Caso caso(JsonNode nodo, String ruta, Proyecto proyecto) {
        String id = nodo.get("id").asText();
        String rol = nodo.has("rol") ? nodo.get("rol").asText() : null;
        if (rol != null && proyecto.buscarRol(rol).isEmpty()) {
            errores.add(
                    new ErrorConfiguracion(
                            ruta + "/rol",
                            "el rol " + rol + " no está definido en los roles del proyecto"));
        }
        List<Paso> pasos = pasos(nodo.get("pasos"), ruta + "/pasos");
        try {
            return new Caso(
                    id,
                    nodo.has("descripcion") ? nodo.get("descripcion").asText() : null,
                    rol,
                    pasos,
                    textos(nodo.path("ataques")),
                    textos(nodo.path("oraculos")),
                    extensiones(nodo, ruta));
        } catch (IllegalArgumentException | NullPointerException e) {
            errores.add(new ErrorConfiguracion(ruta, e.getMessage()));
            return null;
        }
    }

    /**
     * Traduce una lista de pasos con el formato de una sola clave.
     *
     * <p>{@code llenar} y {@code seleccionar} con varios campos producen un paso por campo, en el
     * orden escrito; los pasos se numeran de nuevo desde 1.
     */
    private List<Paso> pasos(JsonNode nodos, String ruta) {
        List<Paso> pasos = new ArrayList<>();
        for (int i = 0; i < nodos.size(); i++) {
            String rutaPaso = ruta + "/" + i;
            try {
                agregarPasos(nodos.get(i), rutaPaso, pasos);
            } catch (IllegalArgumentException | NullPointerException e) {
                errores.add(new ErrorConfiguracion(rutaPaso, e.getMessage()));
            }
        }
        return pasos;
    }

    private void agregarPasos(JsonNode nodo, String ruta, List<Paso> pasos) {
        Duration espera =
                nodo.has(CLAVE_ESPERA) ? Duration.ofMillis(nodo.get(CLAVE_ESPERA).asLong()) : null;
        Map<String, Object> extensiones = extensiones(nodo, ruta);
        Map.Entry<String, JsonNode> entrada = accion(nodo);
        Accion accion = Accion.valueOf(entrada.getKey().toUpperCase(Locale.ROOT));
        JsonNode valor = entrada.getValue();
        switch (accion) {
            case LLENAR, SELECCIONAR -> {
                for (Map.Entry<String, JsonNode> campo : valor.properties()) {
                    pasos.add(
                            new Paso(
                                    pasos.size() + 1,
                                    accion,
                                    localizadorSimple(campo.getKey()),
                                    campo.getValue().asText(),
                                    espera,
                                    extensiones));
                }
            }
            case ESPERAR, CAPTURAR -> {
                boolean sinObjetivo = valor.isNull() || valor.isIntegralNumber();
                pasos.add(
                        new Paso(
                                pasos.size() + 1,
                                accion,
                                sinObjetivo ? null : localizador(valor),
                                valor.isIntegralNumber() ? valor.asText() : null,
                                espera,
                                extensiones));
            }
            default ->
                    pasos.add(
                            new Paso(
                                    pasos.size() + 1,
                                    accion,
                                    localizador(valor),
                                    null,
                                    espera,
                                    extensiones));
        }
    }

    private static Map.Entry<String, JsonNode> accion(JsonNode paso) {
        for (Map.Entry<String, JsonNode> campo : paso.properties()) {
            if (!campo.getKey().equals(CLAVE_ESPERA) && !campo.getKey().equals(CLAVE_EXTENSIONES)) {
                return campo;
            }
        }
        throw new IllegalArgumentException("el paso no tiene acción");
    }

    /**
     * Traduce un localizador: un texto simple se busca como etiqueta y luego como texto; un objeto
     * da un criterio por clave, en el orden escrito.
     */
    private static Localizador localizador(JsonNode nodo) {
        if (nodo.isTextual()) {
            return localizadorSimple(nodo.asText());
        }
        List<Criterio> criterios = new ArrayList<>();
        for (Map.Entry<String, JsonNode> criterio : nodo.properties()) {
            criterios.add(
                    new Criterio(estrategia(criterio.getKey()), criterio.getValue().asText()));
        }
        return new Localizador(criterios);
    }

    private static Localizador localizadorSimple(String texto) {
        return new Localizador(
                List.of(
                        new Criterio(EstrategiaLocalizacion.ETIQUETA, texto),
                        new Criterio(EstrategiaLocalizacion.TEXTO, texto)));
    }

    /** Convierte una clave del YAML como {@code nombreComponente} en {@code NOMBRE_COMPONENTE}. */
    private static EstrategiaLocalizacion estrategia(String clave) {
        return EstrategiaLocalizacion.valueOf(
                clave.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT));
    }

    private Map<String, List<String>> politica(JsonNode nodo, Set<String> roles) {
        Map<String, List<String>> politica = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> operacion : nodo.properties()) {
            List<String> autorizados = textos(operacion.getValue());
            for (int i = 0; i < autorizados.size(); i++) {
                if (!roles.contains(autorizados.get(i))) {
                    errores.add(
                            new ErrorConfiguracion(
                                    "/politica/" + escaparPuntero(operacion.getKey()) + "/" + i,
                                    "el rol "
                                            + autorizados.get(i)
                                            + " no está definido en los roles del proyecto"));
                }
            }
            politica.put(operacion.getKey(), autorizados);
        }
        return politica;
    }

    private static EntornoDatos datos(JsonNode nodo) {
        if (nodo == null) {
            return EntornoDatos.ninguno();
        }
        Map<String, String> parametros = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> parametro : nodo.path("parametros").properties()) {
            parametros.put(parametro.getKey(), parametro.getValue().asText());
        }
        return new EntornoDatos(nodo.get("tipo").asText(), parametros);
    }

    private static OperacionesSensibles sensibles(JsonNode nodo) {
        OperacionesSensibles porDefecto = OperacionesSensibles.porDefecto();
        if (nodo == null) {
            return porDefecto;
        }
        return new OperacionesSensibles(
                nodo.has("sql") ? Set.copyOf(textos(nodo.get("sql"))) : porDefecto.sql(),
                nodo.has("firestore")
                        ? Set.copyOf(textos(nodo.get("firestore")))
                        : porDefecto.firestore());
    }

    private static ConfigEjecucion config(JsonNode nodo) {
        ConfigEjecucion porDefecto = ConfigEjecucion.porDefecto();
        return new ConfigEjecucion(
                nodo.has("modo")
                        ? ModoAccion.valueOf(nodo.get("modo").asText())
                        : porDefecto.modo(),
                nodo.path("oculto").asBoolean(porDefecto.oculto()),
                nodo.has("esperaMaximaPasoMs")
                        ? Duration.ofMillis(nodo.get("esperaMaximaPasoMs").asLong())
                        : porDefecto.esperaMaximaPaso(),
                nodo.path("reintentos").asInt(porDefecto.reintentos()),
                nodo.path("semilla").asLong(porDefecto.semilla()),
                nodo.path("paralelismo").asInt(porDefecto.paralelismo()));
    }

    private static List<String> textos(JsonNode nodo) {
        List<String> textos = new ArrayList<>();
        nodo.forEach(texto -> textos.add(texto.asText()));
        return textos;
    }

    /** Lee la sección libre {@code extensiones}; el dominio no admite valores null en su raíz. */
    private Map<String, Object> extensiones(JsonNode nodo, String ruta) {
        JsonNode extensiones = nodo.get(CLAVE_EXTENSIONES);
        if (extensiones == null) {
            return Map.of();
        }
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> campo : extensiones.properties()) {
            if (campo.getValue().isNull()) {
                errores.add(
                        new ErrorConfiguracion(
                                ruta
                                        + "/"
                                        + CLAVE_EXTENSIONES
                                        + "/"
                                        + escaparPuntero(campo.getKey()),
                                "las extensiones no admiten valores vacíos"));
            } else {
                mapa.put(campo.getKey(), mapper.convertValue(campo.getValue(), Object.class));
            }
        }
        return mapa;
    }

    /** Escapa una clave para usarla en una ruta JSON Pointer (RFC 6901). */
    private static String escaparPuntero(String clave) {
        return clave.replace("~", "~0").replace("/", "~1");
    }
}
