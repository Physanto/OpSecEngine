package io.github.physanto.opsecengine.soporte;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.physanto.opsecengine.dominio.mapa.MapaApp;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Datos de ejemplo de los recursos del módulo, ya convertidos en objetos del dominio.
 *
 * <ul>
 *   <li>{@link #mapaClientes()}: el mapa de la app de clientes ({@code mapas/clientes.json}), con
 *       los contextos {@code sin-sesion}, {@code rol:cliente}, {@code rol:admin} y {@code
 *       entrada:GUI_Admin.EliminarPersonaAdministrador}.
 *   <li>{@link #resultadoSuite()}: un resultado de suite con un caso de cada estado ({@code
 *       resultados/ejemplo.json}), para diseñar informes.
 * </ul>
 *
 * <p>Los JSON usan los nombres de los componentes de los records del dominio, los enums por su
 * nombre, los instantes en ISO-8601 y las duraciones en ISO-8601 ({@code PT2.5S}). Ejemplo:
 *
 * <pre>{@code
 * MapaApp mapa = DatosEjemplo.mapaClientes();
 * Set<Ventana> alcanzables = mapa.alcanzablesDesde("Portada", MapaApp.SIN_SESION);
 * ResultadoSuite resultado = DatosEjemplo.resultadoSuite();
 * reportero.escribir(resultado, Path.of("target", "informe"));
 * }</pre>
 *
 * <p>La clase no tiene estado y no se instancia; sus métodos son seguros entre hilos.
 */
public final class DatosEjemplo {

    private static final JsonMapper MAPPER =
            JsonMapper.builder()
                    .addModule(new JavaTimeModule())
                    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .build();

    private DatosEjemplo() {}

    /**
     * Carga el mapa de ejemplo de la app de clientes.
     *
     * @return el mapa, validado por el constructor de {@link MapaApp}
     * @throws IOException si no se puede leer o convertir el recurso
     */
    public static MapaApp mapaClientes() throws IOException {
        return leer("mapas/clientes.json", MapaApp.class);
    }

    /**
     * Carga el resultado de suite de ejemplo, con un caso CUMPLE, uno NO_CUMPLE y uno
     * NO_CONCLUYENTE.
     *
     * @return el resultado, validado por los constructores del dominio
     * @throws IOException si no se puede leer o convertir el recurso
     */
    public static ResultadoSuite resultadoSuite() throws IOException {
        return leer("resultados/ejemplo.json", ResultadoSuite.class);
    }

    private static <T> T leer(String recurso, Class<T> tipo) throws IOException {
        try (InputStream flujo = DatosEjemplo.class.getResourceAsStream(recurso)) {
            if (flujo == null) {
                throw new FileNotFoundException("No se encontró el recurso " + recurso);
            }
            return MAPPER.readValue(flujo, tipo);
        }
    }
}
