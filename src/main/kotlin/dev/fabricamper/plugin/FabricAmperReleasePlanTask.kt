package dev.fabricamper.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Explicitly invoked, local release preview. It cannot upload, open a
 * connection or read token environment variables.
 */
@DisableCachingByDefault(because = "Displays a verified local release preview without producing an output")
abstract class FabricAmperReleasePlanTask : DefaultTask() {
    @get:Input
    abstract val planJson: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archiveFile: RegularFileProperty

    @TaskAction
    fun preview() {
        logger.lifecycle(
            FabricAmperReleasePlan.render(
                planJson.get(),
                archiveFile.get().asFile.toPath(),
            ),
        )
    }
}
