package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Enfoque;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.extension.GeneradorCasos;
import io.github.physanto.opsecengine.dominio.mapa.MapaApp;
import io.github.physanto.opsecengine.dominio.mapa.Objetivo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Genera variantes de validación cambiando un solo campo del caso base por cada payload compatible.
 *
 * <p>El mapa filtra las ventanas; la navegación y los campos los aporta el adaptador temporal. No
 * descubre formularios ni abre aplicaciones. Ordena por id de caso base, orden de campo e id de
 * payload. Los ids y canarios dependen de esas identidades y del proyecto, nunca del reloj. No
 * comparte estado mutable entre llamadas y es seguro entre hilos.
 */
public final class GeneradorValidacion implements GeneradorCasos {
    /** Clave temporal de Proyecto.extensiones para proporcionar una instancia del adaptador. */
    public static final String EXTENSION = "validacion";

    private final AdaptadorValidacion adaptador;

    /**
     * Crea el generador para ServiceLoader; obtiene el adaptador del proyecto en cada llamada. Sin
     * una instancia de AdaptadorValidacion en la extensión validacion no genera casos.
     */
    public GeneradorValidacion() {
        adaptador = null;
    }

    /**
     * Crea un generador con datos explícitos para una integración local o para pruebas.
     *
     * @param adaptador formularios y biblioteca proyectada; no puede ser null
     * @throws NullPointerException si el adaptador es null
     */
    public GeneradorValidacion(AdaptadorValidacion adaptador) {
        this.adaptador = Objects.requireNonNull(adaptador, "El adaptador no puede ser null");
    }

    /**
     * Devuelve el enfoque de los casos producidos.
     *
     * @return VALIDACION
     */
    @Override
    public Enfoque enfoque() {
        return Enfoque.VALIDACION;
    }

    /**
     * Genera casos de ventanas presentes en el mapa y seleccionadas por el objetivo.
     *
     * <p>Un objetivo de control selecciona solo ese campo o todos los campos si es el botón de
     * envío. Omite formularios ausentes y roles que el proyecto no declara. Cada caso conserva el
     * rol y la navegación de su base, tiene los payloads ya materializados y no pide expansión de
     * ataques al núcleo. El canario queda en {@link Caso#canario()} y el origen de la variante
     * (caso base, ventana, campo y payload) en {@code extensiones.validacion}.
     *
     * @param mapa mapa de ventanas, posiblemente incompleto
     * @param objetivo toda la app, una ventana, un campo o un botón de envío
     * @param proyecto proyecto con los roles y, si se usa ServiceLoader, el adaptador temporal
     * @return casos inmutables con ids únicos; lista vacía si faltan datos para generarlos
     * @throws NullPointerException si un argumento es null
     */
    @Override
    public List<Caso> generar(MapaApp mapa, Objetivo objetivo, Proyecto proyecto) {
        Objects.requireNonNull(mapa, "El mapa no puede ser null");
        Objects.requireNonNull(objetivo, "El objetivo no puede ser null");
        Objects.requireNonNull(proyecto, "El proyecto no puede ser null");
        AdaptadorValidacion datos = adaptador;
        if (datos == null
                && proyecto.extensiones().get(EXTENSION) instanceof AdaptadorValidacion valor) {
            datos = valor;
        }
        if (datos == null) {
            return List.of();
        }
        Set<String> ventanas =
                mapa.ventanas().stream().map(v -> v.id()).collect(Collectors.toSet());
        List<Caso> casos = new ArrayList<>();
        List<AdaptadorValidacion.Payload> payloads =
                datos.payloads().stream()
                        .sorted(Comparator.comparing(AdaptadorValidacion.Payload::id))
                        .toList();
        for (var formulario :
                datos.formularios().stream()
                        .sorted(Comparator.comparing(f -> f.base().id()))
                        .toList()) {
            if (!ventanas.contains(formulario.ventanaId())
                    || (objetivo.ventanaId() != null
                            && !objetivo.ventanaId().equals(formulario.ventanaId()))
                    || (formulario.base().rol() != null
                            && proyecto.buscarRol(formulario.base().rol()).isEmpty())) {
                continue;
            }
            Paso envio = paso(formulario, formulario.pasoEnvio());
            for (var campo :
                    formulario.campos().stream()
                            .sorted(Comparator.comparingInt(AdaptadorValidacion.Campo::ordenPaso))
                            .toList()) {
                Paso original = paso(formulario, campo.ordenPaso());
                if (objetivo.control() != null
                        && !objetivo.control().equals(original.objetivo())
                        && !objetivo.control().equals(envio.objetivo())) {
                    continue;
                }
                for (var payload : payloads) {
                    if (payload.tiposCampo().contains("CUALQUIERA")
                            || payload.tiposCampo().contains(campo.tipo())) {
                        casos.add(variante(proyecto, formulario, campo, payload));
                    }
                }
            }
        }
        return List.copyOf(casos);
    }

    private static Paso paso(AdaptadorValidacion.Formulario formulario, int orden) {
        return formulario.base().pasos().stream()
                .filter(p -> p.orden() == orden)
                .findFirst()
                .orElseThrow();
    }

    private static Caso variante(
            Proyecto proyecto,
            AdaptadorValidacion.Formulario formulario,
            AdaptadorValidacion.Campo campo,
            AdaptadorValidacion.Payload payload) {
        String huella =
                huella(
                        List.of(
                                proyecto.nombre(),
                                formulario.ventanaId(),
                                formulario.base().id(),
                                Integer.toString(campo.ordenPaso()),
                                payload.id()));
        String canario = "OPSEC" + huella;
        String valor =
                payload.usaCanario()
                        ? payload.texto().replace("{canario}", canario)
                        : payload.texto();
        List<Paso> pasos = new ArrayList<>();
        for (Paso paso : formulario.base().pasos()) {
            pasos.add(
                    new Paso(
                            pasos.size() + 1,
                            paso.accion(),
                            paso.objetivo(),
                            paso.orden() == campo.ordenPaso() ? valor : paso.valor(),
                            paso.esperaMaxima(),
                            paso.extensiones()));
        }
        Set<String> oraculos = new LinkedHashSet<>(formulario.base().oraculos());
        oraculos.addAll(payload.oraculos());
        Map<String, Object> origen = new LinkedHashMap<>();
        origen.put("casoBase", formulario.base().id());
        origen.put("ventanaId", formulario.ventanaId());
        origen.put("ordenCampoBase", campo.ordenPaso());
        origen.put("payloadId", payload.id());
        Map<String, Object> extensiones = new LinkedHashMap<>(formulario.base().extensiones());
        extensiones.put(EXTENSION, Map.copyOf(origen));
        return new Caso(
                "VAL-" + huella,
                "Validación de " + formulario.ventanaId() + ": " + payload.id(),
                formulario.base().rol(),
                pasos,
                List.of(),
                List.copyOf(oraculos),
                payload.usaCanario() ? canario : null,
                null,
                null,
                extensiones);
    }

    private static String huella(List<String> partes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String parte : partes) {
                byte[] bytes = parte.getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) ':');
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no ofrece SHA-256", e);
        }
    }
}
