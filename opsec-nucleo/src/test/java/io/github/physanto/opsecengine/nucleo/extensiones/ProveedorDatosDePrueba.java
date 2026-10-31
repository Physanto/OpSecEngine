package io.github.physanto.opsecengine.nucleo.extensiones;

import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.extension.ProveedorDatos;
import java.util.Optional;

/** Proveedor de datos registrado en META-INF/services de las pruebas; no hace nada. */
public class ProveedorDatosDePrueba implements ProveedorDatos {

    /** Tipo con que se registra. */
    public static final String TIPO = "prueba-nucleo";

    @Override
    public String tipo() {
        return TIPO;
    }

    @Override
    public void preparar(Proyecto proyecto) {}

    @Override
    public void restaurar() {}

    @Override
    public Optional<Object> consultar(String consulta) {
        return Optional.empty();
    }

    @Override
    public void cerrar() {}
}
