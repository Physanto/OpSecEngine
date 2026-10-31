package io.github.physanto.opsecengine.verificacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Lectura de las entradas de los catálogos de {@code contratos/catalogos/} para las pruebas. */
final class Catalogos {

    private Catalogos() {}

    /**
     * Devuelve las entradas de un catálogo.
     *
     * @param archivo nombre del archivo, por ejemplo {@code eventos.yaml}
     * @param lista nombre de la lista de entradas dentro del archivo
     * @return las entradas, en el orden del archivo
     */
    static List<JsonNode> entradas(String archivo, String lista) {
        List<JsonNode> entradas = new ArrayList<>();
        Contratos.leerYaml(Contratos.CATALOGOS.resolve(archivo)).path(lista).forEach(entradas::add);
        return entradas;
    }

    /**
     * Devuelve las entradas del catálogo de eventos.
     *
     * @return una entrada por tipo de evento
     */
    static List<JsonNode> eventos() {
        return entradas("eventos.yaml", "eventos");
    }

    /**
     * Devuelve los ids de oráculos reservados.
     *
     * @return los ids, en el orden del catálogo
     */
    static List<String> idsDeOraculos() {
        return textos(entradas("oraculos.yaml", "oraculos"), "id");
    }

    /**
     * Devuelve los ids del catálogo de CWE.
     *
     * @return los ids, en el orden del catálogo
     */
    static List<String> idsDeCwe() {
        return textos(entradas("cwe.yaml", "cwe"), "id");
    }

    /**
     * Extrae un campo de texto de cada entrada.
     *
     * @param entradas entradas de un catálogo
     * @param campo nombre del campo
     * @return el valor del campo en cada entrada, en orden
     */
    static List<String> textos(List<JsonNode> entradas, String campo) {
        List<String> textos = new ArrayList<>();
        entradas.forEach(entrada -> textos.add(entrada.path(campo).asText()));
        return textos;
    }

    /**
     * Verifica una traza contra el catálogo de eventos: secuencias crecientes, campos obligatorios
     * presentes, ningún campo desconocido y valores enumerados dentro de los permitidos.
     *
     * @param eventos eventos de la traza, uno por línea, en orden
     * @param origen nombre de la traza para los mensajes de error
     */
    static void verificarTraza(List<JsonNode> eventos, String origen) {
        Map<String, JsonNode> catalogo = new TreeMap<>();
        eventos().forEach(evento -> catalogo.put(evento.path("tipo").asText(), evento));
        long anterior = -1;
        for (JsonNode evento : eventos) {
            long secuencia = evento.path("secuencia").asLong();
            String ubicacion = "evento " + secuencia + " de " + origen;
            assertThat(secuencia).as("secuencia creciente en %s", origen).isGreaterThan(anterior);
            anterior = secuencia;

            JsonNode datos = evento.path("datos");
            List<String> presentes = new ArrayList<>();
            datos.fieldNames().forEachRemaining(presentes::add);
            List<String> conocidos = new ArrayList<>();
            for (JsonNode campo : catalogo.get(evento.path("tipo").asText()).path("campos")) {
                String nombre = campo.path("nombre").asText();
                conocidos.add(nombre);
                if (campo.path("obligatorio").asBoolean()) {
                    assertThat(presentes).as("campo obligatorio en %s", ubicacion).contains(nombre);
                }
                if (campo.has("valores") && datos.has(nombre)) {
                    List<String> valores = new ArrayList<>();
                    campo.path("valores").forEach(valor -> valores.add(valor.asText()));
                    assertThat(valores)
                            .as("valor de %s en %s", nombre, ubicacion)
                            .contains(datos.path(nombre).asText());
                }
            }
            assertThat(conocidos).as("campos conocidos en %s", ubicacion).containsAll(presentes);
        }
    }
}
