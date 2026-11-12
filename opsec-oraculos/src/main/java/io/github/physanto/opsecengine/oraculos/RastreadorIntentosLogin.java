package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.caso.IntentoLogin;
import io.github.physanto.opsecengine.dominio.caso.PoliticaAutenticacion;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.util.ArrayList;
import java.util.List;

/**
 * Observa payloads materializados y respuestas de login sin estado ni datos de una app concreta.
 */
final class RastreadorIntentosLogin {
    private RastreadorIntentosLogin() {}

    static PoliticaAutenticacion politica(Contexto contexto) {
        return contexto == null || contexto.caso() == null ? null : contexto.caso().autenticacion();
    }

    static List<Observacion> observar(Traza traza, Contexto contexto) {
        PoliticaAutenticacion politica = politica(contexto);
        if (traza == null || politica == null || !traza.casoId().equals(contexto.caso().id())) {
            return List.of();
        }
        List<Evento> eventos =
                traza.eventos().stream()
                        .filter(e -> traza.casoId().equals(e.contexto().casoId()))
                        .toList();
        List<Observacion> resultado = new ArrayList<>();
        for (IntentoLogin intento : politica.intentos()) {
            resultado.add(observar(eventos, intento, politica));
        }
        return List.copyOf(resultado);
    }

    private static Observacion observar(
            List<Evento> eventos, IntentoLogin intento, PoliticaAutenticacion politica) {
        Evento envio = inicio(eventos, intento.pasoEnvio());
        if (envio == null
                || envio.contexto().sesionActiva()
                || !envio.texto("accion").orElse("").equals("PULSAR")) {
            return incompleta(intento);
        }
        Evento usuario = escritura(eventos, intento.pasoUsuario(), intento.usuario(), envio);
        Evento contrasena =
                escritura(eventos, intento.pasoContrasena(), intento.contrasena(), envio);
        if (usuario == null
                || contrasena == null
                || usuario.texto("objetivo").equals(contrasena.texto("objetivo"))) {
            return incompleta(intento);
        }
        long limite =
                eventos.stream()
                        .filter(e ->
                                e.tipo() == TipoEvento.PASO_INICIO
                                        && e.secuencia() > envio.secuencia())
                        .mapToLong(Evento::secuencia)
                        .min()
                        .orElse(Long.MAX_VALUE);
        List<Evento> respuesta =
                eventos.stream()
                        .filter(e ->
                                e.secuencia() > envio.secuencia()
                                        && e.secuencia() < limite
                                        && e.contexto().numeroPaso() == intento.pasoEnvio())
                        .toList();
        List<Evento> finales =
                respuesta.stream().filter(e -> e.tipo() == TipoEvento.PASO_FIN).toList();
        if (finales.size() != 1
                || !finales.getFirst().texto("codigo").orElse("").equals("OK")
                || eventos.stream()
                        .anyMatch(e ->
                                e.secuencia() >= limite
                                        && e.contexto().numeroPaso() == intento.pasoEnvio()
                                        && (e.tipo() == TipoEvento.SESION
                                                || politica.rechazo().stream()
                                                        .anyMatch(s -> s.coincide(e))
                                                || politica.bloqueo().stream()
                                                        .anyMatch(s -> s.coincide(e))))) {
            return incompleta(intento);
        }
        List<Evento> aceptados =
                respuesta.stream()
                        .filter(e ->
                                e.tipo() == TipoEvento.SESION && e.booleano("activa").orElse(false))
                        .toList();
        List<Evento> rechazados =
                respuesta.stream()
                        .filter(e -> politica.rechazo().stream().anyMatch(s -> s.coincide(e)))
                        .toList();
        List<Evento> bloqueados =
                respuesta.stream()
                        .filter(e -> politica.bloqueo().stream().anyMatch(s -> s.coincide(e)))
                        .toList();
        int tipos =
                (aceptados.isEmpty() ? 0 : 1)
                        + (rechazados.isEmpty() ? 0 : 1)
                        + (bloqueados.isEmpty() ? 0 : 1);
        if (tipos != 1) {
            return incompleta(intento);
        }
        if (aceptados.stream()
                .anyMatch(e ->
                        e.datos().containsKey("usuario")
                                && !e.texto("usuario")
                                        .filter(intento.usuario()::equals)
                                        .isPresent())) {
            return incompleta(intento);
        }
        if (aceptados.isEmpty() && respuesta.stream().anyMatch(e -> e.contexto().sesionActiva())) {
            return incompleta(intento);
        }
        Estado estado =
                !aceptados.isEmpty()
                        ? Estado.ACEPTADO
                        : !bloqueados.isEmpty() ? Estado.BLOQUEADO : Estado.RECHAZADO;
        Evento senal =
                !aceptados.isEmpty()
                        ? aceptados.getFirst()
                        : !bloqueados.isEmpty() ? bloqueados.getFirst() : rechazados.getFirst();
        List<Long> evidencia =
                List.of(
                        usuario.secuencia(),
                        contrasena.secuencia(),
                        envio.secuencia(),
                        senal.secuencia());
        return new Observacion(
                intento,
                estado,
                envio,
                finales.getFirst(),
                senal,
                evidencia.stream().sorted().toList());
    }

    private static Evento inicio(List<Evento> eventos, int paso) {
        List<Evento> inicios =
                eventos.stream()
                        .filter(e ->
                                e.tipo() == TipoEvento.PASO_INICIO
                                        && e.contexto().numeroPaso() == paso)
                        .toList();
        return inicios.size() == 1 ? inicios.getFirst() : null;
    }

    private static Evento escritura(List<Evento> eventos, int paso, String valor, Evento envio) {
        Evento entrada = inicio(eventos, paso);
        if (entrada == null
                || entrada.secuencia() >= envio.secuencia()
                || entrada.contexto().sesionActiva()
                || !entrada.texto("accion").orElse("").equals("LLENAR")
                || !entrada.texto("valor").filter(valor::equals).isPresent()
                || entrada.texto("objetivo").filter(s -> !s.isBlank()).isEmpty()) {
            return null;
        }
        List<Evento> finales =
                eventos.stream()
                        .filter(e ->
                                e.tipo() == TipoEvento.PASO_FIN
                                        && e.contexto().numeroPaso() == paso
                                        && e.secuencia() > entrada.secuencia()
                                        && e.secuencia() < envio.secuencia())
                        .toList();
        if (finales.size() != 1
                || !finales.getFirst().texto("codigo").orElse("").equals("OK")
                || eventos.stream()
                        .anyMatch(e ->
                                e.tipo() == TipoEvento.PASO_INICIO
                                        && e.secuencia() > entrada.secuencia()
                                        && e.secuencia() < envio.secuencia()
                                        && e.texto("accion").orElse("").equals("LLENAR")
                                        && e.texto("objetivo").equals(entrada.texto("objetivo")))) {
            return null;
        }
        if (eventos.stream()
                .anyMatch(e ->
                        e.secuencia() > entrada.secuencia()
                                && e.secuencia() < envio.secuencia()
                                && (e.tipo() == TipoEvento.SESION
                                        || e.tipo() == TipoEvento.APP_TERMINADA
                                        || (e.tipo() == TipoEvento.PASO_INICIO
                                                && e.texto("accion")
                                                        .orElse("")
                                                        .equals("PULSAR"))))) {
            return null;
        }
        return entrada;
    }

    private static Observacion incompleta(IntentoLogin intento) {
        return new Observacion(intento, Estado.INCOMPLETO, null, null, null, List.of());
    }

    enum Estado {
        ACEPTADO,
        RECHAZADO,
        BLOQUEADO,
        INCOMPLETO
    }

    record Observacion(
            IntentoLogin intento,
            Estado estado,
            Evento envio,
            Evento fin,
            Evento senal,
            List<Long> evidencia) {}
}
