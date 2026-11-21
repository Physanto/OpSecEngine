package io.github.physanto.opsecengine.nucleo.grabacion;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Criterio;
import io.github.physanto.opsecengine.dominio.caso.EstrategiaLocalizacion;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.nucleo.sesion.ClienteAgente;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.ModoInteraccionDto;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Servicio del inspector y la grabadora: pide al agente el modo de inspección o de grabación y
 * convierte lo que observa.
 *
 * <p>Uso típico de la grabación:
 *
 * <pre>{@code
 * servicio.iniciarGrabacion(cliente);
 * // ... el usuario usa la aplicación ...
 * ResultadoGrabacion grabado = servicio.terminarGrabacion(cliente, "login", "LOGIN-01");
 * SuiteYaml.guardar(grabado.suite(), Path.of("suites", "login.yaml"));
 * }</pre>
 *
 * <p>La conversión sigue estas reglas, que son las del formato de suites:
 *
 * <ul>
 *   <li>{@code PULSAR} y {@code MARCAR} conservan todos los criterios del localizador grabado.
 *   <li>{@code LLENAR} y {@code SELECCIONAR} localizan el campo por su etiqueta (el formato solo
 *       admite un texto simple); si el componente no tiene una etiqueta verificada, la acción se
 *       omite y se avisa.
 *   <li>{@code MARCAR} con {@code false} se omite y se avisa: el formato no puede expresar
 *       desmarcar.
 *   <li>Un valor escrito en un campo de contraseña se conserva tal como se escribió y se avisa.
 * </ul>
 *
 * <p>No tiene estado propio; es seguro entre hilos si lo es el {@code ClienteAgente} que se le
 * pasa.
 */
public final class GrabacionService {

    /** Tipo del evento que informa una acción grabada. */
    static final String EVENTO_ACCION = "ACCION_GRABADA";

    /** Tipo del evento que informa un componente inspeccionado. */
    static final String EVENTO_INSPECCION = "COMPONENTE_INSPECCIONADO";

    /** Id del caso cuando quien llama no da uno. */
    public static final String ID_CASO_POR_DEFECTO = "GRABADO";

    /** Crea el servicio. */
    public GrabacionService() {
        // Sin estado.
    }

    /**
     * Activa el inspector en el agente. Los componentes que el usuario elija llegan como eventos y
     * se leen con {@link #tomarInspecciones(ClienteAgente)}.
     *
     * @param cliente conexión con el agente; no puede ser null
     * @throws IOException si falla la conexión
     * @throws io.github.physanto.opsecengine.nucleo.sesion.ExcepcionErrorAgente si el agente no
     *     puede activar el modo, por ejemplo sin entorno gráfico
     */
    public void iniciarInspeccion(ClienteAgente cliente) throws IOException {
        Objects.requireNonNull(cliente, "El cliente no puede ser null")
                .modoInteraccion(new ModoInteraccionDto(ModoInteraccionDto.INSPECCIONAR));
    }

    /**
     * Activa la grabadora en el agente. Antes descarta los eventos que ya había recibido el
     * cliente, para que la grabación empiece vacía.
     *
     * @param cliente conexión con el agente; no puede ser null
     * @throws IOException si falla la conexión
     * @throws io.github.physanto.opsecengine.nucleo.sesion.ExcepcionErrorAgente si el agente no
     *     puede activar el modo, por ejemplo sin entorno gráfico
     */
    public void iniciarGrabacion(ClienteAgente cliente) throws IOException {
        Objects.requireNonNull(cliente, "El cliente no puede ser null");
        cliente.tomarEventos();
        cliente.modoInteraccion(new ModoInteraccionDto(ModoInteraccionDto.GRABAR));
    }

    /**
     * Desactiva el inspector o la grabadora.
     *
     * @param cliente conexión con el agente; no puede ser null
     * @throws IOException si falla la conexión
     */
    public void detener(ClienteAgente cliente) throws IOException {
        Objects.requireNonNull(cliente, "El cliente no puede ser null")
                .modoInteraccion(new ModoInteraccionDto(ModoInteraccionDto.NINGUNO));
    }

    /**
     * Lee los componentes que el usuario eligió desde la última lectura. El modo de inspección
     * sigue activo.
     *
     * @param cliente conexión con el agente; no puede ser null
     * @return los componentes en el orden en que se eligieron; vacío si no hubo ninguno
     */
    public List<Inspeccion> tomarInspecciones(ClienteAgente cliente) {
        return inspecciones(
                Objects.requireNonNull(cliente, "El cliente no puede ser null").tomarEventos());
    }

    /**
     * Termina la grabación y la convierte en una suite con un solo caso.
     *
     * @param cliente conexión con el agente; no puede ser null
     * @param nombreSuite nombre de la suite; no puede estar en blanco
     * @param idCaso id del caso; si es null o está en blanco se usa {@link #ID_CASO_POR_DEFECTO}
     * @return la suite y los avisos de lo que no se pudo conservar
     * @throws IOException si falla la conexión
     * @throws IllegalArgumentException si el nombre de la suite está en blanco
     */
    public ResultadoGrabacion terminarGrabacion(
            ClienteAgente cliente, String nombreSuite, String idCaso) throws IOException {
        detener(Objects.requireNonNull(cliente, "El cliente no puede ser null"));
        return convertir(cliente.tomarEventos(), nombreSuite, idCaso);
    }

    /**
     * Convierte los eventos {@code COMPONENTE_INSPECCIONADO} en componentes; ignora los demás.
     *
     * @param eventos eventos recibidos del agente; no puede ser null
     * @return los componentes en el orden de los eventos
     */
    public static List<Inspeccion> inspecciones(List<EventoDto> eventos) {
        List<Inspeccion> inspecciones = new ArrayList<>();
        for (EventoDto evento : Objects.requireNonNull(eventos, "Los eventos no pueden ser null")) {
            if (!EVENTO_INSPECCION.equals(evento.getTipo())) {
                continue;
            }
            Map<String, Object> datos = evento.getDatos();
            inspecciones.add(
                    new Inspeccion(
                            textoOVacio(datos.get("ventana")),
                            textoOVacio(datos.get("componente")),
                            texto(datos.get("ruta")),
                            texto(datos.get("texto")),
                            new Localizador(criterios(datos.get("localizador")))));
        }
        return inspecciones;
    }

    /**
     * Convierte los eventos {@code ACCION_GRABADA} en una suite con un solo caso; ignora los demás
     * eventos.
     *
     * @param eventos eventos recibidos del agente; no puede ser null
     * @param nombreSuite nombre de la suite; no puede estar en blanco
     * @param idCaso id del caso; si es null o está en blanco se usa {@link #ID_CASO_POR_DEFECTO}
     * @return la suite y los avisos de lo que no se pudo conservar; si no hay acciones, la suite
     *     tiene un caso sin pasos y un aviso
     * @throws IllegalArgumentException si el nombre de la suite está en blanco
     */
    public static ResultadoGrabacion convertir(
            List<EventoDto> eventos, String nombreSuite, String idCaso) {
        Objects.requireNonNull(eventos, "Los eventos no pueden ser null");
        List<Paso> pasos = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        int numero = 0;
        for (EventoDto evento : eventos) {
            if (!EVENTO_ACCION.equals(evento.getTipo())) {
                continue;
            }
            numero++;
            Paso paso = paso(evento.getDatos(), pasos.size() + 1, numero, avisos);
            if (paso != null) {
                pasos.add(paso);
            }
        }
        if (numero == 0) {
            avisos.add("No se grabó ninguna acción");
        }
        String id = idCaso == null || idCaso.isBlank() ? ID_CASO_POR_DEFECTO : idCaso;
        Caso caso = new Caso(id, "Caso grabado con la grabadora", null, pasos, null, null, null);
        Suite suite = new Suite(nombreSuite, null, null, null, List.of(caso), null);
        return new ResultadoGrabacion(suite, avisos);
    }

    /** Traduce una acción grabada; devuelve null y agrega un aviso si no se puede expresar. */
    private static Paso paso(
            Map<String, Object> datos, int orden, int numero, List<String> avisos) {
        String accion = textoOVacio(datos.get("accion"));
        String valor = texto(datos.get("valor"));
        List<Criterio> criterios = criterios(datos.get("localizador"));
        String donde =
                "La acción grabada "
                        + numero
                        + " ("
                        + accion
                        + " sobre "
                        + textoOVacio(datos.get("componente"))
                        + " en \""
                        + textoOVacio(datos.get("ventana"))
                        + "\") no se incluyó: ";
        if (criterios.isEmpty()) {
            avisos.add(donde + "el agente no pudo localizar el componente");
            return null;
        }
        switch (accion) {
            case "PULSAR":
                return new Paso(orden, Accion.PULSAR, new Localizador(criterios), null, null, null);
            case "MARCAR":
                if ("false".equals(valor)) {
                    avisos.add(donde + "el formato de suites no puede expresar desmarcar");
                    return null;
                }
                return new Paso(orden, Accion.MARCAR, new Localizador(criterios), null, null, null);
            case "LLENAR":
            case "SELECCIONAR":
                return pasoDeCampo(accion, valor, criterios, datos, orden, donde, avisos);
            default:
                avisos.add(donde + "la acción no se conoce");
                return null;
        }
    }

    private static Paso pasoDeCampo(
            String accion,
            String valor,
            List<Criterio> criterios,
            Map<String, Object> datos,
            int orden,
            String donde,
            List<String> avisos) {
        String etiqueta = null;
        for (Criterio criterio : criterios) {
            if (criterio.estrategia() == EstrategiaLocalizacion.ETIQUETA) {
                etiqueta = criterio.valor();
                break;
            }
        }
        if (etiqueta == null) {
            avisos.add(
                    donde
                            + "el componente no tiene una etiqueta y el formato de suites solo "
                            + "localiza los campos por su etiqueta");
            return null;
        }
        if ("SELECCIONAR".equals(accion) && (valor == null || valor.isBlank())) {
            avisos.add(donde + "no se grabó la opción elegida");
            return null;
        }
        if (Boolean.TRUE.equals(datos.get("sensible"))) {
            avisos.add(
                    "El paso "
                            + orden
                            + " escribe en \""
                            + etiqueta
                            + "\" un dato sensible que quedó en claro en la suite; revíselo o "
                            + "use un rol del proyecto");
        }
        Localizador campo =
                new Localizador(
                        List.of(
                                new Criterio(EstrategiaLocalizacion.ETIQUETA, etiqueta),
                                new Criterio(EstrategiaLocalizacion.TEXTO, etiqueta)));
        return new Paso(
                orden,
                "LLENAR".equals(accion) ? Accion.LLENAR : Accion.SELECCIONAR,
                campo,
                valor == null ? "" : valor,
                null,
                null);
    }

    /** Lee la lista de criterios del evento; ignora los que no se entienden. */
    private static List<Criterio> criterios(Object lista) {
        List<Criterio> criterios = new ArrayList<>();
        if (!(lista instanceof List<?>)) {
            return criterios;
        }
        for (Object elemento : (List<?>) lista) {
            if (!(elemento instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> criterio = (Map<?, ?>) elemento;
            String estrategia = texto(criterio.get("estrategia"));
            String valor = texto(criterio.get("valor"));
            if (estrategia == null || valor == null || valor.isBlank()) {
                continue;
            }
            try {
                criterios.add(new Criterio(EstrategiaLocalizacion.valueOf(estrategia), valor));
            } catch (IllegalArgumentException e) {
                // Una estrategia que este motor no conoce: se ignora, el resto sirve.
            }
        }
        return criterios;
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }

    private static String textoOVacio(Object valor) {
        return valor == null ? "" : valor.toString();
    }
}
