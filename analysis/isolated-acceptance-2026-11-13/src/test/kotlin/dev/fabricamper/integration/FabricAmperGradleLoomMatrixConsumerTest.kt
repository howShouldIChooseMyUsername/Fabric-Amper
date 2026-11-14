package dev.fabricamper.integration

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.readText

/**
 * Opt-in, *separate plugin-build* acceptance matrix.
 *
 * GradleRunner.withGradleVersion changes the consumer Gradle distribution,
 * NOT the bundled Loom dependency. Before G13, rebuild the producer plugin
 * with -PfabricAmperBundledLoomVersion=1.18.4 or 1.19.4, respectively.
 * The preflight checks the real Gradle TestKit plugin-under-test classpath
 * rather than interpreting a different Gradle runtime as a different Loom.
 *
 * G25 runs a separate published-Maven-metadata resolution test WITHOUT
 * withPluginClasspath(): injecting pre-resolved Loom jars would bypass the
 * org.gradle.plugin.api-version variant gate under test. Both sets of tests
 * use real published Gradle distributions, which may be downloaded by TestKit.
 * No consumer build.gradle, Minecraft startup, EULA or world operations.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_MATRIX", matches = "true")
class FabricAmperGradleLoomMatrixConsumerTest {
    @TempDir lateinit var suiteRoot: Path
    private val fixture get() = TestKitFixture(suiteRoot)

    @Test
    @EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_MATRIX_LOOM_1184", matches = "true")
    fun `G13 Gradle 9_7_0 with bundled Loom 1_18_4 configures a real Amper consumer`() {
        assertBundledLoomVersion("1.18.4")
        verifyRealAmperConsumer(gradleVersion = "9.7.0", loomVersion = "1.18.4", key = "g13-970-1184")
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_MATRIX_LOOM_1194", matches = "true")
    fun `G13 Gradle 9_9_1 with bundled Loom 1_19_4 configures a real Amper consumer`() {
        assertBundledLoomVersion("1.19.4")
        verifyRealAmperConsumer(gradleVersion = "9.9.1", loomVersion = "1.19.4", key = "g13-991-1194")
    }

    private fun verifyRealAmperConsumer(gradleVersion: String, loomVersion: String, key: String) {
        val f = fixture
        val root = f.project(key)
        f.write(root, "src/main/java/org/example/TestkitEntry.java", """
            package org.example;
            public final class TestkitEntry implements net.fabricmc.api.ModInitializer {
                @Override public void onInitialize() {}
            }
        """.trimIndent() + "\n")
        // The implementation class code source establishes which Loom jar
        // actually initialized the consumer (not just a Gradle version label).
        f.write(root, "probe-bundled-loom.init.gradle", """
            gradle.projectsEvaluated {
                def extension = rootProject.extensions.getByName('loom')
                def source = extension.class.protectionDomain.codeSource.location
                println('FABRIC_AMPER_ACTUAL_LOOM_JAR=' + source)
            }
        """.trimIndent() + "\n")
        val result = f.runWithGradleVersion(
            root, gradleVersion, key,
            "help", "--init-script", root.resolve("probe-bundled-loom.init.gradle").toString(),
        )
        assertEquals(TaskOutcome.SUCCESS, result.task(":help")?.outcome, result.output)
        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        assertTrue(
            result.output.contains("FABRIC_AMPER_ACTUAL_LOOM_JAR="),
            "Loom extension implementation code source was not exposed:\n${result.output}",
        )
        assertTrue(
            result.output.contains("fabric-loom-$loomVersion.jar"),
            "The consumer did not actually load the expected Loom $loomVersion binary:\n${result.output}",
        )
        assertEquals(1, f.mirrorJson(root).size, "The consumer must produce a real configuration-time Loom metadata mirror")
        assertTrue(!Files.exists(f.generatedJson(root)), "help must not execute the metadata generation task")

        // A successful help is not sufficient for G13: the same independent
        // consumer must compile real Loader API code and produce its own
        // non-remapping Minecraft 26.3 distribution under this exact Gradle
        // distribution and this exact producer-bundled Loom binary.
        val build = f.runWithGradleVersion(root, gradleVersion, key, "build")
        assertTrue(build.output.contains("BUILD SUCCESSFUL"), build.output)
        assertEquals(TaskOutcome.SUCCESS, build.task(":compileJava")?.outcome, build.output)
        assertEquals(TaskOutcome.SUCCESS, build.task(":jar")?.outcome, build.output)
        assertEquals(TaskOutcome.SUCCESS, build.task(":verifyFabricAmperJar")?.outcome, build.output)
        assertTrue(build.task(":remapJar") == null, "Minecraft 26.3 must not execute legacy remapJar:\n${build.output}")
        val jar = f.jars(root).single()
        assertEquals("testkit-mod-1.0.0+26.3.jar", jar.fileName.toString())
        val (rootCount, rootMetadata) = f.jarRootJson(jar)
        assertEquals(1, rootCount, "G13 distribution JAR must have exactly one root fabric.mod.json")
        assertArrayEquals(Files.readAllBytes(f.generatedJson(root)), rootMetadata)
        assertArrayEquals(Files.readAllBytes(f.mirrorJson(root).single()), rootMetadata)
        ZipFile(jar.toFile()).use { zip ->
            assertNotNull(zip.getEntry("org/example/TestkitEntry.class"), "Real compiled ModInitializer class is missing")
        }
        f.assertNoConsumerBuildScript(root)
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_MATRIX_LOOM_1194", matches = "true")
    fun `G25 Gradle 9_7_0 rejects published Loom 1_19_4 API variant`() {
        val (f, root) = variantResolutionFixture("g25-970-1194")
        val result = f.failWithGradleVersion(
            root, "9.7.0", "g25-970-1194", "help", pluginClasspath = false,
        )
        assertTrue(result.tasks.isEmpty(), "Variant selection must fail before any Gradle task graph:\n${result.output}")
        assertTrue(result.output.contains("net.fabricmc:fabric-loom:1.19.4"), result.output)
        val legacyVariantDiagnostic =
            result.output.contains("No matching variant") &&
                result.output.contains("org.gradle.plugin.api-version")
        val upgradedVariantDiagnostic =
            result.output.contains(
                "Plugin net.fabricmc:fabric-loom:1.19.4 requires at least Gradle 9.8.0. " +
                    "This build uses Gradle 9.7.0.",
            ) && result.output.contains("VariantSelectionByAttributesException")
        assertTrue(
            legacyVariantDiagnostic || upgradedVariantDiagnostic,
            "Expected an explicit Gradle API variant incompatibility, not an unrelated failure:\n${result.output}",
        )
        assertTrue(result.output.contains("9.8"), "Expected published Gradle API 9.8 compatibility boundary:\n${result.output}")
        requireNoTransportFailure(result.output)
        assertOnlySettingsAndYaml(f, root)
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_MATRIX_LOOM_1194", matches = "true")
    fun `G25 Gradle 9_8_0 resolves published Loom 1_19_4 API variant`() =
        verifyPublishedVariantResolution("9.8.0", "g25-980-1194")

    @Test
    @EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_MATRIX_LOOM_1194", matches = "true")
    fun `G25 Gradle 9_9_1 resolves published Loom 1_19_4 API variant`() =
        verifyPublishedVariantResolution("9.9.1", "g25-991-1194")

    private fun verifyPublishedVariantResolution(gradleVersion: String, key: String) {
        val (f, root) = variantResolutionFixture(key)
        val result = f.runWithGradleVersion(root, gradleVersion, key, "help", pluginClasspath = false)
        assertEquals(TaskOutcome.SUCCESS, result.task(":help")?.outcome, result.output)
        assertTrue(result.output.contains("FABRIC_AMPER_G25_MODULE_METADATA_RESOLVED=1.19.4"), result.output)
        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        requireNoTransportFailure(result.output)
        assertOnlySettingsAndYaml(f, root)
    }

    /**
     * G25 must resolve the published .module variants on this *consumer's*
     * Gradle, rather than using an already resolved plugin-test classpath.
     * This settings-only resolution probe intentionally does not apply the
     * Fabric Amper plugin. G13 separately verifies full Amper integration.
     */
    private fun variantResolutionFixture(key: String): Pair<TestKitFixture, Path> {
        val f = fixture
        val root = f.project(key)
        f.write(root, "settings.gradle.kts", """
            buildscript {
                repositories {
                    maven("https://maven.fabricmc.net/")
                    mavenCentral()
                    gradlePluginPortal()
                }
                dependencies {
                    classpath("net.fabricmc:fabric-loom:1.19.4")
                }
            }
            rootProject.name = "$key"
            println("FABRIC_AMPER_G25_MODULE_METADATA_RESOLVED=1.19.4")
        """.trimIndent() + "\n")
        return f to root
    }

    private fun assertOnlySettingsAndYaml(f: TestKitFixture, root: Path) {
        assertFalse(Files.exists(root.resolve("build.gradle")))
        assertFalse(Files.exists(root.resolve("build.gradle.kts")))
        assertTrue(Files.isRegularFile(root.resolve("mod.yaml")))
        assertTrue(root.resolve("settings.gradle.kts").readText().contains("net.fabricmc:fabric-loom:1.19.4"))
        assertEquals(
            f.resource("minimal-mod.yaml"), root.resolve("mod.yaml").readText(),
            "G25 published-metadata resolution must not modify the original consumer YAML",
        )
    }

    private fun assertBundledLoomVersion(expected: String) {
        // The Gradle java-gradle-plugin creates this metadata for the TestKit.
        // It reflects the host producer's actual implementation classpath.
        val metadata = Thread.currentThread().contextClassLoader
            .getResource("plugin-under-test-metadata.properties")
        assertNotNull(metadata, "Gradle TestKit plugin-under-test metadata is unavailable")
        val content = metadata!!.openStream().bufferedReader().use { it.readText() }
        assertTrue(
            content.contains("fabric-loom-$expected.jar"),
            "G13 needs a separately rebuilt producer plugin with " +
                "-PfabricAmperBundledLoomVersion=$expected; this plugin TestKit classpath is:\n$content",
        )
    }

    private fun requireNoTransportFailure(output: String) {
        for (marker in listOf(
            "SSLHandshakeException", "PKIX path building failed",
            "Connection timed out", "Read timed out", "UnknownHostException", "offline mode",
        )) {
            assertFalse(output.contains(marker, ignoreCase = true),
                "Network/transport failure must not be classified as a Loom variant result ($marker):\n$output")
        }
    }
}
