package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.PoliticaAutenticacion;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Detecta escrituras de contraseña en componentes de texto visibles conocidos (CWE-549). Sin estado
 * y seguro entre hilos; no inspecciona la GUI ni el carácter de eco de JPasswordField.
 */
public final class OraculoContrasenaEnCampoVisible implements Oraculo {
    private static final Set<String> VISIBLES =
            Set.of(
                    "JTextField",
                    "javax.swing.JTextField",
                    "JTextArea",
                    "javax.swing.JTextArea",
                    "JTextPane",
                    "javax.swing.JTextPane",
                    "JEditorPane",
                    "javax.swing.JEditorPane");

    /** Crea el oráculo para su descubrimiento mediante ServiceLoader. */
    public OraculoContrasenaEnCampoVisible() {}

    /**
     * Devuelve el id del catálogo.
     *
     * @return CONTRASENA-EN-CAMPO-VISIBLE
     */
    @Override
    public String id() {
        return "CONTRASENA-EN-CAMPO-VISIBLE";
    }

    /**
     * Devuelve el momento de correlación entre escritura y resultado.
     *
     * @return CASO
     */
    @Override
    public Momento momento() {
        return Momento.CASO;
    }

    /**
     * Indica que la clasificación se realiza sobre la traza.
     *
     * @param caso caso evaluado; puede ser null
     * @param contexto contexto de evaluación; puede ser null
     * @return true
     */
    @Override
    public boolean aplica(Caso caso, Contexto contexto) {
        return true;
    }

    /**
     * Correlaciona un LLENAR sensible con su final OK y un componente de texto visible conocido.
     *
     * @param traza traza del caso; puede ser null
     * @param contexto contexto opcional con la política de autenticación de la #44
     * @return hallazgos probables sin copiar valores, localizadores ni credenciales
     */
    @Override
    public List<Hallazgo> evaluar(Traza traza, Contexto contexto) {
        if (traza == null
                || (contexto != null
                        && contexto.caso() != null
                        && !contexto.caso().id().equals(traza.casoId()))) {
            return List.of();
        }
        PoliticaAutenticacion politica = RastreadorIntentosLogin.politica(contexto);
        Evento pendiente = null;
        List<Hallazgo> resultado = new ArrayList<>();
        for (Evento evento : traza.eventos()) {
            if (!traza.casoId().equals(evento.contexto().casoId())) {
                continue;
            }
            if (evento.tipo() == TipoEvento.PASO_INICIO) {
                pendiente = null;
                boolean sensible =
                        OraculoContrasenaEnConsola.esCampoDeContrasena(
                                        evento.texto("objetivo").orElse(""))
                                || (politica != null
                                        && politica.intentos().stream()
                                                .anyMatch(i ->
                                                        i.pasoContrasena()
                                                                        == evento.contexto()
                                                                                .numeroPaso()
                                                                && evento.texto("valor")
                                                                        .filter(
                                                                                i.contrasena()
                                                                                        ::equals)
                                                                        .isPresent()));
                if (sensible
                        && evento.texto("accion").orElse("").equals("LLENAR")
                        && evento.texto("valor").filter(v -> !v.isEmpty()).isPresent()) {
                    pendiente = evento;
                }
            } else if (evento.tipo() == TipoEvento.PASO_FIN
                    && pendiente != null
                    && evento.contexto().numeroPaso() == pendiente.contexto().numeroPaso()) {
                if (evento.texto("codigo").orElse("").equals("OK")
                        && VISIBLES.contains(evento.texto("componente").orElse(""))) {
                    resultado.add(
                            new Hallazgo(
                                    id(),
                                    "CWE-549",
                                    Confianza.PROBABLE,
                                    "Contraseña escrita en un campo visible",
                                    "Un LLENAR identificado como contraseña terminó correctamente sobre un componente de texto visible conocido. Use un componente que oculte la contraseña; confirme la clasificación del campo si se dedujo de su nombre.",
                                    List.of(pendiente.secuencia(), evento.secuencia())));
                }
                pendiente = null;
            }
        }
        return List.copyOf(resultado);
    }
}
