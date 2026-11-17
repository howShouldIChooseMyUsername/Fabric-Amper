package dev.fabricamper.generator

import dev.fabricamper.config.LoaderTargetSpec
import dev.fabricamper.config.ModYamlModel
import dev.fabricamper.util.failAmper

/**
 * Projects a namespaced v1.1 Fabric target onto the established v1 Fabric
 * metadata/validator contract. A root mod.yaml may describe both loaders;
 * never let NeoForge-only settings or the root v1 placeholders leak into FMJ.
 *
 * The Project plugin's pre-Loom mirror and the generateFabricModJson task must
 * both use this same transformation to produce byte-identical metadata.
 */
object FabricV11Projection {
    @JvmStatic
    fun fromModel(model: ModYamlModel): ModYamlModel {
        if (model.schemaVersion != "1.1") return model

        val target: LoaderTargetSpec = model.targets.singleOrNull { it.loader == "fabric" }
            ?: failAmper("FA003", "product", "Fabric 메타데이터를 만들 Fabric 대상이 없습니다.")
        val fabric = model.fabric
            ?: failAmper("FA003", "fabric", "v1.1 Fabric 대상에는 fabric 블록이 필요합니다.")

        return model.copy(
            // The established Fabric metadata validator uses the v1 field
            // shape. After projection no second namespace conversion is needed.
            schemaVersion = "1",
            product = target.product,
            targets = listOf(target),
            mod = model.mod.copy(
                environment = fabric.environment,
                entrypoints = fabric.entrypoints,
                languageAdapters = fabric.languageAdapters,
                mixins = fabric.mixins,
                accessWidener = fabric.accessWidener,
                provides = fabric.provides,
                depends = fabric.depends,
                recommends = fabric.recommends,
                suggests = fabric.suggests,
                conflicts = fabric.conflicts,
                breaks = fabric.breaks,
                custom = fabric.custom,
                icon = fabric.icon ?: model.mod.icon,
            ),
            minecraft = model.minecraft.copy(
                loader = fabric.loader,
                fabricApi = fabric.api,
                mappings = fabric.mappings,
            ),
            settings = model.settings.copy(
                loom = fabric.loom,
                kotlin = model.settings.kotlin.copy(
                    fabricLanguageKotlin = fabric.kotlin.fabricLanguageKotlin,
                ),
                resources = fabric.resources,
            ),
            repositories = model.repositories + fabric.repositories,
            dependencies = model.dependencies + fabric.gradleDependencies,
            authoredDependsKeys = fabric.depends.keys.toSet(),
        )
    }
}
