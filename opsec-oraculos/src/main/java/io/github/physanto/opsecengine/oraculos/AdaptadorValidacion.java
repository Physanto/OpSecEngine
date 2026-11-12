package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Datos temporales para generar casos sin ampliar MapaApp ni depender de opsec-ataques.
 *
 * <p>El llamador transforma los payloads de su biblioteca y aporta flujos base conocidos. Este
 * adaptador no descubre campos ni carga la biblioteca. Sus listas son inmutables y es seguro entre
 * hilos si los valores de las extensiones de los casos también lo son.
 *
 * @param formularios formularios con navegación y valores válidos; no admite null ni ids de caso
 *     repetidos
 * @param payloads entradas ya cargadas de la biblioteca; no admite null ni ids repetidos
 */
public record AdaptadorValidacion(List<Formulario> formularios, List<Payload> payloads) {
    private static final Set<String> TIPOS =
            Set.of(
                    "CUALQUIERA",
                    "TEXTO",
                    "CONTRASENA",
                    "NUMERO",
                    "FECHA",
                    "CORREO",
                    "BUSQUEDA",
                    "IDENTIFICADOR");

    /**
     * Copia las listas y comprueba los identificadores.
     *
     * @throws NullPointerException si alguna lista o elemento es null
     * @throws IllegalArgumentException si hay ids repetidos
     */
    public AdaptadorValidacion {
        formularios = List.copyOf(formularios);
        payloads = List.copyOf(payloads);
        Set<String> ids = new HashSet<>();
        for (Formulario formulario : formularios) {
            if (!ids.add(formulario.base().id())) {
                throw new IllegalArgumentException("Caso base repetido: " + formulario.base().id());
            }
        }
        ids.clear();
        for (Payload payload : payloads) {
            if (!ids.add(payload.id())) {
                throw new IllegalArgumentException("Payload repetido: " + payload.id());
            }
        }
    }

    /**
     * Formulario descrito por un flujo completo y conocido, sin nombres específicos de una app.
     *
     * @param ventanaId ventana del mapa donde están los campos
     * @param base caso con navegación, campos válidos, envío y, si procede, cierre de diálogos; no
     *     puede contener ataques pendientes de expansión
     * @param pasoEnvio orden original del paso PULSAR que envía el formulario
     * @param campos pasos LLENAR que se pueden variar, todos anteriores al envío
     */
    public record Formulario(String ventanaId, Caso base, int pasoEnvio, List<Campo> campos) {
        /**
         * Valida que los campos y el envío existan en el caso base.
         *
         * @throws NullPointerException si faltan la ventana, el caso, los campos o un elemento
         * @throws IllegalArgumentException si hay órdenes ambiguos, campos inválidos o no hay envío
         */
        public Formulario {
            textoObligatorio(ventanaId, "ventana");
            Objects.requireNonNull(base, "El caso base no puede ser null");
            campos = List.copyOf(campos);
            if (!base.ataques().isEmpty()) {
                throw new IllegalArgumentException(
                        "El caso base no puede tener ataques sin expandir");
            }
            int anterior = 0;
            boolean envio = false;
            for (Paso paso : base.pasos()) {
                if (paso.orden() <= anterior) {
                    throw new IllegalArgumentException(
                            "Los pasos base deben tener órdenes crecientes");
                }
                anterior = paso.orden();
                if (paso.orden() == pasoEnvio && paso.accion() == Accion.PULSAR) {
                    envio = true;
                }
            }
            if (!envio) {
                throw new IllegalArgumentException("Falta el paso PULSAR de envío");
            }
            Set<Integer> ordenes = new HashSet<>();
            for (Campo campo : campos) {
                if (!ordenes.add(campo.ordenPaso())
                        || campo.ordenPaso() >= pasoEnvio
                        || base.pasos().stream()
                                .noneMatch(p ->
                                        p.orden() == campo.ordenPaso()
                                                && p.accion() == Accion.LLENAR
                                                && p.valor() != null)) {
                    throw new IllegalArgumentException(
                            "Campo inválido en el paso " + campo.ordenPaso());
                }
            }
        }
    }

    /**
     * Campo que se identifica por el orden de su paso, incluso si el localizador se repite en el
     * flujo.
     *
     * @param ordenPaso orden original del paso LLENAR
     * @param tipo tipo de campo de la biblioteca de ataques, escrito en mayúsculas
     */
    public record Campo(int ordenPaso, String tipo) {
        /**
         * Valida el orden y el tipo de campo.
         *
         * @throws NullPointerException si el tipo es null
         * @throws IllegalArgumentException si el orden no es positivo o el tipo es desconocido
         */
        public Campo {
            if (ordenPaso < 1) {
                throw new IllegalArgumentException("Orden de campo no positivo");
            }
            comprobarTipo(tipo);
        }
    }

    /**
     * Proyección local de un payload; no copia ni reemplaza el modelo de opsec-ataques.
     *
     * @param id identificador estable en la biblioteca
     * @param texto valor que se escribirá, posiblemente con la variable canario
     * @param tiposCampo tipos de campo compatibles; CUALQUIERA admite todos
     * @param usaCanario true si se sustituye la variable canario
     * @param oraculos identificadores de los oráculos que deben evaluar el caso
     */
    public record Payload(
            String id,
            String texto,
            Set<String> tiposCampo,
            boolean usaCanario,
            List<String> oraculos) {
        /**
         * Copia las colecciones y comprueba la coherencia del canario.
         *
         * @throws NullPointerException si un dato requerido o un elemento es null
         * @throws IllegalArgumentException si el id está vacío, el tipo es desconocido o falta el
         *     canario
         */
        public Payload {
            textoObligatorio(id, "id del payload");
            Objects.requireNonNull(texto, "El texto del payload no puede ser null");
            tiposCampo = Set.copyOf(tiposCampo);
            tiposCampo.forEach(AdaptadorValidacion::comprobarTipo);
            if (tiposCampo.isEmpty()) {
                tiposCampo = Set.of("CUALQUIERA");
            }
            oraculos = List.copyOf(oraculos);
            oraculos.forEach(idOraculo -> textoObligatorio(idOraculo, "id del oráculo"));
            if (usaCanario && !texto.contains("{canario}")) {
                throw new IllegalArgumentException(
                        "El payload marcado como rastreable no tiene canario");
            }
        }
    }

    private static void textoObligatorio(String texto, String nombre) {
        Objects.requireNonNull(texto, "Falta " + nombre);
        if (texto.isBlank()) {
            throw new IllegalArgumentException("Está vacío: " + nombre);
        }
    }

    private static void comprobarTipo(String tipo) {
        Objects.requireNonNull(tipo, "Falta el tipo de campo");
        if (!TIPOS.contains(tipo)) {
            throw new IllegalArgumentException("Tipo de campo desconocido: " + tipo);
        }
    }
}
