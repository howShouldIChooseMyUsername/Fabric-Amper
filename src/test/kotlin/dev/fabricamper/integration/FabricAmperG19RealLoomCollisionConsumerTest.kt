package dev.fabricamper.integration

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * G19 genuine external Loom classloader/collision acceptance.
 *
 * A preapplied DIFFERENT, REAL fabric-loom:1.18.4 from an isolated init-script
 * classpath must cause Amper's explicit FA010 instead of silently picking the
 * bundled 1.17.21. Both consumers resolve the published G29 plugin marker,
 * POM and JAR; neither uses GradleRunner.withPluginClasspath().
 *
 * This is an opt-in *configuration-only* check: no game, server, EULA,
 * source compilation, existing user Gradle home or user Minecraft directory.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_G19_REAL", matches = "true")
class FabricAmperG19RealLoomCollisionConsumerTest {
    @TempDir lateinit var suiteRoot: Path

    @Test
    fun `G19 separately preapplied genuine Loom 1_18_4 fails FA010 and bundled 1_17_21 succeeds`() {
        val fixture = TestKitFixture(suiteRoot)
        val published = requirePublishedG29Plugin()
        val yaml = fixture.resource("runtime/g19-real.yaml")
        val initScript = fixture.resource("runtime/g19-preapply-external.init.gradle")
        val settings = settingsFromPublishedRepository(published)

        // Separate, immutable consumers ensure that configuration caches,
        // extension instances and init scripts cannot contaminate the control.
        val collision = fixture.project("g19-real-external-1184", yaml)
        fixture.write(collision, "settings.gradle.kts", settings)
        fixture.write(collision, "g19-preapply-external.init.gradle", initScript)
        assertOnlySettingsConsumer(fixture, collision, yaml, settings)
        assertFalse(Files.exists(fixture.generatedJson(collision)))

        val conflict = fixture.failWithGradleVersion(
            collision, "9.7.0", "g19-real-external-1184",
            "help", "--init-script", collision.resolve("g19-preapply-external.init.gradle").toString(),
            pluginClasspath = false,
        )
        val output = conflict.output
        assertFalse(output.contains("G19_INVALID_SETUP"),
            "A classloader, initscript or plugin-initialization failure is NOT a valid G19 collision:\n$output")
        assertEquals("1.18.4", uniqueMarker(output, "G19_EXTERNAL_CLASS_VERSION_CONFIRMED"),
            "The external class version guard must run before applying the external plugin")
        val externalSource = uniqueMarker(output, "G19_EXTERNAL_CLASS_SOURCE")
        val appliedSource = uniqueMarker(output, "G19_APPLIED_PLUGIN_CLASS_SOURCE")
        assertRealExternalLoomJar(externalSource)
        assertRealExternalLoomJar(appliedSource)
        assertEquals(externalSource, appliedSource,
            "The actual installed plugin instance must originate from the same external 1.18.4 JAR")
        assertEquals("1.18.4", uniqueMarker(output, "G19_REAL_EXTERNAL_LOOM_APPLIED"),
            "The external plugin must have been applied and its loom extension found")
        assertTrue(output.contains("FabricAmper FA010 Loom plugin conflict"),
            "Expected Amper's precise Loom conflict diagnostic, not an unrelated Gradle failure:\n$output")
        assertTrue(output.contains("('loom extension')"),
            "Expected the conflict to come from a pre-existing extension, not a different configuration problem:\n$output")
        assertTrue(output.contains("BUILD FAILED"), output)
        assertTrue(conflict.tasks.isEmpty(),
            "The collision must be rejected during project configuration, before any task graph:\n$output")
        assertEquals(null, conflict.task(":help"), "A failed help task is not the required early configuration failure")
        assertFalse(Files.exists(fixture.generatedJson(collision)),
            "Failed configuration must not run generateFabricModJson")
        assertOnlySettingsConsumer(fixture, collision, yaml, settings)

        // Control is a newly-created consumer without -I, not a rerun of the
        // failed consumer with a potentially tainted plugin/classloader scope.
        val control = fixture.project("g19-real-bundled-control", yaml)
        fixture.write(control, "settings.gradle.kts", settings)
        assertOnlySettingsConsumer(fixture, control, yaml, settings)
        assertFalse(Files.exists(control.resolve("g19-preapply-external.init.gradle")),
            "The control must not have the external Loom init script")
        val controlResult = fixture.runWithGradleVersion(
            control, "9.7.0", "g19-real-bundled-control", "help",
            pluginClasspath = false,
        )
        assertEquals(TaskOutcome.SUCCESS, controlResult.task(":help")?.outcome,
            "Bundled Loom 1.17.21 must configure this independent settings-only consumer:\n${controlResult.output}")
        assertTrue(controlResult.output.contains("Fabric Loom: 1.17.21"),
            "The control did not actually report the bundled Loom 1.17.21:\n${controlResult.output}")
        assertTrue(controlResult.output.contains("BUILD SUCCESSFUL"), controlResult.output)
        assertFalse(controlResult.output.contains("G19_REAL_EXTERNAL_LOOM_APPLIED="),
            "External Loom must never be applied to the bundled control")
        assertFalse(controlResult.output.contains("FabricAmper FA010"), controlResult.output)
        assertEquals(1, fixture.mirrorJson(control).size,
            "A successful control must configure Amper's real Loom metadata mirror")
        assertFalse(Files.exists(fixture.generatedJson(control)),
            "The control help task must not generate task-owned mod metadata")
        assertOnlySettingsConsumer(fixture, control, yaml, settings)
    }

    private fun requirePublishedG29Plugin(): Path {
        val producerRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val buildRoot = producerRoot.resolve("build").toRealPath()
        val repository = buildRoot.resolve("g29-probe/maven-repo").toRealPath()
        require(repository.startsWith(buildRoot)) {
            "The published G29 plugin repository must be isolated under producer build/"
        }
        val marker = repository.resolve(
            "dev/fabric-amper/settings/dev.fabric-amper.settings.gradle.plugin/0.2.0/" +
                "dev.fabric-amper.settings.gradle.plugin-0.2.0.pom",
        )
        val modulePom = repository.resolve("dev/fabricamper/fabric-amper/0.2.0/fabric-amper-0.2.0.pom")
        val moduleJar = repository.resolve("dev/fabricamper/fabric-amper/0.2.0/fabric-amper-0.2.0.jar")
        require(listOf(marker, modulePom, moduleJar).all { Files.isRegularFile(it) }) {
            "G19 requires Prime's already published G29 local marker, plugin POM and plugin JAR"
        }
        return repository
    }

    private fun settingsFromPublishedRepository(repository: Path): String {
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
            plugins { id("dev.fabric-amper.settings") version "0.2.0" }
        """.trimIndent() + "\n"
    }

    private fun assertOnlySettingsConsumer(
        fixture: TestKitFixture, consumer: Path, originalYaml: String, originalSettings: String,
    ) {
        fixture.assertNoConsumerBuildScript(consumer)
        assertEquals(originalYaml, consumer.resolve("mod.yaml").readText(),
            "G19 must never rewrite the authored mod.yaml")
        assertEquals(originalSettings, consumer.resolve("settings.gradle.kts").readText(),
            "G19 must never rewrite or add plugin declarations to settings.gradle.kts")
        assertFalse(Files.exists(consumer.resolve("src/main/resources/fabric.mod.json")))
    }

    private fun uniqueMarker(output: String, label: String): String {
        val prefix = "$label="
        val values = output.lineSequence()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix).trim() }
            .toList()
        assertEquals(1, values.size,
            "Expected exactly one real $label marker emitted by the init script:\n$output")
        assertTrue(values.single().isNotBlank(), "Empty $label is not an external Loom proof")
        return values.single()
    }

    private fun assertRealExternalLoomJar(source: String) {
        val canonical = source.replace('\\', '/')
        assertTrue(
            Regex("""(?:^|/)net\.fabricmc/fabric-loom/1\.18\.4/[^\s]+/fabric-loom-1\.18\.4\.jar$""")
                .containsMatchIn(canonical),
            "CodeSource must point to the REAL resolved net.fabricmc:fabric-loom:1.18.4 JAR, got: $source",
        )
    }
}
