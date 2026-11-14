package dev.fabricamper.plugin

import dev.fabricamper.config.DependencySpec
import dev.fabricamper.config.ModYamlModel

/**
 * Read-only checklist for the two Minecraft versions with concrete consumer
 * profile evidence in this project. This explains expected Gradle wiring; it
 * does not resolve dependencies, inspect source code, or modify the model.
 */
object FabricAmperPortingReport {
    private const val LEGACY = "1.21.11"
    private const val MODERN = "26.3"

    private data class Profile(
        val version: String,
        val label: String,
        val loomId: String,
        val archiveTask: String,
        val javaBaseline: Int,
        val resourceExtension: String,
        val resourceHeader: String,
    ) {
        val remapping: Boolean get() = version == LEGACY
    }

    private fun profile(version: String, field: String): Profile = when (version) {
        LEGACY -> Profile(
            version, "legacy remapping", "net.fabricmc.fabric-loom-remap", "remapJar",
            21, ".accesswidener", "accessWidener v2 named (or v2 intermediary; v1 also accepted)",
        )
        MODERN -> Profile(
            version, "Mojang official non-remapping", "net.fabricmc.fabric-loom", "jar",
            25, ".classtweaker", "classTweaker v1 official",
        )
        else -> throw IllegalArgumentException(
            "Unsupported $field Minecraft version for the porting report. " +
                "Only $LEGACY and $MODERN have explicitly scoped report profiles; " +
                "check other versions independently before porting.",
        )
    }

    /**
     * Pure and deterministic: only normalized ModYamlModel values and a target
     * version are read. Both source and target must have a known report profile.
     * Never treats a suggested target profile as proof of source/API compatibility.
     *
     * @throws IllegalArgumentException for unknown or malformed versions.
     */
    @JvmStatic
    fun render(model: ModYamlModel, requestedMinecraftVersion: String): String {
        val source = profile(model.minecraft.version, "source")
        val target = profile(requestedMinecraftVersion, "target")
        val changedFamily = source.remapping != target.remapping
        val entrypoints = model.mod.entrypoints
        val kotlinEntrypoints = entrypoints.values.flatten().count { it.adapter == "kotlin" }

        return buildString {
            appendLine("Fabric Amper porting report — offline, read-only migration checklist")
            appendLine("Source Minecraft: ${source.version} (${source.label})")
            appendLine("Requested Minecraft: ${target.version} (${target.label})")
            appendLine("Scope: these build profiles have specific Fabric Amper evidence; target mod/API compatibility is UNKNOWN.")
            appendLine()

            appendLine("Build profile (source -> target):")
            appendLine("  Loom plugin: ${source.loomId} -> ${target.loomId}")
            appendLine("  Production archive task: ${source.archiveTask} -> ${target.archiveTask}")
            appendLine("  Java: configured source release ${model.settings.java.release} -> target fixture baseline ${target.javaBaseline}")
            if (source.remapping && model.settings.java.release != source.javaBaseline) {
                appendLine("  [REVIEW] Legacy Java 21 is the tested fixture baseline, not a validator-enforced release for every legacy consumer.")
            }
            appendLine("  Source mappings: ${sourceMappings(source, model)}")
            appendLine("  Target mappings: ${targetMappings(target)}")
            if (changedFamily) {
                appendLine("  [ACTION] Review mappings, Loom profile, Java toolchain and archive selection together when editing minecraft.version.")
            } else {
                appendLine("  [ACTION] Recheck target game and API behavior even when the build profile is unchanged.")
            }
            appendLine()

            appendLine("Entrypoints and sides:")
            appendLine(
                "  Authored hooks: main=${entrypoints["main"].orEmpty().size}, " +
                    "client=${entrypoints["client"].orEmpty().size}, " +
                    "server=${entrypoints["server"].orEmpty().size}, " +
                    "preLaunch=${entrypoints["preLaunch"].orEmpty().size}, " +
                    "other=${entrypoints.filterKeys { it !in setOf("main", "client", "server", "preLaunch") }.values.sumOf { it.size }}",
            )
            appendLine("  Kotlin entrypoint adapters declared: $kotlinEntrypoints; mod.environment=${model.mod.environment}")
            appendLine("  [ACTION] Review every entrypoint implementation, target signatures, class loading and dedicated-server side safety; no target entrypoint map was inferred.")
            appendLine()

            appendLine("Dependency configuration changes (declarations retain their order):")
            dependencyChanges(this, "dependencies", model.dependencies, source, target, test = false)
            dependencyChanges(this, "test-dependencies", model.testDependencies, source, target, test = true)
            appendLine("  [ACTION] Recheck the target-specific Fabric Loader and Fabric API artifacts, all third-party mod versions, and nested-JAR runtime metadata.")
            appendLine("  [ACTION] Review mod.depends.minecraft/fabricloader/java/fabric-api and authored predicates; Gradle coordinates and Loader mod IDs are separate.")
            appendLine("  Loader minecraft predicate: ${if ("minecraft" in model.authoredDependsKeys) "author-defined" else "profile-generated"}; target predicate must be revalidated.")
            appendLine()

            appendLine("Resources, Mixins and Kotlin:")
            appendLine("  mod.accessWidener: ${if (model.mod.accessWidener == null) "not declared" else "declared"}; target resource extension ${target.resourceExtension}; target header ${target.resourceHeader}")
            if (model.mod.accessWidener != null && changedFamily) {
                appendLine("  [ACTION] Manually migrate the access-widener/class-tweaker resource and namespace; renaming the extension alone is insufficient.")
            }
            appendLine("  Mixin configurations: ${model.mod.mixins.size}; manually review mapping names, injection selectors, refmaps, target classes and environment sides.")
            if (model.settings.kotlin.enabled) {
                appendLine("  Kotlin: enabled; review Kotlin Gradle plugin/toolchain, Fabric Language Kotlin, adapters and source names against the requested game.")
            } else {
                appendLine("  Kotlin: disabled in settings.kotlin.enabled; do not assume that Java API changes are handled automatically.")
            }
            appendLine("  [ACTION] Manually inspect Minecraft/Fabric API imports, registries, networking, data components, client rendering and saved-data behavior.")
            appendLine()

            appendLine("Verification still required: compile; check exactly one root fabric.mod.json in the production JAR; run unit tests and GameTests; start an isolated client and dedicated server and verify actual behavior.")
            appendLine("[UNKNOWN] Dependency availability, Mixin injection success, source migration, runtime behavior, IDE sync and third-party compatibility were not checked.")
            appendLine("No source, YAML, Gradle settings, caches or remote state were modified.")
            appendLine()
            appendLine("Official migration references:")
            appendLine("  Fabric 26.1 porting: https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/porting/index.md")
            appendLine("  Mapping migration: https://docs.fabricmc.net/develop/porting/mappings/index")
            appendLine("  Fabric API 26.1 porting: https://github.com/FabricMC/fabric-docs/blob/main/versions/26.1.2/develop/porting/fabric-api.md")
            appendLine("  Fabric 26.3 changes: https://fabricmc.net/2026/09/15/263.html")
            appendLine("  Class Tweakers: https://docs.fabricmc.net/develop/class-tweakers/")
            appendLine("  Fabric Loader metadata: https://docs.fabricmc.net/develop/loader/fabric-mod-json")
            appendLine("  Kotlin Gradle compatibility: https://kotlinlang.org/docs/gradle-configure-project.html")
        }
    }

    private fun sourceMappings(profile: Profile, model: ModYamlModel): String =
        if (!profile.remapping) "built-in Mojang official names; no mappings dependency" else
            if (model.minecraft.mappings.type == "yarn") "explicit Yarn mappings coordinate (version authored in YAML)" else
                "Loom official Mojang mappings dependency"

    private fun targetMappings(profile: Profile): String =
        if (profile.remapping) "select explicit official Mojang or version-matched Yarn mappings dependency" else
            "built-in Mojang official names only; remove any Yarn mappings dependency"

    private fun dependencyChanges(
        output: StringBuilder,
        section: String,
        dependencies: List<DependencySpec>,
        source: Profile,
        target: Profile,
        test: Boolean,
    ) {
        output.appendLine("  $section (${dependencies.size}):")
        if (dependencies.isEmpty()) {
            output.appendLine("    (none)")
            return
        }
        dependencies.forEachIndexed { index, dependency ->
            val from = configuration(dependency, source, test) ?: "unsupported"
            val to = configuration(dependency, target, test)
            output.appendLine(
                "    [$index] kind=${dependency.kind}, scope=${dependency.scope}, target=${dependency.target}, " +
                    "exported=${dependency.exported}, include=${dependency.include}: " +
                    "$from -> ${to ?: "UNSUPPORTED (client dependency with legacy remapping Loom)"}",
            )
            if (dependency.include) {
                output.appendLine("      include=true: verify included JAR and Loader metadata in ${target.archiveTask} output.")
            }
            if (to == null) {
                output.appendLine("      [ACTION] Remove or redesign client-targeted dependency before selecting the legacy profile (FA014).")
            }
        }
    }

    private fun configuration(dependency: DependencySpec, profile: Profile, test: Boolean): String? {
        if (test) return when (dependency.scope) {
            "all" -> "testImplementation"
            "compile-only" -> "testCompileOnly"
            else -> "testRuntimeOnly"
        }
        if (dependency.target == "client" && profile.remapping) return null
        val prefix = when {
            dependency.target == "client" -> "client"
            profile.remapping && dependency.kind == "mod" -> "mod"
            else -> ""
        }
        if (dependency.exported) return if (prefix == "mod") "modApi" else "api"
        val suffix = when (dependency.scope) {
            "all" -> "Implementation"
            "compile-only" -> "CompileOnly"
            else -> "RuntimeOnly"
        }
        val value = prefix + suffix
        return value.replaceFirstChar { if (prefix.isEmpty()) it.lowercaseChar() else it }
    }
}
