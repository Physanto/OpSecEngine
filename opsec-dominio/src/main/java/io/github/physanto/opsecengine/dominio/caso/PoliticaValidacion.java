package io.github.physanto.opsecengine.dominio.caso;

import java.util.List;
import java.util.Objects;

/**
 * Política de validación de un caso: qué valor inválido se escribe, en qué paso se envía y qué
 * señales significan que la aplicación lo aceptó o lo rechazó.
 *
 * <p>La usa el oráculo {@code ENTRADA-INVALIDA-ACEPTADA} (CWE-20). Que un paso termine en {@code
 * OK} no prueba que la entrada se aceptara; por eso la política declara las señales observables.
 * Los números de paso son los de {@code ContextoEvento.numeroPaso()}, empezando en 1. Desde
 * contratos v1.2.
 *
 * <p>Es inmutable y segura entre hilos.
 *
 * @param pasoEntrada número del paso {@code LLENAR} que escribe el valor inválido; mayor que 0
 * @param pasoEnvio número del paso {@code PULSAR} que envía el formulario; mayor que {@code
 *     pasoEntrada}
 * @param valorInvalido valor que la aplicación debería rechazar; no puede ser null
 * @param aceptacion señales que indican que la aplicación aceptó el valor; no puede ser null ni
 *     estar vacía
 * @param rechazo señales que indican que la aplicación rechazó el valor; no puede ser null ni estar
 *     vacía, y no comparte señales con {@code aceptacion}
 */
public record PoliticaValidacion(
        int pasoEntrada,
        int pasoEnvio,
        String valorInvalido,
        List<Senal> aceptacion,
        List<Senal> rechazo) {

    /**
     * Crea la política con copias inmutables de sus listas y valida sus datos.
     *
     * @throws NullPointerException si {@code valorInvalido}, {@code aceptacion} o {@code rechazo}
     *     es null o contiene elementos null
     * @throws IllegalArgumentException si los pasos no son positivos o el envío no va después de la
     *     entrada, si alguna lista de señales está vacía o si una señal aparece en las dos
     */
    public PoliticaValidacion {
        Objects.requireNonNull(valorInvalido, "El valor inválido no puede ser null");
        aceptacion = List.copyOf(Objects.requireNonNull(aceptacion, "Faltan las señales"));
        rechazo = List.copyOf(Objects.requireNonNull(rechazo, "Faltan las señales"));
        if (pasoEntrada < 1 || pasoEnvio <= pasoEntrada) {
            throw new IllegalArgumentException(
                    "El paso de entrada debe ser positivo y el de envío, posterior");
        }
        if (aceptacion.isEmpty() || rechazo.isEmpty()) {
            throw new IllegalArgumentException(
                    "La política necesita señales de aceptación y de rechazo");
        }
        for (Senal senal : aceptacion) {
            if (rechazo.contains(senal)) {
                throw new IllegalArgumentException(
                        "Una misma señal no puede indicar aceptación y rechazo");
            }
        }
    }
}
