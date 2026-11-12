package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.oraculos.RastreadorIntentosLogin.Estado;
import io.github.physanto.opsecengine.oraculos.RastreadorIntentosLogin.Observacion;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Detecta login aceptado tras fallos sin protección observada. Sin estado y seguro entre hilos. */
public final class OraculoSinBloqueo implements Oraculo {
    /** Crea el oráculo para su descubrimiento mediante ServiceLoader. */
    public OraculoSinBloqueo() {}

    /**
     * Devuelve el id del catálogo.
     *
     * @return SIN-BLOQUEO
     */
    @Override
    public String id() {
        return "SIN-BLOQUEO";
    }

    /**
     * Devuelve el momento de evaluación.
     *
     * @return CASO
     */
    @Override
    public Momento momento() {
        return Momento.CASO;
    }

    /**
     * Comprueba si se aporta una política explícita de autenticación.
     *
     * @param caso caso recibido; se usa el del contexto
     * @param contexto contexto con extensiones.autenticacion; puede ser null
     * @return true si hay PoliticaAutenticacion
     */
    @Override
    public boolean aplica(Caso caso, Contexto contexto) {
        return RastreadorIntentosLogin.politica(contexto) != null;
    }

    /**
     * Busca una sesión iniciada tras N rechazos seguidos del mismo usuario, antes del retardo
     * mínimo.
     *
     * @param traza traza del caso; puede ser null
     * @param contexto contexto con payloads, señales y umbrales; puede ser null
     * @return hallazgos probables, limitados al escenario y umbrales declarados
     */
    @Override
    public List<Hallazgo> evaluar(Traza traza, Contexto contexto) {
        PoliticaAutenticacion politica = RastreadorIntentosLogin.politica(contexto);
        List<Observacion> observaciones = RastreadorIntentosLogin.observar(traza, contexto);
        List<Observacion> fallos = new ArrayList<>();
        List<Hallazgo> resultado = new ArrayList<>();
        Observacion anterior = null;
        for (Observacion actual : observaciones) {
            if (actual.estado() == Estado.INCOMPLETO || actual.estado() == Estado.BLOQUEADO) {
                fallos.clear();
                anterior = null;
                continue;
            }
            if (anterior != null
                    && (!actual.intento().usuario().equals(anterior.intento().usuario())
                            || !continuos(traza, anterior, actual, politica))) {
                fallos.clear();
            }
            if (actual.estado() == Estado.RECHAZADO) {
                fallos.add(actual);
            } else {
                if (fallos.size() >= politica.fallosPermitidos()) {
                    List<Long> evidencia = new ArrayList<>();
                    fallos.forEach(o -> evidencia.addAll(o.evidencia()));
                    evidencia.addAll(actual.evidencia());
                    resultado.add(
                            new Hallazgo(
                                    id(),
                                    "CWE-307",
                                    Confianza.PROBABLE,
                                    "Inicio de sesión sin protección tras intentos fallidos",
                                    "Se observaron al menos "
                                            + politica.fallosPermitidos()
                                            + " rechazos consecutivos del mismo usuario y luego una sesión iniciada antes del retardo mínimo configurado."
                                            + " No se observó una señal de bloqueo; el resultado se limita al escenario y los umbrales declarados.",
                                    List.copyOf(evidencia)));
                }
                fallos.clear();
            }
            anterior = actual.estado() == Estado.RECHAZADO ? actual : null;
        }
        return List.copyOf(resultado);
    }

    private static boolean continuos(
            Traza traza, Observacion anterior, Observacion actual, PoliticaAutenticacion politica) {
        if (actual.envio().secuencia() <= anterior.fin().secuencia()) {
            return false;
        }
        Duration intervalo =
                Duration.between(anterior.senal().instante(), actual.senal().instante());
        if (intervalo.isNegative() || intervalo.compareTo(politica.retardoMinimo()) >= 0) {
            return false;
        }
        Evento previo = anterior.senal();
        for (Evento evento : traza.eventos()) {
            if (!traza.casoId().equals(evento.contexto().casoId())
                    || evento.secuencia() <= anterior.senal().secuencia()
                    || evento.secuencia() > actual.senal().secuencia()) {
                continue;
            }
            if (evento.instante().isBefore(previo.instante())) {
                return false;
            }
            previo = evento;
            if (evento.secuencia() < actual.envio().secuencia()
                    && (evento.tipo() == TipoEvento.SESION
                            || evento.tipo() == TipoEvento.APP_TERMINADA
                            || politica.bloqueo().stream().anyMatch(s -> s.coincide(evento))
                            || (evento.tipo() == TipoEvento.PASO_INICIO
                                    && evento.texto("accion").orElse("").equals("PULSAR")))) {
                return false;
            }
        }
        return true;
    }
}
