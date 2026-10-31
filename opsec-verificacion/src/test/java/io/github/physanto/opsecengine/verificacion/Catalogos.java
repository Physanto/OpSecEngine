package io.github.physanto.opsecengine.verificacion;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

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
}
