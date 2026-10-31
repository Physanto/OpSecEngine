package io.github.physanto.opsecengine.practica.fallos;

/** Fallos sembrados en la app de práctica, con su debilidad (CWE) asociada. */
public enum Fallo {
    /** Cuenta de soporte con usuario y contraseña escritos en el código. */
    F1("Credenciales embebidas en el código", "CWE-798"),
    /** El modo "Local" abre el menú del administrador sin iniciar sesión. */
    F2("El modo Local abre el menú del administrador sin iniciar sesión", "CWE-306"),
    /** La ventana EliminarPersona tiene un {@code main} público que la abre sin iniciar sesión. */
    F3("La ventana EliminarPersona se abre sin iniciar sesión desde su main", "CWE-306"),
    /** El inicio de sesión concatena el texto de los campos en una sentencia SQL. */
    F4("Inicio de sesión con SQL concatenado en un Statement", "CWE-89"),
    /** El inicio de sesión responde distinto a un usuario inexistente y a una contraseña mala. */
    F5("Mensajes distintos para usuario inexistente y contraseña incorrecta", "CWE-204"),
    /** La contraseña escrita se imprime en la consola al iniciar sesión. */
    F6("La contraseña se imprime en la consola al iniciar sesión", "CWE-532");

    private final String descripcion;
    private final String cwe;

    Fallo(String descripcion, String cwe) {
        this.descripcion = descripcion;
        this.cwe = cwe;
    }

    /**
     * Devuelve la descripción corta del fallo.
     *
     * @return descripción en español
     */
    public String descripcion() {
        return descripcion;
    }

    /**
     * Devuelve la debilidad asociada.
     *
     * @return identificador con la forma {@code CWE-<n>}
     */
    public String cwe() {
        return cwe;
    }
}
