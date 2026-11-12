package io.github.physanto.opsecengine.oraculos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.traza.ContextoEvento;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.soporte.CargadorTrazas;
import io.github.physanto.opsecengine.soporte.TrazaEjemplo;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CargadorReglasTest {
    private final CargadorReglas cargador = new CargadorReglas();

    @Test
    void elEjemploOficialFuncionaSobreUnaTrazaYConElEvaluador() throws Exception {
        List<Oraculo> reglas =
                cargador.leer(
                        Path.of(
                                "..",
                                "contratos",
                                "ejemplos",
                                "oraculos",
                                "mensaje-sql-en-dialogo.yaml"));
        assertThat(reglas)
                .extracting(Oraculo::id)
                .containsExactly("MENSAJE-SQL-EN-DIALOGO", "CONSOLA-ERROR-SQL");
        Contexto contexto = contexto("X");
        var sesion = new EvaluadorOraculos(reglas).iniciarCaso(contexto);
        assertThat(
                        sesion.registrarEvento(
                                        evento(
                                                1,
                                                TipoEvento.DIALOGO,
                                                Map.of(
                                                        "texto",
                                                        "Consulta rechazada: SQLException")))
                                .hallazgos())
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.cwe()).isEqualTo("CWE-209");
                    assertThat(h.confianza()).isEqualTo(Confianza.CONFIRMADO);
                    assertThat(h.evidencia()).containsExactly(1L);
                });
        sesion.registrarEvento(evento(2, TipoEvento.CONSOLA, Map.of("flujo", "ERROR")));
        var resultado = sesion.terminar(null);
        assertThat(resultado.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(resultado.hallazgos()).hasSize(2);
        assertThat(resultado.hallazgos().getLast().confianza()).isEqualTo(Confianza.INFO);
        assertThat(resultado.hallazgos().getLast().explicacion()).isEmpty();
        Traza limpia = new CargadorTrazas().cargarEjemploUnico(TrazaEjemplo.CASO_LIMPIO);
        assertThat(reglas.getFirst().evaluar(limpia, contexto)).isEmpty();
        assertThat(reglas.getFirst().evaluar(null, contexto)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"EVENTO", "CASO", "SUITE"})
    void aplicaLaCardinalidadDelMomento(String momento) throws Exception {
        Oraculo regla = leer(regla(momento, "tipo: DIALOGO"));
        Traza traza =
                new Traza(
                        "X",
                        List.of(
                                evento(1, TipoEvento.DIALOGO, Map.of()),
                                evento(2, TipoEvento.DIALOGO, Map.of())));
        Contexto c = contexto("X");
        var hallazgos =
                regla.evaluar(
                        traza,
                        Momento.valueOf(momento) == Momento.SUITE
                                ? Contexto.deSuite(
                                        c.proyecto(),
                                        c.suite(),
                                        List.of(
                                                traza,
                                                new Traza(
                                                        "Y",
                                                        List.of(
                                                                evento(
                                                                        3,
                                                                        TipoEvento.DIALOGO,
                                                                        Map.of())))))
                                : c);
        assertThat(hallazgos).hasSize(momento.equals("EVENTO") ? 2 : 1);
        if (!momento.equals("EVENTO")) {
            assertThat(hallazgos.getFirst().evidencia())
                    .containsExactlyElementsOf(
                            momento.equals("SUITE") ? List.of(1L, 2L, 3L) : List.of(1L, 2L));
        }
    }

    @Test
    void combinaLasCondicionesYBuscaSoloCamposDeTexto() throws Exception {
        Oraculo regla =
                leer(
                        regla(
                                "EVENTO",
                                "tipo: DIALOGO, campo: texto, texto_contiene: [SQL, ORA-], igual_a: 'ORA-123'"));
        assertThat(evalua(regla, TipoEvento.DIALOGO, Map.of("texto", "ORA-123"))).hasSize(1);
        assertThat(evalua(regla, TipoEvento.DIALOGO, Map.of("texto", "SQL"))).isEmpty();
        assertThat(evalua(regla, TipoEvento.CONSOLA, Map.of("texto", "ORA-123"))).isEmpty();
        assertThat(evalua(regla, TipoEvento.DIALOGO, Map.of("titulo", "ORA-123"))).isEmpty();
        Oraculo todos = leer(regla("CASO", "tipo: DIALOGO, texto_contiene: [SQL, ORA-]"));
        assertThat(evalua(todos, TipoEvento.DIALOGO, Map.of("titulo", "ORA-123"))).hasSize(1);
        assertThat(
                        evalua(
                                todos,
                                TipoEvento.DIALOGO,
                                Map.of("texto", 123, "detalle", List.of("SQL"))))
                .isEmpty();
        assertThat(evalua(todos, TipoEvento.DIALOGO, Map.of())).isEmpty();
        assertThat(evalua(todos, TipoEvento.DIALOGO, Map.of("texto", "sql"))).isEmpty();
    }

    @Test
    void comparaValoresEscalaresSinConvertirTextoNiBooleanosEnNumeros() throws Exception {
        Oraculo numero = leer(regla("CASO", "tipo: DIALOGO, campo: cantidad, igual_a: 3.0"));
        assertThat(evalua(numero, TipoEvento.DIALOGO, Map.of("cantidad", 3))).hasSize(1);
        assertThat(evalua(numero, TipoEvento.DIALOGO, Map.of("cantidad", new BigDecimal("3.000"))))
                .hasSize(1);
        assertThat(evalua(numero, TipoEvento.DIALOGO, Map.of("cantidad", "3.0"))).isEmpty();
        assertThat(evalua(numero, TipoEvento.DIALOGO, Map.of("cantidad", Double.NaN))).isEmpty();
        Oraculo booleano = leer(regla("CASO", "tipo: DIALOGO, campo: capturada, igual_a: false"));
        assertThat(evalua(booleano, TipoEvento.DIALOGO, Map.of("capturada", false))).hasSize(1);
        assertThat(evalua(booleano, TipoEvento.DIALOGO, Map.of("capturada", "false"))).isEmpty();
        Oraculo nulo = leer(regla("CASO", "tipo: DIALOGO, campo: ausente, igual_a: null"));
        assertThat(evalua(nulo, TipoEvento.DIALOGO, Map.of())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "[]",
                "no-es-una-lista",
                "- id: INCOMPLETO",
                "[malformado",
                "- {id: X, id: Y}",
                "- {id: X, momento: EVENTO, si: {tipo: NO_EXISTE}, entonces: {titulo: x, confianza: INFO}}",
                "- {id: X, momento: EVENTO, si: {tipo: DIALOGO, igual_a: 1}, entonces: {titulo: x, confianza: INFO}}",
                "- {id: X, momento: EVENTO, si: {tipo: DIALOGO, texto_contiene: []}, entonces: {titulo: x, confianza: INFO}}",
                "- {id: X, momento: EVENTO, si: {tipo: DIALOGO}, entonces: {titulo: x, confianza: INFO}, desconocido: true}"
            })
    void rechazaYamlYReglasInvalidasConElOrigen(String yaml) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> cargador.leer(new StringReader(yaml), "prueba.yaml"))
                .withMessageContaining("prueba.yaml");
    }

    @Test
    void rechazaIdsReservadosDuplicadosYCwesFueraDelCatalogo() {
        String yaml = regla("EVENTO", "tipo: DIALOGO");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> leer(yaml.replace("REGLA-PRUEBA", "SIN-SESION")))
                .withMessageContaining("/0/id")
                .withMessageContaining("reservado");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> cargador.leer(new StringReader(yaml + yaml), "duplicado.yaml"))
                .withMessageContaining("/1/id")
                .withMessageContaining("repetido");
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        leer(yaml.replace("titulo: Prueba", "titulo: Prueba, cwe: CWE-999999")))
                .withMessageContaining("/0/entonces/cwe");
        assertThatIllegalArgumentException().isThrownBy(() -> leer(yaml + "---\n" + yaml));
    }

    @Test
    void cargaLaCarpetaEnOrdenYDetectaColisionesEntreArchivos(@TempDir Path carpeta)
            throws Exception {
        assertThat(cargador.cargarCarpeta(carpeta)).isEmpty();
        String yaml = regla("EVENTO", "tipo: DIALOGO");
        Files.writeString(
                carpeta.resolve("b.yaml"),
                yaml.replace("REGLA-PRUEBA", "B"),
                StandardCharsets.UTF_8);
        Files.writeString(
                carpeta.resolve("a.yaml"),
                yaml.replace("REGLA-PRUEBA", "A"),
                StandardCharsets.UTF_8);
        Files.writeString(carpeta.resolve("ignorado.md"), "no es YAML", StandardCharsets.UTF_8);
        Files.createDirectory(carpeta.resolve("subcarpeta.yaml"));
        assertThat(cargador.cargarCarpeta(carpeta))
                .extracting(Oraculo::id)
                .containsExactly("A", "B");
        Files.writeString(
                carpeta.resolve("b.yaml"),
                yaml.replace("REGLA-PRUEBA", "A"),
                StandardCharsets.UTF_8);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> cargador.cargarCarpeta(carpeta))
                .withMessageContaining("a.yaml")
                .withMessageContaining("b.yaml");
    }

    @Test
    void noCierraElLectorProporcionado() throws Exception {
        boolean[] cerrado = {false};
        StringReader lector =
                new StringReader(regla("EVENTO", "tipo: DIALOGO")) {
                    @Override
                    public void close() {
                        cerrado[0] = true;
                        super.close();
                    }
                };
        cargador.leer(lector, "lector");
        assertThat(cerrado[0]).isFalse();
        lector.close();
    }

    private Oraculo leer(String yaml) throws IOException {
        return cargador.leer(new StringReader(yaml), "prueba.yaml").getFirst();
    }

    private static String regla(String momento, String condicion) {
        return "- {id: REGLA-PRUEBA, momento: "
                + momento
                + ", si: {"
                + condicion
                + "}, entonces: {titulo: Prueba, confianza: INFO}}\n";
    }

    private static List<io.github.physanto.opsecengine.dominio.resultado.Hallazgo> evalua(
            Oraculo regla, TipoEvento tipo, Map<String, Object> datos) {
        return regla.evaluar(new Traza("X", List.of(evento(1, tipo, datos))), contexto("X"));
    }

    private static Evento evento(long secuencia, TipoEvento tipo, Map<String, Object> datos) {
        return new Evento(
                secuencia, Instant.EPOCH, tipo, new ContextoEvento("X", 0, null, false), datos);
    }

    private static Contexto contexto(String id) {
        Proyecto proyecto =
                new Proyecto(
                        "clientes",
                        Path.of("app.jar"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        Caso caso = new Caso(id, null, null, null, null, null, null);
        return Contexto.deCaso(
                proyecto, new Suite("reglas", null, null, null, List.of(caso), null), caso);
    }
}
