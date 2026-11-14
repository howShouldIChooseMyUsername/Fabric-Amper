package dev.fabricamper.plugin

import dev.fabricamper.config.DependencySpec
import dev.fabricamper.config.ModYamlModel
import dev.fabricamper.generator.MetadataMapper

/**
 * Explains the three independent dependency contracts in a validated mod.yaml.
 * This is a pure rendering function: no Gradle state, dependency resolution,
 * remote lookup, filesystem access, or publishing credentials are involved.
 *
 * The model already contains defaults merged by ModYamlValidator. Use the
 * original authoredDependsKeys to distinguish user requirements from defaults.
 */
internal object FabricAmperDependencyReport {
    @JvmStatic
    fun render(model: ModYamlModel): String = buildString {
        appendLine("Fabric Amper dependency report")
        appendLine("Mod: ${model.mod.id} ${model.mod.version}")
        appendLine()

        appendLine("Gradle dependencies (dependencies; artifact coordinates):")
        appendArtifacts(model.dependencies)
        appendLine("  kind=mod selects Loom mod configurations on legacy remapping profiles; kind=library uses Java dependency configurations.")
        appendLine("  scope=all is compile and runtime; compile-only and runtime-only select one classpath; target selects main or client.")
        appendLine("  exported=true exposes a dependency through an API configuration; include=true requests a nested JAR.")
        appendLine()

        appendLine("Gradle test dependencies (test-dependencies; artifact coordinates):")
        appendArtifacts(model.testDependencies)
        appendLine("  Test dependencies affect test classpaths; they do not create Fabric Loader or release-site relationships.")
        appendLine()

        appendLine("Fabric Loader requirements (mod.depends; mod IDs and version predicates):")
        val loaderRequirements = MetadataMapper.resolvedDepends(model)
        if (loaderRequirements.isEmpty()) {
            appendLine("  (none)")
        } else {
            loaderRequirements.toSortedMap().forEach { (modId, requirement) ->
                val provenance = if (modId in model.authoredDependsKeys) {
                    "authored (mod.depends.$modId)"
                } else {
                    "generated (${defaultSource(modId)})"
                }
                appendLine("  $modId: ${renderPredicate(requirement)}; origin=$provenance")
            }
        }
        appendLine()

        appendLine("Publish common relationships (publish.common.dependencies; release-site references):")
        val shared = model.publish?.common?.dependencies.orEmpty()
        if (shared.isEmpty()) {
            appendLine("  (none)")
        } else {
            shared.forEachIndexed { index, relation ->
                appendLine(
                    "  [$index] slug=${quoted(relation.slug)}, type=${relation.type}, " +
                        "modrinthId=${relation.modrinthId?.let(::quoted) ?: "(not authored)"}, " +
                        "curseforgeId=${relation.curseforgeId?.let(::quoted) ?: "(not authored)"}",
                )
            }
        }
        appendLine()

        appendLine("Effective Modrinth release relationships (publish.modrinth.dependencies; project IDs):")
        val modrinth = model.publish?.modrinth
        when {
            modrinth == null -> appendLine("  (destination not configured)")
            modrinth.dependencies.isEmpty() -> appendLine("  (none)")
            else -> modrinth.dependencies.forEachIndexed { index, relation ->
                appendLine("  [$index] projectId=${quoted(relation.projectId)}, type=${relation.type}")
            }
        }
        appendLine()

        appendLine("Effective CurseForge release relationships (publish.curseforge.relations; slugs/project IDs):")
        val curseforge = model.publish?.curseforge
        when {
            curseforge == null -> appendLine("  (destination not configured)")
            curseforge.relations.isEmpty() -> appendLine("  (none)")
            else -> curseforge.relations.forEachIndexed { index, relation ->
                appendLine(
                    "  [$index] slug=${quoted(relation.slug)}, " +
                        "projectId=${relation.projectId?.let(::quoted) ?: "(not specified)"}, type=${relation.type}",
                )
            }
        }
        appendLine()
        appendLine("These are distinct contracts: Maven artifact coordinates are not Fabric Loader mod IDs or release-site project IDs.")
        appendLine("This report describes declarations only; it does not resolve artifacts or verify remote project identities.")
    }

    private fun StringBuilder.appendArtifacts(dependencies: List<DependencySpec>) {
        if (dependencies.isEmpty()) {
            appendLine("  (none)")
            return
        }
        dependencies.forEachIndexed { index, dependency ->
            appendLine("  [$index] coordinate=${quoted(dependency.coordinate)}")
            appendLine(
                "      kind=${dependency.kind}, scope=${dependency.scope}, target=${dependency.target}, " +
                    "exported=${dependency.exported}, include=${dependency.include}",
            )
        }
    }

    private fun defaultSource(modId: String): String = when (modId) {
        "minecraft" -> "minecraft.version"
        "fabricloader" -> "minecraft.loader"
        "java" -> "settings.java.release"
        "fabric-api" -> "minecraft.fabricApi"
        "fabric-language-kotlin" -> "settings.kotlin.fabricLanguageKotlin"
        else -> "profile default"
    }

    private fun renderPredicate(value: Any): String = when (value) {
        is String -> quoted(value)
        is List<*> -> value.joinToString(prefix = "[", postfix = "]") { quoted(it as String) }
        else -> error("Expected validated Loader requirement (string or string list)")
    }

    private fun quoted(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
}
