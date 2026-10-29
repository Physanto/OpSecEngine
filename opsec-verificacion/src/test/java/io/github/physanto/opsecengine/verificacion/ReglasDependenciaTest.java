package io.github.physanto.opsecengine.verificacion;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas de dependencia entre los módulos del motor, según la arquitectura.
 *
 * <p>Se analizan las clases principales de todos los módulos del motor (sin sus pruebas). Cada
 * regla admite paquetes vacíos porque en la Fase 0 los módulos todavía casi no tienen clases.
 */
@AnalyzeClasses(
        packages = ReglasDependenciaTest.PROYECTO,
        importOptions = ImportOption.DoNotIncludeTests.class)
public class ReglasDependenciaTest {

    /** Paquete raíz del proyecto. */
    static final String PROYECTO = "io.github.physanto.opsecengine";

    private static final String TODO_EL_PROYECTO = PROYECTO + "..";
    private static final String DOMINIO = PROYECTO + ".dominio..";
    private static final String PROTOCOLO = PROYECTO + ".protocolo..";
    private static final String AGENTE = PROYECTO + ".agente..";
    private static final String NUCLEO = PROYECTO + ".nucleo..";
    private static final String ORACULOS = PROYECTO + ".oraculos..";
    private static final String ATAQUES = PROYECTO + ".ataques..";
    private static final String INFORMES = PROYECTO + ".informes..";
    private static final String CLI = PROYECTO + ".cli..";

    /** El dominio no depende de ningún otro paquete del proyecto. */
    @ArchTest
    public static final ArchRule DOMINIO_NO_DEPENDE_DE_OTROS_MODULOS =
            noClasses()
                    .that()
                    .resideInAPackage(DOMINIO)
                    .should()
                    .dependOnClassesThat(
                            resideInAPackage(TODO_EL_PROYECTO).and(not(resideInAPackage(DOMINIO))))
                    .allowEmptyShould(true)
                    .because(
                            "el dominio contiene solo modelos e interfaces y no puede depender de"
                                    + " ningún otro módulo del motor");

    /** El agente solo depende del protocolo dentro del proyecto. */
    @ArchTest
    public static final ArchRule AGENTE_SOLO_DEPENDE_DEL_PROTOCOLO =
            noClasses()
                    .that()
                    .resideInAPackage(AGENTE)
                    .should()
                    .dependOnClassesThat(
                            resideInAPackage(TODO_EL_PROYECTO)
                                    .and(not(resideInAnyPackage(AGENTE, PROTOCOLO))))
                    .allowEmptyShould(true)
                    .because(
                            "el agente corre dentro de la aplicación probada y solo puede usar el"
                                    + " protocolo, nunca el núcleo ni el dominio");

    /** Oráculos, ataques e informes no dependen del núcleo, del agente ni del protocolo. */
    @ArchTest
    public static final ArchRule EXTENSIONES_SOLO_DEPENDEN_DEL_DOMINIO =
            noClasses()
                    .that()
                    .resideInAnyPackage(ORACULOS, ATAQUES, INFORMES)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(NUCLEO, AGENTE, PROTOCOLO)
                    .allowEmptyShould(true)
                    .because(
                            "oráculos, ataques e informes solo dependen del dominio; el núcleo los"
                                    + " descubre con ServiceLoader");

    /** Ningún módulo depende de la línea de comandos. */
    @ArchTest
    public static final ArchRule NADIE_DEPENDE_DE_LA_CLI =
            noClasses()
                    .that()
                    .resideOutsideOfPackage(CLI)
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(CLI)
                    .allowEmptyShould(true)
                    .because("la CLI es una presentación y ningún módulo puede depender de ella");

    /** Solo el agente usa ByteBuddy. */
    @ArchTest
    public static final ArchRule SOLO_EL_AGENTE_USA_BYTEBUDDY =
            noClasses()
                    .that()
                    .resideOutsideOfPackage(AGENTE)
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("net.bytebuddy..")
                    .allowEmptyShould(true)
                    .because(
                            "ByteBuddy solo se usa en el agente, que lo reubica para no chocar con"
                                    + " las librerías de la aplicación probada");
}
