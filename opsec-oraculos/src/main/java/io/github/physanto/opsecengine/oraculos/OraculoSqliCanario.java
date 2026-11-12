package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.util.List;

/**
 * Detecta la propagación de un canario de entrada a SQL de tipo STATEMENT (CWE-89). Es inmutable y
 * seguro entre hilos; no demuestra por sí solo una explotación.
 */
public final class OraculoSqliCanario implements Oraculo {
    /** Crea el oráculo para su descubrimiento mediante ServiceLoader. */
    public OraculoSqliCanario() {}

    /**
     * Devuelve el identificador reservado del catálogo.
     *
     * @return SQLI-CANARIO
     */
    @Override
    public String id() {
        return "SQLI-CANARIO";
    }

    /**
     * Devuelve el momento de evaluación del flujo completo de entrada.
     *
     * @return CASO
     */
    @Override
    public Momento momento() {
        return Momento.CASO;
    }

    /**
     * Comprueba si el caso declara un canario mediante el adaptador local de validación.
     *
     * @param caso caso evaluado; puede ser null
     * @param contexto contexto de evaluación; puede ser null
     * @return true si el contexto contiene un canario válido
     */
    @Override
    public boolean aplica(Caso caso, Contexto contexto) {
        return RastreadorCanarioBackend.canario(contexto) != null;
    }

    /**
     * Busca el canario escrito en LLENAR dentro de SQL de tipo STATEMENT.
     *
     * @param traza traza del caso; puede ser null
     * @param contexto contexto con extensiones.validacion.canario; puede ser null
     * @return hallazgos probables por evento, sin copiar entradas ni datos del backend
     */
    @Override
    public List<Hallazgo> evaluar(Traza traza, Contexto contexto) {
        return RastreadorCanarioBackend.evaluar(traza, contexto, id(), true);
    }
}
