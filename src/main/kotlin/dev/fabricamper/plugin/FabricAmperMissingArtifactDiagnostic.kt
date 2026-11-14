package dev.fabricamper.plugin

import org.gradle.api.flow.BuildWorkResult
import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Adds a precise FA011 message to Gradle's own dependency-resolution failure.
 *
 * A dataflow action also sees failures while Gradle stores the configuration
 * cache, before any task can run. It must not resolve dependencies itself or
 * replace the original exception (which contains the repository diagnostics).
 */
abstract class FabricAmperMissingArtifactDiagnostic : FlowAction<FabricAmperMissingArtifactDiagnostic.Parameters> {
    interface Parameters : FlowParameters {
        @get:Input
        val buildResult: Property<BuildWorkResult>

        @get:Input
        val yamlFileName: Property<String>

        /** Encoded as YAML path, original Gradle coordinate, line, column. */
        @get:Input
        val declarations: ListProperty<String>
    }

    override fun execute(parameters: Parameters) {
        val failure = parameters.buildResult.get().failure.orElse(null) ?: return
        val declarations = parameters.declarations.get().mapNotNull(::decode)
        if (declarations.isEmpty()) return

        val missing = missingCoordinates(failure, declarations.map { it.coordinate }.toSet())
        if (missing.isEmpty()) return

        val logger = Logging.getLogger(FabricAmperMissingArtifactDiagnostic::class.java)
        val yaml = parameters.yamlFileName.get()
        declarations.filter { it.coordinate in missing }.distinctBy { it.yamlPath }.forEach { declaration ->
            logger.error(
                "FabricAmper FA011 $yaml:${declaration.line}:${declaration.column} " +
                    "${declaration.yamlPath}: Maven/Modrinth artifact not found: ${declaration.coordinate}",
            )
        }
    }

    private data class Declaration(
        val yamlPath: String,
        val coordinate: String,
        val line: String,
        val column: String,
    )

    private fun decode(encoded: String): Declaration? {
        val fields = encoded.split('\t')
        return if (fields.size == 4) Declaration(fields[0], fields[1], fields[2], fields[3]) else null
    }

    /**
     * Gradle uses different exceptions for a missing module descriptor and a
     * missing artifact file. Other resolution failures (offline, HTTP errors,
     * TLS, missing variants, authentication) must retain native Gradle output
     * without the misleading FA011 code.
     *
     * The Gradle classes below are internal: if their names/messages change,
     * fail closed by omitting this supplemental diagnostic. Never infer that a
     * coordinate is absent merely because it is mentioned in an exception.
     */
    private fun missingCoordinates(failure: Throwable, declared: Set<String>): Set<String> {
        val unresolved = linkedSetOf<String>()
        val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        val pending = ArrayDeque<Throwable>()
        pending.addLast(failure)

        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!visited.add(current)) continue
            val message = current.message.orEmpty()
            when (current.javaClass.name) {
                "org.gradle.internal.resolve.ModuleVersionNotFoundException" -> {
                    declared.filterTo(unresolved) { coordinate ->
                        message == "Could not find $coordinate." ||
                            message.startsWith("Could not find $coordinate.\n") ||
                            message.startsWith("Could not find $coordinate.\r\n")
                    }
                }
                "org.gradle.internal.resolve.ArtifactNotFoundException" -> {
                    if (message.startsWith("Could not find ")) {
                        declared.filterTo(unresolved) { coordinate ->
                            message.contains(" ($coordinate).")
                        }
                    }
                }
            }
            current.cause?.let(pending::addLast)
            current.suppressed.forEach(pending::addLast)
        }
        return unresolved
    }
}
