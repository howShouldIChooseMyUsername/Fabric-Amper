package dev.fabricamper.unit

import com.google.gson.JsonParser
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.generator.MetadataMapper
import dev.fabricamper.generator.ValidateFabricAmper
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class MetadataMapperUnitTest {
    @TempDir lateinit var project: Path

    @Test
    fun `produces valid deterministic Loader schema with single final LF`() {
        val path = YamlFixtures.write(project)
        val model = ModYamlLoader.load(path)
        val first = MetadataMapper.toUtf8Bytes(model)
        val next = MetadataMapper.toUtf8Bytes(model)
        assertArrayEquals(first, next)
        val json = String(first, StandardCharsets.UTF_8)
        assertTrue(json.startsWith("{\n  \"schemaVersion\": 1,"), json)
        assertTrue(json.endsWith("\n"))
        assertFalse(json.endsWith("\n\n"))
        val root = JsonParser.parseString(json).asJsonObject
        assertEquals(1, root.get("schemaVersion").asInt)
        assertEquals("example_mod", root.get("id").asString)
        assertEquals("1.0.0+26.3", root.get("version").asString)
        assertEquals("*", root.get("environment").asString)
        assertEquals("26.3", root.getAsJsonObject("depends").get("minecraft").asString)
        assertEquals(">=0.20.1", root.getAsJsonObject("depends").get("fabricloader").asString)
        assertEquals(">=25", root.getAsJsonObject("depends").get("java").asString)
    }

    @Test
    fun `preserves all public metadata shapes and omits build only settings`() {
        YamlFixtures.resource(project, "client.mixins.json", "{}")
        YamlFixtures.resource(project, "common.mixins.json", "{}")
        YamlFixtures.resource(project, "tweaks.classtweaker", "classTweaker v1 official\n")
        YamlFixtures.resource(project, "assets/example_mod/icon.png", "fake png fixture")
        val yaml = YamlFixtures.mod(fields = """
            group: com.example
            archivesName: custom-archive
            environment: client
            license: [MIT, Apache-2.0]
            authors:
              - Example Developer
              - name: Contributor
                contact:
                  homepage: https://example.org
            contributors: [Reviewer]
            contact:
              issues: https://example.org/issues
            icon:
              "32": assets/example_mod/icon.png
            provides: [old_example]
            entrypoints:
              main:
                - example.Main
                - adapter: kotlin
                  value: "example.EntryKt::init"
              client:
                - adapter: kotlin
                  value: example.ClientEntry
            languageAdapters:
              custom: example.CustomLanguageAdapter
            mixins:
              - common.mixins.json
              - config: client.mixins.json
                environment: client
            accessWidener: tweaks.classtweaker
            depends:
              minecraft: ["26.2", "26.3"]
            recommends:
              optional_mod: "*"
            suggests:
              addon_mod: ">=1.0.0"
            conflicts:
              legacy_mod: "<1.0.0"
            breaks:
              broken_mod: "1.0.0"
            custom:
              testdata:
                values: [1, true, null]
        """).replace(
            "settings:\n", "settings:\n  kotlin:\n    enabled: true\n",
        ) + """
            dependencies:
              - "com.example:ignored-build-lib:1.0.0": include
            repositories:
              - mavenCentral
        """.trimIndent() + "\n"
        val model = ModYamlLoader.load(YamlFixtures.write(project, yaml))
        ValidateFabricAmper.validateSourceResources(model)
        val root = JsonParser.parseString(MetadataMapper.toJson(model)).asJsonObject
        assertEquals("client", root.get("environment").asString)
        assertEquals(listOf("MIT", "Apache-2.0"),
            root.getAsJsonArray("license").map { it.asString })
        assertEquals("Contributor", root.getAsJsonArray("authors")[1].asJsonObject.get("name").asString)
        assertEquals("assets/example_mod/icon.png", root.getAsJsonObject("icon").get("32").asString)
        assertEquals("old_example", root.getAsJsonArray("provides")[0].asString)
        assertEquals("example.Main", root.getAsJsonObject("entrypoints").getAsJsonArray("main")[0].asString)
        assertEquals("kotlin", root.getAsJsonObject("entrypoints")
            .getAsJsonArray("main")[1].asJsonObject.get("adapter").asString)
        assertEquals("example.EntryKt::init", root.getAsJsonObject("entrypoints")
            .getAsJsonArray("main")[1].asJsonObject.get("value").asString)
        assertEquals("client", root.getAsJsonArray("mixins")[1].asJsonObject.get("environment").asString)
        assertEquals("tweaks.classtweaker", root.get("accessWidener").asString)
        assertEquals("example.CustomLanguageAdapter", root.getAsJsonObject("languageAdapters")
            .get("custom").asString)
        assertEquals(listOf("26.2", "26.3"),
            root.getAsJsonObject("depends").getAsJsonArray("minecraft").map { it.asString })
        assertTrue(root.has("recommends"))
        assertTrue(root.has("suggests"))
        assertTrue(root.has("conflicts"))
        assertTrue(root.has("breaks"))
        assertEquals(1, root.getAsJsonObject("custom").getAsJsonObject("testdata")
            .getAsJsonArray("values")[0].asInt)
        for (excluded in listOf("group", "archivesName", "minecraft", "settings",
            "repositories", "dependencies", "test-dependencies", "jars")) {
            assertFalse(root.has(excluded), "build-only key leaked: $excluded")
        }
    }

    @Test
    fun `normalizes dependency defaults while preserving authored predicates`() {
        val authored = YamlFixtures.mod(fields = """
            depends:
              minecraft: ["26.3", "26.3.1"]
              fabricloader: ">=0.20.1 <0.21.0"
              example_dependency: "*"
        """)
        val model = ModYamlLoader.load(YamlFixtures.write(project, authored))
        val json = JsonParser.parseString(MetadataMapper.toJson(model)).asJsonObject
        val depends = json.getAsJsonObject("depends")
        assertEquals(2, depends.getAsJsonArray("minecraft").size())
        assertEquals(">=0.20.1 <0.21.0", depends.get("fabricloader").asString)
        assertEquals("*", depends.get("example_dependency").asString)
        assertEquals(">=25", depends.get("java").asString)
        assertEquals(">=0.165.2", depends.get("fabric-api").asString)
        assertEquals("mod.depends (user)",
            MetadataMapper.dependencyOrigins(model, setOf("minecraft", "fabricloader", "example_dependency"))
                .getValue("minecraft"))
        assertEquals("profile (generated)",
            MetadataMapper.dependencyOrigins(model, setOf("minecraft", "fabricloader", "example_dependency"))
                .getValue("java"))
    }

    @Test
    fun `byte encoding is stable even when authored nested map order changes`() {
        val first = YamlFixtures.mod(fields = """
            custom:
              example:
                zebra: 2
                alpha: 1
        """)
        val second = YamlFixtures.mod(fields = """
            custom:
              example:
                alpha: 1
                zebra: 2
        """)
        val path = YamlFixtures.write(project, first)
        val firstBytes = MetadataMapper.toUtf8Bytes(ModYamlLoader.load(path))
        YamlFixtures.write(project, second)
        assertArrayEquals(firstBytes, MetadataMapper.toUtf8Bytes(ModYamlLoader.load(path)))
    }

    @Test
    fun `escapes special characters in metadata strings without damaging Unicode`() {
        val model = ModYamlLoader.load(YamlFixtures.write(project)).let {
            it.copy(mod = it.mod.copy(description = "한글 \"quoted\"\\slash\nnewline\ttab"))
        }
        val json = MetadataMapper.toJson(model)
        assertTrue(json.contains("\\n"))
        assertTrue(json.contains("\\t"))
        assertEquals("한글 \"quoted\"\\slash\nnewline\ttab",
            JsonParser.parseString(json).asJsonObject.get("description").asString)
    }
}
