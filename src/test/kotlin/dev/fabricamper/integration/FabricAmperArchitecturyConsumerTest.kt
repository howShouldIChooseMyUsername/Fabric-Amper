package dev.fabricamper.integration

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText

/** Real Maven/Gradle 9 + Loom smoke test; no consumer build script or handwritten fabric.mod.json. */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
class FabricAmperArchitecturyConsumerTest {
    @TempDir lateinit var root: Path

    @Test
    fun `settings only mod yml resolves real Architectury 26_3 and updates on YAML revision`() {
        val fixture = TestKitFixture(root)
        val yaml = fixture.resource("minimal-mod.yaml") + "\narchitectury:\n  enable: true\n"
        val consumer = fixture.project("architectury-26-3", yaml, alias = "mod.yml")
        fixture.assertNoConsumerBuildScript(consumer)
        fixture.write(consumer, "src/main/java/org/example/TestkitEntry.java", """
            package org.example;
            import net.fabricmc.api.ModInitializer;
            import dev.architectury.platform.Platform;
            public class TestkitEntry implements ModInitializer {
                @Override public void onInitialize() {
                    Platform.getMinecraftVersion();
                }
            }
        """.trimIndent() + "\n")

        // Loom may create its Minecraft manifest during the first task graph.
        // Warm that external cache before observing the Fabric Amper CC contract.
        fixture.run(consumer, "help")
        val built = fixture.run(consumer, "build", "--configuration-cache")
        assertTrue(built.output.contains("BUILD SUCCESSFUL"), built.output)
        val metadata = JsonParser.parseString(fixture.generatedJson(consumer).readText()).asJsonObject
        assertEquals(">=22.0.5", metadata.getAsJsonObject("depends").get("architectury").asString)
        assertEquals(1, fixture.jarRootJson(fixture.jars(consumer).single()).first)
        fixture.assertNoConsumerBuildScript(consumer)

        val reused = fixture.run(consumer, "build", "--configuration-cache")
        assertTrue(reused.output.contains("Reusing configuration cache"), reused.output)

        fixture.write(consumer, "mod.yml", yaml.replace("  enable: true", "  enable: true\n  version: \"22.0.4\""))
        val revised = fixture.run(consumer, "build", "--configuration-cache")
        assertFalse(revised.output.contains("Reusing configuration cache"), revised.output)
        assertTrue(revised.output.contains("BUILD SUCCESSFUL"), revised.output)
        val newMetadata = JsonParser.parseString(fixture.generatedJson(consumer).readText()).asJsonObject
        assertEquals(">=22.0.4", newMetadata.getAsJsonObject("depends").get("architectury").asString)
    }
}
