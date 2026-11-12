package io.github.physanto.opsecengine.oraculos;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.configuracion.Rol;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.soporte.CargadorTrazas;
import io.github.physanto.opsecengine.soporte.TrazaEjemplo;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de {@link OraculoContrasenaEnConsola} con trazas del inicio de sesión de la app de
 * práctica: con el fallo F6 activo (la contraseña sale en la consola) y corregido.
 */
class OraculoContrasenaEnConsolaTest {

    private static final String TRAZA_VULNERABLE = "contrasena-en-consola.jsonl";
    private static final String TRAZA_CORREGIDA = "contrasena-no-en-consola.jsonl";
    private static final Rol ADMIN = new Rol("admin", "Admin", "Admin#2026");

    private final OraculoContrasenaEnConsola oraculo = new OraculoContrasenaEnConsola();
    private final CargadorTrazas cargador = new CargadorTrazas();

    @Test
    void encuentraLaContrasenaDelRolEnLaConsola() throws Exception {
        Traza traza = trazaDePrueba(TRAZA_VULNERABLE);

        List<Hallazgo> hallazgos = oraculo.evaluar(traza, contexto(List.of(ADMIN)));

        assertThat(hallazgos)
                .singleElement()
                .satisfies(hallazgo -> {
                    assertThat(hallazgo.oraculoId()).isEqualTo("CONTRASENA-EN-CONSOLA");
                    assertThat(hallazgo.cwe()).isEqualTo("CWE-532");
                    assertThat(hallazgo.confianza()).isEqualTo(Confianza.CONFIRMADO);
                    assertThat(hallazgo.evidencia()).containsExactly(11L);
                    assertThat(hallazgo.explicacion())
                            .contains("la contraseña del rol admin")
                            .contains("la salida estándar")
                            .doesNotContain("Admin#2026");
                });
    }

    @Test
    void encuentraElValorEscritoEnElCampoDeContrasenaAunqueNoHayaRoles() throws Exception {
        Traza traza = trazaDePrueba(TRAZA_VULNERABLE);

        List<Hallazgo> hallazgos = oraculo.evaluar(traza, contexto(List.of()));

        assertThat(hallazgos)
                .singleElement()
                .satisfies(hallazgo ->
                        assertThat(hallazgo.explicacion())
                                .contains("campo de contraseña")
                                .contains("paso 4")
                                .doesNotContain("Admin#2026"));
    }

    @Test
    void noEncuentraNadaSiLaAppNoEscribeLaContrasena() throws Exception {
        Traza traza = trazaDePrueba(TRAZA_CORREGIDA);

        assertThat(oraculo.evaluar(traza, contexto(List.of(ADMIN)))).isEmpty();
    }

    @Test
    void noEncuentraNadaEnElCasoLimpio() throws Exception {
        Traza traza = cargador.cargarEjemploUnico(TrazaEjemplo.CASO_LIMPIO);

        assertThat(oraculo.evaluar(traza, contexto(List.of(ADMIN)))).isEmpty();
    }

    @Test
    void ignoraLasContrasenasMuyCortasYLasTrazasNulas() throws Exception {
        Traza traza = trazaDePrueba(TRAZA_VULNERABLE);
        Rol corta = new Rol("corta", "x", "Adm");

        assertThat(oraculo.evaluar(null, contexto(List.of(ADMIN)))).isEmpty();
        assertThat(oraculo.evaluar(traza, contexto(List.of(corta))))
                .as("el valor del campo de contraseña sigue contando")
                .hasSize(1);
    }

    @Test
    void seEvaluaPorEventoYEstaRegistrado() {
        assertThat(oraculo.momento()).isEqualTo(Momento.EVENTO);
        assertThat(oraculo.aplica(caso(), contexto(List.of()))).isTrue();
        assertThat(ServiceLoader.load(Oraculo.class).stream())
                .extracting(ServiceLoader.Provider::type)
                .contains(OraculoContrasenaEnConsola.class);
    }

    private Traza trazaDePrueba(String archivo) throws Exception {
        try (Reader lector =
                new InputStreamReader(
                        getClass().getResourceAsStream(archivo), StandardCharsets.UTF_8)) {
            return cargador.leer(lector, archivo).get(0);
        }
    }

    private static Contexto contexto(List<Rol> roles) {
        Proyecto proyecto =
                new Proyecto(
                        "clientes",
                        Path.of("app.jar"),
                        null,
                        null,
                        null,
                        roles,
                        null,
                        null,
                        null,
                        null);
        Caso caso = caso();
        return Contexto.deCaso(
                proyecto, new Suite("login", null, null, null, List.of(caso), null), caso);
    }

    private static Caso caso() {
        Paso paso = new Paso(1, Accion.PULSAR, Localizador.porTexto("Avanzar"), null, null, null);
        return new Caso("F6", null, null, List.of(paso), null, null, null);
    }
}
