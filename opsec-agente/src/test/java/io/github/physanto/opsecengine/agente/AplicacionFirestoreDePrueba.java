package io.github.physanto.opsecengine.agente;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.WriteBatch;
import java.util.Map;

/** Aplicación de prueba sin interfaz que usa los dobles de Firestore. */
public final class AplicacionFirestoreDePrueba {

    /** Línea que escribe al terminar. */
    public static final String LINEA_LISTO = "FIRESTORE-LISTO";

    private AplicacionFirestoreDePrueba() {}

    /**
     * Ejecuta las operaciones y espera a que la detengan.
     *
     * @param args sin uso
     * @throws Exception si falla algo inesperado
     */
    public static void main(String[] args) throws Exception {
        while (System.in.read() != '\n') {
            // espera la orden de la prueba
        }
        try {
            new DocumentReference("personas/123").set(Map.of("nombre", "Ana", "clave", "secreta"));
            System.out.println("set: sin fallo");
        } catch (RuntimeException e) {
            System.out.println("set: " + e.getClass().getName() + " " + e.getMessage());
        }
        try {
            new DocumentReference("personas/fallo").delete();
        } catch (IllegalStateException esperada) {
            System.out.println("Falló como se esperaba");
        }
        WriteBatch lote = new WriteBatch();
        lote.set(new DocumentReference("personas/9"), Map.of("edad", 3));
        lote.commit();
        System.out.println(LINEA_LISTO);
        Thread.sleep(60_000);
    }
}
