package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.util.List;

/**
 * Detecta payloads declarados débiles que consiguen iniciar sesión. Sin estado y seguro entre
 * hilos.
 */
public final class OraculoCredencialesDebiles implements Oraculo {
    /** Crea el oráculo para su descubrimiento mediante ServiceLoader. */
    public OraculoCredencialesDebiles() {}

    /**
     * Devuelve el id del catálogo.
     *
     * @return CREDENCIALES-DEBILES
     */
    @Override
    public String id() {
        return "CREDENCIALES-DEBILES";
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
     * Comprueba si hay una política local de autenticación.
     *
     * @param caso caso recibido; se usa el del contexto
     * @param contexto contexto con extensiones.autenticacion; puede ser null
     * @return true si se proporciona PoliticaAutenticacion
     */
    @Override
    public boolean aplica(Caso caso, Contexto contexto) {
        return RastreadorIntentosLogin.politica(contexto) != null;
    }

    /**
     * Exige escrituras materializadas correctas y una señal SESION con activa=true tras el envío.
     *
     * @param traza traza del caso; puede ser null
     * @param contexto contexto con los payloads declarados; puede ser null
     * @return hallazgos confirmados por sesión iniciada, sin copiar las credenciales
     */
    @Override
    public List<Hallazgo> evaluar(Traza traza, Contexto contexto) {
        return RastreadorIntentosLogin.observar(traza, contexto).stream()
                .filter(o ->
                        o.intento().debil()
                                && o.estado() == RastreadorIntentosLogin.Estado.ACEPTADO)
                .map(o ->
                        new Hallazgo(
                                id(),
                                "CWE-521",
                                Confianza.CONFIRMADO,
                                "Credenciales débiles aceptadas",
                                "Un payload declarado débil se escribió y envió correctamente. Un evento SESION confirma el inicio de sesión posterior. Sustituya las credenciales de prueba débiles y revise la política de autenticación.",
                                o.evidencia()))
                .toList();
    }
}
