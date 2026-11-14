package dev.fabricamper.unit

import com.google.gson.JsonParser
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.generator.MetadataMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * README 2026-11-13 fabric/api contract at the YAML/model boundary.
 * These are offline consumer-input tests, not claims about publication tasks.
 * The product normalization and publishing DTO follow config owner7's agreed API.
 */
class FabricLibraryProductContractUnitTest {
    @TempDir lateinit var root: Path

    private fun load(yaml: String) = ModYamlLoader.load(YamlFixtures.write(root, yaml))

    private fun library(product: String): String {
        val base = YamlFixtures.valid.replace("product: fabric/mod", "product: $product")
        return YamlFixtures.mod(base, "group: org.example.fabric\narchivesName: example-api")
    }

    @Test
    fun `fabric mod original yaml continues to load without publication configuration`() {
        val model = load(YamlFixtures.valid)
        assertEquals("fabric/mod", model.product)
        assertNull(model.publishing, "ordinary mods must not implicitly publish Maven artifacts")
        assertNull(model.mod.group)
        assertEquals("example_mod", model.mod.id)
        assertTrue(model.settings.java.sourcesJar)
        val json = JsonParser.parseString(MetadataMapper.toJson(model)).asJsonObject
        assertEquals("example_mod", json.get("id").asString)
        assertEquals("1.0.0+26.3", json.get("version").asString)
        assertFalse(json.has("product"))
        assertFalse(json.has("publishing"))
        assertFalse(json.has("group"))
        assertFalse(json.has("archivesName"))
    }

    @Test
    fun `api and library product alias emit identical Loader metadata and Maven identity inputs`() {
        val api = load(library("fabric/api"))
        val alias = load(library("fabric/library"))

        assertEquals("fabric/api", api.product)
        assertEquals(api.product, alias.product, "fabric/library must normalize to fabric/api")
        assertNull(api.publishing)
        assertNull(alias.publishing)
        assertEquals(api.mod, alias.mod)
        assertEquals(api.minecraft, alias.minecraft)
        assertEquals(api.settings, alias.settings)
        assertEquals("org.example.fabric", api.mod.group)
        assertEquals("example-api", api.mod.archivesName)
        assertEquals("1.0.0+26.3", api.mod.version)
        val apiJson = MetadataMapper.toJson(api)
        assertEquals(apiJson, MetadataMapper.toJson(alias))
        assertTrue(MetadataMapper.toUtf8Bytes(api).contentEquals(MetadataMapper.toUtf8Bytes(alias)),
            "API alias must produce byte-identical Loader metadata")
        val json = JsonParser.parseString(apiJson).asJsonObject
        assertFalse(json.has("product"), "product profile is Gradle-only")
        assertFalse(apiJson.contains("fabric/api") || apiJson.contains("fabric/library"),
            "neither product spelling may be injected into Loader JSON")
        assertFalse(json.has("group"), "Maven group is not Loader metadata")
        assertFalse(json.has("archivesName"), "Maven artifact ID is not Loader metadata")
        assertFalse(json.has("publishing"), "publishing is Gradle-only")
    }

    @Test
    fun `library products require authored Maven group instead of guessing one`() {
        for (product in listOf("fabric/api", "fabric/library")) {
            val yaml = YamlFixtures.valid.replace("product: fabric/mod", "product: $product")
            val error = YamlFixtures.configError("FA003") { load(yaml) }
            assertEquals("mod.group", error.yamlPath, product)
        }
    }

    @Test
    fun `mod product refuses publishing configuration even for local only`() {
        for (publishing in listOf(
            "publishing: {}\n",
            "publishing:\n  repository:\n    url: https://maven.example.org/releases\n",
        )) {
            val error = YamlFixtures.configError("FA003") { load(YamlFixtures.valid + publishing) }
            assertEquals("publishing", error.yamlPath)
        }
    }

    @Test
    fun `unknown product stays invalid while new API alias is accepted`() {
        val error = YamlFixtures.configError("FA003") {
            load(library("fabric/unsupported"))
        }
        assertEquals("product", error.yamlPath)
    }
}
