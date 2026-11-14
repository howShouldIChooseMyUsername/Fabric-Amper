package dev.fabricamper.config

import java.nio.file.Path

/**
 * Does not guess missing strings, silently coerce numbers, or discard unknown keys.
 * All raw values come from StrictYamlReader's JSON-compatible YAML tree.
 */
object ModYamlDecoder {
    @JvmStatic
    fun decode(tree: Map<String, Any?>, source: Path): ModYamlModel {
        keys(tree, setOf("schema", "product", "mod", "minecraft", "settings", "repositories",
            "dependencies", "test-dependencies", "publishing", "publish"), "", source)
        val schema = integer(tree, "schema", "", source, required = true)!!
        val product = when (val name = string(tree, "product", "", source, required = true)!!) {
            "fabric/library" -> "fabric/api"
            else -> name
        }
        val mod = parseMod(requiredMap(tree, "mod", "", source), source)
        val minecraft = parseMinecraft(requiredMap(tree, "minecraft", "", source), source)
        val settings = parseSettings(optionalMap(tree, "settings", "", source), source)
        val repositories = parseRepositories(optionalNode(tree, "repositories", "", source), source)
        val dependencies = parseDependencies(optionalNode(tree, "dependencies", "", source), "dependencies", source, false)
        val testDependencies = parseDependencies(optionalNode(tree, "test-dependencies", "", source), "test-dependencies", source, true)
        val publishing = parsePublishing(optionalNode(tree, "publishing", "", source), source)
        val publish = parsePublish(optionalNode(tree, "publish", "", source), source)
        return ModYamlModel(source, schema, product, mod, minecraft, settings, repositories,
            dependencies, testDependencies, publishing = publishing, publish = publish)
    }

    private fun parsePublish(node: Any?, file: Path): PublishSpec? {
        if (node == null) return null
        val m = map(node, "publish", file)
        keys(m, setOf("maven", "modrinth", "curseforge"), "publish", file)
        val maven = optionalNode(m, "maven", "publish", file)?.let {
            val p = "publish.maven"
            val fields = map(it, p, file)
            keys(fields, setOf("url", "credentialsFromEnv"), p, file)
            MavenSpec(
                url = string(fields, "url", p, file, required = true)!!,
                credentialsFromEnv = optionalNode(fields, "credentialsFromEnv", p, file)?.let { names ->
                    strings(list(names, "$p.credentialsFromEnv", file), "$p.credentialsFromEnv", file)
                },
            )
        }
        val modrinth = optionalNode(m, "modrinth", "publish", file)?.let {
            val p = "publish.modrinth"
            val fields = map(it, p, file)
            keys(fields, setOf("projectId", "tokenEnv", "releaseType", "versionName", "versionNumber",
                "gameVersions", "loaders", "environment", "changelog", "changelogFile", "dependencies"), p, file)
            val dependencies = optionalNode(fields, "dependencies", p, file)?.let { entries ->
                list(entries, "$p.dependencies", file).mapIndexed { i, entry ->
                    val at = "$p.dependencies[$i]"
                    val dep = map(entry, at, file)
                    keys(dep, setOf("projectId", "type"), at, file)
                    ModrinthPublishDependency(
                        projectId = string(dep, "projectId", at, file, required = true)!!,
                        type = string(dep, "type", at, file, required = true)!!,
                    )
                }
            } ?: emptyList()
            ModrinthSpec(
                projectId = string(fields, "projectId", p, file, required = true)!!,
                tokenEnv = string(fields, "tokenEnv", p, file, required = true)!!,
                releaseType = string(fields, "releaseType", p, file) ?: "release",
                versionName = string(fields, "versionName", p, file),
                versionNumber = string(fields, "versionNumber", p, file),
                gameVersions = optionalNode(fields, "gameVersions", p, file)?.let { value ->
                    strings(list(value, "$p.gameVersions", file), "$p.gameVersions", file)
                },
                loaders = optionalNode(fields, "loaders", p, file)?.let { value ->
                    strings(list(value, "$p.loaders", file), "$p.loaders", file)
                },
                environment = string(fields, "environment", p, file),
                changelog = string(fields, "changelog", p, file, permitEmpty = true),
                changelogFile = string(fields, "changelogFile", p, file),
                dependencies = dependencies,
            )
        }
        val curseforge = optionalNode(m, "curseforge", "publish", file)?.let {
            val p = "publish.curseforge"
            val fields = map(it, p, file)
            keys(fields, setOf("projectId", "tokenEnv", "releaseType", "displayName",
                "gameVersionNames", "gameVersions", "changelog", "changelogFile", "changelogType",
                "manualRelease", "relations"), p, file)
            val relations = optionalNode(fields, "relations", p, file)?.let { entries ->
                list(entries, "$p.relations", file).mapIndexed { i, entry ->
                    val at = "$p.relations[$i]"
                    val relation = map(entry, at, file)
                    keys(relation, setOf("slug", "projectId", "type"), at, file)
                    CurseForgePublishRelation(
                        slug = string(relation, "slug", at, file, required = true)!!,
                        projectId = string(relation, "projectId", at, file),
                        type = string(relation, "type", at, file, required = true)!!,
                    )
                }
            } ?: emptyList()
            CurseForgeSpec(
                projectId = integer(fields, "projectId", p, file, required = true)!!,
                tokenEnv = string(fields, "tokenEnv", p, file, required = true)!!,
                releaseType = string(fields, "releaseType", p, file) ?: "release",
                displayName = string(fields, "displayName", p, file),
                gameVersionNames = optionalNode(fields, "gameVersionNames", p, file)?.let { value ->
                    strings(list(value, "$p.gameVersionNames", file), "$p.gameVersionNames", file)
                },
                gameVersions = optionalNode(fields, "gameVersions", p, file)?.let { value ->
                    integers(list(value, "$p.gameVersions", file), "$p.gameVersions", file)
                },
                changelog = string(fields, "changelog", p, file, permitEmpty = true),
                changelogFile = string(fields, "changelogFile", p, file),
                changelogType = string(fields, "changelogType", p, file) ?: "markdown",
                manualRelease = boolean(fields, "manualRelease", p, file) ?: false,
                relations = relations,
            )
        }
        return PublishSpec(maven, modrinth, curseforge)
    }

    private fun parsePublishing(node: Any?, file: Path): PublishingSpec? {
        if (node == null) return null
        val publishing = map(node, "publishing", file)
        keys(publishing, setOf("repository"), "publishing", file)
        val repositoryNode = optionalNode(publishing, "repository", "publishing", file)
            ?: return PublishingSpec()
        val repository = map(repositoryNode, "publishing.repository", file)
        keys(repository, setOf("url", "usernameEnv", "passwordEnv"), "publishing.repository", file)
        return PublishingSpec(PublishingRepositorySpec(
            url = string(repository, "url", "publishing.repository", file, required = true)!!,
            usernameEnv = string(repository, "usernameEnv", "publishing.repository", file),
            passwordEnv = string(repository, "passwordEnv", "publishing.repository", file),
        ))
    }

    private fun parseMod(m: Map<String, Any?>, file: Path): ModSpec {
        val p = "mod"
        keys(m, setOf("id", "version", "group", "archivesName", "name", "description", "environment",
            "license", "authors", "contributors", "contact", "icon", "provides", "entrypoints",
            "languageAdapters", "mixins", "accessWidener", "depends", "recommends", "suggests",
            "conflicts", "breaks", "custom"), p, file)
        val license = optionalNode(m, "license", p, file)?.let { node ->
            when (node) {
                is String -> node.also { nonblank(it, "$p.license", file) }
                is List<*> -> strings(node, "$p.license", file)
                else -> error("FA003", "$p.license", "expected string or string array", file)
            }
        }
        val icon = optionalNode(m, "icon", p, file)?.let { node ->
            when (node) {
                is String -> node.also { nonblank(it, "$p.icon", file) }
                is Map<*, *> -> {
                    val icons = map(node, "$p.icon", file)
                    if (icons.isEmpty()) error("FA003", "$p.icon", "icon size map must not be empty", file)
                    icons.mapValues { (size, value) ->
                        if (size.toIntOrNull()?.let { it > 0 } != true) {
                            error("FA003", "$p.icon.$size", "icon size must be a positive integer", file)
                        }
                        value as? String ?: error("FA003", "$p.icon.$size", "expected PNG resource path", file)
                    }
                }
                else -> error("FA003", "$p.icon", "expected path or size-to-path map", file)
            }
        }
        return ModSpec(
            id = string(m, "id", p, file, required = true)!!,
            version = string(m, "version", p, file, required = true)!!,
            group = string(m, "group", p, file),
            archivesName = string(m, "archivesName", p, file),
            name = string(m, "name", p, file),
            description = string(m, "description", p, file, permitEmpty = true),
            environment = string(m, "environment", p, file) ?: "*",
            license = license,
            authors = people(optionalNode(m, "authors", p, file), "$p.authors", file),
            contributors = people(optionalNode(m, "contributors", p, file), "$p.contributors", file),
            contact = stringMap(optionalNode(m, "contact", p, file), "$p.contact", file),
            icon = icon,
            provides = listOfStrings(optionalNode(m, "provides", p, file), "$p.provides", file),
            entrypoints = entrypoints(optionalNode(m, "entrypoints", p, file), "$p.entrypoints", file),
            languageAdapters = stringMap(optionalNode(m, "languageAdapters", p, file), "$p.languageAdapters", file),
            mixins = mixins(optionalNode(m, "mixins", p, file), "$p.mixins", file),
            accessWidener = string(m, "accessWidener", p, file),
            depends = versionMap(optionalNode(m, "depends", p, file), "$p.depends", file),
            recommends = versionMap(optionalNode(m, "recommends", p, file), "$p.recommends", file),
            suggests = versionMap(optionalNode(m, "suggests", p, file), "$p.suggests", file),
            conflicts = versionMap(optionalNode(m, "conflicts", p, file), "$p.conflicts", file),
            breaks = versionMap(optionalNode(m, "breaks", p, file), "$p.breaks", file),
            custom = optionalNode(m, "custom", p, file)?.let { map(it, "$p.custom", file) } ?: emptyMap(),
        )
    }

    private fun parseMinecraft(m: Map<String, Any?>, file: Path): MinecraftSpec {
        val p = "minecraft"
        keys(m, setOf("version", "mappings", "loader", "fabricApi"), p, file)
        val mappingNode = optionalNode(m, "mappings", p, file) ?: "mojang"
        val mappings = when (mappingNode) {
            is String -> MappingSpec(mappingNode)
            is Map<*, *> -> {
                val mm = map(mappingNode, "$p.mappings", file)
                keys(mm, setOf("type", "version"), "$p.mappings", file)
                MappingSpec(string(mm, "type", "$p.mappings", file, required = true)!!,
                    string(mm, "version", "$p.mappings", file))
            }
            else -> error("FA003", "$p.mappings", "expected 'mojang', 'yarn', or mapping object", file)
        }
        val loader = string(m, "loader", p, file, required = true)!!
        if (!VersionSyntax.isFixedGradleVersion(loader)) {
            error("FA003", "$p.loader", "expected a fixed Fabric Loader version, not a Gradle dynamic selector", file)
        }
        val fabricApi = string(m, "fabricApi", p, file)
        if (fabricApi != null && !VersionSyntax.isFixedGradleVersion(fabricApi)) {
            error("FA003", "$p.fabricApi", "expected a fixed Fabric API version, not a Gradle dynamic selector", file)
        }
        return MinecraftSpec(
            version = string(m, "version", p, file, required = true)!!,
            mappings = mappings,
            loader = loader,
            fabricApi = fabricApi,
        )
    }

    private fun parseSettings(m: Map<String, Any?>, file: Path): SettingsSpec {
        keys(m, setOf("java", "kotlin", "loom", "resources"), "settings", file)
        val j = optionalMap(m, "java", "settings", file)
        keys(j, setOf("release", "sourcesJar"), "settings.java", file)
        val k = optionalMap(m, "kotlin", "settings", file)
        keys(k, setOf("enabled", "pluginVersion", "languageVersion", "fabricLanguageKotlin"), "settings.kotlin", file)
        val l = optionalMap(m, "loom", "settings", file)
        keys(l, setOf("splitEnvironmentSourceSets"), "settings.loom", file)
        val r = optionalMap(m, "resources", "settings", file)
        keys(r, setOf("failOnExistingFabricModJson"), "settings.resources", file)
        return SettingsSpec(
            java = JavaSettings(integer(j, "release", "settings.java", file) ?: 25,
                boolean(j, "sourcesJar", "settings.java", file) ?: true),
            kotlin = KotlinSettings(
                enabled = boolean(k, "enabled", "settings.kotlin", file) ?: false,
                pluginVersion = string(k, "pluginVersion", "settings.kotlin", file) ?: "2.4.21",
                languageVersion = string(k, "languageVersion", "settings.kotlin", file) ?: "2.4",
                fabricLanguageKotlin = string(k, "fabricLanguageKotlin", "settings.kotlin", file)
                    ?: "1.14.2+kotlin.2.4.21",
            ),
            loom = LoomSettings(boolean(l, "splitEnvironmentSourceSets", "settings.loom", file) ?: false),
            resources = ResourceSettings(
                boolean(r, "failOnExistingFabricModJson", "settings.resources", file) ?: true),
        )
    }

    private fun parseRepositories(node: Any?, file: Path): List<RepositorySpec> {
        if (node == null) return emptyList()
        return list(node, "repositories", file).mapIndexed { i, item ->
            val p = "repositories[$i]"
            when (item) {
                is String -> {
                    if (item !in setOf("fabric", "mavenCentral", "modrinth")) {
                        error("FA003", p, "unknown repository '$item'", file)
                    }
                    RepositorySpec(item)
                }
                is Map<*, *> -> {
                    val m = map(item, p, file)
                    keys(m, setOf("maven"), p, file)
                    RepositorySpec("maven", string(m, "maven", p, file, required = true)!!)
                }
                else -> error("FA003", p, "expected repository identifier or {maven: url}", file)
            }
        }.distinct()
    }

    private fun parseDependencies(node: Any?, name: String, file: Path, test: Boolean): List<DependencySpec> {
        if (node == null) return emptyList()
        return list(node, name, file).mapIndexed { index, value ->
            val p = "$name[$index]"
            if (value is String) {
                DependencySpec("library", coordinate(value, p, file))
            } else {
                val m = map(value, p, file)
                if (m.size == 1 && !m.containsKey("maven") && !m.containsKey("modrinth")) {
                    val (rawCoord, rawScope) = m.entries.single()
                    val shortcut = rawScope as? String
                        ?: error("FA003", p, "shorthand dependency scope must be string", file)
                    val scope = when (shortcut) {
                        "all", "compile-only", "runtime-only" -> shortcut
                        "exported", "include" -> "all"
                        else -> error("FA003", p, "unknown dependency scope '$shortcut'", file)
                    }
                    DependencySpec("library", coordinate(rawCoord, p, file), scope, "main",
                        exported = shortcut == "exported", include = shortcut == "include")
                } else {
                    keys(m, setOf("maven", "modrinth", "kind", "scope", "target", "exported", "include"), p, file)
                    val hasMaven = m.containsKey("maven")
                    val hasModrinth = m.containsKey("modrinth")
                    if (hasMaven == hasModrinth) {
                        error("FA003", p, "exactly one of maven or modrinth is required", file)
                    }
                    val coordinate = if (hasMaven) {
                        coordinate(string(m, "maven", p, file, required = true)!!, "$p.maven", file)
                    } else {
                        val mm = map(m["modrinth"], "$p.modrinth", file)
                        keys(mm, setOf("project", "version"), "$p.modrinth", file)
                        val project = string(mm, "project", "$p.modrinth", file, required = true)!!
                        val version = string(mm, "version", "$p.modrinth", file, required = true)!!
                        coordinate("maven.modrinth:$project:$version", "$p.modrinth", file)
                    }
                    val kind = string(m, "kind", p, file) ?: if (hasMaven) "library" else "mod"
                    val scope = string(m, "scope", p, file) ?: "all"
                    val target = string(m, "target", p, file) ?: "main"
                    DependencySpec(kind, coordinate, scope, target,
                        boolean(m, "exported", p, file) ?: false,
                        boolean(m, "include", p, file) ?: false)
                }
            }.also { dep ->
                if (test && (dep.include || dep.exported || dep.target != "main")) {
                    error("FA003", p, "test-dependencies cannot be included, exported, or client-targeted", file)
                }
            }
        }
    }

    private fun coordinate(raw: String, p: String, file: Path): String {
        val parts = raw.split(':')
        if (parts.size != 3 || parts.any { it.isBlank() || it != it.trim() }
            || !parts[0].matches(Regex("[A-Za-z0-9_.-]+"))
            || !parts[1].matches(Regex("[A-Za-z0-9_.-]+"))
            || parts[2] == "+" || parts[2].endsWith(".+") || parts[2].startsWith("+")
            || parts[2].contains("latest.", ignoreCase = true)
            || parts[2].contains('[') || parts[2].contains('(')
        ) {
            error("FA003", p, "expected fixed Maven group:artifact:version coordinate", file)
        }
        return raw
    }

    private fun people(node: Any?, p: String, file: Path): List<Any> {
        if (node == null) return emptyList()
        return list(node, p, file).mapIndexed { index, value ->
            when (value) {
                is String -> nonblank(value, "$p[$index]", file)
                is Map<*, *> -> {
                    val m = map(value, "$p[$index]", file)
                    keys(m, setOf("name", "contact"), "$p[$index]", file)
                    val name = string(m, "name", "$p[$index]", file, required = true)!!
                    val contact = stringMap(
                        optionalNode(m, "contact", "$p[$index]", file), "$p[$index].contact", file)
                    linkedMapOf<String, Any>("name" to name).apply {
                        if (contact.isNotEmpty()) put("contact", contact)
                    }
                }
                else -> error("FA003", "$p[$index]", "expected name string or person map", file)
            }
        }
    }

    private fun entrypoints(node: Any?, p: String, file: Path): Map<String, List<EntrypointSpec>> {
        if (node == null) return emptyMap()
        return map(node, p, file).mapValues { (key, value) ->
            val elements = list(value, "$p.$key", file)
            elements.mapIndexed { index, item ->
                val at = "$p.$key[$index]"
                when (item) {
                    is String -> EntrypointSpec(nonblank(item, at, file))
                    is Map<*, *> -> {
                        val m = map(item, at, file)
                        keys(m, setOf("adapter", "value"), at, file)
                        EntrypointSpec(string(m, "value", at, file, required = true)!!,
                            string(m, "adapter", at, file, required = true)!!, objectForm = true)
                    }
                    else -> error("FA003", at, "expected entrypoint string or {adapter,value}", file)
                }
            }
        }
    }

    private fun mixins(node: Any?, p: String, file: Path): List<MixinSpec> {
        if (node == null) return emptyList()
        return list(node, p, file).mapIndexed { index, item ->
            val at = "$p[$index]"
            when (item) {
                is String -> MixinSpec(nonblank(item, at, file))
                is Map<*, *> -> {
                    val m = map(item, at, file)
                    keys(m, setOf("config", "environment"), at, file)
                    MixinSpec(string(m, "config", at, file, required = true)!!,
                        string(m, "environment", at, file), objectForm = true)
                }
                else -> error("FA003", at, "expected mixin config string or object", file)
            }
        }
    }

    private fun versionMap(node: Any?, p: String, file: Path): Map<String, Any> {
        if (node == null) return emptyMap()
        return map(node, p, file).mapValues { (key, value) ->
            val at = "$p.$key"
            when (value) {
                is String -> nonblank(value, at, file)
                is List<*> -> strings(value, at, file).also {
                    if (it.isEmpty()) error("FA003", at, "version OR-array cannot be empty", file)
                }
                else -> error("FA003", at, "expected version range string or OR-array", file)
            }
        }
    }

    private fun stringMap(node: Any?, p: String, file: Path): Map<String, String> {
        if (node == null) return emptyMap()
        return map(node, p, file).mapValues { (key, value) ->
            value as? String ?: error("FA003", "$p.$key", "expected string", file)
        }
    }

    private fun listOfStrings(node: Any?, p: String, file: Path): List<String> =
        if (node == null) emptyList() else strings(list(node, p, file), p, file)

    private fun strings(node: List<*>, p: String, file: Path): List<String> =
        node.mapIndexed { i, item ->
            nonblank(item as? String ?: error("FA003", "$p[$i]", "expected string", file), "$p[$i]", file)
        }

    private fun integers(node: List<*>, p: String, file: Path): List<Int> =
        node.mapIndexed { i, item ->
            when (item) {
                is Int -> item
                is Long -> item.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
                    ?: error("FA003", "$p[$i]", "expected integer", file)
                else -> error("FA003", "$p[$i]", "expected integer", file)
            }
        }

    private fun list(node: Any?, p: String, file: Path): List<*> =
        node as? List<*> ?: error("FA003", p, "expected array", file)

    private fun map(node: Any?, p: String, file: Path): Map<String, Any?> {
        val raw = node as? Map<*, *> ?: error("FA003", p, "expected mapping", file)
        if (raw.keys.any { it !is String }) error("FA003", p, "mapping keys must be strings", file)
        @Suppress("UNCHECKED_CAST")
        return raw as Map<String, Any?>
    }

    private fun requiredMap(m: Map<String, Any?>, key: String, p: String, file: Path): Map<String, Any?> =
        map(m[key] ?: error("FA003", path(p, key), "missing required mapping", file), path(p, key), file)

    private fun optionalMap(m: Map<String, Any?>, key: String, p: String, file: Path): Map<String, Any?> =
        optionalNode(m, key, p, file)?.let { map(it, path(p, key), file) } ?: emptyMap()

    /** Missing is optional; explicit YAML null for a typed field is not. */
    private fun optionalNode(m: Map<String, Any?>, key: String, p: String, file: Path): Any? {
        if (key !in m) return null
        return m[key] ?: error("FA003", path(p, key), "explicit null is not allowed for this field", file)
    }

    private fun keys(m: Map<String, Any?>, allowed: Set<String>, p: String, file: Path) {
        m.keys.firstOrNull { it !in allowed }?.let { name ->
            error("FA003", path(p, name), "unknown key; supported keys: ${allowed.sorted().joinToString()}", file)
        }
    }

    private fun string(
        m: Map<String, Any?>,
        key: String,
        p: String,
        file: Path,
        required: Boolean = false,
        permitEmpty: Boolean = false,
    ): String? {
        if (key !in m && !required) return null
        val v = m[key] as? String ?: error("FA003", path(p, key),
            if (key !in m) "missing required string" else "expected string (quote version numbers)", file)
        if (!permitEmpty) nonblank(v, path(p, key), file)
        return v
    }

    private fun integer(m: Map<String, Any?>, key: String, p: String, file: Path, required: Boolean = false): Int? {
        if (key !in m && !required) return null
        val value = m[key]
        return when (value) {
            is Int -> value
            is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
            else -> null
        } ?: error("FA003", path(p, key), "expected integer", file)
    }

    private fun boolean(m: Map<String, Any?>, key: String, p: String, file: Path): Boolean? {
        if (key !in m) return null
        return m[key] as? Boolean ?: error("FA003", path(p, key), "expected true or false", file)
    }

    private fun nonblank(v: String, p: String, file: Path): String {
        if (v.isBlank() || v != v.trim()) error("FA003", p, "value must not be blank or padded by whitespace", file)
        return v
    }

    private fun path(parent: String, child: String) = if (parent.isEmpty()) child else "$parent.$child"

    private fun error(code: String, p: String, message: String, file: Path): Nothing =
        throw FabricAmperConfigException(code, p, message, file)
}
