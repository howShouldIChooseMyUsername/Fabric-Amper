package dev.fabricamper.unit

import dev.fabricamper.config.FabricAmperConfigException
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.config.StrictYamlReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Regression contracts for the schema-1 parser and validator.
 * These assertions intentionally precede the owner-1 implementation fixes.
 * They do not launch Gradle or write into the project under test.
 */
class ConfigBoundaryRegressionUnitTest {
    @TempDir lateinit var root: Path

    private fun load(yaml: String) = ModYamlLoader.load(YamlFixtures.write(root, yaml))

    @Test
    fun `ten thousand and one YAML list elements exceed the declared collection limit`() {
        // One compact scalar list makes the collection-element count unambiguous.
        // The file stays below StrictYamlReader.MAX_FILE_BYTES.
        val yaml = buildString {
            append("values:\n")
            repeat(10_001) { append("  - item\n") }
        }
        assertTrue(yaml.toByteArray(Charsets.UTF_8).size < StrictYamlReader.MAX_FILE_BYTES)
        YamlFixtures.configError("FA002") {
            StrictYamlReader.readBytes(yaml.toByteArray(Charsets.UTF_8), root.resolve("mod.yaml"))
        }
    }

    @Test
    fun `explicit null is not silently treated as omitted optional mapping`() {
        val cases = listOf(
            "settings: null" to YamlFixtures.valid.replace("settings:\n  java:\n    release: 25\n", "settings: null\n"),
            "settings.java: null" to YamlFixtures.valid.replace("  java:\n    release: 25", "  java: null"),
            "mod.entrypoints: null" to YamlFixtures.mod(fields = "entrypoints: null"),
            "mod.depends: null" to YamlFixtures.mod(fields = "depends: null"),
            "mod.custom: null" to YamlFixtures.mod(fields = "custom: null"),
        )
        cases.forEach { (field, yaml) ->
            val error = YamlFixtures.configError("FA003") { load(yaml) }
            assertTrue(error.yamlPath.isNotBlank(), field)
        }
    }

    @Test
    fun `explicit null is not silently treated as omitted optional list`() {
        val cases = listOf(
            "repositories" to YamlFixtures.valid + "repositories: null\n",
            "dependencies" to YamlFixtures.valid + "dependencies: null\n",
            "test-dependencies" to YamlFixtures.valid + "test-dependencies: null\n",
            "mod.authors" to YamlFixtures.mod(fields = "authors: null"),
            "mod.mixins" to YamlFixtures.mod(fields = "mixins: null"),
            "mod.provides" to YamlFixtures.mod(fields = "provides: null"),
        )
        cases.forEach { (field, yaml) ->
            val error = YamlFixtures.configError("FA003") { load(yaml) }
            assertTrue(error.yamlPath.isNotBlank(), field)
        }
    }

    @Test
    fun `entrypoint group always requires an array even for a single entry`() {
        val scalar = YamlFixtures.mod(fields = """
            entrypoints:
              main: example.Main
        """)
        val objectInsteadOfArray = YamlFixtures.mod(fields = """
            entrypoints:
              main: {adapter: default, value: example.Main}
        """)
        for (yaml in listOf(scalar, objectInsteadOfArray)) {
            val error = YamlFixtures.configError("FA003") { load(yaml) }
            assertEquals("mod.entrypoints.main", error.yamlPath)
        }
    }

    @Test
    fun `entrypoint object requires an explicitly declared adapter`() {
        val yaml = YamlFixtures.mod(fields = """
            entrypoints:
              main:
                - value: example.Main
        """)
        val error = YamlFixtures.configError("FA003") { load(yaml) }
        assertTrue(error.yamlPath.contains("mod.entrypoints.main"), error.message)
    }

    @Test
    fun `list shaped metadata fields reject scalar substitution`() {
        val invalid = listOf(
            YamlFixtures.mod(fields = "mixins: example.mixins.json"),
            YamlFixtures.mod(fields = "authors: Author"),
            YamlFixtures.mod(fields = "provides: old_mod"),
            YamlFixtures.valid + "repositories: fabric\n",
            YamlFixtures.valid + "dependencies: \"com.example:test:1.0.0\"\n",
        )
        for (yaml in invalid) {
            YamlFixtures.configError("FA003") { load(yaml) }
        }
    }

    @Test
    fun `empty icon width map is rejected rather than emitted to Loader`() {
        val error = YamlFixtures.configError("FA003") {
            load(YamlFixtures.mod(fields = "icon: {}"))
        }
        assertEquals("mod.icon", error.yamlPath)
    }

    @Test
    fun `client-only icon resources require an enabled client source set`() {
        YamlFixtures.resource(root, "assets/example_mod/client.png", "png fixture", sourceSet = "client")
        val yaml = YamlFixtures.mod(fields = "icon: assets/example_mod/client.png")
        val error = YamlFixtures.configError("FA009") { load(yaml) }
        assertEquals("mod.icon", error.yamlPath)
        val splitYaml = yaml.replace(
            "settings:\n",
            "settings:\n  loom:\n    splitEnvironmentSourceSets: true\n",
        )
        assertEquals("assets/example_mod/client.png", load(splitYaml).mod.icon)
    }

    @Test
    fun `client-only mixin resources require an enabled client source set`() {
        YamlFixtures.resource(root, "client.mixins.json", "{}", sourceSet = "client")
        val yaml = YamlFixtures.mod(fields = """
            mixins:
              - config: client.mixins.json
                environment: client
        """)
        val error = YamlFixtures.configError("FA009") { load(yaml) }
        assertTrue(error.yamlPath.contains("mod.mixins[0].config"), error.message)
        val splitYaml = yaml.replace(
            "settings:\n",
            "settings:\n  loom:\n    splitEnvironmentSourceSets: true\n",
        )
        assertEquals("client.mixins.json", load(splitYaml).mod.mixins.single().config)
    }

    @Test
    fun `YAML resource symlink cannot escape either resource root`() {
        // A link to a project-private file is still unsafe even though it
        // remains within the overall project directory.
        val privateResource = root.resolve("private.mixins.json")
        Files.writeString(privateResource, "{}")
        val publicLink = root.resolve("src/main/resources/linked.mixins.json")
        Files.createDirectories(publicLink.parent)
        val linkAvailable = try {
            Files.createSymbolicLink(publicLink, privateResource)
            true
        } catch (_: UnsupportedOperationException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: java.io.IOException) {
            false
        }
        assumeTrue(linkAvailable, "symlink creation is unavailable on this host")
        val error = YamlFixtures.configError("FA009") {
            load(YamlFixtures.mod(fields = "mixins: [linked.mixins.json]"))
        }
        assertTrue(error.yamlPath.contains("mod.mixins"), error.message)
    }

    @Test
    fun `semantic YAML diagnostics identify actual line and column instead of synthetic 1 colon 1`() {
        val yaml = YamlFixtures.valid.replace("id: example_mod", "id: BAD_MOD")
        val error: FabricAmperConfigException = YamlFixtures.configError("FA009") { load(yaml) }
        // YamlFixtures.valid has schema on line 1 and the mod ID on line 4.
        assertEquals("mod.id", error.yamlPath)
        assertEquals(4, error.line)
        assertNotNull(error.column)
        assertTrue(error.column!! > 1, "expected a real column on line 4, got ${error.column}")
    }

    @Test
    fun `conflicting dependency scopes identify both original and new positions`() {
        val yaml = YamlFixtures.valid + """
            dependencies:
              - "com.example:duplicate:1.0.0": all
              - "com.example:unrelated:2.0.0": all
              - "com.example:duplicate:1.0.0": runtime-only
        """.trimIndent() + "\n"
        val error = YamlFixtures.configError("FA012") { load(yaml) }
        assertTrue(error.message!!.contains("dependencies[0]"), error.message)
        assertTrue(error.message!!.contains("dependencies[2]"), error.message)
    }
}
