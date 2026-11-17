package dev.fabricamper.generator

import dev.fabricamper.config.ModYamlModel
import dev.fabricamper.config.NeoForgeDependencySpec
import dev.fabricamper.config.NeoForgeSpec
import dev.fabricamper.util.failAmper
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Deterministic modern NeoForge metadata, independent of Fabric's JSON mapper.
 * Source: https://docs.neoforged.net/docs/gettingstarted/modfiles/
 * and official MDK-26.3-ModDevGradle.
 */
object NeoForgeMetadataMapper {
    const val ENTRY_PATH = "META-INF/neoforge.mods.toml"
    private val modIdPattern = Regex("[a-z][a-z0-9_]{1,63}")
    private val simpleKey = Regex("[A-Za-z0-9_-]+")

    @JvmStatic
    fun toUtf8Bytes(model: ModYamlModel): ByteArray =
        toToml(model).toByteArray(StandardCharsets.UTF_8)

    @JvmStatic
    fun toToml(model: ModYamlModel): String {
        val neo = requiredProfile(model)
        val mod = model.mod
        if (!modIdPattern.matches(mod.id))
            failAmper("FA016", "mod.id", "NeoForge mod ID는 소문자/숫자/밑줄 2–64자여야 합니다.")
        if (mod.version.isBlank())
            failAmper("FA016", "mod.version", "NeoForge 모드 버전이 비어 있습니다.")
        if (neo.version.isBlank() || model.minecraft.version.isBlank())
            failAmper("FA016", "neoforge.version", "NeoForge와 Minecraft 버전은 비어 있으면 안 됩니다.")
        if (neo.modLoader != "javafml")
            failAmper("FA016", "neoforge.modLoader", "현재 javafml 언어 로더만 지원합니다.")
        validateRange(neo.loaderVersion, "neoforge.loaderVersion")
        val license = (mod.license as? String)?.takeIf { it.isNotBlank() }
            ?: failAmper("FA016", "mod.license", "NeoForge license는 비어 있지 않은 단일 문자열이어야 합니다.")
        val authors = mod.authors.mapIndexed { index, value ->
            val name = when (value) {
                is String -> value
                is Map<*, *> -> value["name"] as? String
                else -> null
            }
            name?.takeIf { it.isNotBlank() }
                ?: failAmper("FA016", "mod.authors[$index]", "NeoForge 작성자 이름이 올바르지 않습니다.")
        }
        val dependencies = dependencies(model, neo)
        val out = StringBuilder()
        fun line(value: String = "") { out.append(value).append('\n') }
        fun field(key: String, value: String) = line("$key=" + quote(value))

        field("modLoader", neo.modLoader)
        field("loaderVersion", neo.loaderVersion)
        field("license", license)
        neo.issueTrackerURL?.let { field("issueTrackerURL", it) }
        line()
        line("[[mods]]")
        field("modId", mod.id)
        field("version", mod.version)
        field("displayName", mod.name?.takeIf { it.isNotBlank() } ?: mod.id)
        mod.description?.let { field("description", it) }
        neo.logoFile?.let { field("logoFile", resourcePath(it, "neoforge.logoFile")) }
        neo.displayURL?.let { field("displayURL", it) }
        neo.credits?.let { field("credits", it) }
        if (authors.isNotEmpty()) field("authors", authors.joinToString(", "))
        neo.updateJSONURL?.let { field("updateJSONURL", it) }
        for (config in uniqueResources(neo.mixins, "neoforge.mixins")) {
            line()
            line("[[mixins]]")
            field("config", config)
        }
        for (path in uniqueResources(neo.accessTransformers, "neoforge.accessTransformers")) {
            line()
            line("[[accessTransformers]]")
            field("file", path)
        }
        for (dep in dependencies) {
            line()
            line("[[dependencies.${mod.id}]]")
            field("modId", dep.modId)
            field("type", dep.type)
            field("versionRange", dep.versionRange)
            field("ordering", dep.ordering)
            field("side", dep.side)
            dep.reason?.let { field("reason", it) }
        }
        if (neo.modProperties.isNotEmpty()) {
            line()
            line("[modproperties.${mod.id}]")
            for ((key, value) in neo.modProperties.toSortedMap()) {
                if (key.isBlank()) failAmper("FA016", "neoforge.modProperties", "속성 이름이 비어 있습니다.")
                field(if (simpleKey.matches(key)) key else quote(key), value)
            }
        }
        if (neo.features.isNotEmpty()) {
            line()
            line("[features.${mod.id}]")
            for ((key, value) in neo.features.toSortedMap()) {
                if (!simpleKey.matches(key))
                    failAmper("FA016", "neoforge.features", "NeoForge feature 이름이 올바르지 않습니다: $key")
                field(key, value)
            }
        }
        return out.toString()
    }

    @JvmStatic
    fun validateNoAuthoredToml(model: ModYamlModel, targetProjectRoot: Path? = null) {
        val root = model.source.toAbsolutePath().normalize().parent
            ?: failAmper("FA016", "mod.yaml", "YAML 프로젝트 경로를 찾을 수 없습니다.")
        val roots = linkedSetOf(root)
        targetProjectRoot?.let { roots.add(it.toAbsolutePath().normalize()) }
        for (base in roots) {
            for (sourceRoot in listOf(
                "src/main/resources", "src/client/resources",
                "neoforge/src/main/resources", "common/src/main/resources",
            )) {
                for (name in listOf(ENTRY_PATH, "META-INF/mods.toml")) {
                    val candidate = base.resolve(sourceRoot).resolve(name)
                    if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS))
                        failAmper("FA016", ENTRY_PATH, "작성된 Loader TOML과 자동 생성 TOML이 충돌합니다: $candidate")
                }
            }
        }
    }

    @JvmStatic
    fun validateResources(model: ModYamlModel, targetProjectRoot: Path) {
        val neo = requiredProfile(model)
        val root = targetProjectRoot.toAbsolutePath().normalize()
        val sourceRoots = listOf("src/main/resources", "src/client/resources", "neoforge/src/main/resources")
            .map { root.resolve(it) }
        val resources = buildList {
            neo.logoFile?.let { add(it to "neoforge.logoFile") }
            neo.mixins.forEachIndexed { index, path -> add(path to "neoforge.mixins[$index]") }
            neo.accessTransformers.forEachIndexed { index, path ->
                add(path to "neoforge.accessTransformers[$index]")
            }
        }
        for ((path, location) in resources) {
            val normalized = resourcePath(path, location)
            val found = sourceRoots.any { resourcesRoot ->
                val candidate = resourcesRoot.resolve(normalized).normalize()
                if (!candidate.startsWith(resourcesRoot) ||
                    !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                ) false else {
                    // Forbid symlink ancestors as well as symlink files.
                    var cursor: Path? = candidate
                    var safe = true
                    while (cursor != null && cursor.startsWith(resourcesRoot)) {
                        if (Files.isSymbolicLink(cursor)) safe = false
                        cursor = cursor.parent
                    }
                    safe
                }
            }
            if (!found)
                failAmper("FA016", location, "NeoForge 리소스가 없거나 안전하지 않은 경로입니다: $path")
        }
    }

    private fun requiredProfile(model: ModYamlModel): NeoForgeSpec {
        if (model.schemaVersion != "1.1" || model.targets.none { it.loader == "neoforge" })
            failAmper("FA016", "product", "NeoForge TOML 생성에는 schema: \"1.1\"과 neoforge 대상이 필요합니다.")
        return model.neoforge
            ?: failAmper("FA016", "neoforge", "NeoForge 대상에 neoforge 설정이 없습니다.")
    }

    private fun dependencies(model: ModYamlModel, neo: NeoForgeSpec): List<NeoForgeDependencySpec> {
        val automatic = linkedMapOf(
            "neoforge" to "[${neo.version},)",
            "minecraft" to "[${model.minecraft.version}]",
        )
        val result = linkedMapOf<String, NeoForgeDependencySpec>()
        for (dep in neo.dependencies) {
            val path = "neoforge.dependencies.${dep.modId}"
            if (!modIdPattern.matches(dep.modId) || dep.modId == model.mod.id || result.containsKey(dep.modId))
                failAmper("FA016", path, "NeoForge 의존성 ID가 잘못됐거나 중복/자기참조입니다.")
            if (dep.type !in setOf("required", "optional", "incompatible", "discouraged") ||
                dep.ordering !in setOf("NONE", "BEFORE", "AFTER") ||
                dep.side !in setOf("BOTH", "CLIENT", "SERVER"))
                failAmper("FA016", path, "NeoForge type, ordering 또는 side 값이 올바르지 않습니다.")
            if (dep.versionRange.isNotBlank()) validateRange(dep.versionRange, "$path.versionRange")
            val selected = when (dep.modId) {
                "neoforge" -> neo.version
                "minecraft" -> model.minecraft.version
                else -> null
            }
            if (selected != null) {
                if (dep.type != "required" || dep.side != "BOTH")
                    failAmper("FA016", path, "플랫폼 필수 의존성은 required/BOTH여야 합니다.")
                if (dep.versionRange.isNotBlank() && !includesVersion(dep.versionRange, selected))
                    failAmper("FA016", "$path.versionRange", "선택한 플랫폼 $selected 와 호환성을 입증할 수 없습니다.")
            }
            result[dep.modId] = dep.copy(versionRange = dep.versionRange.ifBlank {
                automatic[dep.modId] ?: "*"
            })
        }
        for ((id, range) in automatic) result.putIfAbsent(
            id, NeoForgeDependencySpec(modId = id, versionRange = range),
        )
        if (model.architectury.enabled) {
            val version = model.architectury.version
                ?: failAmper("FA016", "architectury.version", "Architectury 버전이 필요합니다.")
            result.putIfAbsent("architectury",
                NeoForgeDependencySpec("architectury", versionRange = "[$version,)"))
        }
        return result.values.sortedWith(compareBy<NeoForgeDependencySpec> {
            when (it.modId) { "neoforge" -> 0; "minecraft" -> 1; else -> 2 }
        }.thenBy { it.modId })
    }

    private fun includesVersion(range: String, version: String): Boolean {
        if (range == "*") return true
        if (range.startsWith("[") && range.endsWith("]") && ',' !in range)
            return range.substring(1, range.length - 1) == version
        val match = Regex("""^([\[(])([^,]*),([^,]*)([\])])$""").matchEntire(range) ?: return false
        fun cmp(a: String, b: String): Int {
            val left = a.split('.', '-', '+')
            val right = b.split('.', '-', '+')
            for (i in 0 until maxOf(left.size, right.size)) {
                val l = left.getOrElse(i) { "0" }
                val r = right.getOrElse(i) { "0" }
                val comparison = if (l.toLongOrNull() != null && r.toLongOrNull() != null)
                    l.toLong().compareTo(r.toLong()) else l.compareTo(r)
                if (comparison != 0) return comparison
            }
            return 0
        }
        val low = match.groupValues[2].trim()
        val high = match.groupValues[3].trim()
        if (low.isNotEmpty()) {
            val c = cmp(version, low)
            if (c < 0 || c == 0 && match.groupValues[1] == "(") return false
        }
        if (high.isNotEmpty()) {
            val c = cmp(version, high)
            if (c > 0 || c == 0 && match.groupValues[4] == ")") return false
        }
        return true
    }

    private fun validateRange(range: String, location: String) {
        if (range == "*") return
        val precise = Regex("""^\[[^,\[\]()]+\]$""")
        val bounded = Regex("""^[\[(][^,\[\]()]*,[^,\[\]()]*[\])]$""")
        if (!precise.matches(range) && !bounded.matches(range))
            failAmper("FA016", location, "Maven 범위는 [1.0], [1.0,) 또는 [1.0,2.0) 형식이어야 합니다.")
    }

    private fun uniqueResources(paths: List<String>, location: String): List<String> {
        val normalized = paths.map { resourcePath(it, location) }
        if (normalized.size != normalized.toSet().size)
            failAmper("FA016", location, "중복 NeoForge 리소스 경로가 있습니다.")
        return normalized.sorted()
    }

    private fun resourcePath(value: String, location: String): String {
        if (value.isBlank() || value.startsWith('/') || value.contains('\\') || ':' in value ||
            value.split('/').any { it.isBlank() || it == "." || it == ".." } ||
            value.any { it.code < 0x20 || it.code == 0x7f })
            failAmper("FA016", location, "NeoForge 리소스 경로는 안전한 상대 경로여야 합니다.")
        return value
    }

    /** TOML 0.5 basic strings, with deterministic escaping and strict Unicode. */
    private fun quote(value: String): String = buildString {
        append('"')
        var index = 0
        while (index < value.length) {
            val c = value[index]
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> {
                    if (c.isHighSurrogate()) {
                        if (index + 1 >= value.length || !value[index + 1].isLowSurrogate())
                            failAmper("FA016", "neoforge.metadata", "Unicode surrogate 오류")
                        append(c)
                        append(value[++index])
                    } else if (c.isLowSurrogate())
                        failAmper("FA016", "neoforge.metadata", "Unicode surrogate 오류")
                    else if (c.code < 0x20 || c.code == 0x7f)
                        append("\\u").append(c.code.toString(16).uppercase().padStart(4, '0'))
                    else append(c)
                }
            }
            index++
        }
        append('"')
    }
}
