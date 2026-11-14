package dev.fabricamper.unit

import dev.fabricamper.config.FabricAmperConfigException
import dev.fabricamper.util.FabricAmperException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Small real-file fixtures: no Gradle Project instance and no shared filesystem state. */
internal object YamlFixtures {
    val valid = """
        schema: 1
        product: fabric/mod
        mod:
          id: example_mod
          version: "1.0.0+26.3"
          name: Example Mod
        minecraft:
          version: "26.3"
          mappings: mojang
          loader: "0.20.1"
          fabricApi: "0.165.2+26.3"
        settings:
          java:
            release: 25
    """.trimIndent() + "\n"

    fun write(root: Path, yaml: String = valid, name: String = "mod.yaml"): Path {
        Files.createDirectories(root)
        val file = root.resolve(name)
        Files.writeString(file, yaml, StandardCharsets.UTF_8)
        return file
    }

    /** Adds one or more already indented YAML lines under the mod mapping. */
    fun mod(yaml: String = valid, fields: String): String =
        yaml.replace("mod:\n", "mod:\n" + fields.trimIndent().lines()
            .joinToString("\n") { "  $it" } + "\n")

    fun resource(root: Path, relative: String, content: String, sourceSet: String = "main"): Path {
        val file = root.resolve("src/$sourceSet/resources").resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, content, StandardCharsets.UTF_8)
        return file
    }

    fun configError(code: String, action: () -> Any?): FabricAmperConfigException {
        val error = assertThrows(FabricAmperConfigException::class.java) { action() }
        assertEquals(code, error.code, error.message)
        assertTrue(error.yamlPath.isNotBlank(), error.message)
        return error
    }

    fun generatorError(code: String, action: () -> Any?): FabricAmperException {
        val error = assertThrows(FabricAmperException::class.java) { action() }
        assertEquals(code, error.diagnostic.code, error.message)
        assertTrue(error.diagnostic.location.isNotBlank(), error.message)
        return error
    }
}
