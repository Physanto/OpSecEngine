package io.github.physanto.opsecengine.dominio.traza;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Traza de un caso: la lista de eventos observados durante su ejecución, ordenada por secuencia.
 *
 * <p>El constructor ordena los eventos por {@link Evento#secuencia()} y rechaza secuencias
 * repetidas. Las consultas son puras: no modifican la traza y devuelven listas inmutables en orden
 * de secuencia.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param casoId id del caso al que pertenece la traza; no puede ser null ni estar en blanco
 * @param eventos eventos del caso; si es null queda vacía, y no admite elementos null ni secuencias
 *     repetidas
 */
public record Traza(String casoId, List<Evento> eventos) {

    /**
     * Crea una traza con los eventos ordenados por secuencia.
     *
     * @throws NullPointerException si {@code casoId} es null o si algún evento es null
     * @throws IllegalArgumentException si {@code casoId} está en blanco o si dos eventos tienen la
     *     misma secuencia
     */
    public Traza {
        Objects.requireNonNull(casoId, "El id del caso de la traza no puede ser null");
        if (casoId.isBlank()) {
            throw new IllegalArgumentException(
                    "El id del caso de la traza no puede estar en blanco");
        }
        List<Evento> ordenados =
                new ArrayList<>(eventos == null ? List.<Evento>of() : List.copyOf(eventos));
        ordenados.sort(Comparator.comparingLong(Evento::secuencia));
        for (int i = 1; i < ordenados.size(); i++) {
            if (ordenados.get(i).secuencia() == ordenados.get(i - 1).secuencia()) {
                throw new IllegalArgumentException(
                        "La traza del caso "
                                + casoId
                                + " tiene eventos repetidos con la secuencia "
                                + ordenados.get(i).secuencia());
            }
        }
        eventos = List.copyOf(ordenados);
    }

    /**
     * Devuelve los eventos de un tipo.
     *
     * @param tipo tipo buscado; no puede ser null
     * @return eventos de ese tipo en orden de secuencia; vacía si no hay ninguno
     * @throws NullPointerException si {@code tipo} es null
     */
    public List<Evento> deTipo(TipoEvento tipo) {
        Objects.requireNonNull(tipo, "El tipo de evento buscado no puede ser null");
        return eventos.stream().filter(evento -> evento.tipo() == tipo).toList();
    }

    /**
     * Devuelve el primer evento de un tipo.
     *
     * @param tipo tipo buscado; no puede ser null
     * @return el evento de ese tipo con menor secuencia, o vacío si no hay ninguno
     * @throws NullPointerException si {@code tipo} es null
     */
    public Optional<Evento> primero(TipoEvento tipo) {
        Objects.requireNonNull(tipo, "El tipo de evento buscado no puede ser null");
        return eventos.stream().filter(evento -> evento.tipo() == tipo).findFirst();
    }

    /**
     * Devuelve los eventos cuya secuencia está entre dos valores, ambos incluidos.
     *
     * @param desde secuencia inicial, incluida
     * @param hasta secuencia final, incluida
     * @return eventos del intervalo en orden de secuencia; vacía si {@code desde} es mayor que
     *     {@code hasta}
     */
    public List<Evento> entre(long desde, long hasta) {
        return eventos.stream()
                .filter(evento -> evento.secuencia() >= desde && evento.secuencia() <= hasta)
                .toList();
    }

    /**
     * Indica si la traza no tiene eventos.
     *
     * @return {@code true} si no hay eventos
     */
    public boolean estaVacia() {
        return eventos.isEmpty();
    }
}
