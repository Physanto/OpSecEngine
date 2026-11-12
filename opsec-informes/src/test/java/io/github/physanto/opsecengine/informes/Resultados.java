package io.github.physanto.opsecengine.informes;

import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Manifiesto;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.ContextoEvento;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Constructores de resultados pequeños para las pruebas de los reporteros, para no depender de los
 * datos de ejemplo cuando una prueba necesita un caso concreto.
 */
final class Resultados {

    /** Instante en que empiezan todas las ejecuciones de prueba. */
    static final Instant INICIO = Instant.parse("2026-10-30T10:00:00Z");

    private Resultados() {}

    /** Evento de un caso, 10 ms después del anterior por cada unidad de secuencia. */
    static Evento evento(long secuencia, TipoEvento tipo, Map<String, Object> datos) {
        return new Evento(
                secuencia,
                INICIO.plusMillis(secuencia * 10),
                tipo,
                new ContextoEvento("C1", 1, null, false),
                datos);
    }

    /** Hallazgo con confianza dada, sin evidencia. */
    static Hallazgo hallazgo(String oraculo, String cwe, Confianza confianza, String titulo) {
        return new Hallazgo(oraculo, cwe, confianza, titulo, "Explicación de " + titulo, List.of());
    }

    /** Caso con 500 ms de duración y un intento. */
    static ResultadoCaso caso(String id, Veredicto veredicto, Evento... eventos) {
        return new ResultadoCaso(
                id, veredicto, new Traza(id, List.of(eventos)), Duration.ofMillis(500), 1, false);
    }

    /** Caso con los intentos y la estabilidad indicados. */
    static ResultadoCaso casoInestable(String id, Veredicto veredicto, int intentos) {
        return new ResultadoCaso(
                id, veredicto, new Traza(id, List.of()), Duration.ofMillis(500), intentos, true);
    }

    /** Suite de 2 s sin hallazgos de suite. */
    static ResultadoSuite resultado(String nombre, ResultadoCaso... casos) {
        return resultado(nombre, List.of(), casos);
    }

    /** Suite de 2 s con hallazgos de suite. */
    static ResultadoSuite resultado(
            String nombre, List<Hallazgo> hallazgosSuite, ResultadoCaso... casos) {
        Manifiesto manifiesto =
                new Manifiesto("sha256:aa", "0.1.0", "21", "Linux", 42, "sha256:bb", INICIO);
        return new ResultadoSuite(
                nombre, manifiesto, List.of(casos), hallazgosSuite, INICIO, INICIO.plusSeconds(2));
    }
}
