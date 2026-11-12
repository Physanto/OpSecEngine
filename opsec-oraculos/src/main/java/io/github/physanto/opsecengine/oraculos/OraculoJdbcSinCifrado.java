package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Señala URLs JDBC conocidas que desactivan TLS o permiten conexiones sin cifrado (CWE-319). No
 * observa el cifrado negociado ni propiedades externas a la URL. Sin estado y seguro entre hilos.
 */
public final class OraculoJdbcSinCifrado implements Oraculo {
    /** Crea el oráculo para su descubrimiento mediante ServiceLoader. */
    public OraculoJdbcSinCifrado() {}

    /**
     * Devuelve el id del catálogo.
     *
     * @return JDBC-SIN-CIFRADO
     */
    @Override
    public String id() {
        return "JDBC-SIN-CIFRADO";
    }

    /**
     * Devuelve el momento de evaluación de cada operación SQL.
     *
     * @return EVENTO
     */
    @Override
    public Momento momento() {
        return Momento.EVENTO;
    }

    /**
     * Indica que las conexiones se clasifican desde los eventos observados.
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
     * Examina SQL.conexion para MySQL, PostgreSQL, SQL Server y H2 TCP, excluyendo H2 local.
     *
     * @param traza traza del caso; puede ser null
     * @param contexto contexto opcional, cuyo caso debe coincidir si está presente
     * @return hallazgos probables por evento, sin copiar URL, consultas ni credenciales
     */
    @Override
    public List<Hallazgo> evaluar(Traza traza, Contexto contexto) {
        if (traza == null
                || (contexto != null
                        && contexto.caso() != null
                        && !contexto.caso().id().equals(traza.casoId()))) {
            return List.of();
        }
        List<Hallazgo> resultado = new ArrayList<>();
        for (Evento evento : traza.eventos()) {
            if (evento.tipo() != TipoEvento.SQL
                    || !traza.casoId().equals(evento.contexto().casoId())
                    || (evento.datos().containsKey("error")
                            && !evento.texto("error").map(String::isBlank).orElse(false))) {
                continue;
            }
            String driver = riesgo(evento.texto("conexion").orElse(""));
            if (driver != null) {
                resultado.add(
                        new Hallazgo(
                                id(),
                                "CWE-319",
                                Confianza.PROBABLE,
                                "Conexión JDBC sin garantía de cifrado",
                                "La URL de una operación SQL de "
                                        + driver
                                        + " desactiva TLS o permite una conexión sin cifrar. Exija cifrado y compruebe la conexión efectiva; la traza no informa la negociación TLS ni las propiedades externas a la URL.",
                                List.of(evento.secuencia())));
            }
        }
        return List.copyOf(resultado);
    }

    private static String riesgo(String url) {
        if (url.isBlank() || url.contains("{") || url.contains("}")) {
            return null;
        }
        if (url.startsWith("jdbc:h2:tcp://")
                && hostPresente(url.substring("jdbc:h2:tcp://".length()))) {
            return "H2 TCP";
        }
        if (url.startsWith("jdbc:sqlserver://")
                && hostPresente(url.substring("jdbc:sqlserver://".length()))) {
            int separador = url.indexOf(';');
            Map<String, String> propiedades =
                    parametros(separador < 0 ? "" : url.substring(separador + 1), ";", true);
            return propiedades != null && "false".equalsIgnoreCase(propiedades.get("encrypt"))
                    ? "SQL Server"
                    : null;
        }
        boolean mysql = url.startsWith("jdbc:mysql://");
        boolean postgres = url.startsWith("jdbc:postgresql://");
        if (!mysql && !postgres) {
            return null;
        }
        String resto =
                url.substring(mysql ? "jdbc:mysql://".length() : "jdbc:postgresql://".length());
        if (!hostPresente(resto) || resto.contains("(")) {
            return null;
        }
        int pregunta = url.indexOf('?');
        Map<String, String> p =
                parametros(pregunta < 0 ? "" : url.substring(pregunta + 1), "&", false);
        if (p == null) {
            return null;
        }
        if (mysql) {
            if (p.containsKey("sslMode")) {
                return Set.of("DISABLED", "PREFERRED")
                                .contains(p.get("sslMode").toUpperCase(Locale.ROOT))
                        ? "MySQL"
                        : null;
            }
            for (String clave : List.of("useSSL", "requireSSL", "verifyServerCertificate")) {
                if (p.containsKey(clave)
                        && !Set.of("true", "false")
                                .contains(p.get(clave).toLowerCase(Locale.ROOT))) {
                    return null;
                }
            }
            if ("false".equalsIgnoreCase(p.get("useSSL"))) {
                return "MySQL";
            }
            if ("true".equalsIgnoreCase(p.get("requireSSL"))
                    || "true".equalsIgnoreCase(p.get("verifyServerCertificate"))) {
                return null;
            }
            return "MySQL";
        }
        if (p.containsKey("sslmode")) {
            return Set.of("disable", "allow", "prefer")
                            .contains(p.get("sslmode").toLowerCase(Locale.ROOT))
                    ? "PostgreSQL"
                    : null;
        }
        if (p.containsKey("ssl")
                && !Set.of("true", "false", "").contains(p.get("ssl").toLowerCase(Locale.ROOT))) {
            return null;
        }
        return p.containsKey("ssl")
                        && (p.get("ssl").isEmpty() || "true".equalsIgnoreCase(p.get("ssl")))
                ? null
                : "PostgreSQL";
    }

    private static boolean hostPresente(String resto) {
        return !resto.isBlank() && !"/;?".contains(resto.substring(0, 1));
    }

    private static Map<String, String> parametros(
            String texto, String separador, boolean clavesMinusculas) {
        Map<String, String> resultado = new HashMap<>();
        try {
            for (String parte : texto.split(separador, -1)) {
                if (parte.isEmpty()) {
                    continue;
                }
                int igual = parte.indexOf('=');
                String clave = igual < 0 ? parte : parte.substring(0, igual);
                String valor = igual < 0 ? "" : parte.substring(igual + 1);
                if (!clavesMinusculas) {
                    clave = URLDecoder.decode(clave, StandardCharsets.UTF_8);
                    valor = URLDecoder.decode(valor, StandardCharsets.UTF_8);
                } else {
                    clave = clave.toLowerCase(Locale.ROOT);
                }
                if (clave.isBlank() || resultado.putIfAbsent(clave, valor) != null) {
                    return null;
                }
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return resultado;
    }
}
