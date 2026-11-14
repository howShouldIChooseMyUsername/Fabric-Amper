package dev.fabricamper.config

import java.nio.file.Files
import java.nio.file.Path

/**
 * The cross-worker entrypoint. A caller can pass the project root or the
 * selected mod.yaml/mod.yml path; both aliases are checked every time.
 */
object ModYamlLoader {
    @JvmStatic
    fun load(path: Path): ModYamlModel {
        val source = selectedYaml(path)
        val bytes = try {
            Files.readAllBytes(source)
        } catch (e: Exception) {
            throw FabricAmperConfigException("FA001", "mod.yaml",
                "unable to read selected YAML: ${e.message}", source, cause = e)
        }
        return loadBytes(source, bytes)
    }

    /**
     * Intended for Gradle's configuration-cache-tracked
     * providers.fileContents(selectedRegularFile).asBytes.get().
     * This overload never bypasses the mod.yaml/mod.yml collision check.
     */
    @JvmStatic
    fun loadBytes(path: Path, bytes: ByteArray): ModYamlModel {
        val source = selectedYaml(path)
        val parsed = StrictYamlReader.readDocumentBytes(bytes, source)
        return try {
            ModYamlValidator.validate(ModYamlDecoder.decode(parsed.tree, source))
        } catch (error: FabricAmperConfigException) {
            // Decoder and validator use logical field paths; resolve their errors
            // against the original YAML node marks. Missing fields fall back to
            // the closest existing container, without inventing a line/column.
            if (error.line != null && error.column != null) throw error
            val mark = nearestMark(error.yamlPath, parsed.fieldMarks) ?: throw error
            throw FabricAmperConfigException(
                error.code, error.yamlPath, error.reason,
                error.sourceFile ?: source, mark.line, mark.column, error,
            )
        }
    }

    private fun nearestMark(
        path: String,
        marks: Map<String, StrictYamlReader.FieldMark>,
    ): StrictYamlReader.FieldMark? {
        var current = path
        while (current.isNotEmpty()) {
            marks[current]?.let { return it }
            val dot = current.lastIndexOf('.')
            val bracket = current.lastIndexOf('[')
            current = when {
                dot > bracket -> current.substring(0, dot)
                bracket >= 0 -> current.substring(0, bracket)
                else -> ""
            }
        }
        return null
    }

    @JvmStatic
    fun selectedYaml(path: Path): Path {
        val absolute = path.toAbsolutePath().normalize()
        val dir = if (Files.isDirectory(absolute)) absolute else absolute.parent
        if (dir == null) {
            throw FabricAmperConfigException("FA001", "mod.yaml", "invalid project root", absolute)
        }
        val yaml = dir.resolve("mod.yaml")
        val yml = dir.resolve("mod.yml")
        val yamlExists = Files.isRegularFile(yaml)
        val ymlExists = Files.isRegularFile(yml)
        if (yamlExists == ymlExists) {
            val reason = if (yamlExists) "mod.yaml and mod.yml both exist; keep only one"
                else "neither mod.yaml nor mod.yml exists"
            throw FabricAmperConfigException("FA001", "mod.yaml", reason, absolute)
        }
        val selected = if (yamlExists) yaml else yml
        if (!Files.isDirectory(absolute) && absolute != selected) {
            throw FabricAmperConfigException("FA001", "mod.yaml",
                "specified path is not selected YAML: $absolute", absolute)
        }
        return selected
    }
}
