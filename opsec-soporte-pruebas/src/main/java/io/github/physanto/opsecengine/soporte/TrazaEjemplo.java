package io.github.physanto.opsecengine.soporte;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Trazas de ejemplo incluidas como recursos, escritas a mano y realistas.
 *
 * <p>Cada valor indica qué debe concluir un oráculo correcto con esa traza; el detalle está en el
 * {@code README.md} de los recursos del paquete. Se cargan con {@link CargadorTrazas}:
 *
 * <pre>{@code
 * Traza traza = new CargadorTrazas().cargarEjemploUnico(TrazaEjemplo.N3_LOCAL_SIN_LOGIN);
 * List<Hallazgo> hallazgos = oraculo.evaluar(traza, contexto);
 * }</pre>
 */
public enum TrazaEjemplo {

    /**
     * Caso N3: rama Local, eliminación de una persona sin iniciar sesión. Debe dar NO CUMPLE por
     * {@code SIN-SESION}.
     */
    N3_LOCAL_SIN_LOGIN("n3-local-sin-login.jsonl"),

    /** Caso A1: el mismo borrado con la sesión de Admin. Debe dar CUMPLE. */
    A1_ADMIN_ELIMINA("a1-admin-elimina.jsonl"),

    /**
     * Caso C13: inyección SQL con el canario {@code K7Q2} en el login. Debe dar NO CUMPLE por
     * {@code SQLI-CANARIO}.
     */
    C13_SQLI_LOGIN("c13-sqli-login.jsonl"),

    /**
     * Casos C01 y C02: mensajes distintos para usuario inexistente y contraseña incorrecta. Deben
     * activar {@code ENUMERACION-USUARIOS}; es la única traza con dos casos.
     */
    C01_C02_MENSAJES("c01-c02-mensajes.jsonl"),

    /** Caso LIMPIO: Cliente inicia sesión y busca una persona, sin fallos. Debe dar CUMPLE. */
    CASO_LIMPIO("caso-limpio.jsonl");

    private final String archivo;

    TrazaEjemplo(String archivo) {
        this.archivo = archivo;
    }

    /**
     * Devuelve el nombre del archivo JSONL de la traza.
     *
     * @return nombre del archivo, por ejemplo {@code n3-local-sin-login.jsonl}
     */
    public String archivo() {
        return archivo;
    }

    /**
     * Abre el recurso de la traza; quien llama debe cerrarlo.
     *
     * @return flujo con el contenido JSONL en UTF-8
     * @throws IOException si el recurso no existe o no se puede abrir
     */
    public InputStream abrir() throws IOException {
        InputStream flujo = TrazaEjemplo.class.getResourceAsStream("trazas/" + archivo);
        if (flujo == null) {
            throw new FileNotFoundException("No se encontró el recurso de la traza " + archivo);
        }
        return flujo;
    }
}
