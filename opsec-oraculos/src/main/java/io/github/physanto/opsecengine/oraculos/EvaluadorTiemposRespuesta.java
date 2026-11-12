package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Estadísticas locales de repeticiones comparables de una acción. Sin estado y seguro entre hilos.
 */
public final class EvaluadorTiemposRespuesta {
    /** Crea el evaluador local, no registrado por SPI. */
    public EvaluadorTiemposRespuesta() {}

    /**
     * Calcula mediana y p95 por rango más próximo: posición ceil(0,95*n), empezando en uno. El
     * llamador garantiza mismo caso, acción, datos y condiciones entre las repeticiones.
     *
     * @param repeticiones trazas de repeticiones independientes, una medida por caso y paso
     * @param casoId caso de la acción
     * @param paso paso positivo de la acción
     * @param minimoRepeticiones mínimo de muestras requerido, al menos dos
     * @param umbralP95Ms máximo permitido positivo, en milisegundos
     * @return resumen y veredicto; sin resumen y NO_CONCLUYENTE si falta evidencia válida
     * @throws IllegalArgumentException si las referencias o umbrales son inválidos
     * @throws NullPointerException si hay una traza null
     */
    public Resultado evaluar(
            List<Traza> repeticiones,
            String casoId,
            int paso,
            int minimoRepeticiones,
            long umbralP95Ms) {
        if (casoId == null
                || casoId.isBlank()
                || paso < 1
                || minimoRepeticiones < 2
                || umbralP95Ms < 1) {
            throw new IllegalArgumentException("Se requieren una acción y umbrales válidos");
        }
        if (repeticiones == null || repeticiones.size() < minimoRepeticiones) {
            return incompleto("Faltan repeticiones de la acción");
        }
        List<Long> medidas = new ArrayList<>();
        List<Long> evidencia = new ArrayList<>();
        for (Traza traza : List.copyOf(repeticiones)) {
            if (!casoId.equals(traza.casoId())) {
                return incompleto("Las repeticiones deben corresponder al mismo caso");
            }
            List<Evento> candidatos =
                    traza.eventos().stream()
                            .filter(e ->
                                    casoId.equals(e.contexto().casoId())
                                            && e.contexto().numeroPaso() == paso
                                            && e.tipo() == TipoEvento.TIEMPO
                                            && e.texto("medida")
                                                    .orElse("")
                                                    .equals("RESPUESTA_ACCION"))
                            .toList();
            if (candidatos.size() != 1) {
                return incompleto(
                        "Se requiere una medida de respuesta por repetición, sin reintentos ambiguos");
            }
            Evento evento = candidatos.getFirst();
            Long medida = MedidasTiempo.entero(evento.datos().get("duracionMs"));
            if (medida == null) {
                return incompleto("Hay una duración ausente o inválida");
            }
            medidas.add(medida);
            evidencia.add(evento.secuencia());
        }
        medidas.sort(Long::compareTo);
        int n = medidas.size();
        BigDecimal mediana =
                n % 2 == 1
                        ? BigDecimal.valueOf(medidas.get(n / 2))
                        : BigDecimal.valueOf(medidas.get(n / 2 - 1))
                                .add(BigDecimal.valueOf(medidas.get(n / 2)))
                                .divide(BigDecimal.valueOf(2));
        long p95 = medidas.get((int) ((95L * n + 99) / 100 - 1));
        Resumen resumen = new Resumen(n, mediana, p95);
        Veredicto veredicto =
                p95 > umbralP95Ms
                        ? Veredicto.noCumple(
                                List.of(
                                        new Hallazgo(
                                                "RESPUESTA-LENTA",
                                                null,
                                                Confianza.PROBABLE,
                                                "Percentil de respuesta superior al umbral",
                                                "El p95 de las repeticiones comparables supera el umbral declarado. La evidencia sigue el orden de las trazas de repetición; no se infiere bloqueo del EDT.",
                                                evidencia)))
                        : Veredicto.cumple();
        return new Resultado(resumen, veredicto);
    }

    private static Resultado incompleto(String motivo) {
        return new Resultado(null, Veredicto.noConcluyente(motivo));
    }

    /**
     * Estadísticas de una acción repetida, inmutables y seguras entre hilos.
     *
     * @param muestras número de repeticiones válidas
     * @param medianaMs mediana exacta en milisegundos
     * @param percentil95Ms p95 por rango más próximo
     */
    public record Resumen(int muestras, BigDecimal medianaMs, long percentil95Ms) {}

    /**
     * Resultado local que conserva la incertidumbre y las estadísticas. Inmutable y seguro entre
     * hilos.
     *
     * @param resumen estadísticas calculadas, o null si no se pudieron calcular
     * @param veredicto decisión completa que el integrador debe conservar
     */
    public record Resultado(Resumen resumen, Veredicto veredicto) {}
}
