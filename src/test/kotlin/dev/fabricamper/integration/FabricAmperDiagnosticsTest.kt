package dev.fabricamper.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets

/** Failure paths execute at Settings parsing, before Loom/MC artifacts are requested. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FabricAmperDiagnosticsTest {
    @TempDir lateinit var suiteRoot: Path
    private val fixture get() = TestKitFixture(suiteRoot)

    @Test
    fun `G8 Yarn cannot be selected for Minecraft 26_3`() {
        val f = fixture
        val root = f.project("g8", f.resource("minimal-mod.yaml").replace("mappings: mojang", "mappings: yarn"))
        val result = f.fail(root, "help")
        assertTrue(result.output.contains("FA004"), result.output)
        assertTrue(result.output.contains("minecraft.mappings"), result.output)
        f.assertNoConsumerBuildScript(root)
    }

    @Test
    fun `G12 existing common or client fabric metadata is rejected without overwriting`() {
        for (relative in listOf("src/main/resources/fabric.mod.json", "src/client/resources/fabric.mod.json")) {
            val f = fixture
            val root = f.project("g12")
            val existing = "{\"id\":\"do_not_replace\",\"sentinel\":true}\n"
            f.write(root, relative, existing)
            val result = f.fail(root, "help")
            assertTrue(result.output.contains("FA008"), result.output)
            assertEquals(existing, Files.readString(root.resolve(relative)))
            f.assertNoConsumerBuildScript(root)
        }
    }

    @Test
    fun `YAML aliases are exclusive and missing YAML has FA001 diagnostic`() {
        val f = fixture
        val root = f.project("aliases")
        f.write(root, "mod.yml", f.yaml(root))
        assertTrue(f.fail(root, "help").output.contains("FA001"))
        Files.delete(root.resolve("mod.yaml"))
        // mod.yml alone is a supported alias; this invocation requires Loom and is
        // exercised by the network opt-in suite, so only check the missing-file case.
        Files.delete(root.resolve("mod.yml"))
        assertTrue(f.fail(root, "help").output.contains("FA001"))
    }

    @Test
    fun `invalid YAML syntax and duplicate keys fail before project configuration`() {
        val f = fixture
        val samples = listOf(
            "schema: [\n" to "FA002",
            (f.resource("minimal-mod.yaml") + "\nschema: 1\n") to "FA002",
            f.resource("minimal-mod.yaml").replace("version: \"26.3\"", "version: 26.3") to "FA003",
        )
        samples.forEachIndexed { index, (yaml, expectedCode) ->
            val root = f.project("syntax-$index", yaml)
            val output = f.fail(root, "help").output
            assertTrue(output.contains(expectedCode), "Expected $expectedCode, got:\n$output")
        }
    }

    @Test
    fun `G16 invalid Class Tweaker header and client-only placement fail before Loom`() {
        val f = fixture
        val yaml = f.resource("minimal-mod.yaml").replace(
            "  entrypoints:",
            "  accessWidener: example.classtweaker\n  entrypoints:",
        )
        val wrongHeader = f.project("g16-header", yaml)
        f.write(wrongHeader, "src/main/resources/example.classtweaker", "accessWidener v2 named\n")
        val headerFailure = f.fail(wrongHeader, "help").output
        assertTrue(headerFailure.contains("FA009"), headerFailure)
        assertTrue(headerFailure.contains("mod.accessWidener"), headerFailure)
        assertEquals(
            "accessWidener v2 named\n",
            Files.readString(wrongHeader.resolve("src/main/resources/example.classtweaker")),
        )

        val clientOnly = f.project("g16-client-only", yaml)
        f.write(clientOnly, "src/client/resources/example.classtweaker", "classTweaker v1 official\n")
        val clientFailure = f.fail(clientOnly, "help").output
        assertTrue(clientFailure.contains("FA009"), clientFailure)
        assertTrue(clientFailure.contains("mod.accessWidener"), clientFailure)
        f.assertNoConsumerBuildScript(clientOnly)
    }

    @Test
    fun `G30 invalid UTF8 YAML anchors tags and multiple documents are rejected without Loom`() {
        val f = fixture
        val yaml = f.resource("minimal-mod.yaml")
        val invalid = listOf(
            "utf8" to byteArrayOf(0xC3.toByte(), 0x28),
            "anchor" to yaml.replace("mod:\n", "mod: &shared\n").toByteArray(StandardCharsets.UTF_8),
            "tag" to yaml.replace("product: fabric/mod", "product: !custom fabric/mod")
                .toByteArray(StandardCharsets.UTF_8),
            "multidoc" to (yaml + "\n---\nschema: 1\n").toByteArray(StandardCharsets.UTF_8),
        )
        for ((name, bytes) in invalid) {
            val root = f.project("g30-$name")
            f.writeBytes(root, "mod.yaml", bytes)
            val result = f.fail(root, "help")
            assertTrue(result.output.contains("FA002"), "G30 $name must be FA002:\n${result.output}")
            assertTrue(Files.readAllBytes(root.resolve("mod.yaml")).contentEquals(bytes), "Invalid YAML was modified")
            f.assertNoConsumerBuildScript(root)
        }
    }

    @Test
    fun `G26 contradictory authored Loader Java version requirement is not silently replaced`() {
        val f = fixture
        val yaml = f.resource("minimal-mod.yaml").replace(
            "  entrypoints:",
            "  depends:\n    java: \"<25\"\n  entrypoints:",
        )
        val root = f.project("g26-requirements", yaml)
        val failure = f.fail(root, "help").output
        assertTrue(failure.contains("FA009"), failure)
        assertTrue(failure.contains("mod.depends.java"), failure)
        assertEquals(yaml, f.yaml(root), "Authored version constraint must remain untouched on failure")
    }

    @Test
    fun `G18 client dependency without split source set fails before Loom`() {
        val f = fixture
        val yaml = f.resource("minimal-mod.yaml") + """

            dependencies:
              - maven: "com.google.code.gson:gson:2.14.0"
                target: client
                scope: compile-only
        """.trimIndent() + "\n"
        val root = f.project("g18-unsplit", yaml)
        val failure = f.fail(root, "help").output
        assertTrue(failure.contains("FA014"), failure)
        assertTrue(failure.contains("dependencies[0].target"), failure)
        f.assertNoConsumerBuildScript(root)
    }
}
