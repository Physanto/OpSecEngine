package io.github.physanto.opsecengine.agente.ganchos;

import net.bytebuddy.asm.Advice;

/**
 * Consejos de ByteBuddy que lanzan el fallo inyectado al entrar a un método. Cada clase interna
 * corresponde a un punto de inyección. El código se copia dentro del método de la aplicación; no se
 * debe llamar directamente.
 */
public final class ConsejoFallo {

    private ConsejoFallo() {}

    /** Fallo al conectar con la base de datos. */
    public static final class Conexion {

        private Conexion() {}

        /**
         * Lanza el fallo si el punto está activo.
         *
         * @throws Throwable la excepción configurada
         */
        @Advice.OnMethodEnter
        public static void entrar() throws Throwable {
            Throwable fallo = InyectorFallos.pendiente(InyectorFallos.SQL_CONEXION);
            if (fallo != null) {
                throw fallo;
            }
        }
    }

    /** Fallo al ejecutar una sentencia SQL. */
    public static final class Sql {

        private Sql() {}

        /**
         * Lanza el fallo si el punto está activo.
         *
         * @throws Throwable la excepción configurada
         */
        @Advice.OnMethodEnter
        public static void entrar() throws Throwable {
            Throwable fallo = InyectorFallos.pendiente(InyectorFallos.SQL_EJECUTAR);
            if (fallo != null) {
                throw fallo;
            }
        }
    }

    /** Fallo al escribir en Firestore. */
    public static final class Firestore {

        private Firestore() {}

        /**
         * Lanza el fallo si el punto está activo.
         *
         * @throws Throwable la excepción configurada
         */
        @Advice.OnMethodEnter
        public static void entrar() throws Throwable {
            Throwable fallo = InyectorFallos.pendiente(InyectorFallos.FIRESTORE_ESCRIBIR);
            if (fallo != null) {
                throw fallo;
            }
        }
    }
}
