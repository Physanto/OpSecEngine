package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pruebas de los localizadores que se calculan para un componente; necesitan pantalla. */
class LocalizadoresComponenteTest {

    private VentanaLoginPrueba login;

    @BeforeEach
    void abrir() throws Exception {
        login = VentanaLoginPrueba.abrir();
    }

    @AfterEach
    void cerrar() throws Exception {
        if (login != null) {
            login.cerrar();
        }
    }

    private List<String> estrategias(Component componente) throws Exception {
        return VentanaLoginPrueba.enEdt(() ->
                LocalizadoresComponente.para(componente).stream()
                        .map(CriterioDto::getEstrategia)
                        .collect(Collectors.toList()));
    }

    private List<CriterioDto> criterios(Component componente) throws Exception {
        return VentanaLoginPrueba.enEdt(() -> LocalizadoresComponente.para(componente));
    }

    @Test
    void unBotonConNombreSeLocalizaPorNombreTextoYRuta() throws Exception {
        assertThat(estrategias(login.ingresar))
                .containsExactly("NOMBRE_COMPONENTE", "TEXTO", "RUTA");
        assertThat(criterios(login.ingresar).get(0).getValor()).isEqualTo("botonIngresar");
        assertThat(criterios(login.ingresar).get(1).getValor()).isEqualTo("Iniciar sesion");
    }

    @Test
    void unBotonSinNombreSeLocalizaPorTextoYRuta() throws Exception {
        assertThat(estrategias(login.cancelar)).containsExactly("TEXTO", "RUTA");
    }

    @Test
    void unCampoSeLocalizaPorSuEtiquetaYNuncaPorSuContenido() throws Exception {
        login.usuario.setText("Admin");
        List<CriterioDto> usuario = criterios(login.usuario);
        List<CriterioDto> contrasena = criterios(login.contrasena);

        assertThat(usuario.get(0).getEstrategia()).isEqualTo("ETIQUETA");
        assertThat(usuario.get(0).getValor()).isEqualTo("Usuario");
        assertThat(usuario).extracting(CriterioDto::getEstrategia).doesNotContain("TEXTO");
        assertThat(contrasena.get(0).getEstrategia()).isEqualTo("ETIQUETA");
        assertThat(contrasena.get(0).getValor()).isEqualTo("Contraseña");
        assertThat(contrasena).extracting(CriterioDto::getValor).doesNotContain("12345");
    }

    @Test
    void unComboSeLocalizaPorSuEtiqueta() throws Exception {
        List<CriterioDto> rol = criterios(login.rol);

        assertThat(rol.get(0)).isEqualTo(new CriterioDto("ETIQUETA", "Rol"));
    }

    @Test
    void cadaCriterioEncuentraSoloElComponente() throws Exception {
        for (Component componente :
                List.of(
                        login.usuario,
                        login.contrasena,
                        login.rol,
                        login.recordar,
                        login.ingresar,
                        login.cancelar)) {
            for (CriterioDto criterio : criterios(componente)) {
                Component encontrado =
                        VentanaLoginPrueba.enEdt(() ->
                                BuscadorComponentes.buscar(
                                                new LocalizadorDto(
                                                        Collections.singletonList(criterio)))
                                        .componente());
                assertThat(encontrado)
                        .as("criterio %s de %s", criterio, componente.getClass().getSimpleName())
                        .isSameAs(componente);
            }
        }
    }

    @Test
    void elUltimoCriterioEsSiempreLaRuta() throws Exception {
        for (Component componente : List.of(login.usuario, login.rol, login.ingresar)) {
            List<CriterioDto> criterios = criterios(componente);
            assertThat(criterios.get(criterios.size() - 1).getEstrategia()).isEqualTo("RUTA");
        }
    }

    @Test
    void conTextoRepetidoElSegundoComponenteSoloSeLocalizaPorRuta() throws Exception {
        JButton primero = new JButton("Aceptar");
        JButton segundo = new JButton("Aceptar");
        JFrame[] otra = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = new JPanel(new FlowLayout());
            panel.add(primero);
            panel.add(segundo);
            otra[0] = new JFrame("Repetidos");
            otra[0].add(panel);
            otra[0].setSize(240, 100);
            otra[0].setLocation(520, 80);
            otra[0].setVisible(true);
        });
        try {
            // El buscador elige el primer botón con ese texto: el texto sirve para él, no para el
            // segundo.
            assertThat(estrategias(primero)).containsExactly("TEXTO", "RUTA");
            assertThat(estrategias(segundo)).containsExactly("RUTA");
        } finally {
            SwingUtilities.invokeAndWait(otra[0]::dispose);
        }
    }

    @Test
    void losDatosDelEventoLlevanVentanaClaseYLocalizador() throws Exception {
        java.util.Map<String, Object> datos =
                VentanaLoginPrueba.enEdt(() -> LocalizadoresComponente.datos(login.cancelar));

        assertThat(datos)
                .containsEntry("ventana", "Inicio de sesión")
                .containsEntry("componente", "javax.swing.JButton")
                .containsKey("localizador");
        assertThat((List<?>) datos.get("localizador")).isNotEmpty();
    }
}
