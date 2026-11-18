package io.github.physanto.opsecengine.nucleo.generacion;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Enfoque;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.extension.GeneradorCasos;
import io.github.physanto.opsecengine.dominio.mapa.MapaApp;
import io.github.physanto.opsecengine.dominio.mapa.Objetivo;
import java.util.List;

/**
 * Generador de enfoque {@code ROBUSTEZ} registrado por {@code ServiceLoader} en las pruebas. Por
 * cada llamada produce siempre el mismo caso {@code ROB-1}, de modo que dos objetivos repiten el
 * id.
 */
public class GeneradorDePrueba implements GeneradorCasos {

    @Override
    public Enfoque enfoque() {
        return Enfoque.ROBUSTEZ;
    }

    @Override
    public List<Caso> generar(MapaApp mapa, Objetivo objetivo, Proyecto proyecto) {
        Paso paso = new Paso(1, Accion.PULSAR, Localizador.porTexto("Aceptar"), null, null, null);
        return List.of(
                new Caso(
                        "ROB-1",
                        "Caso de prueba para " + objetivo.descripcion(),
                        null,
                        List.of(paso),
                        null,
                        null,
                        null));
    }
}
