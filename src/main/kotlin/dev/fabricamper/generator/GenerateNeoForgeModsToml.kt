package dev.fabricamper.generator

import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.util.failAmper
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

/**
 * Task-produced NeoForge metadata; never a Fabric Loom configuration mirror.
 * The project plugin must add the output's parent resource root to processResources.
 */
@CacheableTask
abstract class GenerateNeoForgeModsToml : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val yamlFile: RegularFileProperty

    @get:Input
    abstract val targetId: Property<String>

    /** Source trees also invalidate task caching if an authored TOML appears. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceResources: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    /** Captured during configuration; Task.project is not configuration-cache safe in a task action. */
    @get:Internal
    abstract val targetProjectDir: DirectoryProperty

    init {
        targetId.convention("neoforge")
        targetProjectDir.convention(project.layout.projectDirectory)
        outputFile.convention(
            project.layout.buildDirectory.file(
                "generated/fabric-amper/resources/main/META-INF/neoforge.mods.toml",
            ),
        )
        sourceResources.from(
            project.fileTree("src/main/resources"),
            project.fileTree("src/client/resources"),
        )
    }

    @TaskAction
    fun generate() {
        val model = ModYamlLoader.load(yamlFile.get().asFile.toPath())
        if (targetId.get() != "neoforge") {
            failAmper("FA016", "targetId", "NeoForge TOML 생성 대상은 neoforge여야 합니다.")
        }
        val projectRoot = targetProjectDir.get().asFile.toPath()
        NeoForgeMetadataMapper.validateNoAuthoredToml(model, projectRoot)
        NeoForgeMetadataMapper.validateResources(model, projectRoot)
        val bytes = NeoForgeMetadataMapper.toUtf8Bytes(model)
        val path = outputFile.get().asFile.toPath()
        Files.createDirectories(path.parent)
        Files.write(path, bytes)
    }
}
