package dev.fabricamper.loom

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Repository aliases and custom Maven URLs decoded from mod.yaml. */
sealed interface GradleRepositoryDeclaration {
    data class Named(val name: String) : GradleRepositoryDeclaration
    data class Maven(val url: String) : GradleRepositoryDeclaration
}

object RepositoryConfigurer {
    private const val MODRINTH_URL = "https://api.modrinth.com/maven"

    fun configure(project: Project, repositories: List<GradleRepositoryDeclaration>) {
        repositories.distinct().forEach { repository ->
            when (repository) {
                is GradleRepositoryDeclaration.Named -> when (repository.name) {
                    "fabric" -> ensureMaven(project, "Fabric", "https://maven.fabricmc.net/")
                    "neoforge" -> ensureMaven(project, "NeoForge", "https://maven.neoforged.net/releases/")
                    "mavenCentral" -> {
                        if (project.repositories.findByName(ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME) == null) {
                            project.repositories.mavenCentral()
                        }
                    }
                    "modrinth" -> configureModrinth(project)
                    else -> throw GradleException("FabricAmper FA003 repositories: unknown repository '${repository.name}'")
                }
                is GradleRepositoryDeclaration.Maven -> {
                    checkHttps(repository.url)
                    ensureMaven(project, "FabricAmperMaven-${sha256(repository.url)}", repository.url)
                }
            }
        }
    }

    private fun configureModrinth(project: Project) {
        val found = project.repositories.findByName("Modrinth")
        val repository = if (found != null) {
            requireMatchingMaven(found, "Modrinth", MODRINTH_URL)
        } else {
            project.repositories.maven { repo ->
                repo.name = "Modrinth"
                repo.url = project.uri(MODRINTH_URL)
            }
        }
        project.repositories.exclusiveContent { content ->
            content.forRepositories(repository)
            content.filter { filter -> filter.includeGroup("maven.modrinth") }
        }
    }

    private fun ensureMaven(project: Project, name: String, url: String) {
        checkHttps(url)
        val found = project.repositories.findByName(name)
        if (found != null) {
            requireMatchingMaven(found, name, url)
        } else {
            project.repositories.maven { repo ->
                repo.name = name
                repo.url = project.uri(url)
            }
        }
    }

    private fun requireMatchingMaven(
        existing: org.gradle.api.artifacts.repositories.ArtifactRepository,
        name: String,
        expectedUrl: String,
    ): MavenArtifactRepository {
        val maven = existing as? MavenArtifactRepository
            ?: throw GradleException("FabricAmper FA010 repositories: '$name' already exists but is not a Maven repository")
        if (canonicalUrl(maven.url.toString()) != canonicalUrl(expectedUrl)) {
            throw GradleException(
                "FabricAmper FA010 repositories: '$name' already points to '${maven.url}', " +
                    "expected '$expectedUrl'; refusing to reuse an incompatible repository",
            )
        }
        return maven
    }

    private fun canonicalUrl(url: String): String {
        val uri = URI(url).normalize()
        val normalized = uri.toASCIIString()
        // A terminal slash on a Maven repository path is interchangeable,
        // but a slash within a query or fragment must never be stripped.
        return if (uri.rawQuery == null && uri.rawFragment == null) {
            normalized.removeSuffix("/")
        } else {
            normalized
        }
    }

    private fun sha256(url: String): String = MessageDigest.getInstance("SHA-256")
        .digest(canonicalUrl(url).toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun checkHttps(url: String) {
        val parsed = runCatching { URI(url) }.getOrNull()
        if (parsed?.scheme != "https" || parsed.host.isNullOrBlank() || parsed.userInfo != null) {
            throw GradleException("FabricAmper FA003 repositories: custom Maven URL must be HTTPS without credentials")
        }
    }
}
