package io.github.physanto.opsecengine.nucleo.ejecucion;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.OperacionesSensibles;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.protocolo.dto.ConfigGanchosDto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Prepara los canarios de una suite y la configuración de ganchos de cada caso.
 *
 * <p>Un canario es una marca única que un caso escribe en un campo para rastrearla hasta SQL,
 * Firestore o la consola. Si un caso escribe la variable {@code {canario}} en algún paso {@code
 * LLENAR} y no declara canario, se genera uno determinista a partir de la semilla de la suite y del
 * id del caso, de modo que repetir la ejecución con la misma semilla produce la misma marca. Sin
 * estado; seguro entre hilos.
 */
final class Canarios {

    /** Variable que se reemplaza por el canario del caso. */
    static final String VARIABLE = "{canario}";

    /** Prefijo de los canarios generados. */
    static final String PREFIJO = "OPSEC";

    private Canarios() {}

    /**
     * Genera el canario de los casos que lo usan y lo escribe en sus pasos.
     *
     * @param suite suite con las variables sin reemplazar
     * @return la misma suite si ningún caso usa la variable; si no, una suite cuyos casos con
     *     {@code {canario}} tienen el canario declarado y los valores reemplazados
     */
    static Suite preparar(Suite suite) {
        boolean cambia = false;
        List<Caso> casos = new ArrayList<>();
        for (Caso caso : suite.casos()) {
            Caso preparado = usaVariable(caso) ? conCanario(suite, caso) : caso;
            cambia |= preparado != caso;
            casos.add(preparado);
        }
        if (!cambia) {
            return suite;
        }
        return new Suite(
                suite.nombre(),
                suite.version(),
                suite.enfoques(),
                suite.config(),
                casos,
                suite.enumeracionUsuarios(),
                suite.extensiones());
    }

    /**
     * Arma la configuración de ganchos de un caso: operaciones sensibles del proyecto y el canario
     * del caso, si lo tiene.
     *
     * @param proyecto proyecto con las operaciones sensibles
     * @param caso caso que se va a ejecutar
     * @return la configuración para enviar al agente
     */
    static ConfigGanchosDto configuracionGanchos(Proyecto proyecto, Caso caso) {
        OperacionesSensibles sensibles = proyecto.sensibles();
        List<String> sql =
                sensibles == null ? List.of() : new ArrayList<>(new TreeSet<>(sensibles.sql()));
        List<String> firestore =
                sensibles == null
                        ? List.of()
                        : new ArrayList<>(new TreeSet<>(sensibles.firestore()));
        List<String> canarios = caso.canario() == null ? List.of() : List.of(caso.canario());
        return new ConfigGanchosDto(sql, firestore, canarios, List.of());
    }

    /** Canario determinista: {@code OPSEC} y 12 dígitos hexadecimales del SHA-256 de semilla:id. */
    static String generar(long semilla, String idCaso) {
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest((semilla + ":" + idCaso).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(PREFIJO);
            for (int i = 0; i < 6; i++) {
                hex.append(String.format(Locale.ROOT, "%02X", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("El JDK no trae SHA-256", e);
        }
    }

    private static boolean usaVariable(Caso caso) {
        for (Paso paso : caso.pasos()) {
            if (paso.accion() == Accion.LLENAR
                    && paso.valor() != null
                    && paso.valor().contains(VARIABLE)) {
                return true;
            }
        }
        return false;
    }

    private static Caso conCanario(Suite suite, Caso caso) {
        String canario =
                caso.canario() != null
                        ? caso.canario()
                        : generar(suite.config().semilla(), caso.id());
        List<Paso> pasos = new ArrayList<>();
        for (Paso paso : caso.pasos()) {
            String valor =
                    paso.accion() == Accion.LLENAR && paso.valor() != null
                            ? paso.valor().replace(VARIABLE, canario)
                            : paso.valor();
            pasos.add(
                    new Paso(
                            paso.orden(),
                            paso.accion(),
                            paso.objetivo(),
                            valor,
                            paso.esperaMaxima(),
                            paso.extensiones()));
        }
        return new Caso(
                caso.id(),
                caso.descripcion(),
                caso.rol(),
                pasos,
                caso.ataques(),
                caso.oraculos(),
                canario,
                caso.validacion(),
                caso.autenticacion(),
                caso.extensiones());
    }
}
