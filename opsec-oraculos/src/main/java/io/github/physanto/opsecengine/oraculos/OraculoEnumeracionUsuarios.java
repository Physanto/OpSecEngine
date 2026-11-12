package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.ComparacionLogin;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.util.ArrayList;
import java.util.List;

/**
 * Detecta respuestas de login distintas entre dos escenarios fallidos declarados (CWE-204). Es
 * inmutable y seguro entre hilos. No interpreta credenciales ni mensajes de una app concreta.
 */
public final class OraculoEnumeracionUsuarios implements Oraculo {
    /** Crea el oráculo para su descubrimiento mediante ServiceLoader. */
    public OraculoEnumeracionUsuarios() {}

    /**
     * Devuelve el identificador del catálogo.
     *
     * @return ENUMERACION-USUARIOS
     */
    @Override
    public String id() {
        return "ENUMERACION-USUARIOS";
    }

    /**
     * Devuelve el momento de comparación entre casos.
     *
     * @return SUITE
     */
    @Override
    public Momento momento() {
        return Momento.SUITE;
    }

    /**
     * Comprueba que la suite declara la comparación {@code enumeracionUsuarios} (contratos v1.2).
     *
     * @param caso caso recibido; no se utiliza
     * @param contexto contexto de suite; puede ser null
     * @return true si el contexto es de suite y la suite declara la comparación
     */
    @Override
    public boolean aplica(Caso caso, Contexto contexto) {
        return contexto != null
                && contexto.caso() == null
                && contexto.suite().enumeracionUsuarios() != null;
    }

    /**
     * Compara los textos ordenados de los diálogos de dos envíos terminados correctamente.
     *
     * @param traza traza individual; se ignora en favor de trazasSuite
     * @param contexto contexto con la comparación y las trazas de ambos casos
     * @return un hallazgo probable si las respuestas completas difieren; vacío si faltan datos
     */
    @Override
    public List<Hallazgo> evaluar(Traza traza, Contexto contexto) {
        if (!aplica(null, contexto)) {
            return List.of();
        }
        ComparacionLogin comparacion = contexto.suite().enumeracionUsuarios();
        Respuesta inexistente =
                respuesta(
                        contexto,
                        comparacion.casoUsuarioInexistente(),
                        comparacion.pasoUsuarioInexistente());
        Respuesta incorrecta =
                respuesta(
                        contexto,
                        comparacion.casoContrasenaIncorrecta(),
                        comparacion.pasoContrasenaIncorrecta());
        if (inexistente == null
                || incorrecta == null
                || inexistente.textos().equals(incorrecta.textos())) {
            return List.of();
        }
        List<Long> evidencia = new ArrayList<>(inexistente.secuencias());
        evidencia.addAll(incorrecta.secuencias());
        return List.of(
                new Hallazgo(
                        id(),
                        "CWE-204",
                        Confianza.PROBABLE,
                        "Respuestas distintas para credenciales inválidas",
                        "Los diálogos del caso "
                                + comparacion.casoUsuarioInexistente()
                                + " (usuario inexistente) difieren de los del caso "
                                + comparacion.casoContrasenaIncorrecta()
                                + " (contraseña incorrecta). La evidencia lista primero los diálogos del primer caso y después los del segundo."
                                + " Esta diferencia puede permitir distinguir cuentas existentes; confirme que ambos intentos son comparables.",
                        evidencia));
    }

    private static Respuesta respuesta(Contexto contexto, String casoId, int paso) {
        if (contexto.suite().casos().stream().noneMatch(c -> c.id().equals(casoId))) {
            return null;
        }
        List<Traza> trazas =
                contexto.trazasSuite().stream().filter(t -> t.casoId().equals(casoId)).toList();
        if (trazas.size() != 1) {
            return null;
        }
        long envios =
                trazas.getFirst().eventos().stream()
                        .filter(e ->
                                casoId.equals(e.contexto().casoId())
                                        && e.contexto().numeroPaso() == paso
                                        && e.tipo() == TipoEvento.PASO_INICIO)
                        .count();
        if (envios != 1) {
            return null;
        }
        boolean iniciado = false;
        boolean terminado = false;
        List<String> textos = new ArrayList<>();
        List<Long> secuencias = new ArrayList<>();
        for (Evento evento : trazas.getFirst().eventos()) {
            if (!casoId.equals(evento.contexto().casoId())) {
                continue;
            }
            if (evento.tipo() == TipoEvento.PASO_INICIO) {
                if (iniciado && !terminado) {
                    return null;
                }
                if (evento.contexto().numeroPaso() == paso) {
                    if (!evento.texto("accion").orElse("").equals("PULSAR")) {
                        return null;
                    }
                    iniciado = true;
                }
            }
            if (terminado
                    && evento.contexto().numeroPaso() == paso
                    && evento.tipo() == TipoEvento.DIALOGO) {
                return null;
            }
            if (!iniciado || terminado || evento.contexto().numeroPaso() != paso) {
                continue;
            }
            if (evento.contexto().sesionActiva()
                    || (evento.tipo() == TipoEvento.SESION
                            && evento.booleano("activa").orElse(false))) {
                return null;
            }
            if (evento.tipo() == TipoEvento.DIALOGO) {
                String texto = evento.texto("texto").orElse("").strip();
                if (texto.isEmpty()) {
                    return null;
                }
                textos.add(texto);
                secuencias.add(evento.secuencia());
            } else if (evento.tipo() == TipoEvento.PASO_FIN) {
                if (!evento.texto("codigo").orElse("").equals("OK")) {
                    return null;
                }
                terminado = true;
            }
        }
        return terminado && !textos.isEmpty()
                ? new Respuesta(List.copyOf(textos), List.copyOf(secuencias))
                : null;
    }

    private record Respuesta(List<String> textos, List<Long> secuencias) {}
}
