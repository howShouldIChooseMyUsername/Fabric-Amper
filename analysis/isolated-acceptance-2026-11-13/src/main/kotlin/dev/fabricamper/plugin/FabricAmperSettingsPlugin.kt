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
