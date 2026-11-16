package dev.fabricamper.config

import dev.fabricamper.util.LoaderVersionConstraints
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Cross-field invariants, Fabric metadata dependencies and predictable failure
 * diagnostics. Never silently replace a user's explicit Loader constraint.
 */
object ModYamlValidator {
    private val idPattern = Regex("^[a-z][a-z0-9_-]{1,63}$")
    private val archivePattern = Regex("^[A-Za-z0-9._-]+$")
    private val coordinateSegment = Regex("^[A-Za-z0-9_.-]+$")
    private val environmentVariableName = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val platformProjectId = Regex("^[A-Za-z0-9][A-Za-z0-9_-]*$")
    private val platformVersion = Regex("^[A-Za-z0-9][A-Za-z0-9._+-]*$")
    private val platformLoader = Regex("^[a-z][a-z0-9_-]*$")
    private val releaseTypes = setOf("release", "beta", "alpha")
    private val modrinthEnvironments = setOf(
        "client_and_server", "client_only", "client_only_server_optional", "singleplayer_only",
        "server_only", "server_only_client_optional", "dedicated_server_only",
        "client_or_server", "client_or_server_prefers_both",
    )
    private val modrinthDependencyTypes = setOf("required", "optional", "incompatible", "embedded")
    private val curseForgeRelationTypes = setOf(
        "embeddedLibrary", "incompatible", "optionalDependency", "requiredDependency", "tool",
    )
    private val envs = setOf("*", "client", "server")

    @JvmStatic
    fun validate(raw: ModYamlModel): ModYamlModel {
        val file = raw.source
        if (raw.schema != FabricAmperSchemaVersion.LATEST) {
            fail("FA003", "schema", "unsupported schema ${raw.schema}; supported schema: ${FabricAmperSchemaVersion.LATEST}", file)
        }
        val product = if (raw.product == "fabric/library") "fabric/api" else raw.product
        if (product !in setOf("fabric/mod", "fabric/api")) {
            fail("FA003", "product", "expected 'fabric/mod', 'fabric/api', or 'fabric/library'", file)
        }
        val mod = raw.mod
        if (!idPattern.matches(mod.id)) {
            fail("FA009", "mod.id", "mod ID must be 2-64 chars, start with lowercase ASCII, and contain only a-z, 0-9, _ or -", file)
        }
        if (mod.version.isBlank() || mod.version.any { it.isWhitespace() }) {
            fail("FA009", "mod.version", "mod version must be a non-empty single token", file)
        }
        mod.group?.let {
            if (it.split('.').any { segment -> !coordinateSegment.matches(segment) }) {
                fail("FA003", "mod.group", "expected dotted Maven group identifier", file)
            }
        }
        if (product == "fabric/api" && mod.group.isNullOrBlank()) {
            fail("FA003", "mod.group", "API/library Maven publishing requires an explicit mod.group", file)
        }
        if (raw.publishing != null && product != "fabric/api") {
            fail("FA003", "publishing", "publishing is only supported for fabric/api or fabric/library", file)
        }
        raw.publishing?.repository?.let { repo ->
            validateRemoteMavenUrl(repo.url, "publishing.repository.url", file)
            listOf("usernameEnv" to repo.usernameEnv, "passwordEnv" to repo.passwordEnv).forEach { (name, value) ->
                if (value != null && !environmentVariableName.matches(value)) {
                    fail("FA003", "publishing.repository.$name", "expected environment variable name (not a secret value)", file)
                }
            }
            if ((repo.usernameEnv == null) != (repo.passwordEnv == null)) {
                fail("FA003", "publishing.repository",
                    "usernameEnv and passwordEnv must be provided together", file)
            }
        }
        validatePublish(raw, file)
        mod.archivesName?.let {
            if (!archivePattern.matches(it) || it == "." || it == "..") {
                fail("FA003", "mod.archivesName", "invalid output archive base name", file)
            }
        }
        if (mod.environment !in envs) {
            fail("FA009", "mod.environment", "expected '*', 'client', or 'server'", file)
        }
        val aliases = mutableSetOf<String>()
        mod.provides.forEachIndexed { index, alias ->
            if (!idPattern.matches(alias) || alias == mod.id || !aliases.add(alias)) {
                fail("FA009", "mod.provides[$index]", "invalid, duplicate or self-referential provided mod ID", file)
            }
        }
        mod.entrypoints.forEach { (key, values) ->
            if (key.isBlank() || values.isEmpty()) fail("FA009", "mod.entrypoints.$key", "entrypoint must be non-empty", file)
            values.forEachIndexed { index, item ->
                if (item.value.isBlank()) fail("FA009", "mod.entrypoints.$key[$index]", "entrypoint value missing", file)
                if (item.adapter != null && item.adapter.isBlank()) {
                    fail("FA009", "mod.entrypoints.$key[$index].adapter", "adapter ID missing", file)
                }
                if (item.adapter == "kotlin" && !raw.settings.kotlin.enabled) {
                    fail("FA006", "mod.entrypoints.$key[$index].adapter",
                        "Kotlin entrypoint requires settings.kotlin.enabled=true", file)
                }
            }
        }
        mod.languageAdapters.forEach { (name, type) ->
            if (name.isBlank() || type.isBlank()) {
                fail("FA009", "mod.languageAdapters.$name", "adapter name and class are required", file)
            }
            if (name == "kotlin") {
                fail("FA006", "mod.languageAdapters.kotlin", "do not redefine Fabric Language Kotlin's built-in adapter", file)
            }
        }
        mod.mixins.forEachIndexed { index, mixin ->
            if (mixin.environment != null && mixin.environment !in envs) {
                fail("FA009", "mod.mixins[$index].environment", "invalid mixin environment", file)
            }
            checkResource(file, mixin.config, "mod.mixins[$index].config",
                allowClient = raw.settings.loom.splitEnvironmentSourceSets)
            // A Mixin resolved only from the client source set cannot be
            // globally active in a mod that is also loaded on dedicated servers.
            // checkResource has already enforced source-set containment and
            // selects main over client when both resources exist.
            if (mod.environment != "client" && mixin.environment != "client") {
                val base = file.toAbsolutePath().normalize().parent
                    ?: fail("FA009", "mod.mixins[$index].config", "invalid YAML source directory", file)
                val mainResource = base.resolve("src/main/resources").resolve(mixin.config)
                if (!Files.exists(mainResource, LinkOption.NOFOLLOW_LINKS)) {
                    fail("FA009", "mod.mixins[$index].config",
                        "Mixin '${mixin.config}' exists only in src/client/resources; " +
                            "set environment: client for this Mixin or mod.environment: client " +
                            "to prevent dedicated-server loading", file)
                }
            }
        }
        mod.accessWidener?.let { accessWidener ->
            // Loom reads its accessWidenerPath from the main resources root, not client-only resources.
            val resource = checkResource(file, accessWidener, "mod.accessWidener", mainOnly = true)
            val header = try {
                Files.newBufferedReader(resource, Charsets.UTF_8).use { reader -> reader.readLine() }
            } catch (e: Exception) {
                fail("FA009", "mod.accessWidener", "cannot read access widener header: ${e.message}", file)
            }
            when (raw.minecraft.profile) {
                MinecraftProfile.NON_REMAPPING -> {
                    if (!accessWidener.endsWith(".classtweaker")) {
                        fail("FA009", "mod.accessWidener", "non-remapping class tweaker must have .classtweaker extension", file)
                    }
                    if (header != "classTweaker v1 official") {
                        fail("FA009", "mod.accessWidener", "expected 'classTweaker v1 official' header", file)
                    }
                }
                MinecraftProfile.REMAPPING -> {
                    if (!accessWidener.endsWith(".accesswidener")) {
                        fail("FA009", "mod.accessWidener", "legacy remapping access widener must have .accesswidener extension", file)
                    }
                    if (header?.matches(Regex("accessWidener v[12] (named|intermediary)")) != true) {
                        fail("FA009", "mod.accessWidener", "expected legacy 'accessWidener v1/v2 named/intermediary' header", file)
                    }
                }
            }
        }
        when (val icon = mod.icon) {
            is String -> checkResource(file, icon, "mod.icon", png = true,
                allowClient = raw.settings.loom.splitEnvironmentSourceSets)
            is Map<*, *> -> icon.forEach { (size, path) ->
                checkResource(file, path as String, "mod.icon.$size", png = true,
                    allowClient = raw.settings.loom.splitEnvironmentSourceSets)
            }
        }
        mod.contact.forEach { (key, value) ->
            if (value.isBlank()) fail("FA003", "mod.contact.$key", "contact value cannot be empty", file)
            if (key in setOf("homepage", "issues") &&
                !(value.startsWith("https://") || value.startsWith("http://"))
            ) {
                fail("FA003", "mod.contact.$key", "expected HTTP(S) URL", file)
            }
            if (key == "email" && !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(value)) {
                fail("FA003", "mod.contact.email", "invalid email contact", file)
            }
        }

        val architectury = raw.architectury
        if (!architectury.enabled && architectury.version != null) {
            fail("FA003", "architectury.version", "enable Architectury before selecting its version", file)
        }
        if (architectury.enabled && raw.minecraft.version != ArchitecturyDefaults.MINECRAFT_VERSION) {
            fail("FA003", "architectury", "Architectury API convenience is verified only for Minecraft 26.3", file)
        }
        val architecturyVersion = if (architectury.enabled) architectury.version ?: ArchitecturyDefaults.VERSION else null
        if (architecturyVersion != null && !VersionSyntax.isFixedGradleVersion(architecturyVersion)) {
            fail("FA003", "architectury.version", "expected a fixed Architectury API version, not a Gradle dynamic selector", file)
        }
        val mc = if (architectury.enabled && raw.minecraft.fabricApi == null) {
            raw.minecraft.copy(fabricApi = ArchitecturyDefaults.FABRIC_API_VERSION)
        } else raw.minecraft
        if (!mc.version.matches(Regex("^\\d+\\.\\d+(?:\\.\\d+)?$"))) {
            fail("FA003", "minecraft.version", "expected exact Minecraft version (quote it in YAML)", file)
        }
        if (!VersionSyntax.isFixedGradleVersion(mc.loader)) {
            fail("FA003", "minecraft.loader", "expected a fixed Fabric Loader version, not a Gradle dynamic selector", file)
        }
        if (mc.fabricApi != null && !VersionSyntax.isFixedGradleVersion(mc.fabricApi)) {
            fail("FA003", "minecraft.fabricApi", "expected a fixed Fabric API version, not a Gradle dynamic selector", file)
        }
        if (mc.mappings.type !in setOf("mojang", "yarn")) {
            fail("FA003", "minecraft.mappings", "unknown mapping type", file)
        }
        if (mc.profile == MinecraftProfile.NON_REMAPPING && mc.mappings.type != "mojang") {
            fail("FA004", "minecraft.mappings", "Minecraft 26.1+ uses official names, not Yarn", file)
        }
        if (mc.mappings.type == "yarn" && mc.mappings.version.isNullOrBlank()) {
            fail("FA003", "minecraft.mappings.version", "Yarn requires explicit version", file)
        }
        if (mc.mappings.type == "mojang" && mc.mappings.version != null) {
            fail("FA003", "minecraft.mappings.version", "Mojang mappings do not take a version", file)
        }
        val java = raw.settings.java
        if (mc.profile == MinecraftProfile.NON_REMAPPING && java.release != 25) {
            fail("FA005", "settings.java.release", "Minecraft 26.1+ requires Java 25", file)
        }
        if (java.release < 8 || java.release > 30) {
            fail("FA003", "settings.java.release", "unsupported Java release", file)
        }
        val kotlin = raw.settings.kotlin
        if (kotlin.enabled) {
            if (kotlin.pluginVersion != "2.4.21") {
                fail("FA006", "settings.kotlin.pluginVersion",
                    "bundled Kotlin Gradle plugin version is pinned to 2.4.21", file)
            }
            if (kotlin.languageVersion != "2.4") {
                fail("FA006", "settings.kotlin.languageVersion", "pinned Kotlin language version is 2.4", file)
            }
            if (kotlin.fabricLanguageKotlin != "1.14.2+kotlin.2.4.21") {
                fail("FA006", "settings.kotlin.fabricLanguageKotlin",
                    "requires matching Fabric Language Kotlin 1.14.2+kotlin.2.4.21", file)
            }
        }
        if (!raw.settings.resources.failOnExistingFabricModJson) {
            fail("FA003", "settings.resources.failOnExistingFabricModJson", "must remain true", file)
        }
        listOf("src/main/resources/fabric.mod.json", "src/client/resources/fabric.mod.json").forEach { rel ->
            if (Files.exists(file.toAbsolutePath().parent.resolve(rel))) {
                fail("FA008", rel, "existing fabric.mod.json must not be replaced", file)
            }
        }
        raw.repositories.forEachIndexed { index, repo ->
            if (repo.kind == "maven" && (repo.url == null ||
                    !repo.url.startsWith("https://") || !looksLikeUrl(repo.url))
            ) {
                fail("FA003", "repositories[$index]", "custom Maven repositories require valid HTTPS URL", file)
            }
        }
        validateDependencies(raw.dependencies, raw, "dependencies")
        validateDependencies(raw.testDependencies, raw, "test-dependencies")
        if (architecturyVersion != null) {
            raw.dependencies.forEachIndexed { index, dependency ->
                if (dependency.coordinate.substringBeforeLast(':') == ArchitecturyDefaults.GROUP_ARTIFACT) {
                    fail("FA012", "dependencies[$index]", "architectury.enable already manages architectury-fabric; remove the manual duplicate or disable the switch", file)
                }
            }
        }

        // Validate every authored term before evaluating any OR alternative.
        // Otherwise a matching sibling can mask a malformed predicate, and
        // unknown mod IDs would not be syntax-checked at all.
        listOf(
            "mod.depends" to mod.depends,
            "mod.recommends" to mod.recommends,
            "mod.suggests" to mod.suggests,
            "mod.conflicts" to mod.conflicts,
            "mod.breaks" to mod.breaks,
        ).forEach { (prefix, constraints) ->
            constraints.forEach { (modId, requirement) ->
                val path = "$prefix.$modId"
                when (requirement) {
                    is String -> checkLoaderPredicate(requirement, path, file)
                    is List<*> -> {
                        if (requirement.isEmpty() || requirement.any { it !is String }) {
                            fail("FA009", path, "Loader version OR-array must contain strings", file)
                        }
                        requirement.forEachIndexed { index, expression ->
                            checkLoaderPredicate(expression as String, "$path[$index]", file)
                        }
                    }
                    else -> fail("FA009", path, "invalid Loader version requirement type", file)
                }
            }
        }

        // User-provided Loader requirements are immutable. Fill only missing keys.
        val depends = LinkedHashMap(mod.depends)
        val defaults = linkedMapOf(
            "minecraft" to mc.version,
            "fabricloader" to ">=${mc.loader}",
            "java" to ">=${java.release}",
        )
        mc.fabricApi?.let { defaults["fabric-api"] = ">=" + it.substringBefore('+') }
        if (kotlin.enabled) {
            defaults["fabric-language-kotlin"] = ">=" + kotlin.fabricLanguageKotlin.substringBefore('+')
        }
        if (architecturyVersion != null) defaults["architectury"] = ">=$architecturyVersion"
        defaults.forEach { (key, requirement) ->
            depends.putIfAbsent(key, requirement)
        }
        val versions = linkedMapOf(
            "minecraft" to mc.version,
            "fabricloader" to mc.loader,
            "java" to java.release.toString(),
        )
        mc.fabricApi?.let { versions["fabric-api"] = it }
        if (kotlin.enabled) versions["fabric-language-kotlin"] = kotlin.fabricLanguageKotlin
        if (architecturyVersion != null) versions["architectury"] = architecturyVersion
        for ((key, version) in versions) {
            val predicate = depends.getValue(key)
            val expressions = when (predicate) {
                is String -> listOf(predicate)
                is List<*> -> {
                    if (predicate.isEmpty() || predicate.any { it !is String }) {
                        fail("FA009", "mod.depends.$key", "Loader version OR-array must contain strings", file)
                    }
                    predicate.filterIsInstance<String>()
                }
                else -> fail("FA009", "mod.depends.$key", "invalid Loader version requirement type", file)
            }
            when (LoaderVersionConstraints.matchesAny(expressions, version)) {
                true -> Unit
                false -> fail("FA009", "mod.depends.$key", "declared version requirements do not accept chosen $key version $version", file)
                null -> fail("FA009", "mod.depends.$key", "cannot reliably evaluate Loader version requirements for chosen $key version $version", file)
            }
        }
        listOf(
            "mod.depends" to depends,
            "mod.recommends" to mod.recommends,
            "mod.suggests" to mod.suggests,
            "mod.conflicts" to mod.conflicts,
            "mod.breaks" to mod.breaks,
        ).forEach { (path, constraints) ->
            constraints.keys.forEach {
                if (!idPattern.matches(it) && it !in setOf("minecraft", "fabricloader", "java")) {
                    fail("FA009", "$path.$it", "invalid Loader dependency ID", file)
                }
            }
        }
        val repositories = if (architecturyVersion != null && raw.repositories.none {
                it.kind == "maven" && it.url?.trimEnd('/') == ArchitecturyDefaults.MAVEN_URL.trimEnd('/')
            }) raw.repositories + RepositorySpec("maven", ArchitecturyDefaults.MAVEN_URL)
            else raw.repositories
        val dependencies = if (architecturyVersion != null) raw.dependencies + DependencySpec(
            kind = "mod", coordinate = "${ArchitecturyDefaults.GROUP_ARTIFACT}:$architecturyVersion",
        ) else raw.dependencies
        return raw.copy(
            product = product,
            minecraft = mc,
            mod = mod.copy(depends = depends),
            repositories = repositories,
            dependencies = dependencies,
            architectury = architectury.copy(version = architecturyVersion),
        )
    }

    private fun validatePublish(model: ModYamlModel, file: Path) {
        val publish = model.publish ?: return
        if (publish.maven == null && publish.modrinth == null && publish.curseforge == null) {
            fail("FA003", "publish", "publish must configure at least one destination", file)
        }
        publish.common?.let { common ->
            if (publish.modrinth == null && publish.curseforge == null) {
                fail("FA003", "publish.common", "common requires a Modrinth or CurseForge destination", file)
            }
            common.releaseType?.let { validatePublishReleaseType(it, "publish.common.releaseType", file) }
            validatePublishTitle(common.name, "publish.common.name", file)
            validatePublishNames(common.gameVersions, "publish.common.gameVersions", file, platformVersion)
            validatePublishChangelog(common.changelog, common.changelogFile, "publish.common", file)
            val seenSlugs = mutableSetOf<String>()
            val seenModrinthIds = mutableSetOf<String>()
            common.dependencies?.forEachIndexed { index, dependency ->
                val path = "publish.common.dependencies[$index]"
                validatePublishProjectId(dependency.slug, "$path.slug", file)
                dependency.modrinthId?.let { validatePublishProjectId(it, "$path.modrinthId", file) }
                dependency.curseforgeId?.let {
                    if (!it.matches(Regex("^[1-9][0-9]*$"))) {
                        fail("FA003", "$path.curseforgeId", "expected positive numeric-string CurseForge project ID", file)
                    }
                }
                if (dependency.type !in modrinthDependencyTypes) {
                    fail("FA003", "$path.type", "common dependency type must be required, optional, incompatible or embedded", file)
                }
                if (!seenSlugs.add(dependency.slug)) {
                    fail("FA003", "$path.slug", "duplicate common dependency slug", file)
                }
                if (!seenModrinthIds.add(dependency.modrinthId ?: dependency.slug)) {
                    fail("FA003", "$path.modrinthId", "duplicate effective Modrinth project ID", file)
                }
            }
        }
        publish.maven?.let { maven ->
            if (model.publishing?.repository != null) {
                fail("FA003", "publish.maven",
                    "publish.maven and publishing.repository cannot both configure remote Maven publishing", file)
            }
            if (model.mod.group.isNullOrBlank()) {
                fail("FA003", "mod.group", "publish.maven requires an explicit mod.group", file)
            }
            validateRemoteMavenUrl(maven.url, "publish.maven.url", file)
            maven.credentialsFromEnv?.let { names ->
                if (names.size != 2) {
                    fail("FA003", "publish.maven.credentialsFromEnv",
                        "expected exactly two environment variable names (username, password)", file)
                }
                if (names[0] == names[1]) {
                    fail("FA003", "publish.maven.credentialsFromEnv",
                        "username and password must reference different environment variable names", file)
                }
                names.forEachIndexed { index, name ->
                    validatePublishEnvName(name, "publish.maven.credentialsFromEnv[$index]", file)
                }
            }
        }
        publish.modrinth?.let { modrinth ->
            validatePublishProjectId(modrinth.projectId, "publish.modrinth.projectId", file)
            validatePublishEnvName(modrinth.tokenEnv, "publish.modrinth.tokenEnv", file)
            validatePublishReleaseType(modrinth.releaseType, "publish.modrinth.releaseType", file)
            validatePublishTitle(modrinth.versionName, "publish.modrinth.versionName", file)
            modrinth.versionNumber?.let { version ->
                if (!platformVersion.matches(version)) {
                    fail("FA003", "publish.modrinth.versionNumber", "expected a fixed version identifier", file)
                }
            }
            validatePublishNames(modrinth.gameVersions, "publish.modrinth.gameVersions", file, platformVersion)
            validatePublishNames(modrinth.loaders, "publish.modrinth.loaders", file, platformLoader)
            if (modrinth.environment != null && modrinth.environment !in modrinthEnvironments) {
                fail("FA003", "publish.modrinth.environment", "unsupported Modrinth version environment", file)
            }
            validatePublishChangelog(modrinth.changelog, modrinth.changelogFile, "publish.modrinth", file)
            val seen = mutableSetOf<String>()
            modrinth.dependencies.forEachIndexed { index, dependency ->
                val path = "publish.modrinth.dependencies[$index]"
                validatePublishProjectId(dependency.projectId, "$path.projectId", file)
                if (dependency.type !in modrinthDependencyTypes) {
                    fail("FA003", "$path.type", "invalid Modrinth dependency type", file)
                }
                if (!seen.add(dependency.projectId)) {
                    fail("FA003", "$path.projectId", "duplicate Modrinth project dependency", file)
                }
            }
        }
        publish.curseforge?.let { curseforge ->
            if (curseforge.projectId <= 0) {
                fail("FA003", "publish.curseforge.projectId", "CurseForge project ID must be a positive integer", file)
            }
            validatePublishEnvName(curseforge.tokenEnv, "publish.curseforge.tokenEnv", file)
            validatePublishReleaseType(curseforge.releaseType, "publish.curseforge.releaseType", file)
            validatePublishTitle(curseforge.displayName, "publish.curseforge.displayName", file)
            validatePublishNames(curseforge.gameVersionNames, "publish.curseforge.gameVersionNames", file)
            curseforge.gameVersions?.let { versions ->
                if (versions.isEmpty()) {
                    fail("FA003", "publish.curseforge.gameVersions", "version ID array must not be empty", file)
                }
                val seen = mutableSetOf<Int>()
                versions.forEachIndexed { index, id ->
                    if (id <= 0 || !seen.add(id)) {
                        fail("FA003", "publish.curseforge.gameVersions[$index]",
                            "version IDs must be positive and unique", file)
                    }
                }
            }
            validatePublishChangelog(curseforge.changelog, curseforge.changelogFile, "publish.curseforge", file)
            if (curseforge.changelogType !in setOf("text", "markdown", "html")) {
                fail("FA003", "publish.curseforge.changelogType", "invalid changelog type", file)
            }
            val seen = mutableSetOf<String>()
            curseforge.relations.forEachIndexed { index, relation ->
                val path = "publish.curseforge.relations[$index]"
                validatePublishProjectId(relation.slug, "$path.slug", file)
                if (relation.projectId != null &&
                    !relation.projectId.matches(Regex("^[1-9][0-9]*$"))) {
                    fail("FA003", "$path.projectId", "CurseForge relation project ID must be a positive numeric string", file)
                }
                if (relation.type !in curseForgeRelationTypes) {
                    fail("FA003", "$path.type", "invalid CurseForge relation type", file)
                }
                if (!seen.add(relation.slug)) {
                    fail("FA003", "$path.slug", "duplicate CurseForge relation", file)
                }
            }
        }
    }

    private fun validateRemoteMavenUrl(url: String, path: String, file: Path) {
        val uri = try { URI.create(url) } catch (_: IllegalArgumentException) { null }
        if (uri == null || !uri.scheme.equals("https", ignoreCase = true) ||
            uri.host.isNullOrBlank() || uri.userInfo != null || uri.rawQuery != null ||
            uri.rawFragment != null || uri.rawAuthority.isNullOrBlank()) {
            fail("FA003", path,
                "expected HTTPS Maven repository URL without inline credentials, query or fragment", file)
        }
    }

    private fun validatePublishEnvName(value: String, path: String, file: Path) {
        if (!environmentVariableName.matches(value)) {
            fail("FA003", path, "expected an environment variable name, not a secret value", file)
        }
    }

    private fun validatePublishReleaseType(value: String, path: String, file: Path) {
        if (value !in releaseTypes) fail("FA003", path, "expected release, beta or alpha", file)
    }

    private fun validatePublishProjectId(value: String, path: String, file: Path) {
        if (!platformProjectId.matches(value)) {
            fail("FA003", path, "expected non-empty platform project ID or slug", file)
        }
    }

    private fun validatePublishTitle(value: String?, path: String, file: Path) {
        if (value != null && (value.isBlank() || value != value.trim() ||
                value.any { Character.isISOControl(it) })) {
            fail("FA003", path, "expected a non-empty single-line name", file)
        }
    }

    private fun validatePublishNames(
        values: List<String>?, path: String, file: Path, pattern: Regex? = null,
    ) {
        if (values == null) return
        if (values.isEmpty()) fail("FA003", path, "array must not be empty", file)
        val seen = mutableSetOf<String>()
        values.forEachIndexed { index, value ->
            if (value.isBlank() || value != value.trim() ||
                value.any { Character.isISOControl(it) } ||
                (pattern != null && !pattern.matches(value)) || !seen.add(value)) {
                fail("FA003", "$path[$index]", "invalid or duplicate version/loader name", file)
            }
        }
    }

    private fun validatePublishChangelog(
        changelog: String?, changelogFile: String?, path: String, file: Path,
    ) {
        if (changelog != null && changelogFile != null) {
            fail("FA003", path, "changelog and changelogFile are mutually exclusive", file)
        }
        if (changelog != null && changelog.isBlank()) {
            fail("FA003", "$path.changelog", "changelog must not be blank", file)
        }
        if (changelogFile == null) return
        val parsed = try { Path.of(changelogFile) } catch (_: RuntimeException) {
            fail("FA003", "$path.changelogFile", "invalid relative changelog file path", file)
        }
        if (parsed.isAbsolute || changelogFile.contains('\\') || changelogFile.contains(':') ||
            changelogFile.split('/').any { it.isEmpty() || it == "." || it == ".." }) {
            fail("FA003", "$path.changelogFile", "changelogFile must be a project-relative file without traversal", file)
        }
        val sourceRoot = file.toAbsolutePath().normalize().parent
            ?: fail("FA003", "$path.changelogFile", "invalid YAML source directory", file)
        val candidate = sourceRoot.resolve(parsed).normalize()
        val valid = try {
            val actualRoot = sourceRoot.toRealPath()
            val actualCandidate = candidate.toRealPath()
            candidate.startsWith(sourceRoot) && actualCandidate.startsWith(actualRoot) &&
                Files.isRegularFile(actualCandidate, LinkOption.NOFOLLOW_LINKS)
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
        if (!valid) {
            fail("FA003", "$path.changelogFile",
                "changelogFile must exist as a regular file within the project root (no escaping symlinks)", file)
        }
    }

    private fun validateDependencies(items: List<DependencySpec>, model: ModYamlModel, prefix: String) {
        val seen = mutableMapOf<Pair<String, String>, Pair<Int, DependencySpec>>()
        items.forEachIndexed { index, dep ->
            val p = "$prefix[$index]"
            if (dep.kind !in setOf("library", "mod")) fail("FA003", "$p.kind", "expected 'library' or 'mod'", model.source)
            if (dep.scope !in setOf("all", "compile-only", "runtime-only")) {
                fail("FA003", "$p.scope", "unknown dependency scope", model.source)
            }
            if (dep.target !in setOf("main", "client")) fail("FA003", "$p.target", "unknown source target", model.source)
            if (dep.exported && dep.scope != "all") {
                fail("FA007", "$p.exported", "exported=true requires scope=all", model.source)
            }
            if (dep.include && dep.scope != "all") {
                fail("FA007", "$p.include", "include=true requires scope=all", model.source)
            }
            if (dep.target == "client" &&
                (!model.settings.loom.splitEnvironmentSourceSets || dep.exported || dep.include ||
                    model.minecraft.profile == MinecraftProfile.REMAPPING)
            ) {
                fail("FA014", "$p.target",
                    "client requires splitEnvironmentSourceSets, non-remap profile, no exported/include", model.source)
            }
            val previous = seen.putIfAbsent(dep.coordinate to dep.target, index to dep)
            if (previous != null && previous.second != dep) {
                fail("FA012", p,
                    "conflicting declarations at $prefix[${previous.first}] and $prefix[$index] " +
                        "for ${dep.coordinate} target ${dep.target}", model.source)
            }
        }
    }

    private fun checkLoaderPredicate(expression: String, path: String, file: Path) {
        if (!VersionSyntax.isLoaderPredicate(expression)) {
            fail("FA009", path, "invalid Fabric Loader version predicate '$expression'", file)
        }
    }

    private fun findResource(
        source: Path,
        relative: String,
        yamlPath: String,
        mainOnly: Boolean = false,
        allowClient: Boolean = true,
    ): Path? {
        val base = source.toAbsolutePath().normalize().parent
            ?: fail("FA009", yamlPath, "invalid YAML source directory", source)
        val sourceSets = if (mainOnly || !allowClient) listOf("main") else listOf("main", "client")
        for (sourceSet in sourceSets) {
            val root = base.resolve("src/$sourceSet/resources").normalize()
            val candidate = root.resolve(relative).normalize()
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) continue

            // Verify the *specific* source-set root, not merely the project root.
            // This also disallows a symlinked resources directory or a nested link
            // escaping into private/non-packaged project content.
            val (actualBase, actualRoot, actualCandidate) = try {
                Triple(base.toRealPath(), root.toRealPath(), candidate.toRealPath())
            } catch (e: IOException) {
                fail("FA009", yamlPath, "unable to resolve resource '$relative': ${e.message}", source)
            } catch (e: SecurityException) {
                fail("FA009", yamlPath, "access denied to resource '$relative': ${e.message}", source)
            }
            val expectedRoot = actualBase.resolve("src/$sourceSet/resources").normalize()
            if (actualRoot != expectedRoot || !actualCandidate.startsWith(actualRoot)) {
                fail("FA009", yamlPath, "resource '$relative' escapes src/$sourceSet/resources via a symbolic link", source)
            }
            if (!Files.isRegularFile(actualCandidate, LinkOption.NOFOLLOW_LINKS)) {
                fail("FA009", yamlPath, "resource '$relative' must be a regular file", source)
            }
            return actualCandidate
        }
        return null
    }

    private fun checkResource(
        source: Path,
        relative: String,
        path: String,
        png: Boolean = false,
        mainOnly: Boolean = false,
        allowClient: Boolean = true,
    ): Path {
        val parsed = try { Path.of(relative) } catch (e: RuntimeException) {
            fail("FA009", path, "invalid resource path", source)
        }
        if (parsed.isAbsolute || relative.contains('\\') || parsed.toString().startsWith(".") ||
            parsed.normalize().startsWith("..") || relative.contains(':') ||
            relative.split('/').any { it == ".." || it.isEmpty() }
        ) {
            fail("FA009", path, "resource must use a relative JAR-root path without traversal", source)
        }
        if (png && !relative.endsWith(".png", ignoreCase = true)) {
            fail("FA009", path, "icon resource must be PNG", source)
        }
        val found = findResource(source, relative, path, mainOnly, allowClient)
        if (found == null) {
            if (!mainOnly && !allowClient) {
                val clientPath = source.toAbsolutePath().normalize().parent
                    .resolve("src/client/resources").resolve(relative)
                if (Files.exists(clientPath, LinkOption.NOFOLLOW_LINKS)) {
                    fail("FA009", path,
                        "client-only resource '$relative' requires settings.loom.splitEnvironmentSourceSets=true", source)
                }
            }
            val root = if (mainOnly || !allowClient) "src/main/resources" else "src/main/resources or src/client/resources"
            fail("FA009", path, "missing resource $root: $relative", source)
        }
        return found
    }

    private fun looksLikeUrl(url: String): Boolean = try {
        val uri = java.net.URI.create(url)
        uri.host != null && uri.host.isNotBlank() && uri.userInfo == null && uri.fragment == null
    } catch (_: Exception) {
        false
    }

    private fun fail(code: String, path: String, reason: String, source: Path): Nothing =
        throw FabricAmperConfigException(code, path, reason, source)
}
