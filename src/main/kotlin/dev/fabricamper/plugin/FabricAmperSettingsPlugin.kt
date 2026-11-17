package dev.fabricamper.plugin

import dev.fabricamper.config.FabricAmperConfigException
import dev.fabricamper.config.ModYamlLoader
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import java.io.File

/** Single Settings entry point for projects that contain no build.gradle file. */
class FabricAmperSettingsPlugin : Plugin<Settings> {
    override fun apply(target: Settings) {
        val yaml = selectYaml(target.settingsDir)
        // Consume a tracked configuration input, not just an untracked direct read.
        val relativeName = yaml.name
        val yamlBytes = target.providers.fileContents(
            target.layout.settingsDirectory.file(relativeName),
        ).asBytes.get()
        val config = ModYamlLoader.loadBytes(yaml.toPath(), yamlBytes)
        target.rootProject.name = config.mod.archivesName ?: config.mod.id
        if (config.isMultiLoader) {
            val commonDirectory = target.settingsDir.resolve("common")
            if (commonDirectory.isDirectory) {
                if (commonDirectory.resolve("build.gradle").exists() ||
                    commonDirectory.resolve("build.gradle.kts").exists()
                ) {
                    throw FabricAmperConfigException(
                        "FA008", "product", "common/ cannot contain a consumer build.gradle(.kts)", yaml.toPath(),
                    )
                }
                target.include(":common")
                target.project(":common").projectDir = commonDirectory
            }
            // One user-owned settings.gradle.kts drives independent loader
            // projects. Neither child contains an authored build.gradle(.kts).
            // MDG is applied only to :neoforge; Loom only to :fabric.
            config.targets.forEach { selected ->
                val directory = target.settingsDir.resolve(selected.loader)
                if (!directory.isDirectory) {
                    throw FabricAmperConfigException(
                        "FA003", "product", "multi-loader target '${selected.loader}' requires " +
                            "the ${selected.loader}/ source directory; no project is generated or overwritten",
                        yaml.toPath(),
                    )
                }
                if (directory.resolve("build.gradle").exists() || directory.resolve("build.gradle.kts").exists()) {
                    throw FabricAmperConfigException(
                        "FA008", "product", "${selected.loader}/ cannot have a consumer build.gradle(.kts)", yaml.toPath(),
                    )
                }
                target.include(":${selected.loader}")
                target.project(":${selected.loader}").projectDir = directory
            }
        }
        target.gradle.lifecycle.beforeProject(FabricAmperBeforeProjectAction())
    }

    private fun selectYaml(directory: File): File {
        val yaml = directory.resolve("mod.yaml")
        val yml = directory.resolve("mod.yml")
        if (yaml.isFile == yml.isFile) {
            throw FabricAmperConfigException(
                "FA001", "mod.yaml", "exactly one of mod.yaml or mod.yml must exist", directory.toPath(),
            )
        }
        return if (yaml.isFile) yaml else yml
    }
}
