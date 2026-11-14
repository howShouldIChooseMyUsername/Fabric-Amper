package dev.fabricamper.integration

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * G19: a genuine, independently resolved external KGP 2.4.20 must be rejected
 * by Amper's FA010 when preapplied to a settings-only consumer. A separate
 * consumer must configure with the genuinely bundled KGP 2.4.21.
 *
 * Both consumers resolve the prepublished local G29 marker/POM/JAR instead of
 * TestKit's injected plugin classpath. This is an opt-in help-only check, with
 * no Minecraft process, HTTP upload, world, account or EULA operation.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_G19_REAL_KGP", matches = "true")
class FabricAmperG19RealKotlinCollisionConsumerTest {
    @TempDir lateinit var suiteRoot: Path

    @Test
    fun `G19 genuine external KGP 2_4_20 fails FA010 and independent bundled 2_4_21 succeeds`() {
        val fixture = TestKitFixture(suiteRoot)
        val yaml = fixture.resource("minimal-mod.yaml")
            .replace(
                "    enabled: false",
                "    enabled: true\n" +
                    "    pluginVersion: \"2.4.21\"\n" +
                    "    languageVersion: \"2.4\"\n" +
                    "    fabricLanguageKotlin: \"1.14.2+kotlin.2.4.21\"",
            )
        require("    enabled: true" in yaml && "    enabled: false" !in yaml) {
            "The G19 fixture must actually request bundled Kotlin support"
        }
        val settings = publishedSettings(requirePublishedG29Plugin())

        val collision = fixture.project("g19-real-kgp-2420-collision", yaml)
        fixture.write(collision, "settings.gradle.kts", settings)
        fixture.write(collision, "g19-real-kgp.init.gradle", externalKgpInitScript())
        assertSettingsOnly(fixture, collision, yaml, settings)
        assertFalse(Files.exists(fixture.generatedJson(collision)))

        val conflict = fixture.failWithGradleVersion(
            collision, "9.7.0", "g19-real-kgp-2420-collision",
            "help", "--init-script", collision.resolve("g19-real-kgp.init.gradle").toString(),
            pluginClasspath = false,
        )
        val output = conflict.output
        assertFalse(output.contains("G19_KGP_INVALID_SETUP"),
            "An init-script/plugin-classloader failure is not the expected FA010 conflict:\n$output")
        val externalSource = uniqueMarker(output, "G19_KGP_EXTERNAL_WRAPPER_SOURCE")
        val appliedSource = uniqueMarker(output, "G19_KGP_PREAPPLIED_CLASS_SOURCE")
        assertRealKgpCodeSource(externalSource, "2.4.20")
        assertRealKgpCodeSource(appliedSource, "2.4.20")
        assertEquals(externalSource, appliedSource,
            "The installed plugin must be the genuine external 2.4.20 class that the init script resolved")
        assertEquals("org.jetbrains.kotlin.jvm", uniqueMarker(output, "G19_KGP_PREAPPLIED_ID"),
            "Gradle must recognize the actual preapplied plugin ID before Amper checks for conflicts")
        assertTrue(output.contains("FabricAmper FA010 Kotlin plugin conflict"),
            "Only Amper's specific preexisting Kotlin plugin diagnostic satisfies G19:\n$output")
        assertTrue(output.contains("'org.jetbrains.kotlin.jvm' was already applied"), output)
        assertFalse(output.contains("Kotlin Gradle plugin could not be applied"), output)
        assertTrue(output.contains("BUILD FAILED"), output)
        assertTrue(conflict.tasks.isEmpty(), "FA010 must happen before task execution:\n$output")
        assertNull(conflict.task(":help"), "The error must occur during configuration, not during help")
        assertFalse(Files.exists(fixture.generatedJson(collision)),
            "Failed configuration must not execute the mod metadata generator")
        assertSettingsOnly(fixture, collision, yaml, settings)

        // The control uses a distinct project and TestKit home. Its init
        // script observes the applied plugin; it does NOT resolve external KGP.
        val control = fixture.project("g19-real-kgp-2421-control", yaml)
        fixture.write(control, "settings.gradle.kts", settings)
        fixture.write(control, "g19-bundled-kgp-check.init.gradle", bundledKgpInspectionScript())
        assertFalse(Files.exists(control.resolve("g19-real-kgp.init.gradle")))
        assertSettingsOnly(fixture, control, yaml, settings)

        val success = fixture.runWithGradleVersion(
            control, "9.7.0", "g19-real-kgp-2421-control",
            "help", "--init-script", control.resolve("g19-bundled-kgp-check.init.gradle").toString(),
            pluginClasspath = false,
        )
        assertEquals(TaskOutcome.SUCCESS, success.task(":help")?.outcome, success.output)
        assertTrue(success.output.contains("BUILD SUCCESSFUL"), success.output)
        assertFalse(success.output.contains("G19_KGP_INVALID_SETUP"), success.output)
        assertFalse(success.output.contains("FabricAmper FA010"), success.output)
        assertFalse(success.output.contains("G19_KGP_EXTERNAL_WRAPPER_SOURCE="),
            "The independent control must never load the external KGP init script")
        assertEquals("org.jetbrains.kotlin.jvm", uniqueMarker(success.output, "G19_KGP_CONTROL_APPLIED"))
        assertRealKgpCodeSource(uniqueMarker(success.output, "G19_KGP_CONTROL_CLASS_SOURCE"), "2.4.21")
        assertEquals(1, fixture.mirrorJson(control).size,
            "Successful Kotlin-enabled consumer must configure Loom's metadata mirror")
        assertFalse(Files.exists(fixture.generatedJson(control)),
            "The help task must not run the generated metadata task")
        assertSettingsOnly(fixture, control, yaml, settings)
    }

    private fun requirePublishedG29Plugin(): Path {
        val producerRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val buildRoot = producerRoot.resolve("build").toRealPath()
        val repository = buildRoot.resolve("g29-probe/maven-repo").toRealPath()
        require(repository.startsWith(buildRoot)) { "G29 publication must remain inside producer build/" }
        val marker = repository.resolve(
            "dev/fabric-amper/settings/dev.fabric-amper.settings.gradle.plugin/0.1.3/" +
                "dev.fabric-amper.settings.gradle.plugin-0.1.3.pom",
        )
        val pom = repository.resolve("dev/fabricamper/fabric-amper/0.1.3/fabric-amper-0.1.3.pom")
        val jar = repository.resolve("dev/fabricamper/fabric-amper/0.1.3/fabric-amper-0.1.3.jar")
        require(listOf(marker, pom, jar).all { Files.isRegularFile(it) }) {
            "G19 requires Prime's already published G29 local marker, plugin POM and JAR"
        }
        return repository
    }

    private fun publishedSettings(repository: Path): String {
        val repoUri = repository.toUri().toASCIIString()
        return """
            pluginManagement {
                repositories {
                    maven { url = uri("$repoUri") }
                    maven("https://maven.fabricmc.net/")
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            plugins { id("dev.fabric-amper.settings") version "0.1.3" }
        """.trimIndent() + "\n"
    }

    private fun externalKgpInitScript(): String = """
        initscript {
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
            dependencies {
                classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20'
            }
        }

        // This class must be loaded from the external init-script dependency.
        def externalType = this.class.classLoader.loadClass('org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper')
        def externalSource = externalType.protectionDomain.codeSource?.location?.toString()
        println('G19_KGP_EXTERNAL_WRAPPER_SOURCE=' + externalSource)
        if (externalSource == null || !externalSource.replace('\\', '/').contains('/kotlin-gradle-plugin/2.4.20/')) {
            throw new GradleException('G19_KGP_INVALID_SETUP: the requested external KGP 2.4.20 was shadowed')
        }

        // The init script precedes the Settings plugin's beforeProject hook.
        gradle.beforeProject { project ->
            if (project.path == ':') {
                try {
                    project.pluginManager.apply(externalType)
                    // Applying by class can leave some Gradle plugin ID mappings
                    // unregistered; require the real externally loaded ID, too.
                    if (!project.pluginManager.hasPlugin('org.jetbrains.kotlin.jvm')) {
                        project.pluginManager.apply('org.jetbrains.kotlin.jvm')
                    }
                } catch (Exception e) {
                    throw new GradleException('G19_KGP_INVALID_SETUP: external Kotlin plugin could not be preapplied', e)
                }
                def applied = project.plugins.findPlugin('org.jetbrains.kotlin.jvm')
                if (applied == null || !project.pluginManager.hasPlugin('org.jetbrains.kotlin.jvm')) {
                    throw new GradleException('G19_KGP_INVALID_SETUP: the external Kotlin plugin ID was not applied')
                }
                def appliedSource = applied.class.protectionDomain.codeSource?.location?.toString()
                println('G19_KGP_PREAPPLIED_CLASS_SOURCE=' + appliedSource)
                if (appliedSource == null || !appliedSource.replace('\\', '/').contains('/kotlin-gradle-plugin/2.4.20/')) {
                    throw new GradleException('G19_KGP_INVALID_SETUP: preapplied Kotlin came from another version')
                }
                println('G19_KGP_PREAPPLIED_ID=org.jetbrains.kotlin.jvm')
            }
        }
    """.trimIndent() + "\n"

    private fun bundledKgpInspectionScript(): String = """
        // No initscript dependencies: inspect only the binary supplied by
        // the single published Fabric Amper Settings plugin.
        gradle.afterProject { project ->
            if (project.path == ':' && project.state.failure == null) {
                def applied = project.plugins.findPlugin('org.jetbrains.kotlin.jvm')
                if (applied == null || !project.pluginManager.hasPlugin('org.jetbrains.kotlin.jvm')) {
                    throw new GradleException('G19_KGP_INVALID_SETUP: the bundled Kotlin plugin was not applied')
                }
                def appliedSource = applied.class.protectionDomain.codeSource?.location?.toString()
                println('G19_KGP_CONTROL_CLASS_SOURCE=' + appliedSource)
                if (appliedSource == null || !appliedSource.replace('\\', '/').contains('/kotlin-gradle-plugin/2.4.21/')) {
                    throw new GradleException('G19_KGP_INVALID_SETUP: control did not apply bundled KGP 2.4.21')
                }
                println('G19_KGP_CONTROL_APPLIED=org.jetbrains.kotlin.jvm')
            }
        }
    """.trimIndent() + "\n"

    private fun assertSettingsOnly(fixture: TestKitFixture, project: Path, yaml: String, settings: String) {
        fixture.assertNoConsumerBuildScript(project)
        assertEquals(yaml, project.resolve("mod.yaml").readText())
        assertEquals(settings, project.resolve("settings.gradle.kts").readText())
        assertFalse(Files.exists(project.resolve("src/main/resources/fabric.mod.json")))
        assertFalse(Files.exists(project.resolve("src/client/resources/fabric.mod.json")))
    }

    private fun uniqueMarker(output: String, label: String): String {
        val values = output.lineSequence()
            .filter { it.startsWith("$label=") }
            .map { it.removePrefix("$label=").trim() }
            .toList()
        assertEquals(1, values.size, "Expected exactly one $label output from the corresponding init script:\n$output")
        assertTrue(values.single().isNotBlank(), "Empty $label is not a classloader CodeSource proof")
        return values.single()
    }

    private fun assertRealKgpCodeSource(source: String, version: String) {
        val v = version.replace(".", "\\.")
        val actual = source.replace('\\', '/')
        val cacheJar = Regex(
            """(?:^|/)org\.jetbrains\.kotlin/kotlin-gradle-plugin/$v/[^/\s]+/kotlin-gradle-plugin-$v(?:-[^/\s]+)?\.jar$""",
        )
        assertTrue(cacheJar.containsMatchIn(actual),
            "KGP $version must come from Gradle's real resolved org.jetbrains.kotlin:kotlin-gradle-plugin JAR; got: $source")
    }
}
