package dev.fabricamper.integration

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * A small G19 collision probe and a separately gated G9 legacy remapping probe.
 * Neither test starts a Minecraft client/server, accepts an EULA or touches worlds.
 */
class FabricAmperCompatibilityConsumerTest {
    @TempDir lateinit var suiteRoot: Path
    private val fixture get() = TestKitFixture(suiteRoot)

    @Test
    fun `G19 pre-existing Loom extension is rejected with explicit FA010 diagnostic`() {
        val f = fixture
        val root = f.project("g19-loom-extension")
        // Registered by an init script BEFORE settings.gradle.kts applies Amper.
        // Deliberately occupy the exact extension name of an already-applied
        // Loom plugin without downloading/initializing a second real Loom.
        // This verifies the extension-collision half of G19, not an external
        // Loom version/classloader conflict.
        val initScript = """
            gradle.beforeProject { p ->
                if (p == p.rootProject) {
                    p.extensions.add('loom', new Object())
                    println('FABRIC_AMPER_G19_EXISTING_LOOM_EXTENSION=true')
                }
            }
        """.trimIndent() + "\n"
        f.write(root, "preapplied-loom-extension.init.gradle", initScript)
        val result = f.fail(root, "help", "--init-script", root.resolve("preapplied-loom-extension.init.gradle").toString())
        assertTrue(
            result.output.contains("FABRIC_AMPER_G19_EXISTING_LOOM_EXTENSION=true"),
            "Pre-project Loom-extension collision was not injected:\n${result.output}",
        )
        assertTrue(result.output.contains("FA010"), result.output)
        assertTrue(result.output.contains("Loom plugin conflict"), result.output)
        assertTrue(result.output.contains("loom extension"), result.output)
        assertTrue(
            Files.readString(root.resolve("mod.yaml")).contains("id: testkit_mod"),
            "Collision must not overwrite the consumer's YAML",
        )
        f.assertNoConsumerBuildScript(root)
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
    @EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_LEGACY", matches = "true")
    fun `G9 legacy Minecraft 1_21_11 selects remapping Loom and produces remapJar distribution`() {
        // TWO explicit environment gates are required. This consumes legacy
        // Minecraft mappings/game artifacts and needs an installed JDK 21
        // toolchain in addition to the Gradle daemon's JDK 25. No auto-run.
        val f = fixture
        val legacyYaml = f.resource("minimal-mod.yaml")
            .replace("version: \"1.0.0+26.3\"", "version: \"1.0.0+1.21.11\"")
            .replace("version: \"26.3\"", "version: \"1.21.11\"")
            .replace("  fabricApi: \"0.165.2+26.3\"\n", "")
            .replace("release: 25", "release: 21")
        val root = f.project("g9-legacy-remap", legacyYaml)
        f.write(root, "src/main/java/org/example/TestkitEntry.java", """
            package org.example;
            public final class TestkitEntry implements net.fabricmc.api.ModInitializer {
                @Override public void onInitialize() {}
            }
        """.trimIndent() + "\n")
        f.assertNoConsumerBuildScript(root)

        val result = f.run(root, "remapJar")
        assertEquals(TaskOutcome.SUCCESS, result.task(":remapJar")?.outcome, result.output)
        assertNotNull(result.task(":compileJava"), "Legacy remap must compile a real Java mod entrypoint")
        val distribution = root.resolve("build/libs/testkit-mod-1.0.0+1.21.11.jar")
        assertTrue(Files.isRegularFile(distribution), "Legacy remapJar distribution JAR is missing")
        assertEquals(1, f.jarRootJson(distribution).first, "Legacy distribution must have one root fabric.mod.json")
        ZipFile(distribution.toFile()).use { zip ->
            assertNotNull(zip.getEntry("org/example/TestkitEntry.class"), "Legacy compiled mod class missing")
        }
        assertTrue(f.yaml(root).contains("mappings: mojang"))
        f.assertNoConsumerBuildScript(root)
    }
}
