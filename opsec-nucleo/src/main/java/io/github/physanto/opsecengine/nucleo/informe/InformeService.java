package io.github.physanto.opsecengine.nucleo.informe;

import io.github.physanto.opsecengine.dominio.extension.Reportero;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Genera los informes de una suite con los reporteros registrados.
 *
 * <p>Cada formato pedido ({@code "json"}, {@code "html"}, ...) debe tener un reportero en el
 * registro; si falta alguno no se escribe nada. Los formatos se generan en el orden pedido y sin
 * repetir.
 *
 * <p>Ejemplo:
 *
 * <pre>{@code
 * InformeService informes = new InformeService(RegistroExtensiones.descubrir());
 * informes.generar(resultado, Path.of("resultados", "2026-10-28_1530"), List.of("json"));
 * }</pre>
 *
 * <p>Es seguro entre hilos si los reporteros lo son, como pide su contrato.
 */
public final class InformeService {

    private final RegistroExtensiones registro;

    /**
     * Crea el servicio con los reporteros de un registro.
     *
     * @param registro registro de extensiones; no puede ser null
     * @throws NullPointerException si {@code registro} es null
     */
    public InformeService(RegistroExtensiones registro) {
        this.registro = Objects.requireNonNull(registro, "El registro no puede ser null");
    }

    /**
     * Devuelve los formatos que se pueden generar.
     *
     * @return formatos de los reporteros registrados, en el orden en que se encontraron
     */
    public Set<String> formatosDisponibles() {
        Set<String> formatos = new LinkedHashSet<>();
        registro.reporteros().forEach(reportero -> formatos.add(reportero.formato()));
        return Collections.unmodifiableSet(formatos);
    }

    /**
     * Escribe los informes pedidos en la carpeta de resultados.
     *
     * @param resultado resultado de la suite; no puede ser null
     * @param destino carpeta de resultados de la ejecución; se crea si no existe; no puede ser null
     * @param formatos formatos que se generan; no puede ser null ni contener null
     * @throws IllegalArgumentException si algún formato no tiene reportero; en ese caso no se
     *     escribe ningún informe
     * @throws IOException si no se puede crear la carpeta o un reportero no puede escribir
     * @throws NullPointerException si algún parámetro es null
     */
    public void generar(ResultadoSuite resultado, Path destino, List<String> formatos)
            throws IOException {
        Objects.requireNonNull(resultado, "El resultado no puede ser null");
        Objects.requireNonNull(destino, "La carpeta de destino no puede ser null");
        List<Reportero> reporteros = new ArrayList<>();
        for (String formato : new LinkedHashSet<>(List.copyOf(formatos))) {
            reporteros.add(
                    registro.reportero(formato)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "No hay un reportero para el formato "
                                                            + formato
                                                            + "; los disponibles son "
                                                            + formatosDisponibles())));
        }
        Files.createDirectories(destino);
        for (Reportero reportero : reporteros) {
            reportero.escribir(resultado, destino);
        }
    }
}
