package dev.fabricamper.unit

import dev.fabricamper.config.FabricAmperSchemaVersion
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.plugin.FabricAmperPublishMetadata
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PublishCommonAndSchemaFallbackUnitTest {
    @TempDir lateinit var project: Path

    private fun load(input: String) = ModYamlLoader.load(YamlFixtures.write(project, input))
    private fun base() = YamlFixtures.valid.replace("schema: 1\n", "")
    private fun publishing() = base() + """
        publish:
          common:
            releaseType: beta
            name: Shared Release
            gameVersions: ["26.3", "26.3.1"]
            changelogFile: CHANGELOG.md
            dependencies:
              - slug: fabric-api
                modrinthId: fabric-api
                curseforgeId: "306612"
                type: required
              - slug: helper
                type: optional
          modrinth:
            projectId: mr-project
            tokenEnv: MODRINTH_TOKEN
          curseforge:
            projectId: 123456
            tokenEnv: CURSEFORGE_TOKEN
    """.trimIndent() + "\n"

    @Test
    fun defaultAndExplicitSchema() {
        assertEquals(1, FabricAmperSchemaVersion.LATEST)
        assertEquals(1, load(base()).schema)
        assertEquals(1, load(YamlFixtures.valid).schema)
        for (version in listOf("2", "0", "-1", "\"1\"", "null", "1.0", "true")) {
            val error = YamlFixtures.configError("FA003") {
                load(YamlFixtures.valid.replace("schema: 1", "schema: $version"))
            }
            assertEquals("schema", error.yamlPath)
        }
    }

    @Test
    fun commonInheritedIntoBothRealPlatformMetadataBuilders() {
        Files.writeString(project.resolve("CHANGELOG.md"), "Release notes\n")
        val model = load(publishing())
        val mr = model.publish!!.modrinth!!
        val cf = model.publish!!.curseforge!!
        assertEquals("beta", mr.releaseType)
        assertEquals("beta", cf.releaseType)
        assertEquals("Shared Release", mr.versionName)
        assertEquals("Shared Release", cf.displayName)
        assertEquals(listOf("26.3", "26.3.1"), mr.gameVersions)
        assertEquals(listOf("26.3", "26.3.1"), cf.gameVersionNames)
        assertNull(cf.gameVersions, "numeric CurseForge version IDs are not inferred")
        assertEquals("CHANGELOG.md", mr.changelogFile)
        assertEquals("CHANGELOG.md", cf.changelogFile)
        assertEquals(listOf("required", "optional"), mr.dependencies.map { it.type })
        assertEquals(listOf("requiredDependency", "optionalDependency"), cf.relations.map { it.type })
        assertEquals("306612", cf.relations[0].projectId)
        val mrJson = JsonParser.parseString(FabricAmperPublishMetadata.modrinth(model)).asJsonObject
        val cfJson = JsonParser.parseString(FabricAmperPublishMetadata.curseforge(model)).asJsonObject
        assertEquals("beta", mrJson.get("version_type").asString)
        assertEquals("Shared Release", cfJson.get("displayName").asString)
        assertEquals("fabric-api", mrJson.getAsJsonArray("dependencies")[0].asJsonObject.get("project_id").asString)
        assertEquals("306612", cfJson.getAsJsonObject("relations")
            .getAsJsonArray("projects")[0].asJsonObject.get("projectID").asString)
        assertFalse(mrJson.toString().contains("MODRINTH_TOKEN"))
        assertFalse(cfJson.toString().contains("CURSEFORGE_TOKEN"))
        assertFalse(mrJson.toString().contains("CHANGELOG.md"))
        assertFalse(cfJson.toString().contains("CHANGELOG.md"))
    }

    @Test
    fun platformOverridesAndAppendsByPlatformIdentifier() {
        Files.writeString(project.resolve("CHANGELOG.md"), "Release notes\n")
        val input = publishing().replace(
            "    tokenEnv: MODRINTH_TOKEN\n",
            """    tokenEnv: MODRINTH_TOKEN
    releaseType: alpha
    gameVersions: ["26.3"]
    changelog: Platform notes
    dependencies:
      - projectId: fabric-api
        type: embedded
      - projectId: added-mr
        type: required
""").replace(
            "    tokenEnv: CURSEFORGE_TOKEN\n",
            """    tokenEnv: CURSEFORGE_TOKEN
    displayName: CurseForge Override
    gameVersionNames: ["26.3", Fabric]
    gameVersions: [123]
    changelog: CF notes
    relations:
      - slug: fabric-api
        type: embeddedLibrary
      - slug: added-cf
        type: tool
""")
        val model = load(input)
        val mr = model.publish!!.modrinth!!
        val cf = model.publish!!.curseforge!!
        assertEquals("alpha", mr.releaseType)
        assertEquals(listOf("26.3"), mr.gameVersions)
        assertEquals("Platform notes", mr.changelog)
        assertNull(mr.changelogFile)
        assertEquals(listOf("helper", "fabric-api", "added-mr"), mr.dependencies.map { it.projectId })
        assertEquals(listOf("optional", "embedded", "required"), mr.dependencies.map { it.type })
        assertEquals("CurseForge Override", cf.displayName)
        assertEquals(listOf("26.3", "Fabric"), cf.gameVersionNames)
        assertEquals(listOf(123), cf.gameVersions)
        assertEquals("CF notes", cf.changelog)
        assertNull(cf.changelogFile)
        assertEquals(listOf("helper", "fabric-api", "added-cf"), cf.relations.map { it.slug })
        assertEquals("embeddedLibrary", cf.relations[1].type)
    }

    @Test
    fun emptyPlatformListsClearInheritedDependencies() {
        Files.writeString(project.resolve("CHANGELOG.md"), "Release notes\n")
        val model = load(publishing()
            .replace("    tokenEnv: MODRINTH_TOKEN\n", "    tokenEnv: MODRINTH_TOKEN\n    dependencies: []\n")
            .replace("    tokenEnv: CURSEFORGE_TOKEN\n", "    tokenEnv: CURSEFORGE_TOKEN\n    relations: []\n"))
        assertTrue(model.publish!!.modrinth!!.dependencies.isEmpty())
        assertTrue(model.publish!!.curseforge!!.relations.isEmpty())
    }

    @Test
    fun allFourSharedDependencyTypesMapToBothReleasePlatforms() {
        val input = base() + """
            publish:
              common:
                dependencies:
                  - slug: one
                    type: required
                  - slug: two
                    type: optional
                  - slug: three
                    type: incompatible
                  - slug: four
                    type: embedded
              modrinth:
                projectId: mr-project
                tokenEnv: MODRINTH_TOKEN
              curseforge:
                projectId: 123456
                tokenEnv: CURSEFORGE_TOKEN
        """.trimIndent() + "\n"
        val model = load(input)
        assertEquals(listOf("required", "optional", "incompatible", "embedded"),
            model.publish!!.modrinth!!.dependencies.map { it.type })
        assertEquals(listOf("requiredDependency", "optionalDependency", "incompatible", "embeddedLibrary"),
            model.publish!!.curseforge!!.relations.map { it.type })
    }

    @Test
    fun badCommonFieldsAndCommonOnlyMavenAreRejected() {
        Files.writeString(project.resolve("CHANGELOG.md"), "Release notes\n")
        for ((old, replacement, expected) in listOf(
            Triple("releaseType: beta", "releaseType: preview", "publish.common.releaseType"),
            Triple("gameVersions: [\"26.3\", \"26.3.1\"]", "gameVersions: [26.3]", "publish.common.gameVersions"),
            Triple("changelogFile: CHANGELOG.md", "changelogFile: ../escape.md", "publish.common.changelogFile"),
            Triple("type: required", "type: tool", "publish.common.dependencies"),
            Triple("curseforgeId: \"306612\"", "curseforgeId: \"-1\"", "publish.common.dependencies"),
            Triple("name: Shared Release", "unsupported: x", "publish.common.unsupported"),
        )) {
            val error = YamlFixtures.configError("FA003") { load(publishing().replace(old, replacement)) }
            assertTrue(error.yamlPath.startsWith(expected), error.yamlPath)
        }
        val error = YamlFixtures.configError("FA003") {
            load(base().replace("mod:\n", "mod:\n  group: org.example\n") + """
                publish:
                  common:
                    releaseType: beta
                  maven:
                    url: https://maven.example.org/releases
            """.trimIndent() + "\n")
        }
        assertEquals("publish.common", error.yamlPath)
    }
}
