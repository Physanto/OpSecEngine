package io.github.physanto.opsecengine.oraculos;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.IntentoLogin;
import io.github.physanto.opsecengine.dominio.caso.PoliticaAutenticacion;
import io.github.physanto.opsecengine.dominio.caso.Senal;
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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class OraculosExposicionTest {
    private final OraculoContrasenaEnCampoVisible visible = new OraculoContrasenaEnCampoVisible();
    private final OraculoJdbcSinCifrado jdbc = new OraculoJdbcSinCifrado();

    @Test
    void lasTrazasDeCampoVisibleYOcultoDanLosVeredictosEsperados() throws Exception {
        Traza traza = cargar("contrasena-campo-visible.jsonl");
        var sesion =
                new EvaluadorOraculos(List.of(visible)).iniciarCaso(contexto(traza.casoId(), null));
        traza.eventos().forEach(sesion::registrarEvento);
        var resultado = sesion.terminar(null);
        assertThat(resultado.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(resultado.hallazgos())
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.oraculoId()).isEqualTo("CONTRASENA-EN-CAMPO-VISIBLE");
                    assertThat(h.cwe()).isEqualTo("CWE-549");
                    assertThat(h.confianza()).isEqualTo(Confianza.PROBABLE);
                    assertThat(h.evidencia()).containsExactly(0L, 1L);
                    assertThat(h.explicacion()).doesNotContain("ClavePrueba45", "txtPassword");
                });
        assertThat(visible.evaluar(cargar("contrasena-campo-oculto.jsonl"), null)).isEmpty();
    }

    @Test
    void reconocePalabrasYComponentesSinConfundirBypassNiNombresDeClaseArbitrarios() {
        for (String campo : List.of("txtPassword", "Contraseña", "miClave", "pass")) {
            for (String tipo :
                    List.of(
                            "JTextField",
                            "javax.swing.JTextArea",
                            "JTextPane",
                            "javax.swing.JEditorPane")) {
                assertThat(visible.evaluar(campo(campo, tipo, "secreto", "OK"), null)).hasSize(1);
            }
        }
        for (String campo : List.of("Usuario", "Bypass", "Compass", "Passenger")) {
            assertThat(visible.evaluar(campo(campo, "JTextField", "secreto", "OK"), null))
                    .isEmpty();
        }
        for (String tipo :
                List.of(
                        "JPasswordField",
                        "javax.swing.JPasswordField",
                        "personal.JPasswordField",
                        "personal.JTextField",
                        "")) {
            assertThat(visible.evaluar(campo("password", tipo, "secreto", "OK"), null)).isEmpty();
        }
    }

    @Test
    void laPoliticaDeAutenticacionIdentificaUnCampoDeNombreOpaco() {
        PoliticaAutenticacion politica =
                new PoliticaAutenticacion(
                        List.of(new IntentoLogin(2, 1, 3, "usuario", "secreto", false)),
                        2,
                        5_000L,
                        List.of(new Senal(TipoEvento.DIALOGO, Map.of("texto", "rechazo"))),
                        List.of(new Senal(TipoEvento.DIALOGO, Map.of("texto", "bloqueo"))));
        Traza traza = campo("opaco", "JTextField", "secreto", "OK");
        assertThat(visible.evaluar(traza, contexto("C", politica))).hasSize(1);
        assertThat(
                        visible.evaluar(
                                campo("opaco", "JTextField", "otro", "OK"),
                                contexto("C", politica)))
                .isEmpty();
    }

    @Test
    void exigeValorNoVacioFinalCorrectoYMismoCasoYPasoSinAccionesIntercaladas() {
        assertThat(visible.evaluar(campo("password", "JTextField", "", "OK"), null)).isEmpty();
        assertThat(visible.evaluar(campo("password", "JTextField", "secreto", "ERROR"), null))
                .isEmpty();
        Traza base = campo("password", "JTextField", "secreto", "OK");
        Evento inicio = base.eventos().getFirst();
        Evento fin = base.eventos().getLast();
        assertThat(visible.evaluar(new Traza("C", List.of(inicio)), null)).isEmpty();
        assertThat(
                        visible.evaluar(
                                new Traza(
                                        "C",
                                        List.of(
                                                inicio,
                                                new Evento(
                                                        1,
                                                        Instant.EPOCH,
                                                        fin.tipo(),
                                                        new ContextoEvento("OTRO", 1, null, false),
                                                        fin.datos()))),
                                null))
                .isEmpty();
        assertThat(
                        visible.evaluar(
                                new Traza(
                                        "C",
                                        List.of(
                                                inicio,
                                                new Evento(
                                                        1,
                                                        Instant.EPOCH,
                                                        fin.tipo(),
                                                        new ContextoEvento("C", 2, null, false),
                                                        fin.datos()))),
                                null))
                .isEmpty();
        Evento accion = evento(1, TipoEvento.PASO_INICIO, Map.of("accion", "CAPTURAR"));
        assertThat(
                        visible.evaluar(
                                new Traza(
                                        "C",
                                        List.of(
                                                inicio,
                                                accion,
                                                new Evento(
                                                        2,
                                                        Instant.EPOCH,
                                                        fin.tipo(),
                                                        fin.contexto(),
                                                        fin.datos()))),
                                null))
                .isEmpty();
        assertThat(visible.evaluar(base, contexto("OTRO", null))).isEmpty();
    }

    @Test
    void lasTrazasJdbcActivanYDescartanSinExponerLaUrl() throws Exception {
        Traza traza = cargar("jdbc-sin-cifrado.jsonl");
        assertThat(jdbc.evaluar(traza, null))
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.oraculoId()).isEqualTo("JDBC-SIN-CIFRADO");
                    assertThat(h.cwe()).isEqualTo("CWE-319");
                    assertThat(h.confianza()).isEqualTo(Confianza.PROBABLE);
                    assertThat(h.evidencia()).containsExactly(0L);
                    assertThat(h.explicacion()).doesNotContain("jdbc:", "servidor", "SELECT");
                });
        assertThat(jdbc.evaluar(cargar("jdbc-cifrado-exigido.jsonl"), null)).isEmpty();
    }

    @Test
    void mysqlRespetaModosPrecedenciaYOpcionesHeredadas() {
        for (String opciones :
                List.of(
                        "",
                        "?sslMode=DISABLED",
                        "?sslMode=PREFERRED",
                        "?useSSL=false",
                        "?useSSL=true",
                        "?password=sslMode%3DREQUIRED")) {
            assertThat(jdbc.evaluar(sql("jdbc:mysql://host/base" + opciones), null)).hasSize(1);
        }
        for (String opciones :
                List.of(
                        "?sslMode=REQUIRED",
                        "?sslMode=VERIFY_CA",
                        "?sslMode=VERIFY_IDENTITY",
                        "?useSSL=false&sslMode=REQUIRED",
                        "?useSSL=true&requireSSL=true",
                        "?useSSL=true&verifyServerCertificate=true")) {
            assertThat(jdbc.evaluar(sql("jdbc:mysql://host/base" + opciones), null)).isEmpty();
        }
        assertThat(
                        jdbc.evaluar(
                                sql(
                                        "jdbc:mysql://host/base?sslMode=DISABLED&useSSL=true&requireSSL=true"),
                                null))
                .hasSize(1);
    }

    @Test
    void postgresDistingueSslObligatorioDeLosModosQuePermitenTextoClaro() {
        for (String opciones :
                List.of(
                        "",
                        "?sslmode=disable",
                        "?sslmode=allow",
                        "?sslmode=prefer",
                        "?ssl=false",
                        "?ssl=true&sslmode=disable")) {
            assertThat(jdbc.evaluar(sql("jdbc:postgresql://host/base" + opciones), null))
                    .hasSize(1);
        }
        for (String opciones :
                List.of(
                        "?sslmode=require",
                        "?sslmode=verify-ca",
                        "?sslmode=verify-full",
                        "?ssl=true",
                        "?ssl",
                        "?ssl=false&sslmode=require")) {
            assertThat(jdbc.evaluar(sql("jdbc:postgresql://host/base" + opciones), null)).isEmpty();
        }
    }

    @Test
    void sqlServerNoDeduceElValorPredeterminadoYH2DistingueTransporteDeCifradoDeArchivos() {
        assertThat(jdbc.evaluar(sql("jdbc:sqlserver://host;encrypt=false"), null)).hasSize(1);
        assertThat(jdbc.evaluar(sql("jdbc:sqlserver://host;ENCRYPT=false"), null)).hasSize(1);
        for (String url :
                List.of(
                        "jdbc:sqlserver://host",
                        "jdbc:sqlserver://host;encrypt=true",
                        "jdbc:sqlserver://host;encrypt=strict",
                        "jdbc:h2:mem:prueba",
                        "jdbc:h2:file:./prueba",
                        "jdbc:h2:./prueba",
                        "jdbc:h2:ssl://host/prueba")) {
            assertThat(jdbc.evaluar(sql(url), null)).isEmpty();
        }
        assertThat(jdbc.evaluar(sql("jdbc:h2:tcp://host/mem:prueba;CIPHER=AES"), null)).hasSize(1);
    }

    @Test
    void ignoraOpcionesAmbiguasUrlsNoSoportadasYErroresSinLanzarExcepciones() {
        for (String url :
                List.of(
                        "",
                        "jdbc:sqlite:prueba",
                        "jdbc:oracle:thin:@host:1521:db",
                        "jdbc:mysql:///base",
                        "jdbc:mysql://host/base?sslMode=REQUIRED&sslMode=DISABLED",
                        "jdbc:mysql://host/base?sslMode=desconocido",
                        "jdbc:mysql://host/base?useSSL=talvez",
                        "jdbc:mysql://host/base?sslMode=%xx",
                        "jdbc:postgresql://host/base?ssl=talvez",
                        "jdbc:sqlserver://host;password={x;encrypt=false}",
                        "jdbc:sqlserver://host;encrypt=false;encrypt=true")) {
            assertThat(jdbc.evaluar(sql(url), null)).isEmpty();
        }
        Traza error =
                new Traza(
                        "C",
                        List.of(
                                evento(
                                        0,
                                        TipoEvento.SQL,
                                        Map.of(
                                                "conexion",
                                                "jdbc:mysql://host/base",
                                                "error",
                                                "fallo"))));
        assertThat(jdbc.evaluar(error, null)).isEmpty();
        assertThat(
                        jdbc.evaluar(
                                new Traza(
                                        "C",
                                        List.of(evento(0, TipoEvento.SQL, Map.of("conexion", 42)))),
                                null))
                .isEmpty();
        assertThat(jdbc.evaluar(sql("jdbc:mysql://host/base"), contexto("OTRO", null))).isEmpty();
    }

    @Test
    void laAppDePracticaConPasswordFieldYH2EnMemoriaNoActiva() throws Exception {
        Traza practica = new CargadorTrazas().cargarEjemploUnico(TrazaEjemplo.C13_SQLI_LOGIN);
        assertThat(visible.evaluar(practica, null)).isEmpty();
        assertThat(jdbc.evaluar(practica, null)).isEmpty();
    }

    @Test
    void seRegistranEnLosMomentosDelCatalogoYNoConservanEstado() throws Exception {
        assertThat(ServiceLoader.load(Oraculo.class).stream())
                .extracting(ServiceLoader.Provider::type)
                .contains(OraculoContrasenaEnCampoVisible.class, OraculoJdbcSinCifrado.class);
        assertThat(visible.momento()).isEqualTo(Momento.CASO);
        assertThat(jdbc.momento()).isEqualTo(Momento.EVENTO);
        Traza traza = cargar("jdbc-sin-cifrado.jsonl");
        var sesion =
                new EvaluadorOraculos(List.of(jdbc)).iniciarCaso(contexto(traza.casoId(), null));
        assertThat(sesion.registrarEvento(traza.eventos().getFirst()).estado())
                .isEqualTo(EstadoVeredicto.NO_CUMPLE);
        for (Oraculo oraculo : List.of(visible, jdbc)) {
            assertThat(oraculo.evaluar(null, null)).isEmpty();
            assertThat(oraculo.evaluar(new Traza("C", List.of()), null)).isEmpty();
        }
    }

    private static Traza campo(String campo, String tipo, String valor, String codigo) {
        return new Traza(
                "C",
                List.of(
                        evento(
                                0,
                                TipoEvento.PASO_INICIO,
                                Map.of(
                                        "accion",
                                        "LLENAR",
                                        "objetivo",
                                        "nombre=" + campo,
                                        "valor",
                                        valor)),
                        evento(
                                1,
                                TipoEvento.PASO_FIN,
                                Map.of("codigo", codigo, "componente", tipo))));
    }

    private static Traza sql(String url) {
        return new Traza("C", List.of(evento(0, TipoEvento.SQL, Map.of("conexion", url))));
    }

    private static Evento evento(long secuencia, TipoEvento tipo, Map<String, Object> datos) {
        return new Evento(
                secuencia, Instant.EPOCH, tipo, new ContextoEvento("C", 1, null, false), datos);
    }

    private static Contexto contexto(String id, PoliticaAutenticacion politica) {
        Proyecto proyecto =
                new Proyecto(
                        "exposicion",
                        Path.of("app.jar"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        Caso caso = new Caso(id, null, null, null, null, null, null, null, politica, Map.of());
        return Contexto.deCaso(
                proyecto, new Suite("exposicion", null, null, null, List.of(caso), null), caso);
    }

    private static Traza cargar(String nombre) throws Exception {
        try (var lector =
                new InputStreamReader(
                        OraculosExposicionTest.class.getResourceAsStream(nombre),
                        StandardCharsets.UTF_8)) {
            return new CargadorTrazas().leer(lector, nombre).getFirst();
        }
    }
}
