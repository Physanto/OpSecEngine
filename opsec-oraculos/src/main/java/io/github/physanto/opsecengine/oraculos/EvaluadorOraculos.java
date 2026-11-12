package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Evaluador;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Evalúa oráculos al recibir eventos y al finalizar casos y suites, sin depender del núcleo.
 *
 * <p>Recibe los oráculos descubiertos por el llamador. Es seguro entre hilos si los oráculos lo
 * son; cada caso tiene su propia sesión, que debe usarse desde un solo hilo. Implementa el contrato
 * {@link Evaluador} y conserva las APIs anteriores. Usa {@link Oraculo#evaluarResultado} para
 * conservar los veredictos no concluyentes: la falta provisional de evidencia puede resolverse con
 * eventos nuevos; los errores de evaluación persisten.
 */
public final class EvaluadorOraculos implements Evaluador {
    private final List<Oraculo> oraculos;

    /**
     * Crea el evaluador con una copia de los oráculos, en su orden de evaluación.
     *
     * @param oraculos oráculos registrados; no admite null ni ids repetidos
     * @throws NullPointerException si la colección, algún oráculo, id o momento es null
     * @throws IllegalArgumentException si hay ids repetidos
     */
    public EvaluadorOraculos(Collection<? extends Oraculo> oraculos) {
        this.oraculos = List.copyOf(oraculos);
        Set<String> ids = new LinkedHashSet<>();
        for (Oraculo oraculo : this.oraculos) {
            Objects.requireNonNull(oraculo.momento(), "El momento no puede ser null");
            if (!ids.add(Objects.requireNonNull(oraculo.id(), "El id no puede ser null"))) {
                throw new IllegalArgumentException("Id de oráculo repetido: " + oraculo.id());
            }
        }
    }

    /**
     * Inicia una evaluación independiente; todavía no ejecuta ningún oráculo.
     *
     * @param contexto contexto con el caso en ejecución
     * @return sesión sin eventos ni hallazgos
     * @throws NullPointerException si el contexto o su caso son null
     */
    @Override
    public EvaluacionCaso iniciarCaso(Contexto contexto) {
        Objects.requireNonNull(contexto, "El contexto no puede ser null");
        Objects.requireNonNull(contexto.caso(), "El contexto debe tener caso");
        return new EvaluacionCaso(contexto);
    }

    /**
     * Evalúa únicamente los oráculos SUITE al terminar todos los casos.
     *
     * @param contexto contexto sin caso, con las trazas en orden de ejecución
     * @return veredicto de los oráculos de suite, incluidos posibles fallos de evaluación
     * @throws NullPointerException si el contexto es null
     * @throws IllegalArgumentException si el contexto contiene un caso
     */
    @Override
    public Veredicto evaluarSuite(Contexto contexto) {
        Objects.requireNonNull(contexto, "El contexto no puede ser null");
        if (contexto.caso() != null) {
            throw new IllegalArgumentException(
                    "La evaluación de suite requiere un contexto sin caso");
        }
        Set<Hallazgo> hallazgos = new LinkedHashSet<>();
        Set<String> motivos = new LinkedHashSet<>();
        Map<String, String> actuales = new LinkedHashMap<>();
        evaluar(Momento.SUITE, null, contexto, hallazgos, motivos, actuales);
        motivos.addAll(actuales.values());
        return veredicto(hallazgos, motivos);
    }

    private void evaluar(
            Momento momento,
            Traza traza,
            Contexto contexto,
            Set<Hallazgo> hallazgos,
            Set<String> motivos,
            Map<String, String> actuales) {
        Caso caso = contexto.caso();
        for (Oraculo oraculo : oraculos) {
            if (oraculo.momento() != momento) {
                continue;
            }
            try {
                if ((caso != null && caso.oraculos().contains(oraculo.id()))
                        || oraculo.aplica(caso, contexto)) {
                    // Validar la respuesta completa antes de incorporar hallazgos.
                    Veredicto parcial =
                            Objects.requireNonNull(
                                    oraculo.evaluarResultado(traza, contexto),
                                    "El veredicto es null");
                    hallazgos.addAll(parcial.hallazgos());
                    if (parcial.motivo() != null) {
                        actuales.put(oraculo.id(), oraculo.id() + ": " + parcial.motivo());
                    } else {
                        actuales.remove(oraculo.id());
                    }
                } else {
                    actuales.remove(oraculo.id());
                }
            } catch (RuntimeException e) {
                motivos.add("Falló el oráculo " + oraculo.id() + ": " + e);
            }
        }
    }

    private static Veredicto veredicto(Set<Hallazgo> hallazgos, Set<String> motivos) {
        String motivo = motivos.isEmpty() ? null : String.join("; ", motivos);
        EstadoVeredicto estado =
                hallazgos.stream().anyMatch(h -> h.confianza() != Confianza.INFO)
                        ? EstadoVeredicto.NO_CUMPLE
                        : motivo == null ? EstadoVeredicto.CUMPLE : EstadoVeredicto.NO_CONCLUYENTE;
        return new Veredicto(estado, List.copyOf(hallazgos), motivo);
    }

    /**
     * Sesión de un caso: acumula eventos, hallazgos y errores sin compartir estado con otros casos.
     *
     * <p>Los oráculos EVENTO reciben el prefijo observado, nunca eventos futuros. Esto conserva la
     * compatibilidad con los que leen PASO_INICIO para identificar contraseñas. Los hallazgos
     * iguales se conservan una sola vez. El contrato actual no permite evaluación incremental
     * interna: revisar los prefijos puede tener coste cuadrático en el número de eventos.
     *
     * <p>No es segura entre hilos. Tras terminar no se admiten más eventos ni otro cierre.
     */
    public final class EvaluacionCaso
            implements io.github.physanto.opsecengine.dominio.extension.EvaluacionCaso {
        private final Contexto contexto;
        private final List<Evento> eventos = new ArrayList<>();
        private final Set<Hallazgo> hallazgos = new LinkedHashSet<>();
        private final Set<String> motivos = new LinkedHashSet<>();
        private final Map<String, String> actuales = new LinkedHashMap<>();
        private boolean terminada;

        private EvaluacionCaso(Contexto contexto) {
            this.contexto = contexto;
            Set<String> ids = new LinkedHashSet<>();
            for (Oraculo oraculo : oraculos) {
                ids.add(oraculo.id());
            }
            for (String id : contexto.caso().oraculos()) {
                if (!ids.contains(id)) {
                    motivos.add("El caso pide el oráculo " + id + ", que no está registrado");
                }
            }
        }

        /**
         * Registra un evento y ejecuta inmediatamente los oráculos EVENTO que aplican.
         *
         * @param evento evento nuevo, en orden estrictamente creciente de secuencia
         * @return veredicto acumulado hasta este evento; no es el resultado final del caso
         * @throws NullPointerException si el evento es null
         * @throws IllegalArgumentException si pertenece a otro caso o la secuencia no crece
         * @throws IllegalStateException si el caso ya terminó
         */
        @Override
        public Veredicto registrarEvento(Evento evento) {
            comprobarAbierta();
            Objects.requireNonNull(evento, "El evento no puede ser null");
            String id = evento.contexto().casoId();
            if (id != null && !contexto.caso().id().equals(id)) {
                throw new IllegalArgumentException("El evento pertenece a otro caso");
            }
            if (!eventos.isEmpty() && evento.secuencia() <= eventos.getLast().secuencia()) {
                throw new IllegalArgumentException("La secuencia del evento debe ser creciente");
            }
            eventos.add(evento);
            evaluar(Momento.EVENTO, traza(), contexto, hallazgos, motivos, actuales);
            return acumulado();
        }

        /**
         * Termina el caso y ejecuta únicamente sus oráculos CASO con toda la traza observada.
         *
         * @param motivoIncompleto motivo si la ejecución se interrumpió; null si terminó
         * @return veredicto final con los hallazgos de evento y de caso
         * @throws IllegalArgumentException si el motivo no es null pero está en blanco
         * @throws IllegalStateException si el caso ya terminó
         */
        @Override
        public Veredicto terminar(String motivoIncompleto) {
            comprobarAbierta();
            if (motivoIncompleto != null && motivoIncompleto.isBlank()) {
                throw new IllegalArgumentException("El motivo no puede estar en blanco");
            }
            terminada = true;
            if (motivoIncompleto != null) {
                motivos.add(motivoIncompleto);
            }
            evaluar(Momento.CASO, traza(), contexto, hallazgos, motivos, actuales);
            return acumulado();
        }

        /**
         * Obtiene una copia inmutable de la traza observada, incluso después del cierre.
         *
         * @return traza de este caso en orden de secuencia
         */
        @Override
        public Traza traza() {
            return new Traza(contexto.caso().id(), eventos);
        }

        private Veredicto acumulado() {
            Set<String> todos = new LinkedHashSet<>(motivos);
            todos.addAll(actuales.values());
            return veredicto(hallazgos, todos);
        }

        private void comprobarAbierta() {
            if (terminada) {
                throw new IllegalStateException("La evaluación del caso ya terminó");
            }
        }
    }
}
