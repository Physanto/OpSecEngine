package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Política local que declara una entrada inválida y cómo observar su aceptación o rechazo.
 *
 * <p>No deduce restricciones por el tipo de payload ni modifica contratos. El integrador aporta las
 * señales según las reglas de la aplicación. Las colecciones son inmutables y solo contienen
 * escalares inmutables; la política es segura entre hilos.
 *
 * @param casoId caso al que corresponde la política
 * @param pasoEntrada orden del paso LLENAR que introduce el valor inválido
 * @param pasoEnvio orden posterior del paso PULSAR que envía el formulario
 * @param valorInvalido valor exacto que la política declara inválido; puede estar vacío
 * @param aceptacion señales alternativas de aceptación, al menos una
 * @param rechazo señales alternativas de rechazo, al menos una
 */
public record PoliticaValidacionTemporal(
        String casoId,
        int pasoEntrada,
        int pasoEnvio,
        String valorInvalido,
        List<Senal> aceptacion,
        List<Senal> rechazo) {
    /**
     * Valida las referencias de pasos y copia las señales.
     *
     * @throws NullPointerException si falta un dato requerido o hay señales null
     * @throws IllegalArgumentException si el caso está vacío, los pasos son incoherentes o faltan
     *     señales
     */
    public PoliticaValidacionTemporal {
        Objects.requireNonNull(casoId, "Falta el id del caso");
        Objects.requireNonNull(valorInvalido, "Falta el valor inválido");
        aceptacion = List.copyOf(aceptacion);
        rechazo = List.copyOf(rechazo);
        if (casoId.isBlank() || pasoEntrada < 1 || pasoEnvio <= pasoEntrada) {
            throw new IllegalArgumentException(
                    "El caso y los pasos de validación deben ser coherentes");
        }
        if (aceptacion.isEmpty() || rechazo.isEmpty()) {
            throw new IllegalArgumentException("Se requieren señales de aceptación y rechazo");
        }
        if (aceptacion.stream().anyMatch(rechazo::contains)) {
            throw new IllegalArgumentException(
                    "Una misma señal no puede significar aceptación y rechazo");
        }
    }

    /**
     * Condición sobre un evento observable: deben coincidir el tipo y todos los campos indicados.
     * No acepta el OK de un paso como prueba de aceptación de la entrada.
     *
     * @param tipo DIALOGO, SESION o ESTADO
     * @param camposIguales condiciones de igualdad exacta sobre datos, sin conversiones de texto;
     *     los números se comparan por valor decimal
     */
    public record Senal(TipoEvento tipo, Map<String, Object> camposIguales) {
        private static final Set<TipoEvento> OBSERVABLES =
                Set.of(TipoEvento.DIALOGO, TipoEvento.SESION, TipoEvento.ESTADO);

        /**
         * Copia las condiciones y normaliza los números a BigDecimal.
         *
         * @throws NullPointerException si el tipo, el mapa, una clave o un valor son null
         * @throws IllegalArgumentException si el tipo no es observable, faltan campos o hay valores
         *     no escalares
         */
        public Senal {
            Objects.requireNonNull(tipo, "Falta el tipo de señal");
            Objects.requireNonNull(camposIguales, "Faltan las condiciones de la señal");
            if (!OBSERVABLES.contains(tipo) || camposIguales.isEmpty()) {
                throw new IllegalArgumentException(
                        "La señal requiere un evento observable y condiciones explícitas");
            }
            Map<String, Object> copia = new LinkedHashMap<>();
            camposIguales.forEach((campo, valor) -> {
                Objects.requireNonNull(campo, "El campo no puede ser null");
                Objects.requireNonNull(valor, "El valor no puede ser null");
                Object normal = normalizar(valor);
                if (campo.isBlank() || normal == null) {
                    throw new IllegalArgumentException(
                            "La señal solo admite campos y escalares válidos");
                }
                copia.put(campo, normal);
            });
            camposIguales = Map.copyOf(copia);
        }

        /**
         * Comprueba la condición sin lanzar excepciones por datos de evento ausentes o mal tipados.
         *
         * @param evento evento observado; puede ser null
         * @return true si coinciden el tipo y todas las igualdades
         */
        public boolean coincide(Evento evento) {
            if (evento == null || evento.tipo() != tipo) {
                return false;
            }
            return camposIguales.entrySet().stream()
                    .allMatch(entrada -> {
                        Object actual = normalizar(evento.datos().get(entrada.getKey()));
                        if (actual instanceof BigDecimal numero
                                && entrada.getValue() instanceof BigDecimal esperado) {
                            return numero.compareTo(esperado) == 0;
                        }
                        return entrada.getValue().equals(actual);
                    });
        }

        private static Object normalizar(Object valor) {
            if (valor instanceof String || valor instanceof Boolean) {
                return valor;
            }
            if (valor instanceof Number numero) {
                try {
                    return new BigDecimal(numero.toString()).stripTrailingZeros();
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        }
    }
}
