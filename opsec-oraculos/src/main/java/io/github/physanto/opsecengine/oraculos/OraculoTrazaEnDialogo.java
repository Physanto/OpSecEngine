package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Detecta pilas de llamadas o nombres técnicos de excepciones en el texto de un diálogo (CWE-209).
 *
 * <p>Reconoce clases calificadas terminadas en Exception, Error o Throwable, excepciones sin
 * paquete seguidas de dos puntos y líneas de pila Java con ubicación de origen. No basta la palabra
 * «error» o «excepción» de un mensaje habitual. Solo inspecciona el campo textual {@code texto} de
 * eventos {@code DIALOGO}; no copia su contenido al hallazgo.
 *
 * <p>Es una detección por patrones de texto, no un analizador de todas las representaciones de
 * excepciones. No tiene estado mutable y es seguro entre hilos.
 */
public final class OraculoTrazaEnDialogo implements Oraculo {
    /** Identificador reservado en el catálogo de oráculos. */
    public static final String ID = "TRAZA-EN-DIALOGO";

    /** Debilidad de exposición de información técnica al usuario. */
    public static final String CWE = "CWE-209";

    private static final Pattern EXCEPCION =
            Pattern.compile(
                    "(?<![\\p{L}\\p{N}_$])(?:[\\p{L}_$][\\p{L}\\p{N}_$]*\\.)+"
                            + "(?:[\\p{L}_$][\\p{L}\\p{N}_$]*(?:Exception|Error)|Exception|Error|Throwable)\\b"
                            + "|(?<![\\p{L}\\p{N}_$])[A-Z][A-Za-z0-9_$]*(?:Exception|Error)[ \\t]*:");
    private static final Pattern MARCO =
            Pattern.compile(
                    "(?m)^[ \\t]*at[ \\t]+(?:[\\w.$-]+(?:@[\\w.+-]+)?/)?"
                            + "[\\w$]+(?:\\.[\\w$<>]+)+\\("
                            + "(?:[^()\\r\\n]+\\.java:[0-9]+|Native Method|Unknown Source)\\)");

    /** Crea el oráculo para su descubrimiento con {@link java.util.ServiceLoader}. */
    public OraculoTrazaEnDialogo() {}

    /**
     * Devuelve el identificador del catálogo.
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
     * @return {@link Momento#EVENTO}; cada diálogo se juzga por separado
     */
    @Override
    public Momento momento() {
        return Momento.EVENTO;
    }

    /**
     * Indica si el oráculo aplica al caso.
     *
     * @param caso caso evaluado
     * @param contexto contexto de evaluación
     * @return siempre true; mostrar información técnica es independiente del caso
     */
    @Override
    public boolean aplica(Caso caso, Contexto contexto) {
        return true;
    }

    /**
     * Busca firmas técnicas en los mensajes mostrados al usuario.
     *
     * @param traza traza observada; puede ser null
     * @param contexto contexto de evaluación; no se utiliza
     * @return un hallazgo confirmado por cada diálogo que coincide, o lista vacía
     */
    @Override
    public List<Hallazgo> evaluar(Traza traza, Contexto contexto) {
        if (traza == null) {
            return List.of();
        }
        return traza.deTipo(TipoEvento.DIALOGO).stream()
                .filter(evento ->
                        evento.texto("texto")
                                .map(texto ->
                                        EXCEPCION.matcher(texto).find()
                                                || MARCO.matcher(texto).find())
                                .orElse(false))
                .map(evento ->
                        new Hallazgo(
                                ID,
                                CWE,
                                Confianza.CONFIRMADO,
                                "Información técnica en un diálogo",
                                "El mensaje mostrado al usuario contiene una pila de llamadas o el nombre"
                                        + " técnico de una excepción. Esto expone detalles internos de la"
                                        + " aplicación; muestre un mensaje general y conserve el detalle"
                                        + " técnico en registros de acceso restringido.",
                                List.of(evento.secuencia())))
                .toList();
    }
}
