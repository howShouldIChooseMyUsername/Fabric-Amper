package dev.fabricamper.plugin

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import dev.fabricamper.config.ModYamlModel
import java.io.IOException
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Offline release preview. Never looks up a token, creates a network client,
 * publishes an artifact, or writes files. IDs and compatibility tags supplied
 * by the author are shown as unverified external declarations.
 *
 * For Gradle configuration cache, use [prepare] while configuring the task and
 * store its result in a task @Input Property<String>. At task execution pass
 * that string and the final archive's @InputFile path to [render]. This avoids
 * capturing a ModYamlModel, Path, Project or provider inside a task action.
 */
internal object FabricAmperReleasePlan {
    private const val MAX_ROOT_METADATA_BYTES = 4 * 1024 * 1024
    private val prettyJson = GsonBuilder().setPrettyPrinting().create()

    /** Pure configuration-time description: no artifact access or environment reads. */
    fun prepare(model: ModYamlModel): String {
        val mod = JsonObject().apply {
            addProperty("product", model.product)
            addProperty("id", model.mod.id)
            addProperty("name", model.mod.name ?: model.mod.id)
            addProperty("version", model.mod.version)
            addProperty("minecraftVersion", model.minecraft.version)
            addProperty("environment", model.mod.environment)
        }
        val targets = JsonArray()
        val group = model.mod.group
        val artifactId = model.mod.archivesName ?: model.mod.id

        if (model.product == "fabric/api") {
            targets.add(JsonObject().apply {
                addProperty("platform", "maven-local")
                addProperty("repository", "build/fabric-amper/maven-repository")
                addProperty("trigger", "publishMavenJavaPublicationToFabricAmperLocalRepository")
                addProperty("groupId", group)
                addProperty("artifactId", artifactId)
                addProperty("version", model.mod.version)
                addProperty("status", "CONFIGURED_NOT_EXECUTED")
            })
        }

        val remoteMaven = model.publish?.maven
        val legacyMaven = model.publishing?.repository
        val remoteUrl = remoteMaven?.url ?: legacyMaven?.url
        if (remoteUrl != null) {
            targets.add(JsonObject().apply {
                addProperty("platform", "maven-remote")
                addProperty("repository", remoteUrl)
                addProperty("trigger", "publishFabricAmperMaven")
                addProperty("groupId", group)
                addProperty("artifactId", artifactId)
                addProperty("version", model.mod.version)
                addProperty("credentialSource", if (
                    remoteMaven?.credentialsFromEnv != null ||
                    (legacyMaven?.usernameEnv != null && legacyMaven.passwordEnv != null)
                ) "ENVIRONMENT_VARIABLES_NOT_READ" else "UNSPECIFIED")
                addProperty("repositoryVerified", false)
                addProperty("status", "CONFIGURED_NOT_EXECUTED")
            })
        }

        model.publish?.modrinth?.let { selected ->
            targets.add(JsonObject().apply {
                addProperty("platform", "modrinth")
                addProperty("trigger", "publishFabricAmperModrinth")
                addProperty("authentication", "ENVIRONMENT_VARIABLE_NOT_READ")
                addProperty("externalProjectAndVersionsVerified", false)
                addProperty("status", "CONFIGURED_NOT_EXECUTED")
                add("metadataWithoutChangelog", JsonParser.parseString(FabricAmperPublishMetadata.modrinth(model)))
                addProperty("changelogSource", changelogSource(selected.changelog, selected.changelogFile))
            })
        }

        model.publish?.curseforge?.let { selected ->
            targets.add(JsonObject().apply {
                addProperty("platform", "curseforge")
                addProperty("trigger", "publishFabricAmperCurseForge")
                addProperty("authentication", "ENVIRONMENT_VARIABLE_NOT_READ")
                addProperty("externalProjectAndVersionsVerified", false)
                addProperty("status", "CONFIGURED_NOT_EXECUTED")
                addProperty("projectId", selected.projectId)
                add("metadataWithoutChangelog", JsonParser.parseString(FabricAmperPublishMetadata.curseforge(model)))
                addProperty("changelogSource", changelogSource(selected.changelog, selected.changelogFile))
            })
        }

        return JsonObject().apply {
            addProperty("formatVersion", 1)
            add("mod", mod)
            add("targets", targets)
        }.toString()
    }

    /** Convenience for callers outside Gradle configuration-cache task wiring. */
    fun render(model: ModYamlModel, jarPath: Path): String = render(prepare(model), jarPath)

    /**
     * Reads only the local final JAR. SHA-256 and root Loader JSON identity are
     * checked on the selected file; the full VerifyFabricAmperJar task remains
     * responsible for nested-JAR validation and must precede publication.
     */
    fun render(preparedPlanJson: String, jarPath: Path): String {
        val prepared = JsonParser.parseString(preparedPlanJson).asJsonObject
        require(prepared.get("formatVersion")?.asInt == 1) { "Unsupported Fabric Amper release plan format" }
        val mod = prepared.getAsJsonObject("mod")
        val artifact = inspectJar(jarPath, mod.get("id").asString, mod.get("version").asString)
        val notes = JsonArray().apply {
            add("PREVIEW ONLY: no upload, remote lookup, authentication, or HTTP request was performed.")
            add("External project IDs, release versions, compatibility tags and remote repository access are UNVERIFIED.")
            add("Changelog content and all authentication values are intentionally excluded from this report.")
            add("A successful build or release plan does not prove remote publication or CurseForge review approval.")
            add("Full archive and nested-JAR validation is handled by verifyFabricAmperJar.")
        }
        val report = JsonObject().apply {
            addProperty("title", "Fabric Amper RELEASE PLAN")
            addProperty("mode", "OFFLINE_PREVIEW_ONLY")
            addProperty("remoteRequests", 0)
            add("mod", mod.deepCopy())
            add("artifact", artifact)
            add("targets", prepared.getAsJsonArray("targets").deepCopy())
            add("limitations", notes)
        }
        return prettyJson.toJson(report)
    }

    private fun changelogSource(inline: String?, file: String?): String = when {
        file != null -> "PROJECT_FILE_CONTENT_OMITTED"
        inline != null -> "INLINE_CONTENT_OMITTED"
        else -> "AUTO_GENERATED_ON_UPLOAD"
    }

    private fun inspectJar(path: Path, expectedId: String, expectedVersion: String): JsonObject {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            "Release plan requires an existing regular final JAR"
        }
        val rootEntries: Int
        try {
            ZipFile(path.toFile()).use { zip ->
                var count = 0
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    if (entries.nextElement().name == "fabric.mod.json") count++
                }
                rootEntries = count
                require(count == 1) { "Release plan requires exactly one root fabric.mod.json; found $count" }
                val root = requireNotNull(zip.getEntry("fabric.mod.json"))
                require(!root.isDirectory) { "Release plan requires a regular root fabric.mod.json" }
                val bytes = zip.getInputStream(root).use { it.readNBytes(MAX_ROOT_METADATA_BYTES + 1) }
                require(bytes.isNotEmpty() && bytes.size <= MAX_ROOT_METADATA_BYTES) {
                    "Release plan requires a bounded, nonempty root fabric.mod.json"
                }
                val metadata = parseRootMetadata(bytes)
                require(metadata.get("id")?.asString == expectedId &&
                    metadata.get("version")?.asString == expectedVersion
                ) { "Final JAR Loader ID/version differs from the validated YAML release" }
            }
        } catch (_: ZipException) {
            throw IllegalArgumentException("Release plan requires a valid final JAR ZIP archive")
        } catch (_: IOException) {
            throw IllegalArgumentException("Release plan could not read the final JAR")
        }

        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(16384)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (count == 0) continue
                    digest.update(buffer, 0, count)
                    size += count
                }
            }
        } catch (_: IOException) {
            throw IllegalArgumentException("Release plan could not hash the final JAR")
        }
        require(size > 0 && size == Files.size(path)) { "Final JAR changed while preparing release plan" }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return JsonObject().apply {
            addProperty("filename", path.fileName.toString())
            addProperty("sizeBytes", size)
            addProperty("sha256", sha256)
            addProperty("rootFabricModJsonEntries", rootEntries)
            addProperty("loaderId", expectedId)
            addProperty("loaderVersion", expectedVersion)
        }
    }

    private fun parseRootMetadata(bytes: ByteArray): JsonObject = try {
        val decoded = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        val reader = JsonReader(StringReader(decoded))
        reader.setStrictness(Strictness.STRICT)
        val metadata = JsonParser.parseReader(reader).asJsonObject
        require(reader.peek() == JsonToken.END_DOCUMENT)
        val schemaVersion = metadata.get("schemaVersion")
        require(schemaVersion != null && schemaVersion.isJsonPrimitive &&
            schemaVersion.asJsonPrimitive.isNumber && schemaVersion.asString == "1")
        for (field in listOf("id", "version")) {
            val property = metadata.get(field)
            require(property != null && property.isJsonPrimitive &&
                property.asJsonPrimitive.isString && property.asString.isNotBlank())
        }
        metadata
    } catch (_: RuntimeException) {
        throw IllegalArgumentException("Release plan requires valid Loader JSON in root fabric.mod.json")
    } catch (_: java.nio.charset.CharacterCodingException) {
        throw IllegalArgumentException("Release plan requires UTF-8 root fabric.mod.json")
    } catch (_: IOException) {
        // Gson's strict JsonReader.peek() can throw MalformedJsonException,
        // an IOException, when the document contains trailing non-JSON data.
        // Classify it as invalid Loader metadata, not a ZIP/read failure.
        throw IllegalArgumentException("Release plan requires valid Loader JSON in root fabric.mod.json")
    }
}
