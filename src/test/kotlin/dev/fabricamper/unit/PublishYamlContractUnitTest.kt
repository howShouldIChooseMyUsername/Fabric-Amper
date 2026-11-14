package dev.fabricamper.unit

import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.generator.MetadataMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * README 2026-11-13 unified publish profile contract.
 * Offline strict YAML input -> model tests only; no Gradle task or network calls.
 * Uses worker7's PublishSpec/MavenSpec/ModrinthSpec/CurseForgeSpec DTO contract.
 */
class PublishYamlContractUnitTest {
    @TempDir lateinit var project: Path

    private fun yaml(product: String = "fabric/mod", extraMod: String = "", publish: String = ""): String {
        val base = YamlFixtures.valid.replace("product: fabric/mod", "product: $product")
        val withMod = if (extraMod.isBlank()) base else YamlFixtures.mod(base, extraMod)
        return withMod + publish
    }

    private fun load(text: String) = ModYamlLoader.load(YamlFixtures.write(project, text))

    private fun rejectedAt(text: String, prefix: String) {
        val error = YamlFixtures.configError("FA003") { load(text) }
        assertTrue(error.yamlPath == prefix || error.yamlPath.startsWith("$prefix."),
            "Expected FA003 at $prefix, got ${error.yamlPath}: ${error.reason}")
    }

    @Test
    fun `all three products support explicit Modrinth and CurseForge publish sections`() {
        for (product in listOf("fabric/mod", "fabric/api", "fabric/library")) {
            val modFields = if (product == "fabric/mod") "" else "group: org.example.fabric"
            val model = load(yaml(product, modFields, """
                publish:
                  modrinth:
                    projectId: "AABBCCDD"
                    tokenEnv: MODRINTH_TOKEN
                  curseforge:
                    projectId: 123456
                    tokenEnv: CURSEFORGE_TOKEN
            """.trimIndent() + "\n"))
            assertEquals(if (product == "fabric/library") "fabric/api" else product, model.product)
            assertEquals("example_mod", model.mod.id)
            assertNull(model.publish?.maven)
            assertEquals("AABBCCDD", model.publish?.modrinth?.projectId)
            assertEquals("MODRINTH_TOKEN", model.publish?.modrinth?.tokenEnv)
            assertEquals("release", model.publish?.modrinth?.releaseType)
            assertEquals(123456, model.publish?.curseforge?.projectId)
            assertEquals("CURSEFORGE_TOKEN", model.publish?.curseforge?.tokenEnv)
            assertEquals("markdown", model.publish?.curseforge?.changelogType)
            assertEquals(false, model.publish?.curseforge?.manualRelease)
        }
    }

    @Test
    fun `Maven package credentials pair and all three publish destinations coexist on all products`() {
        Files.writeString(project.resolve("CHANGELOG.md"), "Initial release\n")
        for (product in listOf("fabric/mod", "fabric/api", "fabric/library")) {
            val model = load(yaml(product, "group: org.example.fabric", """
                publish:
                  maven:
                    url: https://maven.pkg.github.com/OWNER/REPOSITORY
                    credentialsFromEnv: [GITHUB_ACTOR, GITHUB_TOKEN]
                  modrinth:
                    projectId: "AABBCCDD"
                    tokenEnv: MODRINTH_TOKEN
                    releaseType: beta
                    gameVersions: ["26.3"]
                    loaders: [fabric]
                    changelogFile: CHANGELOG.md
                  curseforge:
                    projectId: 123456
                    tokenEnv: CURSEFORGE_TOKEN
                    releaseType: alpha
                    gameVersionNames: ["26.3", Fabric, Client, Server]
                    changelogFile: CHANGELOG.md
            """.trimIndent() + "\n"))
            assertEquals("org.example.fabric", model.mod.group)
            assertEquals("https://maven.pkg.github.com/OWNER/REPOSITORY", model.publish?.maven?.url)
            assertEquals(listOf("GITHUB_ACTOR", "GITHUB_TOKEN"), model.publish?.maven?.credentialsFromEnv)
            assertEquals("beta", model.publish?.modrinth?.releaseType)
            assertEquals(listOf("26.3"), model.publish?.modrinth?.gameVersions)
            assertEquals(listOf("fabric"), model.publish?.modrinth?.loaders)
            assertEquals("CHANGELOG.md", model.publish?.modrinth?.changelogFile)
            assertEquals("alpha", model.publish?.curseforge?.releaseType)
            assertEquals(listOf("26.3", "Fabric", "Client", "Server"), model.publish?.curseforge?.gameVersionNames)
            assertEquals("CHANGELOG.md", model.publish?.curseforge?.changelogFile)
        }
    }

    @Test
    fun `publish omission preserves old mod input and API legacy publishing behavior`() {
        val oldMod = load(yaml())
        assertEquals("fabric/mod", oldMod.product)
        assertNull(oldMod.publishing)
        assertNull(oldMod.publish)
        for (product in listOf("fabric/api", "fabric/library")) {
            val legacy = load(yaml(product, "group: org.example.fabric", """
                publishing:
                  repository:
                    url: https://maven.example.org/releases
                    usernameEnv: RELEASE_USER
                    passwordEnv: RELEASE_PASSWORD
            """.trimIndent() + "\n"))
            assertEquals("fabric/api", legacy.product)
            assertEquals("https://maven.example.org/releases", legacy.publishing?.repository?.url)
            assertNull(legacy.publish)
        }
    }

    @Test
    fun `legacy remote can coexist with non Maven publish targets but not a second remote Maven`() {
        val oldRemote = """
            publishing:
              repository:
                url: https://maven.example.org/releases
        """.trimIndent() + "\n"
        val modrinth = """
            publish:
              modrinth:
                projectId: sample-project
                tokenEnv: MODRINTH_TOKEN
        """.trimIndent() + "\n"
        val maven = """
            publish:
              maven:
                url: https://maven.pkg.github.com/OWNER/REPOSITORY
        """.trimIndent() + "\n"
        val projectFields = "group: org.example.fabric"
        val both = load(yaml("fabric/library", projectFields, oldRemote + modrinth))
        assertEquals("fabric/api", both.product)
        assertEquals("https://maven.example.org/releases", both.publishing?.repository?.url)
        assertNull(both.publish?.maven)
        assertEquals("sample-project", both.publish?.modrinth?.projectId)
        rejectedAt(yaml("fabric/api", projectFields, oldRemote + maven), "publish")
    }

    @Test
    fun `publish Maven requires Maven group even for a regular mod`() {
        rejectedAt(yaml(publish = """
            publish:
              maven:
                url: https://maven.pkg.github.com/OWNER/REPOSITORY
        """.trimIndent() + "\n"), "mod.group")
    }

    @Test
    fun `typed Modrinth dependency and CurseForge relation lists accept explicit platform identifiers`() {
        val model = load(yaml(publish = """
            publish:
              modrinth:
                projectId: "AABBCCDD"
                tokenEnv: MODRINTH_TOKEN
                releaseType: release
                dependencies:
                  - projectId: "required-project-id"
                    type: required
                  - projectId: "optional-project-id"
                    type: optional
                  - projectId: "incompatible-project-id"
                    type: incompatible
                  - projectId: "embedded-project-id"
                    type: embedded
              curseforge:
                projectId: 123456
                tokenEnv: CURSEFORGE_TOKEN
                changelogType: markdown
                manualRelease: true
                gameVersions: [111, 222]
                relations:
                  - slug: another-project
                    type: requiredDependency
                  - slug: another-helper
                    projectId: "456789"
                    type: embeddedLibrary
                  - slug: incompatible-project
                    type: incompatible
                  - slug: optional-project
                    type: optionalDependency
                  - slug: tool-project
                    type: tool
        """.trimIndent() + "\n"))
        assertEquals("fabric/mod", model.product)
        assertEquals(listOf("required", "optional", "incompatible", "embedded"),
            model.publish?.modrinth?.dependencies?.map { it.type })
        assertEquals(listOf(111, 222), model.publish?.curseforge?.gameVersions)
        assertEquals(true, model.publish?.curseforge?.manualRelease)
        assertEquals(listOf("requiredDependency", "embeddedLibrary", "incompatible", "optionalDependency", "tool"),
            model.publish?.curseforge?.relations?.map { it.type })
        assertEquals("456789", model.publish?.curseforge?.relations?.get(1)?.projectId)
    }

    @Test
    fun `publish configuration never enters generated Fabric Loader metadata`() {
        val baseline = load(yaml())
        val configured = load(yaml(publish = """
            publish:
              modrinth:
                projectId: sample-project
                tokenEnv: MODRINTH_TOKEN
              curseforge:
                projectId: 123456
                tokenEnv: CURSEFORGE_TOKEN
        """.trimIndent() + "\n"))
        assertEquals(MetadataMapper.toJson(baseline), MetadataMapper.toJson(configured),
            "New Gradle distribution controls must never alter Loader JSON")
        assertTrue(MetadataMapper.toUtf8Bytes(baseline).contentEquals(MetadataMapper.toUtf8Bytes(configured)))
    }
}
