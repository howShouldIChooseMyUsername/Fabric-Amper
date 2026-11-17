package dev.fabricamper.unit

import dev.fabricamper.config.ArchitecturyDefaults
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.config.RepositorySpec
import dev.fabricamper.plugin.FabricAmperCommonConfigurer
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Offline :common repository wiring; never resolves/downloads artifacts. */
class FabricAmperCommonRepositoryWiringTest {
    @TempDir lateinit var workspace: Path

    @Test
    fun `dual Architectury common receives only neutral repositories plus Architectury`() {
        val model = ModYamlLoader.load(YamlFixtures.write(workspace, dualArchitecturyYaml))
        assertEquals("1.1", model.schemaVersion)
        assertTrue(model.isMultiLoader)
        assertTrue(model.architectury.enabled)
        val common = newCommonProject()

        FabricAmperCommonConfigurer.configure(common, model)

        val repos = common.repositories.filterIsInstance<MavenArtifactRepository>()
        val central = repos.single { it.name == ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME }
        assertEquals("https://repo.maven.apache.org/maven2", central.url.toString().trimEnd('/'))

        val modrinth = repos.single { it.name == "Modrinth" }
        assertEquals("https://api.modrinth.com/maven", modrinth.url.toString().trimEnd('/'))

        val custom = repos.single { it.url.toString().trimEnd('/') == "https://repo.example.org/releases" }
        assertTrue(custom.name.startsWith("FabricAmperMaven-"), custom.name)

        val architectury = repos.single { it.name == "ArchitecturyCommon" }
        assertEquals(ArchitecturyDefaults.MAVEN_URL.trimEnd('/'), architectury.url.toString().trimEnd('/'))
        // The v1.1 validator may also supply an auto-declared Architectury
        // custom Maven entry; both paths must remain loader-neutral.
        assertTrue(repos.any { it.url.toString().trimEnd('/') == ArchitecturyDefaults.MAVEN_URL.trimEnd('/') })

        assertFalse(repos.any { it.name == "Fabric" || it.name == "NeoForge" })
        assertFalse(repos.any { it.url.toString().contains("maven.fabricmc.net") })
        assertFalse(repos.any { it.url.toString().contains("maven.neoforged.net") })
        assertEquals(1, repos.count { it.name == "Modrinth" })
        assertEquals(1, repos.count { it.name == ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME })

        val api = common.configurations.getByName("api").dependencies
        assertTrue(api.any { it.group == "dev.architectury" && it.name == "architectury" })
        val implementation = common.configurations.getByName("implementation").dependencies
        assertTrue(implementation.any { it.group == "org.example" && it.name == "neutral-library" })
    }

    @Test
    fun `common rejects non HTTPS or credentialed custom Maven without resolution`() {
        val model = ModYamlLoader.load(YamlFixtures.write(workspace, dualArchitecturyYaml))
        for ((index, url) in listOf(
            "http://repo.example.org/releases",
            "https://user:secret@repo.example.org/releases",
        ).withIndex()) {
            val altered = model.copy(repositories = model.repositories + RepositorySpec("maven", url))
            val common = newCommonProject("common$index")
            val failure = assertThrows(GradleException::class.java) {
                FabricAmperCommonConfigurer.configure(common, altered)
            }
            assertTrue(failure.message.orEmpty().contains("FA003"), failure.message)
        }
    }

    private fun newCommonProject(name: String = "common"): Project {
        val rootProject = ProjectBuilder.builder().withProjectDir(workspace.toFile()).build()
        val childDir = workspace.resolve(name)
        Files.createDirectories(childDir)
        return ProjectBuilder.builder()
            .withParent(rootProject)
            .withName(name)
            .withProjectDir(childDir.toFile())
            .build()
            .also { assertEquals(":$name", it.path) }
    }

    private val dualArchitecturyYaml = """
        schema: "1.1"
        product: [fabric/mod, neoforge/mod]
        mod:
          id: commonrepo
          version: "1.0.0+26.3"
          name: Common Repository Test
          license: MIT
        minecraft:
          version: "26.3"
        settings:
          java:
            release: 25
        fabric:
          loader: "0.20.1"
          api: "0.165.2+26.3"
          mappings: mojang
          entrypoints:
            main: [org.example.CommonRepoMod]
        neoforge:
          version: "26.3.0.101"
          modLoader: javafml
        architectury:
          enable: true
        repositories:
          - mavenCentral
          - fabric
          - neoforge
          - modrinth
          - maven: "https://repo.example.org/releases"
        dependencies:
          - maven: "org.example:neutral-library:1.2.3"
            kind: library
    """.trimIndent() + "\n"
}
