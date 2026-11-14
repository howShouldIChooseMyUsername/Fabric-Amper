package dev.fabricamper.plugin

import com.google.gson.JsonParser
import dev.fabricamper.util.PlatformReleaseClient
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption

/** Explicit-only upload: no outputs/up-to-date optimization, and never retries a POST. */
@DisableCachingByDefault(because = "External publishing has side effects and must not be cached")
abstract class FabricAmperPlatformUploadTask : DefaultTask() {
    @get:Input
    abstract val platform: Property<String>

    @get:Input
    abstract val metadataJson: Property<String>

    @get:Input
    abstract val defaultChangelog: Property<String>

    @get:Input
    @get:Optional
    abstract val changelog: Property<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val changelogFile: RegularFileProperty

    /** Root is used only for a second path-escape check at task execution. */
    @get:Internal
    abstract val consumerDirectory: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val curseForgeProjectId: Property<Int>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archiveFile: RegularFileProperty

    /** Secret provider remains unresolved until the explicitly invoked task action. */
    @get:Internal
    abstract val token: Property<String>

    @TaskAction
    fun upload() {
        val platformName = platform.get()
        val accessToken = token.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException("FabricAmper FA003 publish.$platformName.tokenEnv: upload token environment variable is missing")
        val content = when {
            changelogFile.isPresent -> {
                val root = consumerDirectory.get().asFile.toPath().toRealPath()
                val requested = changelogFile.get().asFile.toPath().toAbsolutePath().normalize()
                if (!requested.startsWith(root) || Files.isSymbolicLink(requested)) {
                    throw GradleException("FabricAmper FA003 publish.$platformName.changelogFile: file must be inside the consumer project")
                }
                val real = requested.toRealPath()
                if (!real.startsWith(root) || !Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) {
                    throw GradleException("FabricAmper FA003 publish.$platformName.changelogFile: invalid changelog file")
                }
                Files.readString(real, StandardCharsets.UTF_8)
            }
            changelog.isPresent -> changelog.get()
            else -> defaultChangelog.get()
        }

        val metadata = JsonParser.parseString(metadataJson.get()).asJsonObject
        metadata.addProperty("changelog", content)
        val payload = metadata.toString()
        val jar = archiveFile.get().asFile.toPath()
        try {
            when (platformName) {
                "modrinth" -> PlatformReleaseClient.uploadModrinth(jar, accessToken, payload)
                "curseforge" -> PlatformReleaseClient.uploadCurseForge(
                    jar, accessToken, curseForgeProjectId.get(), payload,
                )
                else -> throw GradleException("FabricAmper FA003 publish: unknown platform")
            }
        } catch (failure: PlatformReleaseClient.PlatformReleaseException) {
            val status = failure.statusCode?.let { " (HTTP $it)" } ?: ""
            throw GradleException(
                "FabricAmper publish.$platformName: ${failure.kind.name.lowercase()}$status; request and response details suppressed",
            )
        } catch (_: Exception) {
            // Never leak authorization headers, multipart fields, remote body,
            // or a server's echoed credentials through Gradle --stacktrace.
            throw GradleException("FabricAmper publish.$platformName: remote upload failed; request and response details suppressed")
        }
    }
}
