package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.configuracion.Rol;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Oráculo {@value #ID} (CWE-532): una contraseña de prueba aparece en la salida estándar o de error
 * de la aplicación.
 *
 * <p>Las contraseñas que busca salen de dos lugares:
 *
 * <ul>
 *   <li>las de los roles del proyecto ({@code roles} en {@code proyecto.yaml});
 *   <li>los valores que el caso escribió con {@code LLENAR} en un campo cuyo localizador menciona
 *       "contraseña", "password", "clave" o "pass" (sin importar mayúsculas ni tildes), tomados de
 *       los eventos {@code PASO_INICIO}.
 * </ul>
 *
 * <p>Se ignoran las contraseñas de menos de {@value #LONGITUD_MINIMA} caracteres, porque aparecen
 * por casualidad en cualquier texto. Produce un hallazgo {@code CONFIRMADO} por cada evento {@code
 * CONSOLA} cuyo texto contiene alguna de ellas, con la secuencia del evento como evidencia. El
 * hallazgo nunca incluye la contraseña: dice de dónde salió.
 *
 * <p>No tiene estado y es seguro entre hilos.
 */
public final class OraculoContrasenaEnConsola implements Oraculo {

    /** Id del oráculo en el catálogo. */
    public static final String ID = "CONTRASENA-EN-CONSOLA";

    /** Debilidad asociada en el catálogo. */
    public static final String CWE = "CWE-532";

    /** Longitud mínima de una contraseña para buscarla. */
    public static final int LONGITUD_MINIMA = 4;

    private static final List<String> PALABRAS_CONTRASENA =
            List.of("contrasena", "password", "clave", "pass");

    /** Crea el oráculo; lo usa {@link java.util.ServiceLoader}. */
    public OraculoContrasenaEnConsola() {}

    /**
     * Devuelve el id del oráculo.
     *
     * @return {@value #ID}
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Devuelve el momento de evaluación.
     *
     * @return {@link Momento#EVENTO}, porque cada línea de consola se juzga por separado
     */
    @Override
    public Momento momento() {
        return Momento.EVENTO;
    }

    /**
     * Indica si el oráculo aplica al caso.
     *
     * @param caso caso que se evalúa
     * @param ctx contexto de la evaluación
     * @return siempre {@code true}: las contraseñas escritas en el caso solo se conocen en la traza
     */
    @Override
    public boolean aplica(Caso caso, Contexto ctx) {
        return true;
    }

    /**
     * Busca contraseñas de prueba en las líneas de consola de la traza.
     *
     * @param traza traza del caso; si es null no hay nada que evaluar
     * @param ctx contexto de la evaluación; si es null solo se buscan las contraseñas escritas en
     *     el caso
     * @return un hallazgo por cada evento {@code CONSOLA} que contiene una contraseña de prueba;
     *     lista vacía si no hay ninguno
     */
    @Override
    public List<Hallazgo> evaluar(Traza traza, Contexto ctx) {
        if (traza == null) {
            return List.of();
        }
        Map<String, String> contrasenas = contrasenas(traza, ctx);
        if (contrasenas.isEmpty()) {
            return List.of();
        }
        List<Hallazgo> hallazgos = new ArrayList<>();
        for (Evento evento : traza.deTipo(TipoEvento.CONSOLA)) {
            String texto = evento.texto("texto").orElse("");
            for (Map.Entry<String, String> contrasena : contrasenas.entrySet()) {
                if (texto.contains(contrasena.getKey())) {
                    hallazgos.add(hallazgo(evento, contrasena.getValue()));
                    break;
                }
            }
        }
        return hallazgos;
    }

    /** Contraseñas a buscar, con una descripción de su origen que no las revela. */
    private static Map<String, String> contrasenas(Traza traza, Contexto ctx) {
        Map<String, String> contrasenas = new LinkedHashMap<>();
        if (ctx != null && ctx.proyecto() != null) {
            for (Rol rol : ctx.proyecto().roles()) {
                agregar(contrasenas, rol.contrasena(), "la contraseña del rol " + rol.nombre());
            }
        }
        for (Evento inicio : traza.deTipo(TipoEvento.PASO_INICIO)) {
            String objetivo = inicio.texto("objetivo").orElse("");
            if (inicio.texto("accion").orElse("").equals("LLENAR")
                    && esCampoDeContrasena(objetivo)) {
                agregar(
                        contrasenas,
                        inicio.texto("valor").orElse(null),
                        "el valor escrito en el campo "
                                + objetivo
                                + " (paso "
                                + inicio.contexto().numeroPaso()
                                + ")");
            }
        }
        return contrasenas;
    }

    private static void agregar(Map<String, String> contrasenas, String valor, String origen) {
        if (valor != null && valor.length() >= LONGITUD_MINIMA) {
            contrasenas.putIfAbsent(valor, origen);
        }
    }

    private static boolean esCampoDeContrasena(String objetivo) {
        String normal =
                Normalizer.normalize(objetivo, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .toLowerCase(Locale.ROOT);
        return PALABRAS_CONTRASENA.stream().anyMatch(normal::contains);
    }

    private static Hallazgo hallazgo(Evento evento, String origen) {
        String flujo =
                evento.texto("flujo").orElse("").equals("ERROR")
                        ? "la salida de error"
                        : "la salida estándar";
        return new Hallazgo(
                ID,
                CWE,
                Confianza.CONFIRMADO,
                "Contraseña de prueba en la consola",
                "La aplicación escribió "
                        + origen
                        + " en "
                        + flujo
                        + ". Cualquiera con acceso a la consola o a sus registros puede leerla; la"
                        + " aplicación no debe escribir contraseñas, ni siquiera para depurar.",
                List.of(evento.secuencia()));
    }
}
