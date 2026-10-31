package io.github.physanto.opsecengine.nucleo.extensiones;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.util.List;

/** Oráculo registrado en META-INF/services de las pruebas; nunca encuentra nada. */
public class OraculoDePrueba implements Oraculo {

    /** Id con que se registra. */
    public static final String ID = "PRUEBA-NUCLEO";

    private final String id;

    /** Constructor que usa {@link java.util.ServiceLoader}. */
    public OraculoDePrueba() {
        this(ID);
    }

    OraculoDePrueba(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Momento momento() {
        return Momento.CASO;
    }

    @Override
    public boolean aplica(Caso caso, Contexto ctx) {
        return true;
    }

    @Override
    public List<Hallazgo> evaluar(Traza traza, Contexto ctx) {
        return List.of();
    }
}
