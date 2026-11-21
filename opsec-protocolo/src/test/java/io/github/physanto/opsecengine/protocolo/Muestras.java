package io.github.physanto.opsecengine.protocolo;

import io.github.physanto.opsecengine.protocolo.dto.ArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.CapturaDto;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.ConfigGanchosDto;
import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.ErrorDto;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import io.github.physanto.opsecengine.protocolo.dto.ModoInteraccionDto;
import io.github.physanto.opsecengine.protocolo.dto.NodoArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import io.github.physanto.opsecengine.protocolo.dto.SolicitudCapturaDto;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Cuerpos de ejemplo para cada tipo de mensaje, compartidos por las pruebas. */
final class Muestras {

    static final HolaDto HOLA = new HolaDto("token-secreto", "0.0.1", "1.8.0_402", "Linux 6.8");

    static final PasoDto PASO =
            new PasoDto(
                    "LLENAR",
                    new LocalizadorDto(
                            List.of(
                                    new CriterioDto("ETIQUETA", "Contraseña"),
                                    new CriterioDto("NOMBRE_COMPONENTE", "txtClave"))),
                    "ñandú \"12345\"\nsegunda línea",
                    "EVENTOS",
                    10_000);

    static final ResultadoPasoDto RESULTADO =
            new ResultadoPasoDto(CodigoResultado.NO_ALCANZABLE, "Botón deshabilitado", 42);

    static final EventoDto EVENTO =
            new EventoDto(
                    7,
                    1_793_287_800_123L,
                    "SQL",
                    Map.of(
                            "sql",
                            "DELETE FROM personas WHERE id = ?",
                            "parametros",
                            List.of(3),
                            "filasAfectadas",
                            1));

    static final NodoArbolDto BOTON =
            new NodoArbolDto(
                    "javax.swing.JButton",
                    "Eliminar",
                    "btnEliminar",
                    null,
                    true,
                    false,
                    false,
                    null);

    static final Map<TipoMensaje, Object> CUERPOS = new EnumMap<>(TipoMensaje.class);

    static final Map<TipoMensaje, Class<?>> CLASES = new EnumMap<>(TipoMensaje.class);

    static {
        registrar(TipoMensaje.HOLA, HOLA);
        registrar(TipoMensaje.EJECUTAR_PASO, PASO);
        registrar(TipoMensaje.RESULTADO_PASO, RESULTADO);
        registrar(TipoMensaje.EVENTO, EVENTO);
        registrar(TipoMensaje.CAPTURAR, new SolicitudCapturaDto("Menú"));
        registrar(TipoMensaje.CAPTURA, new CapturaDto("Menú", "png", "iVBORw0KGgo="));
        registrar(TipoMensaje.ARBOL_SOLICITUD, null);
        registrar(
                TipoMensaje.ARBOL,
                new ArbolDto(
                        List.of(
                                new NodoArbolDto(
                                        "javax.swing.JFrame",
                                        "Menú",
                                        null,
                                        null,
                                        true,
                                        true,
                                        false,
                                        List.of(BOTON)))));
        registrar(
                TipoMensaje.CONFIG_GANCHOS,
                new ConfigGanchosDto(
                        List.of("DELETE"), List.of("delete"), List.of("CANARIO-1"), null));
        registrar(TipoMensaje.MODO_INTERACCION, new ModoInteraccionDto(ModoInteraccionDto.GRABAR));
        registrar(TipoMensaje.CERRAR, null);
        registrar(TipoMensaje.ERROR, new ErrorDto(ErrorDto.TOKEN_INVALIDO, "Token distinto"));
    }

    private Muestras() {}

    private static void registrar(TipoMensaje tipo, Object cuerpo) {
        CUERPOS.put(tipo, cuerpo);
        CLASES.put(tipo, cuerpo == null ? null : cuerpo.getClass());
    }
}
