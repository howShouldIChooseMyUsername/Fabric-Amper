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
        if (raw.schemaVersion == "1.1") return validateV11(raw)
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

    /** Schema 1.1 has distinct loader metadata; never feed NeoForge through Fabric's v1 validator. */
    private fun validateV11(raw: ModYamlModel): ModYamlModel {
        val file = raw.source
        val mod = raw.mod
        val mc = raw.minecraft
        val java = raw.settings.java
        if (raw.targets.isEmpty() || raw.targets.map { it.loader }.distinct().size != raw.targets.size) {
            fail("FA003", "product", "target loaders must be unique and nonempty", file)
        }
        if (raw.targets.map { it.loader } != raw.targets.map { it.loader }.sorted()) {
            fail("FA003", "product", "loader targets must have canonical ordering", file)
        }
        val hasFabric = raw.targets.any { it.loader == "fabric" }
        val hasNeo = raw.targets.any { it.loader == "neoforge" }
        if ((raw.fabric != null) != hasFabric || (raw.neoforge != null) != hasNeo) {
            fail("FA003", "product", "each selected loader must have exactly its matching configuration", file)
        }
        rejectV11SourceMetadata(raw, file)
        if (!idPattern.matches(mod.id)) {
            fail("FA009", "mod.id", "mod ID must start lowercase and contain 2-64 ASCII letters, digits, _ or -", file)
        }
        if (hasNeo && !mod.id.matches(Regex("^[a-z][a-z0-9_]{1,63}$"))) {
            fail("FA016", "mod.id", "NeoForge mod ID must use lowercase letters, digits and underscores, without hyphens", file)
        }
        if (mod.version.isBlank() || mod.version.any { it.isWhitespace() }) {
            fail("FA009", "mod.version", "version must be one nonempty token", file)
        }
        mod.group?.let { group ->
            if (group.split('.').any { !coordinateSegment.matches(it) }) {
                fail("FA003", "mod.group", "expected dotted Maven group identifier", file)
            }
        }
        if (raw.targets.any { it.kind == "api" } && mod.group.isNullOrBlank()) {
            fail("FA003", "mod.group", "API targets require an explicit Maven group", file)
        }
        if (hasNeo && (mod.license !is String || mod.license.isBlank())) {
            fail("FA016", "mod.license", "NeoForge requires a nonempty string license", file)
        }
        if (raw.publishing != null && !(raw.targets.size == 1 && raw.targets.first().product == "fabric/api")) {
            fail("FA003", "publishing", "legacy publishing.repository is supported only for a single Fabric API target", file)
        }
        if (mod.archivesName != null && (!archivePattern.matches(mod.archivesName) ||
                mod.archivesName in setOf(".", ".."))) {
            fail("FA003", "mod.archivesName", "invalid archive base name", file)
        }
        if (!mc.version.matches(Regex("^\\d+\\.\\d+(?:\\.\\d+)?$"))) {
            fail("FA003", "minecraft.version", "expected an exact quoted Minecraft version", file)
        }
        if (hasNeo && mc.version != "26.3") {
            fail("FA016", "minecraft.version", "NeoForge backend is currently defined only for Minecraft 26.3", file)
        }
        if (java.release !in 8..30 || (mc.profile == MinecraftProfile.NON_REMAPPING && java.release != 25)) {
            fail("FA005", "settings.java.release", "Minecraft 26.1+ requires Java 25", file)
        }
        if (raw.settings.kotlin.enabled) {
            if (raw.settings.kotlin.pluginVersion != "2.4.21" || raw.settings.kotlin.languageVersion != "2.4") {
                fail("FA006", "settings.kotlin", "only the pinned Kotlin Gradle plugin 2.4.21/language 2.4 is supported", file)
            }
            // NeoForge's pinned Kotlin Gradle plugin is configured by the NeoForge
            // backend; Fabric Language Kotlin remains Fabric-only.
        }
        if (raw.architectury.enabled && mc.version != ArchitecturyDefaults.MINECRAFT_VERSION) {
            fail("FA003", "architectury", "Architectury convenience requires Minecraft 26.3", file)
        }
        if (!raw.architectury.enabled && raw.architectury.version != null) {
            fail("FA003", "architectury.version", "enable Architectury before selecting a version", file)
        }
        val archVersion = if (raw.architectury.enabled) raw.architectury.version ?: ArchitecturyDefaults.VERSION else null
        if (archVersion != null && !VersionSyntax.isFixedGradleVersion(archVersion)) {
            fail("FA003", "architectury.version", "Architectury requires a fixed version", file)
        }
        validatePublish(raw, file)
        raw.publish?.modrinth?.loaders?.let { loaders ->
            val selected = raw.targets.map { it.loader }.toSet()
            if (loaders.toSet() != selected) {
                fail("FA003", "publish.modrinth.loaders", "release loader tags must match exactly the selected target loaders", file)
            }
        }
        raw.repositories.forEachIndexed { index, repo -> validateV11Repository(repo, "repositories[$index]", file) }
        raw.fabric?.repositories.orEmpty().forEachIndexed { index, repo ->
            validateV11Repository(repo, "fabric.repositories[$index]", file)
        }
        raw.neoforge?.repositories.orEmpty().forEachIndexed { index, repo ->
            validateV11Repository(repo, "neoforge.repositories[$index]", file)
        }
        val allDependencyGroups = listOf(
            "dependencies" to raw.dependencies, "test-dependencies" to raw.testDependencies,
            "fabric.gradleDependencies" to raw.fabric?.gradleDependencies.orEmpty(),
            "neoforge.gradleDependencies" to raw.neoforge?.gradleDependencies.orEmpty(),
        )
        allDependencyGroups.forEach { (path, deps) ->
            // Client Gradle dependencies belong to Fabric's split source set only.
            // A dual-loader root dependency is shared with NeoForge, so it can
            // never target Fabric's client even when Fabric has split enabled.
            // In dual Architectury mode --split is ignored for both backends.
            val clientSourceSetEnabled = hasFabric &&
                raw.fabric?.loom?.splitEnvironmentSourceSets == true &&
                mc.profile == MinecraftProfile.NON_REMAPPING &&
                (path == "fabric.gradleDependencies" ||
                    (path == "dependencies" && !raw.isMultiLoader)) &&
                (!raw.isMultiLoader || !raw.architectury.enabled)
            deps.forEachIndexed { index, dep ->
                if (dep.target == "client" && !clientSourceSetEnabled) {
                    fail("FA014", "$path[$index].target",
                        "client Gradle dependencies require Fabric's split source set; " +
                            "multi-loader common/NeoForge and dual Architectury clients are not supported", file)
                }
                // Fabric's Loom include/JIJ is valid in its own dependency group,
                // even when this YAML also produces a separate NeoForge target.
                // Common dependencies of a dual-loader build and NeoForge's MDG
                // JarJar must never inherit Fabric's include semantics.
                val fabricOnlyInclude = hasFabric && (path == "fabric.gradleDependencies" ||
                    (path == "dependencies" && !hasNeo && !raw.isMultiLoader))
                if (dep.include && !fabricOnlyInclude) {
                    fail("FA007", "$path[$index].include",
                        "include requires a Fabric-only dependency group; NeoForge JarJar needs verified bounds", file)
                }
                if (raw.isMultiLoader && path == "dependencies" &&
                    (dep.kind == "mod" || dep.include || dep.target != "main")) {
                    fail("FA007", "$path[$index]", "multi-loader common dependencies must be neutral main libraries", file)
                }
            }
            validateDependencies(deps, raw, path, clientSourceSetEnabled)
        }
        val fabric = raw.fabric?.let { f ->
            if (!VersionSyntax.isFixedGradleVersion(f.loader)) {
                fail("FA003", "fabric.loader", "expected a fixed Fabric Loader version", file)
            }
            f.api?.let { if (!VersionSyntax.isFixedGradleVersion(it)) fail("FA003", "fabric.api", "expected fixed Fabric API version", file) }
            if (f.mappings.type !in setOf("mojang", "yarn") ||
                (mc.profile == MinecraftProfile.NON_REMAPPING && f.mappings.type != "mojang") ||
                (f.mappings.type == "yarn" && f.mappings.version.isNullOrBlank()) ||
                (f.mappings.type == "mojang" && f.mappings.version != null)) {
                fail("FA004", "fabric.mappings", "mapping namespace does not match Minecraft profile", file)
            }
            if (f.environment !in envs) fail("FA009", "fabric.environment", "expected '*', client or server", file)
            if (f.provides.distinct().size != f.provides.size || f.provides.any { it == mod.id || !idPattern.matches(it) }) {
                fail("FA009", "fabric.provides", "duplicate, invalid or self-referential Fabric mod ID alias", file)
            }
            f.entrypoints.forEach { (group, entries) ->
                if (group.isBlank() || entries.isEmpty()) {
                    fail("FA009", "fabric.entrypoints.$group", "nonempty entrypoint list required", file)
                }
                entries.forEachIndexed { index, entrypoint ->
                    if (entrypoint.value.isBlank() || (entrypoint.adapter == "kotlin" &&
                            !raw.settings.kotlin.enabled)) {
                        fail("FA006", "fabric.entrypoints.$group[$index]", "invalid Fabric entrypoint or disabled Kotlin adapter", file)
                    }
                }
            }
            f.mixins.forEachIndexed { index, mixin ->
                if (mixin.environment != null && mixin.environment !in envs) {
                    fail("FA009", "fabric.mixins[$index].environment", "expected '*', client or server", file)
                }
            }
            if (!f.resources.failOnExistingFabricModJson) {
                fail("FA003", "fabric.resources.failOnExistingFabricModJson", "must remain true", file)
            }
            if (raw.settings.kotlin.enabled && f.kotlin.fabricLanguageKotlin != "1.14.2+kotlin.2.4.21") {
                fail("FA006", "fabric.kotlin.fabricLanguageKotlin", "bundled Fabric Language Kotlin version required", file)
            }
            val depends = LinkedHashMap(f.depends)
            val defaults = linkedMapOf(
                "minecraft" to mc.version,
                "fabricloader" to ">=${f.loader}",
                "java" to ">=${java.release}",
            )
            f.api?.let { defaults["fabric-api"] = ">=" + it.substringBefore('+') }
            if (raw.settings.kotlin.enabled) {
                defaults["fabric-language-kotlin"] = ">=" + f.kotlin.fabricLanguageKotlin.substringBefore('+')
            }
            if (archVersion != null) defaults["architectury"] = ">=$archVersion"
            val selectedVersions = linkedMapOf(
                "minecraft" to mc.version, "fabricloader" to f.loader, "java" to java.release.toString(),
            )
            f.api?.let { selectedVersions["fabric-api"] = it }
            if (raw.settings.kotlin.enabled) selectedVersions["fabric-language-kotlin"] = f.kotlin.fabricLanguageKotlin
            if (archVersion != null) selectedVersions["architectury"] = archVersion
            listOf(f.depends, f.recommends, f.suggests, f.conflicts, f.breaks).forEach { constraints ->
                constraints.forEach { (id, requirement) ->
                    val expressions = when (requirement) {
                        is String -> listOf(requirement)
                        is List<*> -> requirement.filterIsInstance<String>()
                        else -> emptyList()
                    }
                    if (expressions.isEmpty() || expressions.any { !VersionSyntax.isLoaderPredicate(it) }) {
                        fail("FA009", "fabric.depends.$id", "invalid Fabric Loader version predicate", file)
                    }
                }
            }
            defaults.forEach { (id, requirement) -> depends.putIfAbsent(id, requirement) }
            selectedVersions.forEach { (id, version) ->
                val value = depends.getValue(id)
                val patterns = if (value is String) listOf(value) else (value as List<*>).filterIsInstance<String>()
                if (LoaderVersionConstraints.matchesAny(patterns, version) != true) {
                    fail("FA009", "fabric.depends.$id", "version predicate does not accept selected $id version", file)
                }
            }
            for ((index, path) in f.mixins.map { it.config }.withIndex()) {
                checkV11Resource(raw, "fabric", path, "fabric.mixins[$index]", file)
            }
            f.accessWidener?.let { relative ->
                val path = "fabric.accessWidener"
                val expectedSuffix = if (mc.profile == MinecraftProfile.NON_REMAPPING) ".classtweaker"
                    else ".accesswidener"
                if (!relative.endsWith(expectedSuffix)) {
                    fail("FA009", path, "access widener extension does not match Minecraft naming profile", file)
                }
                val resource = checkV11Resource(raw, "fabric", relative, path, file)
                val header = try { Files.newBufferedReader(resource).use { it.readLine() } }
                    catch (_: IOException) { null }
                val validHeader = if (mc.profile == MinecraftProfile.NON_REMAPPING) {
                    header == "classTweaker v1 official"
                } else {
                    header?.matches(Regex("accessWidener v[12] (named|intermediary)")) == true
                }
                if (!validHeader) fail("FA009", path, "access widener header does not match selected profile", file)
            }
            f.icon?.let { icon ->
                if (icon is String) checkV11Resource(raw, "fabric", icon, "fabric.icon", file)
                if (icon is Map<*, *>) icon.forEach { (size, path) ->
                    checkV11Resource(raw, "fabric", path as String, "fabric.icon.$size", file)
                }
            }
            f.copy(
                depends = depends,
                gradleDependencies = if (archVersion == null) f.gradleDependencies else
                    f.gradleDependencies + DependencySpec("mod",
                        "${ArchitecturyDefaults.GROUP_ARTIFACT}:$archVersion"),
            )
        }
        val neo = raw.neoforge?.let { nf ->
            if (!VersionSyntax.isFixedGradleVersion(nf.version) || !nf.version.startsWith("26.3.")) {
                fail("FA016", "neoforge.version", "expected a fixed NeoForge 26.3 version", file)
            }
            if (nf.modLoader != "javafml") {
                fail("FA016", "neoforge.modLoader", "the verified initial NeoForge backend uses javafml", file)
            }
            validateMavenRange(nf.loaderVersion, "neoforge.loaderVersion", file)
            val dependencyIDs = mutableSetOf<String>()
            nf.dependencies.forEachIndexed { index, dependency ->
                val path = "neoforge.dependencies[$index]"
                if (!dependency.modId.matches(Regex("^[a-z][a-z0-9_]{1,63}$"))) {
                    fail("FA016", "$path.modId", "invalid NeoForge dependency modId", file)
                }
                if (dependency.modId == mod.id) {
                    fail("FA016", "$path.modId", "mod cannot depend on itself", file)
                }
                if (!dependencyIDs.add(dependency.modId)) {
                    fail("FA016", "$path.modId", "duplicate NeoForge dependency", file)
                }
                if (dependency.type !in setOf("required", "optional", "incompatible", "discouraged")) {
                    fail("FA016", "$path.type", "unsupported NeoForge dependency type", file)
                }
                if (dependency.ordering !in setOf("NONE", "BEFORE", "AFTER")) {
                    fail("FA016", "$path.ordering", "expected NONE, BEFORE or AFTER", file)
                }
                if (dependency.side !in setOf("BOTH", "CLIENT", "SERVER")) {
                    fail("FA016", "$path.side", "expected BOTH, CLIENT or SERVER", file)
                }
                if (dependency.versionRange.isNotEmpty()) {
                    validateMavenRange(dependency.versionRange, "$path.versionRange", file)
                }
            }
            val defaults = linkedMapOf(
                "minecraft" to mc.version,
                "neoforge" to nf.version,
            )
            if (archVersion != null) defaults["architectury"] = archVersion
            defaults.forEach { (id, version) ->
                val authored = nf.dependencies.firstOrNull { it.modId == id }
                if (authored == null) return@forEach
                if (authored.type != "required" || authored.side != "BOTH" ||
                    authored.versionRange.isEmpty() ||
                    !matchesKnownMavenRange(authored.versionRange, version)) {
                    fail("FA016", "neoforge.dependencies", "$id must be a required BOTH dependency accepting the selected version", file)
                }
            }
            val generated = defaults.entries.filter { it.key !in dependencyIDs }.map { (id, version) ->
                NeoForgeDependencySpec(
                    modId = id,
                    type = "required",
                    versionRange = if (id == "minecraft") "[$version]" else "[$version,)",
                )
            }
            nf.mixins.forEachIndexed { index, path ->
                checkV11Resource(raw, "neoforge", path, "neoforge.mixins[$index]", file)
            }
            nf.accessTransformers.forEachIndexed { index, path ->
                checkV11Resource(raw, "neoforge", path, "neoforge.accessTransformers[$index]", file)
            }
            nf.logoFile?.let {
                if (!it.endsWith(".png", ignoreCase = true)) {
                    fail("FA009", "neoforge.logoFile", "NeoForge logo must be a PNG resource", file)
                }
                checkV11Resource(raw, "neoforge", it, "neoforge.logoFile", file)
            }
            nf.copy(
                dependencies = generated + nf.dependencies,
                gradleDependencies = if (archVersion == null) nf.gradleDependencies else
                    nf.gradleDependencies + DependencySpec("mod",
                        "dev.architectury:architectury-neoforge:$archVersion"),
            )
        }
        val repo = if (archVersion != null && raw.repositories.none {
                it.kind == "maven" && it.url?.trimEnd('/') == ArchitecturyDefaults.MAVEN_URL.trimEnd('/')
            }) raw.repositories + RepositorySpec("maven", ArchitecturyDefaults.MAVEN_URL)
            else raw.repositories
        // Architectury's dual-target code layout does not use Fabric's optional
        // client split. Ignore --split even if the YAML was authored by hand.
        val ignoreDualArchitecturySplit = raw.isMultiLoader && raw.architectury.enabled
        return raw.copy(
            mod = if (fabric != null) mod.copy(depends = fabric.depends) else mod,
            settings = if (ignoreDualArchitecturySplit) raw.settings.copy(loom = LoomSettings(false))
                else raw.settings,
            fabric = if (ignoreDualArchitecturySplit) fabric?.copy(loom = LoomSettings(false)) else fabric,
            neoforge = neo,
            repositories = repo,
            architectury = raw.architectury.copy(version = archVersion),
        )
    }

    private fun validateV11Repository(repo: RepositorySpec, path: String, file: Path) {
        if (repo.kind !in setOf("fabric", "neoforge", "mavenCentral", "modrinth", "maven")) {
            fail("FA003", path, "unknown repository", file)
        }
        if (repo.kind == "maven") {
            validateRemoteMavenUrl(repo.url ?: "", path, file)
        }
    }

    private fun rejectV11SourceMetadata(raw: ModYamlModel, file: Path) {
        val projectRoot = file.toAbsolutePath().normalize().parent
            ?: fail("FA008", "mod.yaml", "invalid project root", file)
        val sourceSets = if (raw.isMultiLoader) {
            listOf("common/src/main/resources", "fabric/src/main/resources",
                "fabric/src/client/resources", "neoforge/src/main/resources")
        } else listOf("src/main/resources", "src/client/resources")
        sourceSets.forEach { root ->
            listOf("fabric.mod.json", "META-INF/neoforge.mods.toml").forEach { filename ->
                val relative = "$root/$filename"
                if (Files.exists(projectRoot.resolve(relative), LinkOption.NOFOLLOW_LINKS)) {
                    fail("FA008", relative, "Loader metadata must be generated by Fabric Amper", file)
                }
            }
        }
    }

    private fun validateMavenRange(value: String, path: String, file: Path) {
        if (value.length < 3 || value.first() !in "[(" || value.last() !in "])" ||
            value.any { it == '\n' || it == '\r' || it == '\t' } || value.contains(' ')) {
            fail("FA016", path, "expected NeoForge Maven version range like [1.0,) or [1.0]", file)
        }
        val body = value.substring(1, value.lastIndex)
        val boundaries = body.split(',')
        if (boundaries.size !in 1..2 || boundaries.all(String::isBlank) ||
            boundaries.any { it.isNotBlank() && !VersionSyntax.isFixedGradleVersion(it) } ||
            (boundaries.size == 1 && (value.first() != '[' || value.last() != ']'))) {
            fail("FA016", path, "invalid Maven version range", file)
        }
    }

    private fun matchesKnownMavenRange(range: String, selected: String): Boolean {
        val body = range.substring(1, range.lastIndex)
        val boundaries = body.split(',')
        if (boundaries.size == 1) return boundaries.single() == selected
        val left = boundaries.first()
        val right = boundaries.last()
        // Compare purely numeric version components only. Unparseable comparisons must not
        // be silently treated as compatible when overriding a required platform default.
        fun numeric(version: String): List<Int>? = version.split('.').map { it.toIntOrNull() }
            .takeIf { it.none { n -> n == null } }?.filterNotNull()
        fun compare(a: String, b: String): Int? {
            val aa = numeric(a) ?: return null
            val bb = numeric(b) ?: return null
            for (i in 0 until maxOf(aa.size, bb.size)) {
                val diff = (aa.getOrElse(i) { 0 }).compareTo(bb.getOrElse(i) { 0 })
                if (diff != 0) return diff
            }
            return 0
        }
        val lower = if (left.isBlank()) true else compare(selected, left)?.let {
            it > 0 || (it == 0 && range.first() == '[')
        } ?: false
        val upper = if (right.isBlank()) true else compare(selected, right)?.let {
            it < 0 || (it == 0 && range.last() == ']')
        } ?: false
        return lower && upper
    }

    private fun checkV11Resource(raw: ModYamlModel, loader: String, relative: String, path: String, file: Path): Path {
        if (relative.isBlank() || relative.contains('\\') || relative.contains(':') ||
            relative.split('/').any { it.isEmpty() || it == "." || it == ".." }) {
            fail("FA009", path, "resource must be a relative JAR path without traversal", file)
        }
        val base = file.toAbsolutePath().normalize().parent
            ?: fail("FA009", path, "invalid project root", file)
        val resourceRoot = base.resolve(
            if (raw.isMultiLoader) "$loader/src/main/resources" else "src/main/resources")
        val candidate = resourceRoot.resolve(relative).normalize()
        if (!candidate.startsWith(resourceRoot)) fail("FA009", path, "resource escapes source root", file)
        val valid = try {
            val actual = candidate.toRealPath()
            val root = resourceRoot.toRealPath()
            actual.startsWith(root) && Files.isRegularFile(actual, LinkOption.NOFOLLOW_LINKS)
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
        if (!valid) fail("FA009", path, "required $loader resource missing or outside source root", file)
        return candidate
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

    private fun validateDependencies(
        items: List<DependencySpec>, model: ModYamlModel, prefix: String,
        clientSourceSetEnabled: Boolean = model.settings.loom.splitEnvironmentSourceSets,
    ) {
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
                (!clientSourceSetEnabled || dep.exported || dep.include ||
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
