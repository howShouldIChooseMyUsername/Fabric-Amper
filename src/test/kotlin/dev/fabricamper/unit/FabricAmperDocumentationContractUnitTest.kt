package dev.fabricamper.unit

import dev.fabricamper.config.ModYamlLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Prevent documentation-only YAML contract drift. Full, copyable examples
 * are exercised by the actual strict decoder/validator, not a relaxed YAML
 * parser. Fragment recipes without all three mandatory root maps are skipped.
 */
class FabricAmperDocumentationContractUnitTest {
    @TempDir lateinit var fixtures: Path

    private val fullYamlFence = Regex("(?ms)^\\x60\\x60\\x60yaml[^\\r\\n]*\\r?\\n(.*?)^\\x60\\x60\\x60[ \\t]*$")
    private val documents = listOf(
        "README.md",
        "HUMAN.md",
        "HUMAN_QuickGuide.md",
        "HUMAN_QuickStart.md",
        "FabricAmperSKILL.md",
        "FabricAmperSKILL_SYN.md",
    )

    @Test
    fun `all complete documented starter YAML examples parse through current Fabric Amper schema`() {
        val root = Path.of(System.getProperty("user.dir"))
        var verified = 0
        for (document in documents) {
            val source = root.resolve(document)
            assertTrue(Files.isRegularFile(source), "Missing requested documentation: $document")
            val text = Files.readString(source, StandardCharsets.UTF_8)
            var index = 0
            for (match in fullYamlFence.findAll(text)) {
                index++
                val yaml = match.groupValues[1]
                // Intentional partial fragments, frontmatter examples and
                // migration anti-patterns are not standalone mod.yaml files.
                if (!(Regex("(?m)^product:").containsMatchIn(yaml)
                    && Regex("(?m)^mod:").containsMatchIn(yaml)
                    && Regex("(?m)^minecraft:").containsMatchIn(yaml))) continue
                val folder = fixtures.resolve("${document.removeSuffix(".md")}-$index")
                Files.createDirectories(folder)
                // Documentation explicitly requires users to create this file.
                // The fixture supplies safe release notes but no credentials.
                Files.writeString(folder.resolve("CHANGELOG.md"), "Documentation test changelog", StandardCharsets.UTF_8)
                val model = try {
                    ModYamlLoader.load(YamlFixtures.write(folder, yaml))
                } catch (failure: Exception) {
                    throw AssertionError("$document YAML block $index does not satisfy the current strict decoder: ${failure.message}", failure)
                }
                assertTrue(model.targets.isNotEmpty(), "Documentation must select at least one supported loader")
                assertTrue(model.schemaVersion in setOf("1", "1.1"),
                    "Documentation must select either explicit legacy schema 1 or namespaced schema 1.1")
                verified++
            }
        }
        assertTrue(verified >= 7, "Expected at least seven complete, independently parsed YAML examples; got $verified")
    }

    @Test
    fun `legacy schema 1 standalone examples stay in one dedicated reference per guide`() {
        val root = Path.of(System.getProperty("user.dir"))
        val legacyHeadings = mapOf(
            "README.md" to "## Historical schema 1 reference",
            "HUMAN.md" to "## Historical schema 1 reference",
            "HUMAN_QuickGuide.md" to "## Historical schema 1 reference",
            "FabricAmperSKILL.md" to "## Legacy schema 1 (Fabric-only reference)",
            "FabricAmperSKILL_SYN.md" to "## Legacy schema 1 (Fabric-only reference)",
        )
        for ((filename, heading) in legacyHeadings) {
            val text = Files.readString(root.resolve(filename), StandardCharsets.UTF_8)
            val section = text.indexOf(heading)
            assertTrue(section >= 0, "$filename must have one historical schema 1 section")
            val examples = Regex("(?m)^schema: 1\\r?$").findAll(text).toList()
            assertEquals(1, examples.size, "$filename must have one explicit legacy YAML example")
            assertTrue(examples.single().range.first > section,
                "$filename must place its legacy YAML example inside the historical section")
        }
    }

    @Test
    fun `english quick guide combines first-run instructions and feature cheat sheets`() {
        val root = Path.of(System.getProperty("user.dir"))
        val quickGuide = Files.readString(root.resolve("HUMAN_QuickGuide.md"), StandardCharsets.UTF_8)
        val quickStart = Files.readString(root.resolve("HUMAN_QuickStart.md"), StandardCharsets.UTF_8)

        listOf(
            "## The two-minute command path",
            "## Minimal project: copy and run",
            "## Feature cheat sheet",
            "## Dependencies, side safety, and test recipes",
            "## Shared Modrinth and CurseForge release settings",
            "## Publishing and API/library projects",
            "## Windows command cheat sheet",
            "## Troubleshooting and next steps",
            "fabric-amper.bat init quick_mod",
            "com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.3.0",
            "fabric-amper.bat plan",
            "publish.common",
        ).forEach { expected ->
            assertTrue(quickGuide.contains(expected), "Merged English Quick Guide is missing '$expected'")
        }
        assertTrue(
            quickGuide.contains("```yaml\n# yaml-language-server: \$schema=./schemas/mod.schema.json\n# \$schema: ./schemas/mod.schema.json\nschema: \"1.1\"\nproduct: fabric/mod"),
            "Quick Guide must provide a schema 1.1 starter YAML with both YAML Language Server and IntelliJ schema directives",
        )
        assertTrue(quickGuide.contains("```kotlin\npluginManagement"), "Quick Guide must provide Settings bootstrap")
        assertTrue(!quickGuide.contains("fabric-amper.bat setup"), "Quick Guide must not recommend removed setup command")
        assertTrue(quickGuide.contains("```java\npackage org.example;"), "Quick Guide must provide the starter source")
        assertTrue(!Regex("[\\uAC00-\\uD7A3]").containsMatchIn(quickGuide), "Quick Guide must be English-only")
        val readme = Files.readString(root.resolve("README.md"), StandardCharsets.UTF_8)
        assertTrue(!Regex("[\\uAC00-\\uD7A3]").containsMatchIn(readme), "README must be English-only")
        assertTrue(quickStart.contains("[Quick Guide](HUMAN_QuickGuide.md)"), "Old Quick Start path must link to merged guide")
        assertTrue(!quickStart.contains("```yaml"), "Quick Start must not retain a duplicate configuration tutorial")
    }
}
