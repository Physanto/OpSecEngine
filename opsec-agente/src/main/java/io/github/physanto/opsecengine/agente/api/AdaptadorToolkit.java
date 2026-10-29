package io.github.physanto.opsecengine.agente.api;

import io.github.physanto.opsecengine.protocolo.dto.CapturaDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import io.github.physanto.opsecengine.protocolo.dto.NodoArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import java.util.List;

/**
 * Puente entre el agente y el toolkit gráfico de la aplicación probada: recorre la interfaz,
 * encuentra componentes, ejecuta acciones y toma capturas.
 *
 * <p>Hoy existe una implementación para Swing; una para JavaFX implementaría esta misma interfaz
 * sin tocar el resto del agente.
 *
 * <p><b>Hilos.</b> El servidor del protocolo llama a estos métodos desde su propio hilo, nunca
 * desde el hilo de la interfaz. La implementación debe acceder a los componentes solo en el hilo
 * del toolkit (en Swing, el EDT, con {@code invokeAndWait}) y bloquear hasta tener el resultado.
 * Debe ser segura entre hilos.
 *
 * <p><b>Errores.</b> Los métodos no deben lanzar excepciones por el estado de la aplicación
 * (componentes que desaparecen, ventanas que se cierran, excepciones de la propia aplicación): lo
 * informan en el resultado, para que la aplicación probada y el agente sigan funcionando.
 */
public interface AdaptadorToolkit {

    /**
     * Devuelve el árbol de componentes de las ventanas visibles.
     *
     * @return una raíz por ventana o diálogo visible, en orden de apertura; lista vacía, nunca
     *     null, si no hay ventanas
     */
    List<NodoArbolDto> arbol();

    /**
     * Busca un componente probando los criterios del localizador en orden de prioridad.
     *
     * @param localizador localizador del componente; nunca es null
     * @return resultado de la búsqueda con código {@code OK}, {@code NO_ENCONTRADO} o {@code
     *     NO_ALCANZABLE}; nunca null
     */
    ResultadoBusqueda resolver(LocalizadorDto localizador);

    /**
     * Ejecuta un paso con el modo que indica y espera a que termine.
     *
     * <p>Si el paso no termina dentro de {@link PasoDto#getEsperaMaximaMs()}, devuelve {@code
     * TIEMPO_AGOTADO}. Si el componente no existe o no se puede usar, devuelve {@code
     * NO_ENCONTRADO} o {@code NO_ALCANZABLE}. Si ocurre otro problema, devuelve {@code ERROR} con
     * el detalle.
     *
     * @param paso paso a ejecutar; nunca es null
     * @return resultado del paso, con su duración; nunca null
     */
    ResultadoPasoDto ejecutar(PasoDto paso);

    /**
     * Toma una captura pintando la ventana en una imagen, sin usar {@code java.awt.Robot}, para que
     * funcione también en modo oculto.
     *
     * @param ventana título o clase de la ventana; puede ser null para capturar la ventana activa
     * @return la captura en PNG, o null si no hay ninguna ventana que coincida
     */
    CapturaDto capturar(String ventana);
}
