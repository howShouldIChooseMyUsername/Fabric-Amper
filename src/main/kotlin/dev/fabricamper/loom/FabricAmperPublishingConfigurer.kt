package dev.fabricamper.loom

import org.gradle.api.GradleException
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import java.net.URI

/** Only repository configuration, never a publishing action. */
data class FabricAmperRemoteMavenRepository(
    val url: String,
    val usernameEnv: String? = null,
    val passwordEnv: String? = null,
)

/**
 * Publishes an actual Fabric library mod using Loom's outgoing Java variants.
 *
 * In a remapping Loom project, Loom owns the mapping from Java's outgoing
 * variants to remapJar/remapSourcesJar. Attaching remapJar explicitly to a
 * component publication would create a second main artifact (or publish a
 * dev JAR); retain one component as the single source of artifacts and scopes.
 */
object FabricAmperPublishingConfigurer {
    fun configure(
        project: Project,
        groupId: String,
        artifactId: String,
        version: String,
        displayName: String?,
        description: String?,
        licenses: List<String>,
        homepage: String?,
        remote: FabricAmperRemoteMavenRepository?,
    ) {
        // Loom has already applied `java`; java-library adds the `api` bucket
        // without requiring a consumer build.gradle(.kts).
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("maven-publish")

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        val javaComponent = project.components.getByName("java")
        publishing.publications.register("mavenJava", MavenPublication::class.java) { publication ->
            publication.groupId = groupId
            publication.artifactId = artifactId
            publication.version = version
            publication.from(javaComponent)

            // Java.withSourcesJar() already contributes sourcesElements to this
            // component when enabled. Never add a second sources JAR manually.
            displayName?.let { publication.pom.name.set(it) }
            description?.let { publication.pom.description.set(it) }
            homepage?.let { publication.pom.url.set(it) }
            publication.pom.licenses { container ->
                licenses.forEach { licenseName ->
                    container.license { license -> license.name.set(licenseName) }
                }
            }
        }

        publishing.repositories.maven { repository ->
            repository.name = "fabricAmperLocal"
            repository.url = project.layout.buildDirectory.dir("fabric-amper/maven-repository")
                .get().asFile.toURI()
        }

        remote?.let { selected ->
            val uri = validateRemoteUrl(selected.url)
            if ((selected.usernameEnv == null) != (selected.passwordEnv == null)) {
                throw GradleException("FabricAmper FA003 publish.maven: both Maven credential variable names must be supplied together")
            }
            val remoteRepository = publishing.repositories.maven { repository ->
                repository.name = "fabricAmperRemote"
                repository.url = uri
            }
            // Gradle's native PublishToMavenRepository task snapshots its
            // repository during configuration-cache serialization and discards
            // the task.repository getter afterwards. Changing repository
            // credentials in doFirst is therefore too late and fails with
            // "The value of this property has been discarded during serialization."
            //
            // PasswordCredentials expose String setters rather than Property
            // setters. Set them when the selected remote publication task is
            // configured (not at Project configuration or during task execution).
            // The resulting environment reads are configuration-cache inputs;
            // Gradle encrypts its cache entry. Normal help/build/local Maven
            // tasks do not realize the remote publishing task or need secrets.
            val username = selected.usernameEnv?.let(project.providers::environmentVariable)
            val password = selected.passwordEnv?.let(project.providers::environmentVariable)
            // Gradle 9 flags native PublishToMavenRepository tasks with
            // explicitly configured PasswordCredentials as incompatible with
            // configuration cache, including when running --dry-run. A dry run
            // executes no task actions and performs no upload, so do not attach
            // those credentials to its repository snapshot. Real publishing
            // keeps the exact original credential behavior.
            val dryRun = project.gradle.startParameter.isDryRun
            if (username != null && password != null) {
                project.tasks.withType(PublishToMavenRepository::class.java).configureEach { task ->
                    if (task.name == "publishMavenJavaPublicationToFabricAmperRemoteRepository") {
                        if (!dryRun) {
                            remoteRepository.credentials { credentials ->
                                // Gradle validates native credential inputs before
                                // running doFirst. Null values prevent our FA003
                                // missing-credential diagnostic from executing.
                                // Empty sentinels are never accepted by the
                                // RequireRemoteMavenCredentials action below.
                                credentials.username = username.orNull ?: ""
                                credentials.password = password.orNull ?: ""
                            }
                        }
                        // Only selected remote execution requires both values;
                        // an absent test/local credential cannot break `help`,
                        // `build`, or a publication to fabricAmperLocal.
                        task.doFirst(RequireRemoteMavenCredentials(username, password))
                    }
                }
            }
        }
    }

    private fun validateRemoteUrl(value: String): URI {
        val uri = try {
            URI.create(value)
        } catch (e: IllegalArgumentException) {
            throw GradleException("FabricAmper FA003 publishing.repository.url: invalid HTTPS Maven repository URL", e)
        }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || uri.userInfo != null ||
            uri.fragment != null || uri.rawQuery != null
        ) {
            throw GradleException("FabricAmper FA003 publishing.repository.url: expected HTTPS URL without credentials, query or fragment")
        }
        return uri
    }
}

/** Validates only: native repository credentials were wired before serialization. */
private class RequireRemoteMavenCredentials(
    private val usernameProvider: Provider<String>,
    private val passwordProvider: Provider<String>,
) : Action<Task> {
    override fun execute(task: Task) {
        val username = usernameProvider.orNull?.takeIf { it.isNotBlank() }
        val password = passwordProvider.orNull?.takeIf { it.isNotBlank() }
        if (username == null || password == null) {
            throw GradleException("FabricAmper FA003 publish.maven: both Maven credential environment variables must be set for remote publishing")
        }
    }
}
