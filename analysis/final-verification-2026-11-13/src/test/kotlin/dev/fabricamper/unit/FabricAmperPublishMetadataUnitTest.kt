package dev.fabricamper.unit

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.config.ModYamlModel
import dev.fabricamper.plugin.FabricAmperPublishMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** The exact JSON factories used to configure the explicit platform upload tasks. No HTTP or Gradle. */
class FabricAmperPublishMetadataUnitTest {
    @TempDir lateinit var project: Path

    private fun load(
        environment: String,
        publish: String,
        product: String = "fabric/mod",
    ): ModYamlModel {
        val base = YamlFixtures.valid.replace("product: fabric/mod", "product: $product")
        val fields = "environment: \"$environment\"" +
            (if (product == "fabric/mod") "" else "\ngroup: org.example.fabric")
        val yaml = YamlFixtures.mod(base, fields) + publish.trimIndent() + "\n"
        return ModYamlLoader.load(YamlFixtures.write(project, yaml))
    }

    private fun JsonObject.strings(key: String): List<String> =
        getAsJsonArray(key).map { it.asString }

    @Test
    fun `validated YAML determines Modrinth environment and CurseForge side tags by mod side`() {
        val publish = """
            publish:
              modrinth:
                projectId: sample-project
                tokenEnv: MODRINTH_TOKEN_ENV
              curseforge:
                projectId: 123456
                tokenEnv: CURSEFORGE_TOKEN_ENV
        """
        for ((side, modrinthEnvironment, curseforgeTags) in listOf(
            Triple("*", "client_and_server", listOf("26.3", "Fabric", "Client", "Server")),
            Triple("client", "client_only", listOf("26.3", "Fabric", "Client")),
            Triple("server", "server_only", listOf("26.3", "Fabric", "Server")),
        )) {
            val model = load(side, publish)
            val modrinth = JsonParser.parseString(FabricAmperPublishMetadata.modrinth(model)).asJsonObject
            assertEquals("sample-project", modrinth.get("project_id").asString, side)
            assertEquals("Example Mod 1.0.0+26.3", modrinth.get("name").asString, side)
            assertEquals("1.0.0+26.3", modrinth.get("version_number").asString, side)
            assertEquals("release", modrinth.get("version_type").asString, side)
            assertEquals(modrinthEnvironment, modrinth.get("environment").asString, side)
            assertEquals(listOf("26.3"), modrinth.strings("game_versions"), side)
            assertEquals(listOf("fabric"), modrinth.strings("loaders"), side)
            assertEquals(listOf("file"), modrinth.strings("file_parts"), side)
            assertEquals("file", modrinth.get("primary_file").asString, side)
            assertFalse(modrinth.get("featured").asBoolean, side)
            assertEquals(0, modrinth.getAsJsonArray("dependencies").size(), side)

            val curseforge = JsonParser.parseString(FabricAmperPublishMetadata.curseforge(model)).asJsonObject
            assertEquals(curseforgeTags, curseforge.strings("gameVersionNames"), side)
            assertEquals("Example Mod 1.0.0+26.3", curseforge.get("displayName").asString, side)
            assertEquals("release", curseforge.get("releaseType").asString, side)
            assertEquals("markdown", curseforge.get("changelogType").asString, side)
            assertFalse(curseforge.get("isMarkedForManualRelease").asBoolean, side)
            assertFalse(curseforge.has("gameVersions"), side)
            assertFalse(curseforge.has("relations"), side)

            for (payload in listOf(modrinth, curseforge)) {
                assertFalse(payload.has("tokenEnv"), side)
                assertFalse(payload.has("changelogFile"), side)
                assertFalse(payload.has("changelog"), "Task action supplies changelog after configuration: $side")
                assertFalse(payload.toString().contains("MODRINTH_TOKEN_ENV"), side)
                assertFalse(payload.toString().contains("CURSEFORGE_TOKEN_ENV"), side)
            }
        }
    }

    @Test
    fun `explicit platform overrides and relations are serialized without private changelog paths`() {
        Files.createDirectories(project.resolve("docs"))
        Files.writeString(project.resolve("docs/PRIVATE_RELEASE_NOTES.md"), "Local notes are read at task execution.\n")
        val model = load("client", """
            publish:
              modrinth:
                projectId: sample-project
                tokenEnv: MODRINTH_TOKEN_ENV
                versionName: Display version
                versionNumber: 2.0.0+26.3
                releaseType: beta
                gameVersions: ["26.3", "1.21.11"]
                loaders: [fabric, quilt]
                environment: client_only_server_optional
                changelogFile: docs/PRIVATE_RELEASE_NOTES.md
                dependencies:
                  - projectId: required-dependency
                    type: required
                  - projectId: optional-dependency
                    type: optional
              curseforge:
                projectId: 123456
                tokenEnv: CURSEFORGE_TOKEN_ENV
                displayName: Override display
                releaseType: alpha
                gameVersionNames: ["1.21.11", Fabric, Server]
                gameVersions: [111, 222]
                changelogType: text
                manualRelease: true
                changelogFile: docs/PRIVATE_RELEASE_NOTES.md
                relations:
                  - slug: dependency-project
                    type: requiredDependency
                  - slug: embedded-project
                    projectId: "456789"
                    type: embeddedLibrary
        """, product = "fabric/library")
        assertEquals("fabric/api", model.product, "The library alias must normalize through the real YAML loader")

        val modrinth = JsonParser.parseString(FabricAmperPublishMetadata.modrinth(model)).asJsonObject
        assertEquals("Display version", modrinth.get("name").asString)
        assertEquals("2.0.0+26.3", modrinth.get("version_number").asString)
        assertEquals("beta", modrinth.get("version_type").asString)
        assertEquals("client_only_server_optional", modrinth.get("environment").asString)
        assertEquals(listOf("26.3", "1.21.11"), modrinth.strings("game_versions"))
        assertEquals(listOf("fabric", "quilt"), modrinth.strings("loaders"))
        val dependencies = modrinth.getAsJsonArray("dependencies").map { it.asJsonObject }
        assertEquals(listOf("required-dependency", "optional-dependency"),
            dependencies.map { it.get("project_id").asString })
        assertEquals(listOf("required", "optional"), dependencies.map { it.get("dependency_type").asString })

        val curseforge = JsonParser.parseString(FabricAmperPublishMetadata.curseforge(model)).asJsonObject
        assertEquals("Override display", curseforge.get("displayName").asString)
        assertEquals("alpha", curseforge.get("releaseType").asString)
        assertEquals("text", curseforge.get("changelogType").asString)
        assertEquals(listOf("1.21.11", "Fabric", "Server"), curseforge.strings("gameVersionNames"))
        assertEquals(listOf(111, 222), curseforge.getAsJsonArray("gameVersions").map { it.asInt })
        assertEquals(true, curseforge.get("isMarkedForManualRelease").asBoolean)
        val relations = curseforge.getAsJsonObject("relations").getAsJsonArray("projects")
            .map { it.asJsonObject }
        assertEquals(listOf("dependency-project", "embedded-project"), relations.map { it.get("slug").asString })
        assertEquals(listOf("requiredDependency", "embeddedLibrary"), relations.map { it.get("type").asString })
        assertFalse(relations[0].has("projectID"))
        assertEquals("456789", relations[1].get("projectID").asString)

        for (payload in listOf(modrinth, curseforge)) {
            assertFalse(payload.has("changelog"), "Changelog bytes are injected only by the upload task")
            assertFalse(payload.has("changelogFile"), "Local file paths must stay out of upload JSON")
            assertFalse(payload.has("tokenEnv"), "Environment variable names must stay out of upload JSON")
            val json = payload.toString()
            for (privateValue in listOf(
                "docs/PRIVATE_RELEASE_NOTES.md", "Local notes are read at task execution.",
                "MODRINTH_TOKEN_ENV", "CURSEFORGE_TOKEN_ENV",
            )) {
                assertFalse(json.contains(privateValue), "Upload JSON leaked private config: $privateValue")
            }
        }
    }
}
