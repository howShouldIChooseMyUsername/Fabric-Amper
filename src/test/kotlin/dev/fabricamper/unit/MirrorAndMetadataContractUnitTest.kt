package dev.fabricamper.unit

import com.google.gson.JsonParser
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.generator.MetadataMapper
import dev.fabricamper.loom.LoomMetadataMirror
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Offline contract tests of the configuration-time snapshot and emitted JSON.
 * Configuration-cache reuse, actual Loom consumption, and client/server launch
 * are deliberately out of scope here.
 */
class MirrorAndMetadataContractUnitTest {
    @TempDir lateinit var root: Path

    @Test
    fun `mirror copies metadata and referenced resources and repairs tampered copies`() {
        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        val metadata = """{"schemaVersion":1,"id":"sample_mod","version":"1.0"}""".toByteArray(StandardCharsets.UTF_8)
        val source = root.resolve("sample.mixins.json")
        Files.writeString(source, "{\"required\":false}", StandardCharsets.UTF_8)
        val references = mapOf("sample.mixins.json" to source.toFile())

        val mirror = LoomMetadataMirror.create(project, metadata, references).toPath()
        val resourceCopy = mirror.parent.resolve("sample.mixins.json")
        assertArrayEquals(metadata, Files.readAllBytes(mirror))
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(resourceCopy))
        assertTrue(mirror.toString().replace('\\', '/').contains("/.gradle/fabric-amper/loom/"))

        Files.writeString(mirror, "corrupt metadata", StandardCharsets.UTF_8)
        Files.writeString(resourceCopy, "corrupt reference", StandardCharsets.UTF_8)
        val repaired = LoomMetadataMirror.create(project, metadata, references).toPath()
        assertEquals(mirror, repaired)
        assertArrayEquals(metadata, Files.readAllBytes(repaired))
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(repaired.parent.resolve("sample.mixins.json")))
    }

    @Test
    fun `changing a referenced resource selects a different content-addressed mirror`() {
        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        val metadata = """{"schemaVersion":1,"id":"sample_mod","version":"1.0"}""".toByteArray(StandardCharsets.UTF_8)
        val source = root.resolve("sample.mixins.json")
        Files.writeString(source, "{\"required\":false}", StandardCharsets.UTF_8)
        val refs = mapOf("sample.mixins.json" to source.toFile())
        val first = LoomMetadataMirror.create(project, metadata, refs).toPath()
        val firstBytes = Files.readAllBytes(first.parent.resolve("sample.mixins.json"))

        Files.writeString(source, "{\"required\":true}", StandardCharsets.UTF_8)
        val second = LoomMetadataMirror.create(project, metadata, refs).toPath()
        assertNotEquals(first, second)
        assertArrayEquals(firstBytes, Files.readAllBytes(first.parent.resolve("sample.mixins.json")))
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(second.parent.resolve("sample.mixins.json")))
    }

    @Test
    fun `Loader JSON retains environment and side-specific entrypoints without emitting build-only client metadata`() {
        YamlFixtures.resource(root, "client.mixins.json", "{}", sourceSet = "client")
        val yaml = YamlFixtures.mod(fields = """
            environment: "*"
            languageAdapters:
              custom: example.CustomAdapter
            entrypoints:
              main:
                - example.CommonInitializer
              client:
                - adapter: custom
                  value: "example.ClientInitializer::init"
            mixins:
              - config: client.mixins.json
                environment: client
            custom:
              example:
                enabled: true
        """).replace(
            "settings:\n", "settings:\n  loom:\n    splitEnvironmentSourceSets: true\n",
        ) + """
            dependencies:
              - maven: "com.example:client-only-lib:1.0"
                target: client
                scope: compile-only
        """.trimIndent() + "\n"
        val model = ModYamlLoader.load(YamlFixtures.write(root, yaml))
        val json = JsonParser.parseString(MetadataMapper.toJson(model)).asJsonObject

        assertEquals("*", json.get("environment").asString)
        assertEquals("example.CommonInitializer",
            json.getAsJsonObject("entrypoints").getAsJsonArray("main")[0].asString)
        val clientEntry = json.getAsJsonObject("entrypoints").getAsJsonArray("client")[0].asJsonObject
        assertEquals("custom", clientEntry.get("adapter").asString)
        assertEquals("example.ClientInitializer::init", clientEntry.get("value").asString)
        val mixin = json.getAsJsonArray("mixins")[0].asJsonObject
        assertEquals("client", mixin.get("environment").asString)
        assertEquals("client.mixins.json", mixin.get("config").asString)
        assertEquals(true, json.getAsJsonObject("custom").getAsJsonObject("example").get("enabled").asBoolean)
        assertEquals("example.CustomAdapter", json.getAsJsonObject("languageAdapters").get("custom").asString)

        // Gradle-only target/source-set concepts must not leak into Loader v1.
        for (buildOnly in listOf("client", "settings", "dependencies", "repositories", "target")) {
            assertFalse(json.has(buildOnly), "unexpected Loader root property $buildOnly")
        }
        assertFalse(json.getAsJsonObject("depends").has("client-only-lib"))
    }
}
