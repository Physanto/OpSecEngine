package io.github.physanto.opsecengine.nucleo.extensiones;

import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.EvaluacionCaso;
import io.github.physanto.opsecengine.dominio.extension.Evaluador;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.extension.ProveedorEvaluacion;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Proveedor local de pruebas: permite probar la fachada y la ejecución sin depender de oráculos. La
 * implementación real y su cargador YAML se prueban en su propio módulo y en la CLI.
 */
public final class ProveedorEvaluacionDePrueba implements ProveedorEvaluacion {
    /** Crea el doble para ServiceLoader. */
    public ProveedorEvaluacionDePrueba() {}

    @Override
    public Evaluador crear(Collection<? extends Oraculo> oraculos, Path carpetaReglas) {
        List<Oraculo> copia = List.copyOf(oraculos);
        return new Evaluador() {
            @Override
            public EvaluacionCaso iniciarCaso(Contexto contexto) {
                return new Sesion(copia, contexto);
            }

            @Override
            public Veredicto evaluarSuite(Contexto contexto) {
                Sesion sesion = new Sesion(copia, contexto);
                sesion.aplicar(Momento.SUITE);
                return sesion.resultado();
            }
        };
    }

    /** Estado del doble, aislado de las demás evaluaciones. */
    private static final class Sesion implements EvaluacionCaso {
        private final List<Oraculo> oraculos;
        private final Contexto contexto;
        private final List<Evento> eventos = new ArrayList<>();
        private final Set<Hallazgo> hallazgos = new LinkedHashSet<>();
        private final Set<String> persistentes = new LinkedHashSet<>();
        private final Map<String, String> provisionales = new LinkedHashMap<>();

        Sesion(List<Oraculo> oraculos, Contexto contexto) {
            this.oraculos = oraculos;
            this.contexto = contexto;
            if (contexto.caso() != null) {
                for (String id : contexto.caso().oraculos()) {
                    if (oraculos.stream().noneMatch(o -> o.id().equals(id))) {
                        persistentes.add(
                                "El caso pide el oráculo " + id + ", que no está registrado");
                    }
                }
            }
        }

        @Override
        public Veredicto registrarEvento(Evento evento) {
            eventos.add(evento);
            aplicar(Momento.EVENTO);
            return resultado();
        }

        @Override
        public Veredicto terminar(String motivo) {
            if (motivo != null) persistentes.add(motivo);
            aplicar(Momento.CASO);
            return resultado();
        }

        @Override
        public Traza traza() {
            return new Traza(contexto.caso().id(), eventos);
        }

        void aplicar(Momento momento) {
            for (Oraculo oraculo : oraculos) {
                if (oraculo.momento() != momento) continue;
                try {
                    if ((contexto.caso() != null
                                    && contexto.caso().oraculos().contains(oraculo.id()))
                            || oraculo.aplica(contexto.caso(), contexto)) {
                        Veredicto parcial =
                                oraculo.evaluarResultado(
                                        momento == Momento.SUITE ? null : traza(), contexto);
                        hallazgos.addAll(parcial.hallazgos());
                        if (parcial.motivo() == null) provisionales.remove(oraculo.id());
                        else
                            provisionales.put(oraculo.id(), oraculo.id() + ": " + parcial.motivo());
                    }
                } catch (RuntimeException e) {
                    persistentes.add("Falló el oráculo " + oraculo.id() + ": " + e);
                }
            }
        }

        Veredicto resultado() {
            Set<String> motivos = new LinkedHashSet<>(persistentes);
            motivos.addAll(provisionales.values());
            EstadoVeredicto estado =
                    hallazgos.stream().anyMatch(h -> h.confianza() != Confianza.INFO)
                            ? EstadoVeredicto.NO_CUMPLE
                            : motivos.isEmpty()
                                    ? EstadoVeredicto.CUMPLE
                                    : EstadoVeredicto.NO_CONCLUYENTE;
            return new Veredicto(
                    estado,
                    List.copyOf(hallazgos),
                    motivos.isEmpty() ? null : String.join("; ", motivos));
        }
    }
}
