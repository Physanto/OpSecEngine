package io.github.physanto.opsecengine.oraculos;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Regla validada e inmutable; evalúa condiciones sin ejecutar código del YAML. */
final class OraculoRegla implements Oraculo {
    private final String id;
    private final Momento momento;
    private final TipoEvento tipo;
    private final String campo;
    private final List<String> textos;
    private final JsonNode igualdad;
    private final String titulo;
    private final String explicacion;
    private final String cwe;
    private final Confianza confianza;

    OraculoRegla(JsonNode regla) {
        id = regla.get("id").asText();
        momento = Momento.valueOf(regla.get("momento").asText());
        JsonNode si = regla.get("si");
        tipo = TipoEvento.valueOf(si.get("tipo").asText());
        campo = si.has("campo") ? si.get("campo").asText() : null;
        List<String> fragmentos = new ArrayList<>();
        si.path("texto_contiene").forEach(texto -> fragmentos.add(texto.asText()));
        textos = List.copyOf(fragmentos);
        igualdad = si.has("igual_a") ? si.get("igual_a").deepCopy() : null;
        JsonNode entonces = regla.get("entonces");
        titulo = entonces.get("titulo").asText();
        explicacion = entonces.path("explicacion").asText("");
        cwe = entonces.has("cwe") ? entonces.get("cwe").asText() : null;
        confianza = Confianza.valueOf(entonces.get("confianza").asText());
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Momento momento() {
        return momento;
    }

    @Override
    public boolean aplica(Caso caso, Contexto contexto) {
        return true;
    }

    @Override
    public List<Hallazgo> evaluar(Traza traza, Contexto contexto) {
        Stream<Evento> eventos =
                momento == Momento.SUITE
                        ? contexto.trazasSuite().stream().flatMap(t -> t.eventos().stream())
                        : traza == null ? Stream.empty() : traza.eventos().stream();
        List<Long> evidencia = eventos.filter(this::coincide).map(Evento::secuencia).toList();
        if (momento == Momento.EVENTO) {
            return evidencia.stream().map(secuencia -> hallazgo(List.of(secuencia))).toList();
        }
        return evidencia.isEmpty() ? List.of() : List.of(hallazgo(evidencia));
    }

    private boolean coincide(Evento evento) {
        if (evento.tipo() != tipo) {
            return false;
        }
        Object valor = campo == null ? null : evento.datos().get(campo);
        if (igualdad != null && (!evento.datos().containsKey(campo) || !igual(valor))) {
            return false;
        }
        if (textos.isEmpty()) {
            return true;
        }
        Stream<Object> valores =
                campo == null ? evento.datos().values().stream() : Stream.ofNullable(valor);
        return valores.filter(String.class::isInstance)
                .map(String.class::cast)
                .anyMatch(texto -> textos.stream().anyMatch(texto::contains));
    }

    private boolean igual(Object valor) {
        if (igualdad.isNull()) {
            return valor == null;
        }
        if (igualdad.isNumber() && valor instanceof Number numero) {
            try {
                return igualdad.decimalValue().compareTo(new BigDecimal(numero.toString())) == 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (igualdad.isBoolean()) {
            return Boolean.valueOf(igualdad.booleanValue()).equals(valor);
        }
        return igualdad.isTextual() && igualdad.textValue().equals(valor);
    }

    private Hallazgo hallazgo(List<Long> evidencia) {
        return new Hallazgo(id, cwe, confianza, titulo, explicacion, evidencia);
    }
}
