package dev.fabricamper.unit

import dev.fabricamper.config.ModYamlLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Schema 1.1 loader targeting is separate from the legacy schema 1 consumer. */
class FabricAmperSchema11ParsingTest {
    @TempDir lateinit var workspace: Path
    private var sequence = 0

    @Test
    fun `old Fabric schema keeps its single target when schema is omitted`() {
        val legacy = YamlFixtures.valid.replace("schema: 1\n", "")
        val original = ModYamlLoader.load(YamlFixtures.write(workspace, legacy))
        assertEquals(listOf("fabric/mod"), original.targets.map { it.product })
        assertFalse(original.isMultiLoader)
        assertEquals("1", original.schemaVersion)
        assertNull(original.neoforge)
    }

    @Test
    fun `schema 11 accepts Fabric and NeoForge single targets and one item arrays`() {
        for ((product, fabric, neoforge) in listOf(
            Triple("fabric/mod", true, false),
            Triple("neoforge/mod", false, true),
            Triple("[fabric/mod]", true, false),
            Triple("[neoforge/mod]", false, true),
        )) {
            val model = load(config(product, fabric, neoforge))
            val expected = if (fabric) "fabric/mod" else "neoforge/mod"
            assertEquals("1.1", model.schemaVersion)
            assertEquals(listOf(expected), model.targets.map { it.product })
            assertFalse(model.isMultiLoader, "One target must keep single-loader project semantics")
            if (fabric) assertNotNull(model.fabric) else assertNull(model.fabric)
            if (neoforge) assertNotNull(model.neoforge) else assertNull(model.neoforge)
        }
    }

    @Test
    fun `schema 11 normalizes stable target order regardless of YAML array order`() {
        val forward = load(config("[fabric/mod, neoforge/mod]", fabric = true, neoforge = true))
        val reverse = load(config("[neoforge/mod, fabric/mod]", fabric = true, neoforge = true))
        val expected = listOf("fabric/mod", "neoforge/mod")
        assertEquals(expected, forward.targets.map { it.product })
        assertEquals(expected, reverse.targets.map { it.product })
        assertTrue(forward.isMultiLoader)
        assertTrue(reverse.isMultiLoader)
        assertNotNull(forward.fabric)
        assertNotNull(forward.neoforge)
    }

    @Test
    fun `schema 11 normalizes the Fabric library alias after reading product array`() {
        val model = load(config("[fabric/library]", fabric = true, neoforge = false))
        assertEquals("1.1", model.schemaVersion)
        assertEquals(listOf("fabric/api"), model.targets.map { it.product })
        assertFalse(model.isMultiLoader)
    }

    @Test
    fun `schema 11 accepts independent API and mod kinds across different loaders`() {
        val fabricApi = load(config("[fabric/api, neoforge/mod]", fabric = true, neoforge = true))
        val neoApi = load(config("[fabric/mod, neoforge/api]", fabric = true, neoforge = true))
        assertEquals(listOf("fabric/api", "neoforge/mod"), fabricApi.targets.map { it.product })
        assertEquals(listOf("fabric/mod", "neoforge/api"), neoApi.targets.map { it.product })
    }

    @Test
    fun `schema 11 rejects empty duplicate unknown and same loader product arrays`() {
        val invalid = listOf(
            "[]" to false,
            "[fabric/mod, fabric/mod]" to true,
            "[fabric/api, fabric/library]" to true,
            "[fabric/mod, fabric/api]" to true,
            "[neoforge/mod, neoforge/api]" to false,
            "[fabric/mod, unknown/mod]" to true,
            "[fabric/mod, 123]" to true,
        )
        for ((product, includesFabric) in invalid) {
            val message = config(product, fabric = includesFabric, neoforge = !includesFabric)
            YamlFixtures.configError("FA003") { load(message) }
        }
    }

    @Test
    fun `schema 11 requires quoted version and preserves schema 1 type rules`() {
        val modern = config("neoforge/mod", fabric = false, neoforge = true)
        YamlFixtures.configError("FA003") { load(modern.replace("schema: \"1.1\"", "schema: 1.1")) }
        YamlFixtures.configError("FA003") { load(modern.replace("schema: \"1.1\"", "schema: 1")) }
        YamlFixtures.configError("FA003") {
            load(config("[fabric/mod, neoforge/mod]", true, true).replace("schema: \"1.1\"", "schema: 1"))
        }
    }

    @Test
    fun `schema 11 refuses loader fields in shared root or unselected loader blocks`() {
        val fabric = config("fabric/mod", fabric = true, neoforge = false)
        val neo = config("neoforge/mod", fabric = false, neoforge = true)
        YamlFixtures.configError("FA003") {
            load(fabric + "neoforge:\n  version: \"26.3.0.101\"\n")
        }
        YamlFixtures.configError("FA003") {
            load(neo + "fabric:\n  loader: \"0.20.1\"\n")
        }
        YamlFixtures.configError("FA003") {
            load(fabric.replace(
                "  version: \"26.3\"\n",
                "  version: \"26.3\"\n  loader: \"0.20.1\"\n",
            ))
        }
        YamlFixtures.configError("FA003") {
            load(fabric.replace(
                "  group: org.example\n",
                "  group: org.example\n  entrypoints:\n    main: [org.example.ExampleMod]\n",
            ))
        }
    }

    private fun load(text: String) = Files.createDirectory(workspace.resolve("schema11-" + sequence++)).let {
        val path = it.resolve("mod.yaml")
        Files.writeString(path, text)
        ModYamlLoader.load(path)
    }

    private fun config(product: String, fabric: Boolean, neoforge: Boolean): String = buildString {
        appendLine("schema: \"1.1\"")
        appendLine("product: $product")
        appendLine("mod:")
        appendLine("  id: schemaeleven")
        appendLine("  version: \"1.0.0+26.3\"")
        appendLine("  group: org.example")
        appendLine("  name: Schema Test")
        appendLine("  license: MIT")
        appendLine("minecraft:")
        appendLine("  version: \"26.3\"")
        appendLine("settings:")
        appendLine("  java:")
        appendLine("    release: 25")
        if (fabric) {
            appendLine("fabric:")
            appendLine("  loader: \"0.20.1\"")
            appendLine("  api: \"0.165.2+26.3\"")
            appendLine("  mappings: mojang")
            appendLine("  entrypoints:")
            appendLine("    main: [org.example.ExampleMod]")
        }
        if (neoforge) {
            appendLine("neoforge:")
            appendLine("  version: \"26.3.0.101\"")
            appendLine("  modLoader: javafml")
        }
        val repos = buildList {
            add("mavenCentral")
            if (fabric) add("fabric")
            if (neoforge) add("neoforge")
        }
        appendLine("repositories: [" + repos.joinToString(", ") + "]")
    }
}
