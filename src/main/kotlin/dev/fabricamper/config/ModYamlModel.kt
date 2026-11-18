package dev.fabricamper.config

import java.nio.file.Path

/**
 * Immutable, validated representation of a single Fabric Amper YAML document.
 * No Gradle or Fabric Loom types are exposed to metadata generation.
 */
data class ModYamlModel(
    val source: Path,
    val schema: Int,
    val product: String,
    val mod: ModSpec,
    val minecraft: MinecraftSpec,
    val settings: SettingsSpec,
    val repositories: List<RepositorySpec>,
    val dependencies: List<DependencySpec>,
    val testDependencies: List<DependencySpec>,
    /** Original authored Loader dependency IDs, before profile defaults are merged. */
    val authoredDependsKeys: Set<String> = mod.depends.keys.toSet(),
    /** API/library-only publishing options; null means publishing was not authored. */
    val publishing: PublishingSpec? = null,
    /** Explicit upload destinations, independent of API/library Maven publishing. */
    val publish: PublishSpec? = null,
    /** Optional Fabric Architectury API convenience switch. */
    val architectury: ArchitecturySpec = ArchitecturySpec(),
    /** Exact authoring schema token. Kept separate from the v1 Int ABI. */
    val schemaVersion: String = "1",
    /** Normalized in stable loader order; never inferred from a declared dependency. */
    val targets: List<LoaderTargetSpec> = listOf(LoaderTargetSpec("fabric", if (product == "fabric/api" || product == "fabric/library") "api" else "mod")),
    /** Present only when a schema 1.1 Fabric target is selected. */
    val fabric: FabricSpec? = null,
    /** Present only when a schema 1.1 NeoForge target is selected. */
    val neoforge: NeoForgeSpec? = null,
) {
    val modId: String get() = mod.id
    val isMultiLoader: Boolean get() = targets.size > 1
}

/** The loader is a build backend; kind describes the artifact/public API contract. */
data class LoaderTargetSpec(val loader: String, val kind: String) {
    val product: String get() = "$loader/$kind"
}

/** All Fabric-only v1.1 fields; legacy v1 fields remain available through mod/minecraft/settings. */
data class FabricSpec(
    val loader: String,
    val api: String? = null,
    val mappings: MappingSpec = MappingSpec(),
    val environment: String = "*",
    val entrypoints: Map<String, List<EntrypointSpec>> = emptyMap(),
    val languageAdapters: Map<String, String> = emptyMap(),
    val mixins: List<MixinSpec> = emptyList(),
    val accessWidener: String? = null,
    val provides: List<String> = emptyList(),
    val depends: Map<String, Any> = emptyMap(),
    val recommends: Map<String, Any> = emptyMap(),
    val suggests: Map<String, Any> = emptyMap(),
    val conflicts: Map<String, Any> = emptyMap(),
    val breaks: Map<String, Any> = emptyMap(),
    val custom: Map<String, Any?> = emptyMap(),
    val icon: Any? = null,
    val loom: LoomSettings = LoomSettings(),
    val kotlin: FabricKotlinSpec = FabricKotlinSpec(),
    val resources: ResourceSettings = ResourceSettings(),
    val gradleDependencies: List<DependencySpec> = emptyList(),
    val repositories: List<RepositorySpec> = emptyList(),
)

data class FabricKotlinSpec(val fabricLanguageKotlin: String = "1.14.2+kotlin.2.4.21")

/** NeoForge metadata is not encoded as Fabric Loader predicates or JSON. */
data class NeoForgeSpec(
    val version: String,
    val modLoader: String = "javafml",
    val loaderVersion: String = "[1,)",
    val dependencies: List<NeoForgeDependencySpec> = emptyList(),
    val mixins: List<String> = emptyList(),
    val accessTransformers: List<String> = emptyList(),
    val logoFile: String? = null,
    val displayURL: String? = null,
    val issueTrackerURL: String? = null,
    val credits: String? = null,
    val updateJSONURL: String? = null,
    val modProperties: Map<String, String> = emptyMap(),
    val features: Map<String, String> = emptyMap(),
    val gradleDependencies: List<DependencySpec> = emptyList(),
    val repositories: List<RepositorySpec> = emptyList(),
)

data class NeoForgeDependencySpec(
    val modId: String,
    val type: String = "required",
    val versionRange: String = "",
    val ordering: String = "NONE",
    val side: String = "BOTH",
    val reason: String? = null,
)

/** Legacy integer schema ABI. Omitted YAML schema selects the v1.1 decoder directly. */
object FabricAmperSchemaVersion {
    const val LATEST: Int = 1
}

/** Audited fixed defaults for MC 26.3; never use Gradle dynamic versions. */
object ArchitecturyDefaults {
    const val MINECRAFT_VERSION = "26.3"
    const val VERSION = "22.0.5"
    const val FABRIC_API_VERSION = "0.165.2+26.3"
    const val MAVEN_URL = "https://maven.architectury.dev/"
    const val GROUP_ARTIFACT = "dev.architectury:architectury-fabric"
}

data class ArchitecturySpec(
    val enabled: Boolean = false,
    /** Explicit user version, or the validated 26.3 default after normalization. */
    val version: String? = null,
)

/** An API product always has its built-in local Maven repository. */
data class PublishingSpec(val repository: PublishingRepositorySpec? = null)

/** Remote Maven credentials are environment variable NAMES, never secret values. */
data class PublishingRepositorySpec(
    val url: String,
    val usernameEnv: String? = null,
    val passwordEnv: String? = null,
)

/** Opt-in upload targets; absence never registers a remote upload by itself. */
data class PublishSpec(
    val maven: MavenSpec? = null,
    val modrinth: ModrinthSpec? = null,
    val curseforge: CurseForgeSpec? = null,
    /** Author's shared publish values; per-platform specs above are fully resolved. */
    val common: PublishCommonSpec? = null,
)

/** Shared release values; platform-specific names, IDs and token envs stay separate. */
data class PublishCommonSpec(
    val releaseType: String? = null,
    val name: String? = null,
    val gameVersions: List<String>? = null,
    val changelog: String? = null,
    val changelogFile: String? = null,
    val dependencies: List<PublishCommonDependency>? = null,
)

/** Slug is the CF relation slug and fallback Modrinth project ID. */
data class PublishCommonDependency(
    val slug: String,
    val type: String,
    val modrinthId: String? = null,
    val curseforgeId: String? = null,
)

/** Credential entries are exactly [username variable name, password variable name]. */
data class MavenSpec(
    val url: String,
    val credentialsFromEnv: List<String>? = null,
)

data class ModrinthSpec(
    val projectId: String,
    val tokenEnv: String,
    val releaseType: String = "release",
    val versionName: String? = null,
    val versionNumber: String? = null,
    val gameVersions: List<String>? = null,
    val loaders: List<String>? = null,
    val environment: String? = null,
    val changelog: String? = null,
    val changelogFile: String? = null,
    val dependencies: List<ModrinthPublishDependency> = emptyList(),
)

data class ModrinthPublishDependency(
    val projectId: String,
    val type: String,
)

data class CurseForgeSpec(
    val projectId: Int,
    val tokenEnv: String,
    val releaseType: String = "release",
    val displayName: String? = null,
    val gameVersionNames: List<String>? = null,
    val gameVersions: List<Int>? = null,
    val changelog: String? = null,
    val changelogFile: String? = null,
    val changelogType: String = "markdown",
    val manualRelease: Boolean = false,
    val relations: List<CurseForgePublishRelation> = emptyList(),
)

data class CurseForgePublishRelation(
    val slug: String,
    val projectId: String? = null,
    val type: String,
)

data class ModSpec(
    val id: String,
    val version: String,
    val group: String? = null,
    val archivesName: String? = null,
    val name: String? = null,
    val description: String? = null,
    val environment: String = "*",
    // JSON-compatible Kotlin values: String, List, Map, Boolean, Number, null.
    // Distinguish primitive and object forms so the generator can preserve author intent.
    val license: Any? = null,
    val authors: List<Any> = emptyList(),
    val contributors: List<Any> = emptyList(),
    val contact: Map<String, String> = emptyMap(),
    val icon: Any? = null,
    val provides: List<String> = emptyList(),
    val entrypoints: Map<String, List<EntrypointSpec>> = emptyMap(),
    val languageAdapters: Map<String, String> = emptyMap(),
    val mixins: List<MixinSpec> = emptyList(),
    val accessWidener: String? = null,
    // Each value is a version-requirement String or an OR-list of Strings.
    val depends: Map<String, Any> = emptyMap(),
    val recommends: Map<String, Any> = emptyMap(),
    val suggests: Map<String, Any> = emptyMap(),
    val conflicts: Map<String, Any> = emptyMap(),
    val breaks: Map<String, Any> = emptyMap(),
    val custom: Map<String, Any?> = emptyMap(),
)

data class EntrypointSpec(
    val value: String,
    val adapter: String? = null,
    val objectForm: Boolean = false,
)

data class MixinSpec(
    val config: String,
    val environment: String? = null,
    val objectForm: Boolean = false,
)

data class MappingSpec(val type: String = "mojang", val version: String? = null)

data class MinecraftSpec(
    val version: String,
    val mappings: MappingSpec = MappingSpec(),
    val loader: String,
    val fabricApi: String? = null,
) {
    val profile: MinecraftProfile get() = MinecraftProfile.forVersion(version)
}

data class SettingsSpec(
    val java: JavaSettings = JavaSettings(),
    val kotlin: KotlinSettings = KotlinSettings(),
    val loom: LoomSettings = LoomSettings(),
    val resources: ResourceSettings = ResourceSettings(),
)

data class JavaSettings(val release: Int = 25, val sourcesJar: Boolean = true)

data class KotlinSettings(
    val enabled: Boolean = false,
    val pluginVersion: String = "2.4.21",
    val languageVersion: String? = "2.4",
    val fabricLanguageKotlin: String = "1.14.2+kotlin.2.4.21",
)

data class LoomSettings(val splitEnvironmentSourceSets: Boolean = false)

data class ResourceSettings(val failOnExistingFabricModJson: Boolean = true)

data class RepositorySpec(
    val kind: String,
    val url: String? = null,
)

/** A resolved Maven or Modrinth coordinate, normalized to a fixed version. */
data class DependencySpec(
    val kind: String,
    val coordinate: String,
    val scope: String = "all",
    val target: String = "main",
    val exported: Boolean = false,
    val include: Boolean = false,
    /** Schema 1.1 root-only discriminator; null is shared by all selected loaders. */
    val loader: String? = null,
)
