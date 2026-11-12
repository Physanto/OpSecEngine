package io.github.physanto.opsecengine.nucleo.evaluacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.EvaluacionCaso;
import io.github.physanto.opsecengine.dominio.extension.Evaluador;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.extension.ProveedorEvaluacion;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.ContextoEvento;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.nucleo.extensiones.ProveedorEvaluacionDePrueba;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifica la delegación y la selección del proveedor, sin clases concretas de oráculos. */
public class EvaluacionDelegadaTest {
    @TempDir Path temporal;
    private static final String SERVICIO =
            "META-INF/services/" + ProveedorEvaluacion.class.getName();

    @Test
    void reproduceLaTrazaEnOrdenYCierraLaSesionConElMotivoOriginal() {
        Sonda sonda = new Sonda();
        EvaluacionService servicio = new EvaluacionService(sonda);
        Contexto contexto = contexto();
        Evento uno = evento(1);
        Evento dos = evento(2);
        Traza traza = new Traza("C1", List.of(dos, uno));

        Veredicto resultado = servicio.evaluarCaso(traza, contexto, "interrumpido");

        assertThat(resultado).isSameAs(sonda.resultado);
        assertThat(sonda.contexto).isSameAs(contexto);
        assertThat(sonda.eventos).containsExactly(uno, dos);
        assertThat(sonda.motivo).isEqualTo("interrumpido");
        assertThat(sonda.cierres).isEqualTo(1);
    }

    @Test
    void conservaElVeredictoCompletoDeSuiteYLaFachadaDeHallazgos() {
        Sonda sonda = new Sonda();
        EvaluacionService servicio = new EvaluacionService(sonda);
        Contexto c = contexto();
        Contexto suite = Contexto.deSuite(c.proyecto(), c.suite(), List.of());
        assertThat(servicio.evaluarResultadoSuite(suite)).isSameAs(sonda.resultado);
        assertThat(servicio.evaluarSuite(suite)).isEmpty();
        assertThat(sonda.contexto).isSameAs(suite);
    }

    @Test
    void rechazaUnaTrazaAjenaAntesDeInvocarElEvaluador() {
        Sonda sonda = new Sonda();
        assertThatThrownBy(() ->
                        new EvaluacionService(sonda)
                                .evaluarCaso(new Traza("OTRO", List.of()), contexto(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(sonda.contexto).isNull();
    }

    @Test
    void usaElCargadorDelRegistroAunqueCambieElContextoDelHilo() throws Exception {
        try (URLClassLoader cargador = cargador(ProveedorEvaluacionDePrueba.class.getName());
                URLClassLoader vacio = cargador("")) {
            RegistroExtensiones registro = RegistroExtensiones.descubrir(cargador);
            ClassLoader original = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(vacio);
                assertThat(new EvaluacionService(registro).iniciarCaso(contexto())).isNotNull();
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
    }

    @Test
    void rechazaLaAusenciaOLaAmbiguedadDeProveedores() throws Exception {
        for (String proveedores :
                List.of(
                        "",
                        ProveedorEvaluacionDePrueba.class.getName()
                                + "\n"
                                + ProveedorNulo.class.getName())) {
            try (URLClassLoader cargador = cargador(proveedores)) {
                RegistroExtensiones registro = RegistroExtensiones.descubrir(cargador);
                assertThatThrownBy(() -> new EvaluacionService(registro))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("exactamente un ProveedorEvaluacion");
            }
        }
    }

    @Test
    void rechazaUnProveedorQueDevuelveNull() throws Exception {
        try (URLClassLoader cargador = cargador(ProveedorNulo.class.getName())) {
            assertThatThrownBy(() -> new EvaluacionService(RegistroExtensiones.descubrir(cargador)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("devolvió null");
        }
    }

    private URLClassLoader cargador(String proveedores) throws IOException {
        Path archivo = Files.createTempFile(temporal, "servicio", ".txt");
        Files.writeString(archivo, proveedores, StandardCharsets.UTF_8);
        URL recurso = archivo.toUri().toURL();
        return new URLClassLoader(new URL[0], getClass().getClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String nombre) throws IOException {
                return nombre.equals(SERVICIO)
                        ? Collections.enumeration(List.of(recurso))
                        : super.getResources(nombre);
            }
        };
    }

    /** Proveedor inválido que permite verificar el rechazo de respuestas nulas. */
    public static final class ProveedorNulo implements ProveedorEvaluacion {
        /** Crea el proveedor de prueba para ServiceLoader. */
        public ProveedorNulo() {}

        @Override
        public Evaluador crear(Collection<? extends Oraculo> oraculos, Path carpeta) {
            return null;
        }
    }

    /** Registra las llamadas sin implementar las reglas del evaluador real. */
    private static final class Sonda implements Evaluador {
        private final Veredicto resultado = Veredicto.noConcluyente("falta evidencia");
        private final List<Evento> eventos = new ArrayList<>();
        private Contexto contexto;
        private String motivo;
        private int cierres;

        @Override
        public EvaluacionCaso iniciarCaso(Contexto recibido) {
            contexto = recibido;
            return new EvaluacionCaso() {
                @Override
                public Veredicto registrarEvento(Evento evento) {
                    eventos.add(evento);
                    return resultado;
                }

                @Override
                public Veredicto terminar(String recibido) {
                    motivo = recibido;
                    cierres++;
                    return resultado;
                }

                @Override
                public Traza traza() {
                    return new Traza(contexto.caso().id(), eventos);
                }
            };
        }

        @Override
        public Veredicto evaluarSuite(Contexto recibido) {
            contexto = recibido;
            return resultado;
        }
    }

    private static Contexto contexto() {
        Proyecto proyecto =
                new Proyecto(
                        "p", Path.of("app.jar"), null, null, null, null, null, null, null, null);
        Caso caso = new Caso("C1", null, null, null, null, null, null);
        return Contexto.deCaso(
                proyecto, new Suite("s", null, null, null, List.of(caso), null), caso);
    }

    private static Evento evento(long secuencia) {
        return new Evento(
                secuencia,
                Instant.EPOCH,
                TipoEvento.CONSOLA,
                new ContextoEvento("C1", 0, null, false),
                null);
    }
}
