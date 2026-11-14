package dev.fabricamper.unit

import dev.fabricamper.config.MinecraftProfile
import dev.fabricamper.config.ModYamlLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ModYamlLoaderUnitTest {
    @TempDir lateinit var project: Path

    @Test
    fun `loads mod yaml both by project root and direct path`() {
        val yaml = YamlFixtures.write(project)
        val fromRoot = ModYamlLoader.load(project)
        val fromFile = ModYamlLoader.load(yaml)
        assertEquals(fromRoot, fromFile)
        assertEquals("example_mod", fromRoot.mod.id)
        assertEquals("1.0.0+26.3", fromRoot.mod.version)
        assertEquals("0.20.1", fromRoot.minecraft.loader)
        assertEquals(">=0.20.1", fromRoot.mod.depends["fabricloader"])
        assertEquals("26.3", fromRoot.mod.depends["minecraft"])
        assertEquals(">=25", fromRoot.mod.depends["java"])
        assertEquals(">=0.165.2", fromRoot.mod.depends["fabric-api"])
        assertEquals(MinecraftProfile.NON_REMAPPING, fromRoot.minecraft.profile)
    }

    @Test
    fun `accepts yml alias when yaml does not exist`() {
        val path = YamlFixtures.write(project, name = "mod.yml")
        assertEquals(path, ModYamlLoader.selectedYaml(project))
        assertEquals("example_mod", ModYamlLoader.load(path).mod.id)
    }

    @Test
    fun `rejects no configuration file and both competing aliases`() {
        YamlFixtures.configError("FA001") { ModYamlLoader.selectedYaml(project) }
        YamlFixtures.write(project)
        YamlFixtures.write(project, name = "mod.yml")
        val error = YamlFixtures.configError("FA001") { ModYamlLoader.load(project) }
        assertTrue(error.reason.contains("both"), error.reason)
    }

    @Test
    fun `loadBytes never bypasses alias collision check`() {
        YamlFixtures.write(project)
        YamlFixtures.write(project, name = "mod.yml")
        YamlFixtures.configError("FA001") {
            ModYamlLoader.loadBytes(project.resolve("mod.yaml"), YamlFixtures.valid.toByteArray())
        }
    }

    @Test
    fun `switching between yaml filenames recalculates selected source after a collision`() {
        val shortName = YamlFixtures.write(project,
            YamlFixtures.valid.replace("name: Example Mod", "name: From YML"), "mod.yml")
        assertEquals("From YML", ModYamlLoader.load(project).mod.name)

        val longName = YamlFixtures.write(project,
            YamlFixtures.valid.replace("name: Example Mod", "name: From YAML"))
        val beforeShort = Files.readAllBytes(shortName)
        val beforeLong = Files.readAllBytes(longName)
        YamlFixtures.configError("FA001") { ModYamlLoader.load(project) }
        YamlFixtures.configError("FA001") {
            ModYamlLoader.loadBytes(longName, beforeLong)
        }
        assertTrue(beforeShort.contentEquals(Files.readAllBytes(shortName)))
        assertTrue(beforeLong.contentEquals(Files.readAllBytes(longName)))

        Files.delete(shortName)
        assertEquals("From YAML", ModYamlLoader.load(project).mod.name)
        Files.delete(longName)
        YamlFixtures.write(project,
            YamlFixtures.valid.replace("name: Example Mod", "name: Restored YML"), "mod.yml")
        assertEquals("Restored YML", ModYamlLoader.load(project).mod.name)
    }

    @Test
    fun `selected yml strictly rejects malformed UTF8 and unquoted Loader wildcard alias`() {
        val path = YamlFixtures.write(project, name = "mod.yml")
        val original = Files.readAllBytes(path)
        val malformed = original + byteArrayOf(0xc3.toByte(), 0x28)
        YamlFixtures.configError("FA002") { ModYamlLoader.loadBytes(path, malformed) }
        assertTrue(original.contentEquals(Files.readAllBytes(path)), "loadBytes must not modify YAML")
        Files.write(path, malformed)
        YamlFixtures.configError("FA002") { ModYamlLoader.load(project) }
        assertTrue(malformed.contentEquals(Files.readAllBytes(path)), "failed load must not rewrite YAML")

        YamlFixtures.write(project,
            YamlFixtures.mod(fields = "depends:\n  optional_mod: *"), "mod.yml")
        YamlFixtures.configError("FA002") { ModYamlLoader.load(project) }
        YamlFixtures.write(project,
            YamlFixtures.mod(fields = "depends:\n  optional_mod: \"*\""), "mod.yml")
        assertEquals("*", ModYamlLoader.load(project).mod.depends["optional_mod"])
    }

    @Test
    fun `rejects a path other than selected YAML`() {
        YamlFixtures.write(project)
        YamlFixtures.configError("FA001") {
            ModYamlLoader.selectedYaml(project.resolve("other.yml"))
        }
    }

    @Test
    fun `tracked bytes overload parses supplied bytes from selected path`() {
        val path = YamlFixtures.write(project)
        val supplied = YamlFixtures.valid.replace("name: Example Mod", "name: Updated in memory")
        val loaded = ModYamlLoader.loadBytes(path, supplied.toByteArray(Charsets.UTF_8))
        assertEquals("Updated in memory", loaded.mod.name)
        assertFalse(Files.readString(path).contains("Updated in memory"))
    }

    @Test
    fun `missing required fields and unknown product produce FA003`() {
        val path = YamlFixtures.write(project, YamlFixtures.valid.replace("product: fabric/mod\n", ""))
        YamlFixtures.configError("FA003") { ModYamlLoader.load(path) }
        YamlFixtures.write(project, YamlFixtures.valid.replace("fabric/mod", "jvm/lib"))
        YamlFixtures.configError("FA003") { ModYamlLoader.load(path) }
    }

    @Test
    fun `unquoted numeric Minecraft version is a type error`() {
        val yaml = YamlFixtures.valid.replace("  version: \"26.3\"", "  version: 26.3")
        YamlFixtures.write(project, yaml)
        val error = YamlFixtures.configError("FA003") { ModYamlLoader.load(project) }
        assertEquals("minecraft.version", error.yamlPath)
    }

    @Test
    fun `unknown root and nested keys are rejected rather than silently ignored`() {
        YamlFixtures.write(project, YamlFixtures.valid + "fabricModJson: override\n")
        YamlFixtures.configError("FA003") { ModYamlLoader.load(project) }
        val nested = YamlFixtures.mod(fields = "unspecifiedKey: false")
        YamlFixtures.write(project, nested)
        val error = YamlFixtures.configError("FA003") { ModYamlLoader.load(project) }
        assertTrue(error.yamlPath.contains("unspecifiedKey"), error.message)
    }

    @Test
    fun `YAML files remain untouched on failing validation`() {
        val yaml = YamlFixtures.valid.replace("  id: example_mod", "  id: INVALID-ID")
        val path = YamlFixtures.write(project, yaml)
        val before = Files.readAllBytes(path)
        YamlFixtures.configError("FA009") { ModYamlLoader.load(project) }
        assertTrue(before.contentEquals(Files.readAllBytes(path)))
    }
}
