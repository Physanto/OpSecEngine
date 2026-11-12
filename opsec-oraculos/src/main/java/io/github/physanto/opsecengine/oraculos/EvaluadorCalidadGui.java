package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Evalúa observaciones locales explícitas de liberación, etiquetas y teclado. Sin estado y seguro
 * entre hilos. El llamador obtiene los datos de la GUI; no se infieren desde ventanas ocultas.
 */
public final class EvaluadorCalidadGui {
    /** Crea el evaluador local, no registrado por SPI. */
    public EvaluadorCalidadGui() {}

    /**
     * Comprueba displayable en una observación final posterior a solicitar la liberación.
     *
     * @param esperadas ids de ventanas que la política exige liberar, sin duplicados
     * @param observadas observaciones finales del mismo proceso y sesión, con ids estables
     * @return NO_CONCLUYENTE si faltan datos; NO_CUMPLE si una ventana aún tiene recursos nativos
     * @throws IllegalArgumentException si hay ids vacíos o repetidos
     * @throws NullPointerException si una colección contiene null
     */
    public Veredicto evaluarLiberacion(List<String> esperadas, List<Ventana> observadas) {
        if (esperadas == null || esperadas.isEmpty() || observadas == null) {
            return Veredicto.noConcluyente(
                    "Faltan ventanas esperadas u observaciones finales de liberación");
        }
        Set<String> ids = new HashSet<>();
        Map<String, Ventana> porId = new java.util.HashMap<>();
        for (String id : List.copyOf(esperadas)) {
            if (id.isBlank() || !ids.add(id)) {
                throw new IllegalArgumentException(
                        "Las ventanas esperadas deben tener ids únicos y no vacíos");
            }
        }
        for (Ventana ventana : List.copyOf(observadas)) {
            if (porId.putIfAbsent(ventana.id(), ventana) != null) {
                throw new IllegalArgumentException("Hay observaciones de ventana repetidas");
            }
        }
        if (!porId.keySet().containsAll(ids)) {
            return Veredicto.noConcluyente("Falta la observación final de una ventana esperada");
        }
        List<Hallazgo> hallazgos = new ArrayList<>();
        for (String id : esperadas) {
            Ventana ventana = porId.get(id);
            if (ventana.displayable()) {
                hallazgos.add(
                        hallazgo(
                                "VENTANA-NO-LIBERADA",
                                "Ventana que conserva recursos nativos",
                                "Una ventana que debía liberarse sigue siendo displayable en la observación final. Ocultarla o recibir VENTANA_CERRADA no demuestra dispose ni una fuga de memoria.",
                                ventana.secuencia()));
            }
        }
        return hallazgos.isEmpty() ? Veredicto.cumple() : Veredicto.noCumple(hallazgos);
    }

    /**
     * Comprueba etiquetas asociadas o nombres accesibles de controles interactivos activos.
     *
     * @param controles instantánea completa del ámbito declarado; null o vacía deja incertidumbre
     * @return hallazgos por controles visibles y habilitados sin etiqueta ni nombre accesible
     * @throws IllegalArgumentException si se repite un id
     * @throws NullPointerException si hay elementos null
     */
    public Veredicto evaluarEtiquetas(List<Control> controles) {
        if (controles == null || controles.isEmpty()) {
            return Veredicto.noConcluyente("Falta la instantánea de controles del ámbito evaluado");
        }
        List<Hallazgo> hallazgos = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        boolean aplicable = false;
        for (Control control : List.copyOf(controles)) {
            if (!ids.add(control.id())) {
                throw new IllegalArgumentException("Hay ids de control repetidos");
            }
            if (control.interactivo() && control.visible() && control.habilitado()) {
                aplicable = true;
                if ((control.etiquetaAsociada() == null || control.etiquetaAsociada().isBlank())
                        && (control.nombreAccesible() == null
                                || control.nombreAccesible().isBlank())) {
                    hallazgos.add(
                            hallazgo(
                                    "CONTROL-SIN-ETIQUETA",
                                    "Control sin identificación accesible",
                                    "Un control interactivo visible y habilitado carece de etiqueta asociada y nombre accesible en la observación aportada.",
                                    control.secuencia()));
                }
            }
        }
        if (!aplicable) {
            return Veredicto.noConcluyente("No hay controles interactivos activos que evaluar");
        }
        return hallazgos.isEmpty() ? Veredicto.cumple() : Veredicto.noCumple(hallazgos);
    }

    /**
     * Compara un recorrido real de tabulación y activaciones por teclado con la política.
     *
     * @param recorrido observación completa del mismo ámbito y estado inicial; puede ser null
     * @return NO_CONCLUYENTE si está incompleta; hallazgos por orden distinto o acciones no
     *     realizadas
     */
    public Veredicto evaluarTeclado(RecorridoTeclado recorrido) {
        if (recorrido == null || !recorrido.completo()) {
            return Veredicto.noConcluyente("Falta un recorrido completo y comparable de teclado");
        }
        List<Hallazgo> hallazgos = new ArrayList<>();
        if (!recorrido.ordenEsperado().equals(recorrido.ordenObservado())) {
            hallazgos.add(
                    hallazgo(
                            "ORDEN-TABULACION",
                            "Orden de tabulación distinto del esperado",
                            "El recorrido observado de foco difiere del orden explícito de la política.",
                            recorrido.secuencia()));
        }
        if (!recorrido.accionesObservadas().containsAll(recorrido.accionesEsperadas())) {
            hallazgos.add(
                    hallazgo(
                            "ACCION-TECLADO-NO-ALCANZADA",
                            "Acción no realizada con teclado",
                            "El recorrido completo no acredita todas las activaciones por teclado exigidas por la política.",
                            recorrido.secuencia()));
        }
        return hallazgos.isEmpty() ? Veredicto.cumple() : Veredicto.noCumple(hallazgos);
    }

    private static Hallazgo hallazgo(String id, String titulo, String explicacion, long secuencia) {
        return new Hallazgo(id, null, Confianza.PROBABLE, titulo, explicacion, List.of(secuencia));
    }

    private static void referencia(String id, long secuencia) {
        Objects.requireNonNull(id, "Falta el identificador de la observación");
        if (id.isBlank() || secuencia < 0) {
            throw new IllegalArgumentException(
                    "La observación requiere id no vacío y secuencia no negativa");
        }
    }

    /**
     * Observación final inmutable de una ventana tras solicitar su liberación.
     *
     * @param id identificador estable en la sesión
     * @param displayable resultado observado de isDisplayable, no de isVisible
     * @param secuencia referencia al registro local de observaciones, no a un evento inventado
     */
    public record Ventana(String id, boolean displayable, long secuencia) {
        /**
         * Valida la referencia de la observación.
         *
         * @throws NullPointerException si falta el id
         * @throws IllegalArgumentException si el id o la secuencia no son válidos
         */
        public Ventana {
            referencia(id, secuencia);
        }
    }

    /**
     * Observación inmutable de un control; una etiqueta debe estar realmente asociada al control.
     *
     * @param id identificador estable del control
     * @param interactivo si la política incluye el control entre los interactivos
     * @param visible visibilidad observada
     * @param habilitado estado habilitado observado
     * @param etiquetaAsociada texto de la etiqueta asociada, o null si falta
     * @param nombreAccesible nombre accesible observado, o null si falta
     * @param secuencia referencia al registro local de observaciones
     */
    public record Control(
            String id,
            boolean interactivo,
            boolean visible,
            boolean habilitado,
            String etiquetaAsociada,
            String nombreAccesible,
            long secuencia) {
        /**
         * Valida la referencia de la observación.
         *
         * @throws NullPointerException si falta el id
         * @throws IllegalArgumentException si el id o la secuencia no son válidos
         */
        public Control {
            referencia(id, secuencia);
        }
    }

    /**
     * Recorrido de teclado inmutable y seguro entre hilos, aportado por el observador y la
     * política.
     *
     * @param ordenEsperado orden explícito de foco, incluido el cierre de ciclo si se exige
     * @param ordenObservado ids en el orden observado al tabular
     * @param accionesEsperadas ids de activaciones de teclado que deben realizarse
     * @param accionesObservadas ids de activaciones acreditadas, no simples clics semánticos
     * @param completo si la observación del recorrido concluyó sin interrupciones
     * @param secuencia referencia al registro local de observaciones
     */
    public record RecorridoTeclado(
            List<String> ordenEsperado,
            List<String> ordenObservado,
            Set<String> accionesEsperadas,
            Set<String> accionesObservadas,
            boolean completo,
            long secuencia) {
        /**
         * Copia las colecciones y valida que exista una expectativa de tabulación.
         *
         * @throws NullPointerException si falta una colección o contiene null
         * @throws IllegalArgumentException si hay ids vacíos, secuencia negativa u orden esperado
         *     vacío
         */
        public RecorridoTeclado {
            ordenEsperado = List.copyOf(ordenEsperado);
            ordenObservado = List.copyOf(ordenObservado);
            accionesEsperadas = Set.copyOf(accionesEsperadas);
            accionesObservadas = Set.copyOf(accionesObservadas);
            if (ordenEsperado.isEmpty() || secuencia < 0) {
                throw new IllegalArgumentException(
                        "Se requiere un orden esperado y una secuencia válida");
            }
            for (var ids :
                    List.of(ordenEsperado, ordenObservado, accionesEsperadas, accionesObservadas)) {
                if (ids.stream().anyMatch(String::isBlank)) {
                    throw new IllegalArgumentException(
                            "Los ids del recorrido no pueden estar vacíos");
                }
            }
        }
    }
}
