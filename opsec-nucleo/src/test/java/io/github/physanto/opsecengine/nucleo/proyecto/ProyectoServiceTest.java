package io.github.physanto.opsecengine.nucleo.proyecto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.ComparacionLogin;
import io.github.physanto.opsecengine.dominio.caso.Criterio;
import io.github.physanto.opsecengine.dominio.caso.Enfoque;
import io.github.physanto.opsecengine.dominio.caso.EstrategiaLocalizacion;
import io.github.physanto.opsecengine.dominio.caso.IntentoLogin;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.ModoAccion;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.PoliticaAutenticacion;
import io.github.physanto.opsecengine.dominio.caso.Senal;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.OperacionesSensibles;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProyectoServiceTest {

    private static final Path EJEMPLOS = Path.of("..", "contratos", "ejemplos");
    private static final Path PROYECTO_EJEMPLO = EJEMPLOS.resolve("proyecto.yaml");

    private final ProyectoService servicio = new ProyectoService();

    @TempDir Path carpeta;

    @Test
    void cargaElProyectoDeEjemplo() throws Exception {
        Proyecto proyecto = servicio.cargarProyecto(PROYECTO_EJEMPLO);

        assertThat(proyecto.nombre()).isEqualTo("clientes");
        assertThat(proyecto.jar())
                .isEqualTo(
                        Path.of("..", "contratos", "MiApp", "dist", "MiApp.jar")
                                .toAbsolutePath()
                                .normalize());
        assertThat(proyecto.clasePrincipal()).isEqualTo("GUI_Local.Portada");
        assertThat(proyecto.datos().tipo()).isEqualTo("firebase-emulador");
        assertThat(proyecto.datos().parametros())
                .containsEntry("proyectoId", "demo-clientes")
                .containsEntry("puerto", "8080");
        assertThat(proyecto.buscarRol("admin")).get().extracting("usuario").isEqualTo("Admin");
        assertThat(proyecto.politica()).containsEntry("Eliminar Persona", List.of("admin"));
        assertThat(proyecto.sensibles().sql())
                .containsExactlyInAnyOrder("DELETE", "UPDATE", "INSERT");
    }

    @Test
    void expandeElLlenarDeVariosCamposEnUnPasoPorCampo() throws Exception {
        List<Paso> login = servicio.cargarProyecto(PROYECTO_EJEMPLO).pasosLogin();

        assertThat(login).extracting(Paso::orden).containsExactly(1, 2, 3, 4, 5);
        assertThat(login)
                .extracting(Paso::accion)
                .containsExactly(
                        Accion.PULSAR, Accion.PULSAR, Accion.LLENAR, Accion.LLENAR, Accion.PULSAR);
        assertThat(login.get(2).objetivo()).isEqualTo(simple("Usuario"));
        assertThat(login.get(2).valor()).isEqualTo("{usuario}");
        assertThat(login.get(3).objetivo()).isEqualTo(simple("Contraseña"));
        assertThat(login.get(3).valor()).isEqualTo("{contrasena}");
    }

    @Test
    void cargaLaSuiteDeEjemplo() throws Exception {
        Proyecto proyecto = servicio.cargarProyecto(PROYECTO_EJEMPLO);

        Suite suite =
                servicio.cargarSuite(EJEMPLOS.resolve("suites").resolve("login.yaml"), proyecto);

        assertThat(suite.nombre()).isEqualTo("login");
        assertThat(suite.version()).isEqualTo("1");
        assertThat(suite.enfoques())
                .containsExactlyInAnyOrder(Enfoque.SEGURIDAD, Enfoque.VALIDACION);
        assertThat(suite.config().modo()).isEqualTo(ModoAccion.EVENTOS);
        assertThat(suite.config().oculto()).isTrue();
        assertThat(suite.config().esperaMaximaPaso()).isEqualTo(Duration.ofSeconds(10));
        assertThat(suite.config().reintentos()).isEqualTo(1);
        assertThat(suite.config().semilla()).isEqualTo(42L);
        assertThat(suite.casos())
                .extracting(Caso::id)
                .containsExactly("C01", "C02", "C04", "C13", "C15");
    }

    @Test
    void traduceLocalizadoresObjetoYCapturasSinObjetivo() throws Exception {
        Suite suite = suiteDeEjemplo();
        Caso c01 = suite.casos().get(0);

        assertThat(c01.pasos()).hasSize(6);
        assertThat(c01.pasos().get(4).objetivo().criterios())
                .containsExactly(
                        new Criterio(EstrategiaLocalizacion.NOMBRE_COMPONENTE, "btnIniciarSesion"),
                        new Criterio(EstrategiaLocalizacion.TEXTO, "Iniciar Sesion"));
        assertThat(c01.pasos().get(5).accion()).isEqualTo(Accion.CAPTURAR);
        assertThat(c01.pasos().get(5).objetivo()).isNull();
        assertThat(c01.oraculos()).containsExactly("ENUMERACION-USUARIOS");
    }

    @Test
    void conservaLaEsperaPropiaDeUnPasoYLosAtaques() throws Exception {
        Caso c13 = suiteDeEjemplo().casos().get(3);

        assertThat(c13.pasos().get(4).esperaMaxima()).isEqualTo(Duration.ofSeconds(15));
        assertThat(c13.pasos().get(0).esperaMaxima()).isNull();
        assertThat(c13.ataques()).containsExactly("SQLI-TAUTOLOGIA-CANARIO");
        assertThat(c13.pasos().get(2).valor()).isEqualTo("{payload}");
    }

    @Test
    void traduceEsperarYCerrarVentanaConLocalizador() throws Exception {
        List<Paso> pasos = suiteDeEjemplo().casos().get(4).pasos();

        assertThat(pasos.get(5).accion()).isEqualTo(Accion.CERRAR_VENTANA);
        assertThat(pasos.get(5).objetivo())
                .isEqualTo(Localizador.porClaseVentana("javax.swing.JDialog"));
        Paso esperar = pasos.get(pasos.size() - 2);
        assertThat(esperar.accion()).isEqualTo(Accion.ESPERAR);
        assertThat(esperar.objetivo())
                .isEqualTo(Localizador.porClaseVentana("GUI_Admin.MenuAdministrador"));
    }

    @Test
    void cargaLasSuitesDeUnaCarpeta() throws Exception {
        Proyecto proyecto = servicio.cargarProyecto(PROYECTO_EJEMPLO);

        List<Suite> suites = servicio.cargarSuites(EJEMPLOS.resolve("suites"), proyecto);

        assertThat(suites).extracting(Suite::nombre).containsExactly("login");
        assertThat(servicio.cargarSuites(carpeta.resolve("no-existe"), proyecto)).isEmpty();
    }

    @Test
    void resuelveLaCarpetaDeJavaDeLaAppRespectoAlProyecto() throws Exception {
        Path archivo = escribir("proyecto.yaml", "app:\n  jar: dist/app.jar\n  java: jdks/jdk8\n");

        Proyecto proyecto = servicio.cargarProyecto(archivo);

        assertThat(proyecto.javaApp()).isEqualTo(carpeta.resolve("jdks").resolve("jdk8"));
    }

    @Test
    void usaValoresPorDefectoYElNombreDeLaCarpeta() throws Exception {
        Path archivo = escribir("proyecto.yaml", "app:\n  jar: dist/app.jar\n");
        Path suite =
                escribir(
                        "minima.yaml",
                        "suite: minima\ncasos:\n  - id: X\n    pasos:\n      - esperar: 500\n");

        Proyecto proyecto = servicio.cargarProyecto(archivo);
        Suite cargada = servicio.cargarSuite(suite, proyecto);

        assertThat(proyecto.nombre()).isEqualTo(carpeta.getFileName().toString());
        assertThat(proyecto.jar()).isEqualTo(carpeta.resolve("dist").resolve("app.jar"));
        assertThat(proyecto.clasePrincipal()).isNull();
        assertThat(proyecto.javaApp()).isNull();
        assertThat(proyecto.sensibles()).isEqualTo(OperacionesSensibles.porDefecto());
        assertThat(cargada.config().esperaMaximaPaso()).isEqualTo(Duration.ofSeconds(10));
        Paso esperar = cargada.casos().get(0).pasos().get(0);
        assertThat(esperar.accion()).isEqualTo(Accion.ESPERAR);
        assertThat(esperar.objetivo()).isNull();
        assertThat(esperar.valor()).isEqualTo("500");
    }

    @Test
    void conservaLasExtensiones() throws Exception {
        Path archivo =
                escribir(
                        "proyecto.yaml",
                        "app: {jar: app.jar}\nextensiones:\n  equipo: azul\n  nivel: 2\n");

        Proyecto proyecto = servicio.cargarProyecto(archivo);

        assertThat(proyecto.extensiones()).isEqualTo(Map.of("equipo", "azul", "nivel", 2));
    }

    @Test
    void informaLaRutaDeUnCampoObligatorioQueFalta() throws Exception {
        Path archivo = escribir("proyecto.yaml", "nombre: x\napp: {principal: a.B}\n");

        assertThat(errores(() -> servicio.cargarProyecto(archivo)))
                .containsExactly(
                        new ErrorConfiguracion(
                                "/app", "no se encontró la propiedad requerida 'jar'"));
    }

    @Test
    void rechazaUnPasoConDosAcciones() throws Exception {
        Proyecto proyecto = proyectoMinimo();
        Path suite =
                escribir(
                        "suite.yaml",
                        "suite: s\ncasos:\n  - id: A\n    pasos:\n      - {pulsar: X, marcar: Y}\n");

        assertThat(errores(() -> servicio.cargarSuite(suite, proyecto)))
                .extracting(ErrorConfiguracion::ruta, ErrorConfiguracion::mensaje)
                .contains(
                        tuple(
                                "/casos/0/pasos/0",
                                "un paso lleva exactamente una acción: pulsar, llenar, seleccionar,"
                                        + " marcar, esperar, ir_a, cerrar_ventana o capturar"));
    }

    @Test
    void rechazaRolesInexistentesEIdsRepetidos() throws Exception {
        Proyecto proyecto = proyectoMinimo();
        Path suite =
                escribir(
                        "suite.yaml",
                        """
                        suite: s
                        casos:
                          - id: A
                            rol: admin
                            pasos: [{pulsar: X}]
                          - id: A
                            pasos: [{pulsar: Y}]
                        """);

        assertThat(errores(() -> servicio.cargarSuite(suite, proyecto)))
                .containsExactly(
                        new ErrorConfiguracion(
                                "/casos/0/rol",
                                "el rol admin no está definido en los roles del proyecto"),
                        new ErrorConfiguracion("/casos/1/id", "el id A ya se usa en /casos/0/id"));
    }

    @Test
    void rechazaUnaPoliticaConRolesInexistentes() throws Exception {
        Path archivo =
                escribir(
                        "proyecto.yaml",
                        """
                        app: {jar: app.jar}
                        roles:
                          admin: {usuario: Admin, contrasena: x}
                        politica:
                          Eliminar Persona: [admin, soporte]
                        """);

        assertThat(errores(() -> servicio.cargarProyecto(archivo)))
                .containsExactly(
                        new ErrorConfiguracion(
                                "/politica/Eliminar Persona/1",
                                "el rol soporte no está definido en los roles del proyecto"));
    }

    @Test
    void cargaElCanarioLasPoliticasYLaComparacionDeLaSuiteDeEjemplo() throws Exception {
        Suite suite = suiteDeEjemplo();

        assertThat(suite.enumeracionUsuarios()).isEqualTo(new ComparacionLogin("C01", 5, "C02", 5));
        assertThat(suite.casos().get(3).canario()).isEqualTo("K7Q2");
        PoliticaAutenticacion autenticacion = suite.casos().get(4).autenticacion();
        assertThat(autenticacion.fallosPermitidos()).isEqualTo(3);
        assertThat(autenticacion.retardoMinimoMs()).isEqualTo(30_000L);
        assertThat(autenticacion.intentos())
                .hasSize(6)
                .first()
                .isEqualTo(new IntentoLogin(3, 4, 5, "Admin", "mala1", false));
        assertThat(autenticacion.intentos().get(5).pasoEnvio()).isEqualTo(25);
        assertThat(autenticacion.rechazo())
                .containsExactly(new Senal(TipoEvento.DIALOGO, Map.of("tipoMensaje", "ERROR")));
        assertThat(suite.casos().get(0).autenticacion()).isNull();
    }

    @Test
    void cargaLaPoliticaDeValidacion() throws Exception {
        Path suite =
                escribir(
                        "suite.yaml",
                        """
                        suite: s
                        casos:
                          - id: V1
                            pasos:
                              - llenar: {Edad: "-5"}
                              - pulsar: Guardar
                            validacion:
                              pasoEntrada: 1
                              pasoEnvio: 2
                              valorInvalido: "-5"
                              aceptacion: [{tipo: DIALOGO, campos: {tipoMensaje: INFORMACION}}]
                              rechazo: [{tipo: DIALOGO, campos: {tipoMensaje: ERROR}}]
                        """);

        Caso caso = servicio.cargarSuite(suite, proyectoMinimo()).casos().get(0);

        assertThat(caso.validacion().valorInvalido()).isEqualTo("-5");
        assertThat(caso.validacion().aceptacion())
                .containsExactly(
                        new Senal(TipoEvento.DIALOGO, Map.of("tipoMensaje", "INFORMACION")));
    }

    @Test
    void rechazaPoliticasQueApuntanAPasosQueNoExistenOTienenOtraAccion() throws Exception {
        Path suite =
                escribir(
                        "suite.yaml",
                        """
                        suite: s
                        enumeracionUsuarios:
                          casoUsuarioInexistente: V1
                          pasoUsuarioInexistente: 1
                          casoContrasenaIncorrecta: V2
                          pasoContrasenaIncorrecta: 1
                        casos:
                          - id: V1
                            pasos:
                              - llenar: {Edad: "-5"}
                              - pulsar: Guardar
                            validacion:
                              pasoEntrada: 1
                              pasoEnvio: 3
                              valorInvalido: "-5"
                              aceptacion: [{tipo: SESION, campos: {activa: true}}]
                              rechazo: [{tipo: DIALOGO, campos: {tipoMensaje: ERROR}}]
                        """);

        Proyecto proyecto = proyectoMinimo();
        assertThat(errores(() -> servicio.cargarSuite(suite, proyecto)))
                .containsExactly(
                        new ErrorConfiguracion(
                                "/casos/0/validacion/pasoEnvio",
                                "el paso 3 no existe; el caso tiene 2 pasos"));

        Path conCasos =
                escribir(
                        "suite2.yaml",
                        """
                        suite: s
                        enumeracionUsuarios:
                          casoUsuarioInexistente: V1
                          pasoUsuarioInexistente: 1
                          casoContrasenaIncorrecta: V2
                          pasoContrasenaIncorrecta: 1
                        casos:
                          - id: V1
                            pasos: [{llenar: {Usuario: x}}, {pulsar: Entrar}]
                        """);

        assertThat(errores(() -> servicio.cargarSuite(conCasos, proyecto)))
                .containsExactly(
                        new ErrorConfiguracion(
                                "/enumeracionUsuarios/casoUsuarioInexistente",
                                "el paso 1 es LLENAR y debería ser PULSAR"),
                        new ErrorConfiguracion(
                                "/enumeracionUsuarios/casoContrasenaIncorrecta",
                                "el caso V2 no existe"));
    }

    @Test
    void informaClavesRepetidasConSuLinea() throws Exception {
        Path archivo = escribir("proyecto.yaml", "app: {jar: a.jar}\nnombre: a\nnombre: b\n");

        assertThat(errores(() -> servicio.cargarProyecto(archivo)))
                .singleElement()
                .extracting(ErrorConfiguracion::mensaje, STRING)
                .startsWith("línea 3")
                .endsWith("la clave 'nombre' está repetida");
    }

    @Test
    void rechazaUnArchivoVacio() throws Exception {
        Path archivo = escribir("proyecto.yaml", "# sin contenido\n");

        assertThatThrownBy(() -> servicio.cargarProyecto(archivo))
                .isInstanceOf(ExcepcionConfiguracion.class)
                .hasMessageContaining("el archivo está vacío");
    }

    @Test
    void elMensajeDeLaExcepcionListaTodosLosErrores() throws Exception {
        Path archivo = escribir("proyecto.yaml", "app: {}\notro: 1\n");

        assertThatThrownBy(() -> servicio.cargarProyecto(archivo))
                .isInstanceOf(ExcepcionConfiguracion.class)
                .hasMessageContaining("tiene 2 errores")
                .hasMessageContaining("/app: no se encontró la propiedad requerida 'jar'")
                .hasMessageContaining("/: la propiedad 'otro' no está definida");
    }

    private Suite suiteDeEjemplo() throws Exception {
        Proyecto proyecto = servicio.cargarProyecto(PROYECTO_EJEMPLO);
        return servicio.cargarSuite(EJEMPLOS.resolve("suites").resolve("login.yaml"), proyecto);
    }

    private Proyecto proyectoMinimo() throws Exception {
        return servicio.cargarProyecto(escribir("proyecto.yaml", "app: {jar: app.jar}\n"));
    }

    private Path escribir(String nombre, String contenido) throws IOException {
        return Files.writeString(carpeta.resolve(nombre), contenido, StandardCharsets.UTF_8);
    }

    private static Localizador simple(String texto) {
        return new Localizador(
                List.of(
                        new Criterio(EstrategiaLocalizacion.ETIQUETA, texto),
                        new Criterio(EstrategiaLocalizacion.TEXTO, texto)));
    }

    private static List<ErrorConfiguracion> errores(Carga carga) {
        try {
            carga.ejecutar();
        } catch (ExcepcionConfiguracion e) {
            return e.errores();
        } catch (IOException e) {
            throw new AssertionError("Se esperaba un error de configuración", e);
        }
        throw new AssertionError("Se esperaba un error de configuración");
    }

    /** Carga que puede fallar con un error de configuración. */
    @FunctionalInterface
    private interface Carga {
        Object ejecutar() throws IOException, ExcepcionConfiguracion;
    }
}
