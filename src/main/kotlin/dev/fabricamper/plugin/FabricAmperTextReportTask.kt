package dev.fabricamper.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * A non-mutating, configuration-cache-compatible report task. The validated
 * YAML model is rendered to a plain value at configuration time. The task
 * action reads only its declared input and never captures a Gradle Project.
 */
@DisableCachingByDefault(because = "Displays an explicit diagnostic and has no output files")
abstract class FabricAmperTextReportTask : DefaultTask() {
    @get:Input
    abstract val reportText: Property<String>

    /** An unsupported requested report profile must fail only when this task executes. */
    @get:Input
    abstract val failureReason: Property<String>

    init {
        failureReason.convention("")
    }

    @TaskAction
    fun report() {
        val failure = failureReason.get()
        if (failure.isNotEmpty()) throw GradleException(failure)
        logger.lifecycle(reportText.get())
    }
}
