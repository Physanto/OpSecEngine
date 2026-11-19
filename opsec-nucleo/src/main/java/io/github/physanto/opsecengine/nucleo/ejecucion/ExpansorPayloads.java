package io.github.physanto.opsecengine.nucleo.ejecucion;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.ComparacionLogin;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Materializa casos independientes para los valores explícitos de extensiones.payloads. */
final class ExpansorPayloads {

    private ExpansorPayloads() {}

    /** Valida toda la expansión antes de abrir sesiones; no modifica la suite recibida. */
    static Suite expandir(Suite suite) {
        if (suite.casos().stream().noneMatch(caso -> caso.extensiones().containsKey("payloads"))) {
            return suite;
        }
        Set<String> ids = new HashSet<>();
        suite.casos().forEach(caso -> ids.add(caso.id()));
        List<Caso> casos = new ArrayList<>();
        for (Caso caso : suite.casos()) {
            if (!caso.extensiones().containsKey("payloads")) {
                casos.add(caso);
                continue;
            }
            Object valor = caso.extensiones().get("payloads");
            if (!(valor instanceof List<?> payloads)
                    || payloads.isEmpty()
                    || payloads.stream().anyMatch(payload -> !(payload instanceof String))) {
                throw invalido(caso, "extensiones.payloads debe ser una lista no vacía de textos");
            }
            if (!caso.ataques().isEmpty()) {
                throw invalido(caso, "no combine ataques del catálogo con extensiones.payloads");
            }
            ComparacionLogin comparacion = suite.enumeracionUsuarios();
            if (comparacion != null
                    && (caso.id().equals(comparacion.casoUsuarioInexistente())
                            || caso.id().equals(comparacion.casoContrasenaIncorrecta()))) {
                throw invalido(
                        caso,
                        "un caso de enumeracionUsuarios no puede expandirse en varios payloads");
            }
            boolean marcador = false;
            for (Paso paso : caso.pasos()) {
                if (paso.valor() != null && paso.valor().contains("{payload}")) {
                    if (paso.accion() != Accion.LLENAR) {
                        throw invalido(caso, "{payload} solo se admite en valores de LLENAR");
                    }
                    marcador = true;
                }
            }
            if (!marcador) {
                throw invalido(caso, "falta {payload} en un paso LLENAR");
            }
            for (int i = 0; i < payloads.size(); i++) {
                String id = caso.id() + "-P" + (i + 1);
                if (!ids.add(id)) {
                    throw invalido(caso, "el id expandido " + id + " ya existe en la suite");
                }
                String payload = (String) payloads.get(i);
                List<Paso> pasos = new ArrayList<>();
                for (Paso paso : caso.pasos()) {
                    String texto =
                            paso.accion() == Accion.LLENAR && paso.valor() != null
                                    ? paso.valor().replace("{payload}", payload)
                                    : paso.valor();
                    pasos.add(
                            new Paso(
                                    paso.orden(),
                                    paso.accion(),
                                    paso.objetivo(),
                                    texto,
                                    paso.esperaMaxima(),
                                    paso.extensiones()));
                }
                Map<String, Object> extensiones = new LinkedHashMap<>(caso.extensiones());
                extensiones.remove("payloads");
                casos.add(
                        new Caso(
                                id,
                                caso.descripcion(),
                                caso.rol(),
                                pasos,
                                List.of(),
                                caso.oraculos(),
                                caso.canario(),
                                caso.validacion(),
                                caso.autenticacion(),
                                extensiones,
                                caso.fallos()));
            }
        }
        return new Suite(
                suite.nombre(),
                suite.version(),
                suite.enfoques(),
                suite.config(),
                casos,
                suite.enumeracionUsuarios(),
                suite.extensiones());
    }

    private static IllegalArgumentException invalido(Caso caso, String detalle) {
        return new IllegalArgumentException("Payloads del caso " + caso.id() + ": " + detalle);
    }
}
