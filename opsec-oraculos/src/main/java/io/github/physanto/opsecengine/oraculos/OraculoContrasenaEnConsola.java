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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Oráculo {@value #ID} (CWE-532): una contraseña de prueba aparece en la salida estándar o de error
 * de la aplicación.
 *
 * <p>Las contraseñas y marcas que busca salen de estos lugares:
 *
 * <ul>
 *   <li>las de los roles del proyecto ({@code roles} en {@code proyecto.yaml});
 *   <li>los valores que el caso escribió con {@code LLENAR} en un campo cuyo localizador menciona
 *       "contraseña", "password", "clave" o "pass" (sin importar mayúsculas ni tildes), tomados de
 *       los eventos {@code PASO_INICIO}.
 *   <li>valores escritos en un componente identificado como JPasswordField por PASO_FIN;
 *   <li>el canario de Caso.extensiones.validacion, solo después de observarlo dentro de un valor
 *       escrito en uno de esos campos sensibles. No interpreta cualquier canario como contraseña.
 * </ul>
 *
 * <p>Se ignoran las contraseñas de menos de {@value #LONGITUD_MINIMA} caracteres, porque aparecen
 * por casualidad en cualquier texto. Produce un hallazgo {@code CONFIRMADO} por cada evento {@code
 * CONSOLA} cuyo texto contiene alguna de ellas, con la secuencia del evento como evidencia. El
 * hallazgo no copia el valor, el canario, el localizador ni la línea de consola. La identificación
 * por nombre separa palabras y camelCase para evitar confundir «bypass» o «compass» con «pass».
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
        Map<String, Origen> contrasenas = contrasenasDeRoles(ctx);
        Map<String, Origen> canarios = new LinkedHashMap<>();
        Set<Long> camposPassword = camposPassword(traza);
        String canario = canario(traza, ctx);
        List<Hallazgo> hallazgos = new ArrayList<>();
        for (Evento evento : traza.eventos()) {
            if (!traza.casoId().equals(evento.contexto().casoId())) {
                continue;
            }
            if (evento.tipo() == TipoEvento.PASO_INICIO
                    && evento.texto("accion").orElse("").equals("LLENAR")
                    && (camposPassword.contains(evento.secuencia())
                            || esCampoDeContrasena(evento.texto("objetivo").orElse("")))) {
                String valor = evento.texto("valor").orElse(null);
                String paso = " (paso " + evento.contexto().numeroPaso() + ")";
                agregar(
                        contrasenas,
                        valor,
                        new Origen("el valor escrito en un campo de contraseña" + paso, null));
                if (canario != null && valor != null && valor.contains(canario)) {
                    canarios.putIfAbsent(
                            canario,
                            new Origen(
                                    "un canario de un payload escrito en un campo de contraseña"
                                            + paso,
                                    evento.secuencia()));
                }
            } else if (evento.tipo() == TipoEvento.CONSOLA) {
                String texto = evento.texto("texto").orElse("");
                Origen origen = buscar(texto, canarios);
                if (origen == null) {
                    origen = buscar(texto, contrasenas);
                }
                if (origen != null) {
                    hallazgos.add(hallazgo(evento, origen));
                }
            }
        }
        return hallazgos;
    }

    private static Map<String, Origen> contrasenasDeRoles(Contexto ctx) {
        Map<String, Origen> contrasenas = new LinkedHashMap<>();
        if (ctx != null && ctx.proyecto() != null) {
            for (Rol rol : ctx.proyecto().roles()) {
                agregar(
                        contrasenas,
                        rol.contrasena(),
                        new Origen("la contraseña del rol " + rol.nombre(), null));
            }
        }
        return contrasenas;
    }

    private static void agregar(Map<String, Origen> contrasenas, String valor, Origen origen) {
        if (valor != null && valor.length() >= LONGITUD_MINIMA) {
            contrasenas.putIfAbsent(valor, origen);
        }
    }

    private static Origen buscar(String texto, Map<String, Origen> candidatos) {
        for (var candidato : candidatos.entrySet()) {
            if (texto.contains(candidato.getKey())) {
                return candidato.getValue();
            }
        }
        return null;
    }

    private static Set<Long> camposPassword(Traza traza) {
        Map<Integer, Evento> pendientes = new HashMap<>();
        Set<Long> sensibles = new HashSet<>();
        for (Evento evento : traza.eventos()) {
            if (!traza.casoId().equals(evento.contexto().casoId())) {
                continue;
            }
            int paso = evento.contexto().numeroPaso();
            if (evento.tipo() == TipoEvento.PASO_INICIO) {
                pendientes.put(paso, evento);
            } else if (evento.tipo() == TipoEvento.PASO_FIN) {
                Evento inicio = pendientes.remove(paso);
                String tipo = evento.texto("componente").orElse("");
                if (inicio != null
                        && inicio.texto("accion").orElse("").equals("LLENAR")
                        && evento.texto("codigo").orElse("").equals("OK")
                        && (tipo.equals("javax.swing.JPasswordField")
                                || tipo.equals("JPasswordField"))) {
                    sensibles.add(inicio.secuencia());
                }
            }
        }
        return sensibles;
    }

    private static String canario(Traza traza, Contexto ctx) {
        if (ctx == null
                || ctx.caso() == null
                || !ctx.caso().id().equals(traza.casoId())
                || !(ctx.caso().extensiones().get("validacion") instanceof Map<?, ?> datos)
                || !(datos.get("canario") instanceof String marca)
                || marca.length() < LONGITUD_MINIMA
                || List.of("{canario}", "{payload}", "{contrasena}", "{usuario}").contains(marca)) {
            return null;
        }
        return marca;
    }

    static boolean esCampoDeContrasena(String objetivo) {
        String normal =
                Normalizer.normalize(objetivo, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .replaceAll("([A-Z])([A-Z][a-z])", "$1 $2")
                        .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                        .toLowerCase(Locale.ROOT);
        for (String palabra : normal.split("[^a-z]+")) {
            if (PALABRAS_CONTRASENA.contains(palabra)) {
                return true;
            }
        }
        return false;
    }

    private static Hallazgo hallazgo(Evento evento, Origen origen) {
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
                        + origen.descripcion()
                        + " en "
                        + flujo
                        + ". Cualquiera con acceso a la consola o a sus registros puede leerla; la"
                        + " aplicación no debe escribir contraseñas, ni siquiera para depurar.",
                origen.inicio() == null
                        ? List.of(evento.secuencia())
                        : List.of(origen.inicio(), evento.secuencia()));
    }

    private record Origen(String descripcion, Long inicio) {}
}
