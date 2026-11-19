package io.github.physanto.opsecengine.dominio.caso;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Caso de prueba: una secuencia de pasos que se ejecuta con un rol, junto con los ataques y
 * oráculos que se le aplican.
 *
 * <p>Es inmutable y seguro entre hilos. Los valores de {@code extensiones} se copian de forma
 * superficial: quien los crea debe usar valores inmutables.
 *
 * @param id identificador del caso, único dentro de su suite, por ejemplo {@code "SEC-001"}; no
 *     puede ser null ni estar en blanco
 * @param descripcion explicación legible del caso; puede ser null
 * @param rol nombre del rol del proyecto con el que se inicia sesión antes de los pasos; puede ser
 *     null, lo que significa que el caso se ejecuta sin sesión
 * @param pasos pasos en el orden en que se ejecutan; si es null queda vacía, y no admite elementos
 *     null
 * @param ataques identificadores de los ataques del catálogo que se aplican al caso; si es null
 *     queda vacía, y no admite elementos null
 * @param oraculos identificadores de los oráculos que se evalúan además de los implícitos; si es
 *     null queda vacía, y no admite elementos null
 * @param canario marca exacta que el caso escribe para rastrearla hasta SQL, Firestore o la
 *     consola, ya materializada (sin llaves de plantilla); puede ser null si el caso no usa
 *     canario. Desde contratos v1.2
 * @param validacion política de validación del caso para el oráculo {@code
 *     ENTRADA-INVALIDA-ACEPTADA}; puede ser null. Desde contratos v1.2
 * @param autenticacion política de autenticación del caso para los oráculos {@code
 *     CREDENCIALES-DEBILES} y {@code SIN-BLOQUEO}; puede ser null. Desde contratos v1.2
 * @param extensiones campos adicionales para cambios aditivos del contrato; si es null queda vacío,
 *     y no admite claves ni valores null
 * @param fallos fallos que los ganchos del agente provocan durante el caso, cada uno con el formato
 *     {@code <punto>:<clase-excepcion>[:<mensaje>]} (ver {@code contratos/protocolo/README.md}); si
 *     es null queda vacía, y no admite elementos null ni en blanco. Desde contratos v1.6
 */
public record Caso(
        String id,
        String descripcion,
        String rol,
        List<Paso> pasos,
        List<String> ataques,
        List<String> oraculos,
        String canario,
        PoliticaValidacion validacion,
        PoliticaAutenticacion autenticacion,
        Map<String, Object> extensiones,
        List<String> fallos) {

    /** Longitud mínima de un canario, para que no se confunda con datos normales. */
    public static final int LONGITUD_MINIMA_CANARIO = 4;

    /**
     * Crea un caso con copias inmutables de sus colecciones y valida sus datos.
     *
     * @throws NullPointerException si {@code id} es null o si alguna colección contiene elementos,
     *     claves o valores null
     * @throws IllegalArgumentException si {@code id} está en blanco, si {@code rol} no es null y
     *     está en blanco, o si {@code canario} no es null y tiene menos de {@value
     *     #LONGITUD_MINIMA_CANARIO} caracteres, espacios o llaves
     */
    public Caso {
        Objects.requireNonNull(id, "El id del caso no puede ser null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("El id del caso no puede estar en blanco");
        }
        if (rol != null && rol.isBlank()) {
            throw new IllegalArgumentException(
                    "El rol del caso "
                            + id
                            + " no puede estar en blanco; use null para sin sesión");
        }
        if (canario != null
                && (canario.length() < LONGITUD_MINIMA_CANARIO
                        || canario.chars()
                                .anyMatch(
                                        c -> Character.isWhitespace(c) || c == '{' || c == '}'))) {
            throw new IllegalArgumentException(
                    "El canario del caso "
                            + id
                            + " debe tener al menos "
                            + LONGITUD_MINIMA_CANARIO
                            + " caracteres, sin espacios ni llaves");
        }
        pasos = pasos == null ? List.of() : List.copyOf(pasos);
        ataques = ataques == null ? List.of() : List.copyOf(ataques);
        oraculos = oraculos == null ? List.of() : List.copyOf(oraculos);
        extensiones = extensiones == null ? Map.of() : Map.copyOf(extensiones);
        fallos = fallos == null ? List.of() : List.copyOf(fallos);
        for (String fallo : fallos) {
            if (fallo.isBlank()) {
                throw new IllegalArgumentException(
                        "Un fallo del caso " + id + " no puede estar en blanco");
            }
        }
    }

    /**
     * Crea un caso sin fallos inyectados, con la forma de los contratos v1.2 a v1.5.
     *
     * @param id identificador del caso; ver {@link Caso}
     * @param descripcion explicación legible del caso; puede ser null
     * @param rol rol con el que se inicia sesión; null para sin sesión
     * @param pasos pasos del caso; si es null queda vacía
     * @param ataques ids de ataques; si es null queda vacía
     * @param oraculos ids de oráculos adicionales; si es null queda vacía
     * @param canario canario del caso; puede ser null
     * @param validacion política de validación; puede ser null
     * @param autenticacion política de autenticación; puede ser null
     * @param extensiones campos adicionales; si es null queda vacío
     * @throws NullPointerException si {@code id} es null o si alguna colección contiene null
     * @throws IllegalArgumentException si los datos no son válidos
     */
    public Caso(
            String id,
            String descripcion,
            String rol,
            List<Paso> pasos,
            List<String> ataques,
            List<String> oraculos,
            String canario,
            PoliticaValidacion validacion,
            PoliticaAutenticacion autenticacion,
            Map<String, Object> extensiones) {
        this(
                id,
                descripcion,
                rol,
                pasos,
                ataques,
                oraculos,
                canario,
                validacion,
                autenticacion,
                extensiones,
                null);
    }

    /**
     * Crea un caso sin canario ni políticas, con la forma de los contratos v1 y v1.1.
     *
     * @param id identificador del caso; ver {@link Caso}
     * @param descripcion explicación legible del caso; puede ser null
     * @param rol rol con el que se inicia sesión; null para sin sesión
     * @param pasos pasos del caso; si es null queda vacía
     * @param ataques ids de ataques; si es null queda vacía
     * @param oraculos ids de oráculos adicionales; si es null queda vacía
     * @param extensiones campos adicionales; si es null queda vacío
     * @throws NullPointerException si {@code id} es null o si alguna colección contiene null
     * @throws IllegalArgumentException si {@code id} o {@code rol} están en blanco
     */
    public Caso(
            String id,
            String descripcion,
            String rol,
            List<Paso> pasos,
            List<String> ataques,
            List<String> oraculos,
            Map<String, Object> extensiones) {
        this(id, descripcion, rol, pasos, ataques, oraculos, null, null, null, extensiones);
    }

    /**
     * Indica si el caso se ejecuta sin iniciar sesión.
     *
     * @return {@code true} si {@link #rol()} es null
     */
    public boolean sinSesion() {
        return rol == null;
    }
}
