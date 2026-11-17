package dev.fabricamper.generator

import dev.fabricamper.config.ModYamlLoader
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

/**
 * Runtime-packaged Loader metadata. The separate .gradle snapshot is prepared
 * by the Project plugin before Loom applies; this task never packages that snapshot.
 */
@CacheableTask
abstract class GenerateFabricModJson : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val yamlFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    /** Actual :fabric project directory, not the root mod.yaml directory. */
    @get:Internal
    abstract val sourceDirectory: DirectoryProperty

    /** Changes to authored resources must invalidate cached metadata checks. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceResources: ConfigurableFileCollection

    init {
        sourceDirectory.convention(project.layout.projectDirectory)
        sourceResources.from(
            project.fileTree("src/main/resources"),
            project.fileTree("src/client/resources"),
            project.rootProject.fileTree("src/main/resources"),
            project.rootProject.fileTree("src/client/resources"),
            project.rootProject.fileTree("common/src/main/resources"),
            project.rootProject.fileTree("common/src/client/resources"),
        )
        outputFile.convention(
            project.layout.buildDirectory.file("generated/fabric-amper/resources/main/fabric.mod.json"),
        )
    }

    @TaskAction
    fun generate() {
        val parsed = ModYamlLoader.load(yamlFile.get().asFile.toPath())
        ValidateFabricAmper.validateSourceResources(parsed, sourceDirectory.get().asFile.toPath())
        val bytes = MetadataMapper.toUtf8Bytes(parsed)
        val output = outputFile.get().asFile.toPath()
        Files.createDirectories(output.parent)
        Files.write(output, bytes)
    }
}
