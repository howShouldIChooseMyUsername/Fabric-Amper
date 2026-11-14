package dev.fabricamper.unit

import com.google.gson.JsonParser
import dev.fabricamper.util.PlatformReleaseClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure JSON serialization only. NEVER call uploadModrinth/uploadCurseForge or
 * issue HTTP/API requests: the acceptance policy explicitly forbids live tests.
 */
class PublishMetadataBuilderUnitTest {
    @Test
    fun `Modrinth JSON uses v2 snake case keys with a matching file multipart reference`() {
        val metadata = PlatformReleaseClient.buildModrinthMetadata(
            PlatformReleaseClient.ModrinthVersion(
                projectId = "sample-project",
                name = "Sample Fabric 1.0",
                versionNumber = "1.0.0+26.3",
                versionType = "beta",
                gameVersions = listOf("26.3"),
                loaders = listOf("fabric"),
                environment = "client_only",
                changelog = "Offline release fixture",
                dependencies = listOf(
                    PlatformReleaseClient.ModrinthDependency("external-project", "required"),
                    PlatformReleaseClient.ModrinthDependency("optional-project", "optional"),
                ),
            ),
        )
        val json = JsonParser.parseString(metadata).asJsonObject
        assertEquals("sample-project", json.get("project_id").asString)
        assertEquals("Sample Fabric 1.0", json.get("name").asString)
        assertEquals("1.0.0+26.3", json.get("version_number").asString)
        assertEquals("beta", json.get("version_type").asString)
        assertEquals("client_only", json.get("environment").asString)
        assertEquals(listOf("26.3"), json.getAsJsonArray("game_versions").map { it.asString })
        assertEquals(listOf("fabric"), json.getAsJsonArray("loaders").map { it.asString })
        assertEquals(listOf("file"), json.getAsJsonArray("file_parts").map { it.asString })
        assertEquals("file", json.get("primary_file").asString)
        assertFalse(json.get("featured").asBoolean)
        val relations = json.getAsJsonArray("dependencies").map { it.asJsonObject }
        assertEquals(listOf("external-project", "optional-project"), relations.map { it.get("project_id").asString })
        assertEquals(listOf("required", "optional"), relations.map { it.get("dependency_type").asString })
        assertFalse(json.has("tokenEnv"))
        assertFalse(json.has("Authorization"))
        assertFalse(json.has("fabric_mod_json"))
    }

    @Test
    fun `CurseForge JSON uses upload API string enums and relations projects not v1 numeric enums`() {
        val metadata = PlatformReleaseClient.buildCurseForgeMetadata(
            PlatformReleaseClient.CurseForgeFile(
                projectId = 123456,
                displayName = "Sample Fabric 1.0",
                gameVersionNames = listOf("26.3", "Fabric", "Client", "Server"),
                gameVersions = listOf(111, 222),
                releaseType = "alpha",
                changelog = "Offline release fixture",
                changelogType = "markdown",
                manualRelease = true,
                relations = listOf(
                    PlatformReleaseClient.CurseForgeRelation("required-project", "requiredDependency"),
                    PlatformReleaseClient.CurseForgeRelation("embedded-lib", "embeddedLibrary", "456789"),
                ),
            ),
        )
        val json = JsonParser.parseString(metadata).asJsonObject
        assertEquals("alpha", json.get("releaseType").asString)
        assertEquals("markdown", json.get("changelogType").asString)
        assertEquals("Sample Fabric 1.0", json.get("displayName").asString)
        assertTrue(json.get("isMarkedForManualRelease").asBoolean)
        assertEquals(listOf("26.3", "Fabric", "Client", "Server"),
            json.getAsJsonArray("gameVersionNames").map { it.asString })
        assertEquals(listOf(111, 222), json.getAsJsonArray("gameVersions").map { it.asInt })
        val relations = json.getAsJsonObject("relations").getAsJsonArray("projects").map { it.asJsonObject }
        assertEquals(listOf("requiredDependency", "embeddedLibrary"), relations.map { it.get("type").asString })
        assertEquals("required-project", relations[0].get("slug").asString)
        assertFalse(relations[0].has("projectID"))
        assertEquals("456789", relations[1].get("projectID").asString)
        assertFalse(json.has("modLoader"), "Public CurseForge v1 enums do not belong to the upload payload")
        assertFalse(json.has("projectId"), "CurseForge project ID belongs in the author upload endpoint")
        assertFalse(json.has("tokenEnv"))
        assertFalse(json.has("X-Api-Token"))
    }

    @Test
    fun `empty optional relations and unverified numeric game versions are omitted safely`() {
        val modrinth = JsonParser.parseString(PlatformReleaseClient.buildModrinthMetadata(
            PlatformReleaseClient.ModrinthVersion("sample", "Sample", "1.0", listOf("26.3"),
                environment = "client_and_server"),
        )).asJsonObject
        assertEquals(0, modrinth.getAsJsonArray("dependencies").size())
        assertEquals("release", modrinth.get("version_type").asString)
        assertEquals("client_and_server", modrinth.get("environment").asString)

        val curseforge = JsonParser.parseString(PlatformReleaseClient.buildCurseForgeMetadata(
            PlatformReleaseClient.CurseForgeFile(123456, "Sample", listOf("26.3", "Fabric")),
        )).asJsonObject
        assertEquals("release", curseforge.get("releaseType").asString)
        assertFalse(curseforge.get("isMarkedForManualRelease").asBoolean)
        assertFalse(curseforge.has("gameVersions"), "Do not invent unsupported CurseForge numeric version IDs")
        assertFalse(curseforge.has("relations"), "Do not infer remote relations from Loader or Maven dependencies")
    }

    @Test
    fun `pure builder rejects unsafe enums duplicate platform references and invalid numeric IDs`() {
        fun modrinth() = PlatformReleaseClient.ModrinthVersion("sample", "Sample", "1.0", listOf("26.3"),
            environment = "client_and_server")
        assertThrows(IllegalArgumentException::class.java) {
            PlatformReleaseClient.buildModrinthMetadata(modrinth().copy(versionType = "snapshot"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlatformReleaseClient.buildModrinthMetadata(modrinth().copy(gameVersions = emptyList()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlatformReleaseClient.buildModrinthMetadata(modrinth().copy(dependencies = listOf(
                PlatformReleaseClient.ModrinthDependency("same-project", "required"),
                PlatformReleaseClient.ModrinthDependency("same-project", "optional"),
            )))
        }
        fun curseforge() = PlatformReleaseClient.CurseForgeFile(123456, "Sample", listOf("26.3", "Fabric"))
        assertThrows(IllegalArgumentException::class.java) {
            PlatformReleaseClient.buildCurseForgeMetadata(curseforge().copy(projectId = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlatformReleaseClient.buildCurseForgeMetadata(curseforge().copy(changelogType = "yaml"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlatformReleaseClient.buildCurseForgeMetadata(curseforge().copy(gameVersions = listOf(111, 111)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlatformReleaseClient.buildCurseForgeMetadata(curseforge().copy(relations = listOf(
                PlatformReleaseClient.CurseForgeRelation("sample-dep", "requiredDependency", "not-a-numeric-id"),
            )))
        }
    }
}
