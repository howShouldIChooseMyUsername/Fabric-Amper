package dev.fabricamper.unit

import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.config.MixinSpec
import dev.fabricamper.generator.ValidateFabricAmper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ResourceValidationUnitTest {
    @TempDir lateinit var project: Path

    @Test
    fun `valid main and client Mixin resources and classTweaker pass both validators`() {
        YamlFixtures.resource(project, "common.mixins.json", "{}")
        YamlFixtures.resource(project, "client.mixins.json", "{}", sourceSet = "client")
        YamlFixtures.resource(project, "mod.classtweaker", "classTweaker v1 official\n")
        YamlFixtures.resource(project, "assets/example_mod/icon.png", "PNG signature is not checked here")
        val yaml = YamlFixtures.mod(fields = """
            icon: assets/example_mod/icon.png
            mixins:
              - common.mixins.json
              - config: client.mixins.json
                environment: client
            accessWidener: mod.classtweaker
        """).replace(
            "settings:\n", "settings:\n  loom:\n    splitEnvironmentSourceSets: true\n",
        )
        val parsed = ModYamlLoader.load(YamlFixtures.write(project, yaml))
        ValidateFabricAmper.validate(parsed)
        ValidateFabricAmper.validateSourceResources(parsed)
        assertEquals(2, parsed.mod.mixins.size)
        assertEquals("client", parsed.mod.mixins[1].environment)
    }

    @Test
    fun `existing metadata in main or client source resources is FA008`() {
        val path = YamlFixtures.write(project)
        for (sourceSet in listOf("main", "client")) {
            val metadata = YamlFixtures.resource(project, "fabric.mod.json", "{}", sourceSet)
            YamlFixtures.configError("FA008") { ModYamlLoader.load(path) }
            Files.delete(metadata)
        }
    }

    @Test
    fun `missing mixin icon or classTweaker is a named validation error`() {
        for (resource in listOf(
            "mixins:\n    - absent.mixins.json",
            "icon: assets/example_mod/absent.png",
            "accessWidener: absent.classtweaker",
        )) {
            val yaml = YamlFixtures.mod(fields = resource)
            val error = YamlFixtures.configError("FA009") {
                ModYamlLoader.load(YamlFixtures.write(project, yaml))
            }
            assertTrue(error.yamlPath.startsWith("mod."), error.message)
        }
    }

    @Test
    fun `invalid tweaker header is rejected and original resource remains unchanged`() {
        val resource = YamlFixtures.resource(project, "mod.classtweaker", "accessWidener v2 named\n")
        val originalBytes = Files.readAllBytes(resource)
        val yaml = YamlFixtures.mod(fields = "accessWidener: mod.classtweaker")
        YamlFixtures.configError("FA009") {
            ModYamlLoader.load(YamlFixtures.write(project, yaml))
        }
        assertTrue(originalBytes.contentEquals(Files.readAllBytes(resource)))
    }

    @Test
    fun `rejects URI absolute path traversal and Windows separators`() {
        val paths = listOf(
            "../escape.mixins.json",
            "./local.mixins.json",
            "/absolute.mixins.json",
            "C:/absolute.mixins.json",
            "mixins\\subfolder.json",
            "mixins//double.json",
        )
        for (invalid in paths) {
            val yaml = YamlFixtures.mod(fields = """
                mixins:
                  - '$invalid'
            """)
            val error = YamlFixtures.configError("FA009") {
                ModYamlLoader.load(YamlFixtures.write(project, yaml))
            }
            assertTrue(error.yamlPath.startsWith("mod.mixins"), error.message)
        }
    }

    @Test
    fun `icon must be PNG and icon size map must use positive sizes`() {
        val badIcon = YamlFixtures.mod(fields = "icon: assets/example_mod/logo.jpg")
        YamlFixtures.configError("FA009") {
            ModYamlLoader.load(YamlFixtures.write(project, badIcon))
        }
        val badSize = YamlFixtures.mod(fields = """
            icon:
              "0": assets/example_mod/icon.png
        """)
        YamlFixtures.configError("FA003") {
            ModYamlLoader.load(YamlFixtures.write(project, badSize))
        }
    }

    @Test
    fun `generator resource validation rejects missing resources independently of structural validation`() {
        // Direct DTOs are a supported mapper input; a missing physical file must
        // still be discovered by the source-resource validation phase.
        val model = ModYamlLoader.load(YamlFixtures.write(project))
        val unchecked = model.copy(mod = model.mod.copy(
            mixins = listOf(MixinSpec("missing.mixins.json")),
        ))
        YamlFixtures.generatorError("FA009") {
            ValidateFabricAmper.validateSourceResources(unchecked)
        }
    }

    @Test
    fun `Symlink targets outside resources should not be accepted as mod resources`() {
        // Both config and generator independently reject links outside their
        // source-set resources root, even when the target is project-local.
        val privateFile = project.resolve("secret.mixins.json")
        Files.writeString(privateFile, "{}")
        val publicPath = project.resolve("src/main/resources/linked.mixins.json")
        Files.createDirectories(publicPath.parent)
        val canMakeLink = try {
            Files.createSymbolicLink(publicPath, privateFile)
            true
        } catch (_: Exception) {
            false
        }
        assumeTrue(canMakeLink, "symlinks unavailable in this environment")
        val yaml = YamlFixtures.mod(fields = "mixins: [linked.mixins.json]")
        val path = YamlFixtures.write(project, yaml)
        YamlFixtures.configError("FA009") { ModYamlLoader.load(path) }
        val baseline = ModYamlLoader.load(YamlFixtures.write(project))
        val unchecked = baseline.copy(mod = baseline.mod.copy(
            mixins = listOf(MixinSpec("linked.mixins.json")),
        ))
        YamlFixtures.generatorError("FA009") {
            ValidateFabricAmper.validateSourceResources(unchecked)
        }
        assertFalse(Files.isRegularFile(project.resolve("src/main/resources/secret.mixins.json")))
    }
}
