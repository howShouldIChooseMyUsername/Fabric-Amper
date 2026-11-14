package dev.fabricamper.unit

import dev.fabricamper.loom.DependencyConfigurer
import dev.fabricamper.loom.GradleDependencyDeclaration
import dev.fabricamper.loom.GradleRepositoryDeclaration
import dev.fabricamper.loom.RepositoryConfigurer
import org.gradle.api.GradleException
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Offline Gradle ProjectBuilder checks of the public owner-2 configuration API.
 *
 * These verify declarations and repository registrations, NOT dependency
 * downloads, remapped compilation, publication, or Fabric Loader runtime.
 */
class GradleWiringContractUnitTest {
    @TempDir lateinit var projectRoot: Path

    private fun project() = ProjectBuilder.builder().withProjectDir(projectRoot.toFile())
        .build().also { it.pluginManager.apply("java-library") }

    @Test
    fun `included Maven library has both compile classpath and packaged include declaration`() {
        val project = project()
        project.configurations.maybeCreate("include")
        val coordinate = "com.example:embedded-library:1.2.3"
        DependencyConfigurer.configure(
            project, "26.3",
            listOf(GradleDependencyDeclaration(coordinate, include = true)),
            emptyList(),
        )
        val main = project.configurations.getByName("implementation").dependencies
        val packed = project.configurations.getByName("include").dependencies
        assertEquals(1, main.count { it.group == "com.example" && it.name == "embedded-library" && it.version == "1.2.3" })
        assertEquals(1, packed.count { it.group == "com.example" && it.name == "embedded-library" && it.version == "1.2.3" })
        assertFalse(project.configurations.getByName("compileOnly").dependencies.any { it.name == "embedded-library" })
    }

    @Test
    fun `scope and exported dependencies populate correct Gradle configurations`() {
        val project = project()
        DependencyConfigurer.configure(project, "26.3", listOf(
            GradleDependencyDeclaration("com.example:api-library:1.0", exported = true),
            GradleDependencyDeclaration("com.example:compile-library:1.0", scope = "compile-only"),
            GradleDependencyDeclaration("com.example:runtime-library:1.0", scope = "runtime-only"),
        ), emptyList())
        assertEquals(listOf("api-library"), project.configurations.getByName("api").dependencies.map { it.name })
        assertEquals(listOf("compile-library"), project.configurations.getByName("compileOnly").dependencies.map { it.name })
        assertEquals(listOf("runtime-library"), project.configurations.getByName("runtimeOnly").dependencies.map { it.name })
        assertTrue(project.configurations.getByName("implementation").dependencies.isEmpty())
    }

    @Test
    fun `client targeted Modrinth mod dependency is isolated to client compilation declaration`() {
        val project = project()
        project.configurations.maybeCreate("clientCompileOnly")
        DependencyConfigurer.configure(project, "26.3", listOf(
            GradleDependencyDeclaration(
                coordinate = "maven.modrinth:sodium:mc26.3-0.8.0",
                kind = "mod",
                scope = "compile-only",
                target = "client",
            ),
        ), emptyList())
        val client = project.configurations.getByName("clientCompileOnly").dependencies
        assertEquals(1, client.size)
        val dependency = client.single()
        assertEquals("maven.modrinth", dependency.group)
        assertEquals("sodium", dependency.name)
        assertEquals("mc26.3-0.8.0", dependency.version)
        assertTrue(project.configurations.getByName("implementation").dependencies.isEmpty())
        assertTrue(project.configurations.getByName("compileOnly").dependencies.isEmpty())
    }

    @Test
    fun `Modrinth repository alias registers one canonical Maven endpoint without network access`() {
        val project = project()
        RepositoryConfigurer.configure(project, listOf(
            GradleRepositoryDeclaration.Named("modrinth"),
            GradleRepositoryDeclaration.Named("modrinth"),
        ))
        val modrinth = project.repositories.findByName("Modrinth") as? MavenArtifactRepository
        assertNotNull(modrinth)
        assertEquals("https://api.modrinth.com/maven", modrinth!!.url.toString().removeSuffix("/"))
        assertEquals(1, project.repositories.count { it.name == "Modrinth" })
    }

    @Test
    fun `equivalent custom Maven repository URLs reuse deterministic hashed identity`() {
        val project = project()
        RepositoryConfigurer.configure(project, listOf(
            GradleRepositoryDeclaration.Maven("https://example.org/repository"),
            GradleRepositoryDeclaration.Maven("https://example.org/repository/"),
        ))
        val matching = project.repositories.filter { it.name.startsWith("FabricAmperMaven-") }
        assertEquals(1, matching.size)
        val maven = matching.single() as MavenArtifactRepository
        assertEquals("https://example.org/repository", maven.url.toString().removeSuffix("/"))
    }

    @Test
    fun `conflicting preexisting Modrinth endpoint yields explicit FA010 without silently reusing it`() {
        val project = project()
        project.repositories.maven { repo ->
            repo.name = "Modrinth"
            repo.url = project.uri("https://example.org/not-modrinth")
        }
        val error = assertThrows(GradleException::class.java) {
            RepositoryConfigurer.configure(project, listOf(GradleRepositoryDeclaration.Named("modrinth")))
        }
        assertTrue(error.message!!.contains("FA010"), error.message)
        val existing = project.repositories.findByName("Modrinth") as MavenArtifactRepository
        assertEquals("https://example.org/not-modrinth", existing.url.toString().removeSuffix("/"))
    }
}
