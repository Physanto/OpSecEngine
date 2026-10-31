package io.github.physanto.opsecengine.nucleo.sesion;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.ConfigEjecucion;
import io.github.physanto.opsecengine.dominio.caso.Criterio;
import io.github.physanto.opsecengine.dominio.caso.EstrategiaLocalizacion;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.traza.ContextoEvento;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Convierte entre los modelos del dominio y los DTO del protocolo.
 *
 * <p>Las acciones, estrategias y tipos de evento viajan por su nombre de enum ({@code PULSAR},
 * {@code ETIQUETA}, {@code SQL}); las esperas, en milisegundos; los instantes, en milisegundos
 * desde la época. Un nombre que el dominio no conoce produce {@link IllegalArgumentException}.
 *
 * <p>La clase no tiene estado y es segura entre hilos.
 */
public final class TraductorProtocolo {

    private TraductorProtocolo() {}

    /**
     * Convierte un paso del dominio en el DTO que se envía al agente.
     *
     * @param paso paso del caso; no puede ser null
     * @param config configuración de la suite, de la que salen el modo y la espera por defecto; no
     *     puede ser null
     * @return el DTO, con la espera del paso o, si no tiene, la de la configuración
     * @throws NullPointerException si algún parámetro es null
     */
    public static PasoDto aPasoDto(Paso paso, ConfigEjecucion config) {
        Objects.requireNonNull(paso, "El paso no puede ser null");
        Objects.requireNonNull(config, "La configuración no puede ser null");
        Duration espera =
                paso.esperaMaxima() != null ? paso.esperaMaxima() : config.esperaMaximaPaso();
        return new PasoDto(
                paso.accion().name(),
                paso.objetivo() == null ? null : aLocalizadorDto(paso.objetivo()),
                paso.valor(),
                config.modo().name(),
                espera.toMillis());
    }

    /**
     * Convierte un DTO de paso en un paso del dominio; el modo no se conserva porque el dominio lo
     * guarda en la configuración de la suite.
     *
     * @param dto paso recibido; no puede ser null
     * @param orden posición del paso en su caso, desde 1
     * @return el paso del dominio
     * @throws IllegalArgumentException si la acción o una estrategia no existen en el dominio, o si
     *     {@code orden} es menor que 1
     * @throws NullPointerException si {@code dto} es null o falta el localizador en una acción que
     *     lo necesita
     */
    public static Paso aPaso(PasoDto dto, int orden) {
        Objects.requireNonNull(dto, "El DTO del paso no puede ser null");
        return new Paso(
                orden,
                enumerado(Accion.class, dto.getAccion(), "la acción"),
                dto.getLocalizador() == null ? null : aLocalizador(dto.getLocalizador()),
                dto.getValor(),
                Duration.ofMillis(dto.getEsperaMaximaMs()),
                Map.of());
    }

    /**
     * Convierte un localizador del dominio en su DTO, con los criterios en el mismo orden.
     *
     * @param localizador localizador; no puede ser null
     * @return el DTO
     * @throws NullPointerException si {@code localizador} es null
     */
    public static LocalizadorDto aLocalizadorDto(Localizador localizador) {
        Objects.requireNonNull(localizador, "El localizador no puede ser null");
        List<CriterioDto> criterios = new ArrayList<>();
        for (Criterio criterio : localizador.criterios()) {
            criterios.add(new CriterioDto(criterio.estrategia().name(), criterio.valor()));
        }
        return new LocalizadorDto(criterios);
    }

    /**
     * Convierte un DTO de localizador en un localizador del dominio.
     *
     * @param dto localizador recibido; no puede ser null
     * @return el localizador, con los criterios en el mismo orden
     * @throws IllegalArgumentException si una estrategia no existe en el dominio
     * @throws NullPointerException si {@code dto} es null
     */
    public static Localizador aLocalizador(LocalizadorDto dto) {
        Objects.requireNonNull(dto, "El DTO del localizador no puede ser null");
        List<Criterio> criterios = new ArrayList<>();
        for (CriterioDto criterio : dto.getCriterios()) {
            criterios.add(
                    new Criterio(
                            enumerado(
                                    EstrategiaLocalizacion.class,
                                    criterio.getEstrategia(),
                                    "la estrategia de localización"),
                            criterio.getValor()));
        }
        return new Localizador(criterios);
    }

    /**
     * Convierte un evento recibido del agente en un evento de la traza.
     *
     * <p>El motor numera los eventos de cada traza, así que la secuencia del agente se reemplaza
     * por la que se recibe; el contexto también lo pone el motor, porque el agente no sabe qué caso
     * ni qué paso se está ejecutando.
     *
     * @param dto evento recibido; no puede ser null
     * @param secuencia número del evento dentro de la traza; no puede ser negativo
     * @param contexto caso, paso y sesión en que ocurrió el evento; no puede ser null
     * @return el evento de la traza
     * @throws IllegalArgumentException si el tipo de evento no existe en el dominio o la secuencia
     *     es negativa
     * @throws NullPointerException si algún parámetro es null
     */
    public static Evento aEvento(EventoDto dto, long secuencia, ContextoEvento contexto) {
        Objects.requireNonNull(dto, "El DTO del evento no puede ser null");
        return new Evento(
                secuencia,
                Instant.ofEpochMilli(dto.getInstanteEpochMs()),
                enumerado(TipoEvento.class, dto.getTipo(), "el tipo de evento"),
                contexto,
                dto.getDatos());
    }

    /**
     * Escribe un localizador como texto, el formato del campo {@code objetivo} del evento {@code
     * PASO_INICIO}.
     *
     * @param localizador localizador; no puede ser null
     * @return los criterios en orden, separados por coma, por ejemplo {@code etiqueta=Usuario,
     *     texto=Usuario}; la estrategia se escribe como en el YAML de las suites
     * @throws NullPointerException si {@code localizador} es null
     */
    public static String describir(Localizador localizador) {
        Objects.requireNonNull(localizador, "El localizador no puede ser null");
        StringJoiner texto = new StringJoiner(", ");
        for (Criterio criterio : localizador.criterios()) {
            texto.add(nombreYaml(criterio.estrategia()) + "=" + criterio.valor());
        }
        return texto.toString();
    }

    /** Convierte {@code NOMBRE_COMPONENTE} en {@code nombreComponente}. */
    private static String nombreYaml(EstrategiaLocalizacion estrategia) {
        String[] partes = estrategia.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder nombre = new StringBuilder(partes[0]);
        for (int i = 1; i < partes.length; i++) {
            nombre.append(Character.toUpperCase(partes[i].charAt(0)))
                    .append(partes[i].substring(1));
        }
        return nombre.toString();
    }

    private static <E extends Enum<E>> E enumerado(
            Class<E> tipo, String nombre, String descripcion) {
        try {
            return Enum.valueOf(tipo, nombre);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "El dominio no conoce " + descripcion + " " + nombre, e);
        }
    }
}
