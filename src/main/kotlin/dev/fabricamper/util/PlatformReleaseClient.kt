package dev.fabricamper.util

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

/**
 * Explicit, single-attempt author uploads. This utility is inert during Gradle
 * configuration: callers must supply a validated *final* distribution JAR and
 * a token resolved only inside an explicitly invoked publication task.
 *
 * Neither user-defined endpoints nor response payloads are accepted or exposed.
 * The JSON builders do not perform I/O, read environment variables, or retain
 * credentials. Project IDs and relations are never inferred from Loader depends.
 */
object PlatformReleaseClient {
    enum class Platform { MODRINTH, CURSEFORGE }

    enum class FailureKind {
        AUTHENTICATION, CONFLICT, VALIDATION, RATE_LIMIT, SERVER, REDIRECT,
        CLIENT, NETWORK, INTERRUPTED, LOCAL_ARTIFACT
    }

    /** Deliberately contains neither request/response data nor a Throwable cause. */
    class PlatformReleaseException(
        val platform: Platform,
        val kind: FailureKind,
        val statusCode: Int? = null,
    ) : RuntimeException(
        "Fabric Amper ${platform.name.lowercase()} upload failed: ${kind.name.lowercase()}" +
            (statusCode?.let { " (HTTP $it)" } ?: ""),
    )

    data class UploadResult(val platform: Platform, val statusCode: Int)

    data class ModrinthDependency(
        val projectId: String,
        val type: String,
    )

    data class ModrinthVersion(
        val projectId: String,
        val name: String,
        val versionNumber: String,
        val gameVersions: List<String>,
        val versionType: String = "release",
        val loaders: List<String> = listOf("fabric"),
        val changelog: String = "Release published with Fabric Amper.",
        // Caller must map mod.environment or honor an explicit publish override.
        val environment: String,
        val dependencies: List<ModrinthDependency> = emptyList(),
    )

    data class CurseForgeRelation(
        val slug: String,
        val type: String,
        val projectId: String? = null,
    )

    data class CurseForgeFile(
        val projectId: Int,
        val displayName: String,
        val gameVersionNames: List<String>,
        val releaseType: String = "release",
        val gameVersions: List<Int> = emptyList(),
        val changelog: String = "Release published with Fabric Amper.",
        val changelogType: String = "markdown",
        val manualRelease: Boolean = false,
        val relations: List<CurseForgeRelation> = emptyList(),
    )

    private val releaseTypes = setOf("release", "beta", "alpha")
    private val dependencyTypes = setOf("required", "optional", "incompatible", "embedded")
    private val relationTypes = setOf(
        "embeddedLibrary", "incompatible", "optionalDependency", "requiredDependency", "tool",
    )
    private val changelogTypes = setOf("text", "markdown", "html")
    private val environments = setOf(
        "client_and_server", "client_only", "client_only_server_optional",
        "singleplayer_only", "server_only", "server_only_client_optional",
        "dedicated_server_only", "client_or_server", "client_or_server_prefers_both",
    )
    private const val MAX_JAR_BYTES = 1024L * 1024 * 1024
    private const val MAX_JSON_BYTES = 1024 * 1024
    private const val USER_AGENT = "FabricAmper-Gradle-Publisher/0.1.0 (dev.fabric-amper.settings; Java25)"
    private const val MODRINTH_URL = "https://api.modrinth.com/v2/version"
    private const val CURSEFORGE_BASE = "https://minecraft.curseforge.com/api/projects/"

    /**
     * The Gradle archive base name may legitimately begin with '.', '_' or '-',
     * and version strings may contain punctuation beyond a Maven-like token.
     * Treat the local leaf name as a quoted multipart filename rather than
     * imposing a second, incompatible archive naming grammar.
     *
     * Quotes, separators and controls are disallowed so the multipart header
     * cannot be escaped or injected. This is pure and never performs an upload.
     */
    internal fun isSafeReleaseJarFilename(filename: String): Boolean {
        if (filename.length !in 5..185 || !filename.endsWith(".jar")) return false
        val base = filename.dropLast(4)
        if (base == "." || base == "..") return false
        if (filename.any { it == '/' || it == '\\' || it == '"' || it.isISOControl() }) return false
        val lower = filename.lowercase(java.util.Locale.ROOT)
        return !lower.endsWith("-sources.jar") &&
            !lower.endsWith("-javadoc.jar") &&
            !lower.endsWith("-dev.jar")
    }

    /** Pure JSON: multipart part names must remain data and file. */
    @JvmStatic
    fun buildModrinthMetadata(version: ModrinthVersion): String {
        requireSafeText(version.projectId, "Modrinth project ID")
        requireSafeText(version.name, "Modrinth release name")
        requireSafeText(version.versionNumber, "Modrinth version number")
        require(version.versionType in releaseTypes) { "Invalid Modrinth release type" }
        require(version.environment in environments) { "Invalid Modrinth environment" }
        requireStrings(version.gameVersions, "Modrinth game versions")
        requireStrings(version.loaders, "Modrinth loaders")
        require(version.dependencies.distinctBy { it.projectId }.size == version.dependencies.size) {
            "Duplicate Modrinth dependency"
        }
        validateChangelog(version.changelog)

        val json = JsonObject().apply {
            addProperty("project_id", version.projectId)
            add("file_parts", stringArray(listOf("file")))
            addProperty("primary_file", "file")
            addProperty("name", version.name)
            addProperty("version_number", version.versionNumber)
            addProperty("version_type", version.versionType)
            add("game_versions", stringArray(version.gameVersions))
            add("loaders", stringArray(version.loaders))
            addProperty("featured", false)
            addProperty("changelog", version.changelog)
            addProperty("environment", version.environment)
            add("dependencies", JsonArray().apply {
                version.dependencies.forEach { dep ->
                    requireSafeText(dep.projectId, "Modrinth dependency project ID")
                    require(dep.type in dependencyTypes) { "Invalid Modrinth dependency type" }
                    add(JsonObject().apply {
                        addProperty("project_id", dep.projectId)
                        addProperty("dependency_type", dep.type)
                    })
                }
            })
        }
        return boundedJson(json)
    }

    /** Pure JSON for the CurseForge *author* endpoint, not the Core read API. */
    @JvmStatic
    fun buildCurseForgeMetadata(file: CurseForgeFile): String {
        require(file.projectId > 0) { "Invalid CurseForge project ID" }
        requireSafeText(file.displayName, "CurseForge display name")
        require(file.releaseType in releaseTypes) { "Invalid CurseForge release type" }
        require(file.changelogType in changelogTypes) { "Invalid CurseForge changelog type" }
        requireStrings(file.gameVersionNames, "CurseForge game version names")
        require(file.gameVersions.all { it > 0 } && file.gameVersions.distinct().size == file.gameVersions.size) {
            "Invalid CurseForge numeric game versions"
        }
        validateChangelog(file.changelog)

        val json = JsonObject().apply {
            addProperty("changelog", file.changelog)
            addProperty("changelogType", file.changelogType)
            addProperty("displayName", file.displayName)
            addProperty("releaseType", file.releaseType)
            add("gameVersionNames", stringArray(file.gameVersionNames))
            if (file.gameVersions.isNotEmpty()) {
                add("gameVersions", JsonArray().apply { file.gameVersions.forEach { add(it) } })
            }
            addProperty("isMarkedForManualRelease", file.manualRelease)
            if (file.relations.isNotEmpty()) {
                add("relations", JsonObject().apply {
                    add("projects", JsonArray().apply {
                        file.relations.forEach { relation ->
                            requireSafeText(relation.slug, "CurseForge relation slug")
                            require(relation.type in relationTypes) { "Invalid CurseForge relation type" }
                            add(JsonObject().apply {
                                addProperty("slug", relation.slug)
                                relation.projectId?.let { project ->
                                    require(project.toLongOrNull()?.let { it > 0 } == true) {
                                        "Invalid CurseForge relation project ID"
                                    }
                                    addProperty("projectID", project)
                                }
                                addProperty("type", relation.type)
                            })
                        }
                    })
                })
            }
        }
        return boundedJson(json)
    }

    /**
     * Performs exactly one POST. Only call from a requested publication task;
     * never on configuration, task registration or ordinary build.
     */
    @JvmStatic
    fun uploadModrinth(jar: Path, token: String, version: ModrinthVersion): UploadResult =
        uploadModrinth(jar, token, buildModrinthMetadata(version))

    /**
     * Worker-facing API for Gradle @Input metadata: the task may prepare JSON
     * without reading a token and provide it only during its @TaskAction.
     */
    @JvmStatic
    fun uploadModrinth(jar: Path, token: String, metadataJson: String): UploadResult {
        val metadata = strictMetadataObject(metadataJson)
        requireMetadataText(metadata, "project_id")
        requireMetadataText(metadata, "name")
        requireMetadataText(metadata, "version_number")
        requireMetadataText(metadata, "version_type")
        requireMetadataText(metadata, "environment")
        requireMetadataArray(metadata, "game_versions")
        requireMetadataArray(metadata, "loaders")
        requireMetadataFileParts(metadata)
        require(metadata.get("version_type").asString in releaseTypes &&
            metadata.get("environment").asString in environments &&
            metadata.get("featured")?.isJsonPrimitive == true &&
            metadata.get("featured").asJsonPrimitive.isBoolean &&
            !metadata.get("featured").asBoolean &&
            metadata.get("dependencies")?.isJsonArray == true
        ) { "Invalid Modrinth release metadata" }
        return upload(
            platform = Platform.MODRINTH,
            endpoint = URI.create(MODRINTH_URL),
            token = token,
            tokenHeader = "Authorization",
            metadataPartName = "data",
            metadata = metadataJson,
            jar = jar,
        )
    }

    /**
     * Metadata is configured ahead of time, but the token is a task-action-only
     * argument; no arbitrary URL can ever be supplied by a consumer.
     */
    @JvmStatic
    fun uploadCurseForge(
        jar: Path,
        token: String,
        projectId: Int,
        metadataJson: String,
    ): UploadResult {
        require(projectId > 0) { "Invalid CurseForge project ID" }
        val metadata = strictMetadataObject(metadataJson)
        requireMetadataText(metadata, "changelog")
        requireMetadataText(metadata, "releaseType")
        requireMetadataText(metadata, "displayName")
        requireMetadataText(metadata, "changelogType")
        requireMetadataArray(metadata, "gameVersionNames")
        require(metadata.get("releaseType").asString in releaseTypes &&
            metadata.get("changelogType").asString in changelogTypes &&
            metadata.get("isMarkedForManualRelease")?.isJsonPrimitive == true &&
            metadata.get("isMarkedForManualRelease").asJsonPrimitive.isBoolean
        ) { "Invalid CurseForge release metadata" }
        return upload(
            platform = Platform.CURSEFORGE,
            endpoint = URI.create(CURSEFORGE_BASE + projectId + "/upload-file"),
            token = token,
            tokenHeader = "X-Api-Token",
            metadataPartName = "metadata",
            metadata = metadataJson,
            jar = jar,
        )
    }

    /** Convenience overload for direct validated DTO callers. */
    @JvmStatic
    fun uploadCurseForge(jar: Path, token: String, file: CurseForgeFile): UploadResult =
        uploadCurseForge(jar, token, file.projectId, buildCurseForgeMetadata(file))

    /*
     * There is deliberately no configurable endpoint or bearer-token mode.
     * Modrinth upload uses the raw Authorization token value, not Bearer.
     */

    private fun upload(
        platform: Platform,
        endpoint: URI,
        token: String,
        tokenHeader: String,
        metadataPartName: String,
        metadata: String,
        jar: Path,
    ): UploadResult {
        // The metadata builders/endpoint selection run before this; no
        // caller-provided URL, auth scheme or arbitrary request header exists.
        require(token.isNotBlank() && token.all { it.code in 33..126 } &&
            !token.startsWith("Bearer ", ignoreCase = true)
        ) { "Invalid upload token format" }
        val filename = jar.fileName?.toString().orEmpty()
        if (!isSafeReleaseJarFilename(filename) ||
            !Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw PlatformReleaseException(platform, FailureKind.LOCAL_ARTIFACT)
        }
        val fileSize = try { Files.size(jar) } catch (_: IOException) {
            throw PlatformReleaseException(platform, FailureKind.LOCAL_ARTIFACT)
        } catch (_: SecurityException) {
            throw PlatformReleaseException(platform, FailureKind.LOCAL_ARTIFACT)
        }
        if (fileSize <= 0 || fileSize > MAX_JAR_BYTES) {
            throw PlatformReleaseException(platform, FailureKind.LOCAL_ARTIFACT)
        }

        val boundary = "fabric-amper-" + UUID.randomUUID().toString().replace("-", "")
        val start = (
            "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"$metadataPartName\"\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n\r\n" +
                metadata + "\r\n" +
                "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n" +
                "Content-Type: application/java-archive\r\n\r\n"
            ).toByteArray(StandardCharsets.UTF_8)
        val end = "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.US_ASCII)
        // BodyPublishers.ofFile is streaming: JAR bytes are never buffered in RAM.
        val body = try {
            HttpRequest.BodyPublishers.concat(
                HttpRequest.BodyPublishers.ofByteArray(start),
                HttpRequest.BodyPublishers.ofFile(jar),
                HttpRequest.BodyPublishers.ofByteArray(end),
            )
        } catch (_: IOException) {
            throw PlatformReleaseException(platform, FailureKind.LOCAL_ARTIFACT)
        } catch (_: SecurityException) {
            throw PlatformReleaseException(platform, FailureKind.LOCAL_ARTIFACT)
        }
        val client: HttpClient
        val request: HttpRequest
        try {
            client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
            request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .header(tokenHeader, token)
                .header("User-Agent", USER_AGENT)
                .POST(body)
                .build()
        } catch (_: RuntimeException) {
            // A JVM HTTP implementation must never leak a rejected header
            // value (which could be the token) in a caller's stack trace.
            throw PlatformReleaseException(platform, FailureKind.NETWORK)
        }
        val response = try {
            // Never read, cache or expose the response body, response headers,
            // request metadata, or token. No retry on 429, 5xx, or I/O errors.
            client.send(request, HttpResponse.BodyHandlers.discarding())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw PlatformReleaseException(platform, FailureKind.INTERRUPTED)
        } catch (_: IOException) {
            throw PlatformReleaseException(platform, FailureKind.NETWORK)
        } catch (_: SecurityException) {
            throw PlatformReleaseException(platform, FailureKind.NETWORK)
        } catch (_: RuntimeException) {
            throw PlatformReleaseException(platform, FailureKind.NETWORK)
        }
        val status = response.statusCode()
        if (status in 200..299) return UploadResult(platform, status)
        val kind = when (status) {
            401, 403 -> FailureKind.AUTHENTICATION
            409 -> FailureKind.CONFLICT
            400, 404, 413, 415, 422 -> FailureKind.VALIDATION
            429 -> FailureKind.RATE_LIMIT
            in 300..399 -> FailureKind.REDIRECT
            in 500..599 -> FailureKind.SERVER
            else -> FailureKind.CLIENT
        }
        throw PlatformReleaseException(platform, kind, status)
    }

    private fun requireSafeText(text: String, label: String) {
        require(text.isNotBlank() && text.length <= 255 &&
            !text.any { it.code < 0x20 || it.code == 0x7f }
        ) { "Invalid $label" }
    }

    private fun requireStrings(values: List<String>, label: String) {
        require(values.isNotEmpty() && values.size <= 100 &&
            values.distinct().size == values.size
        ) { "Invalid $label" }
        values.forEach { requireSafeText(it, label) }
    }

    private fun validateChangelog(text: String) {
        require(text.toByteArray(StandardCharsets.UTF_8).size <= MAX_JSON_BYTES / 2) {
            "Release changelog is too large"
        }
    }

    private fun strictMetadataObject(json: String): JsonObject {
        require(json.toByteArray(StandardCharsets.UTF_8).size in 2..MAX_JSON_BYTES) {
            "Invalid upload metadata size"
        }
        return try {
            val reader = JsonReader(StringReader(json))
            reader.setStrictness(Strictness.STRICT)
            val value = JsonParser.parseReader(reader)
            require(value.isJsonObject && reader.peek() == JsonToken.END_DOCUMENT)
            value.asJsonObject
        } catch (_: Exception) {
            // No response/request body, token, or JSON fragment in an error.
            throw IllegalArgumentException("Invalid upload metadata JSON")
        }
    }

    private fun requireMetadataText(metadata: JsonObject, key: String) {
        val value = metadata.get(key)
        require(value != null && value.isJsonPrimitive &&
            value.asJsonPrimitive.isString && value.asString.isNotBlank()
        ) { "Missing or invalid upload metadata field" }
    }

    private fun requireMetadataArray(metadata: JsonObject, key: String) {
        val value = metadata.get(key)
        require(value != null && value.isJsonArray && value.asJsonArray.size() > 0) {
            "Missing or invalid upload metadata array"
        }
    }

    private fun requireMetadataFileParts(metadata: JsonObject) {
        val fileParts = metadata.get("file_parts")
        val primary = metadata.get("primary_file")
        require(fileParts != null && fileParts.isJsonArray &&
            fileParts.asJsonArray.size() == 1 &&
            fileParts.asJsonArray[0].isJsonPrimitive &&
            fileParts.asJsonArray[0].asString == "file" &&
            primary != null && primary.isJsonPrimitive &&
            primary.asJsonPrimitive.isString && primary.asString == "file"
        ) { "Modrinth file_parts and primary_file must reference the file multipart part" }
    }

    private fun stringArray(items: List<String>) = JsonArray().apply {
        items.forEach { add(it) }
    }

    private fun boundedJson(json: JsonObject): String {
        val value = json.toString()
        require(value.toByteArray(StandardCharsets.UTF_8).size <= MAX_JSON_BYTES) {
            "Release metadata is too large"
        }
        return value
    }
}
