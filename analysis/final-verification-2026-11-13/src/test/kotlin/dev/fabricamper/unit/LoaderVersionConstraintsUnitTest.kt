package dev.fabricamper.unit

import dev.fabricamper.config.MinecraftProfile
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.util.LoaderVersionConstraints
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class LoaderVersionConstraintsUnitTest {
    @TempDir lateinit var project: Path

    @Test
    fun `whitespace separated predicates are AND`() {
        assertEquals(true, LoaderVersionConstraints.matches(">=0.20.1 <0.21.0", "0.20.1"))
        assertEquals(true, LoaderVersionConstraints.matches(">=0.20.1 <0.21.0", "0.20.9"))
        assertEquals(false, LoaderVersionConstraints.matches(">=0.20.1 <0.21.0", "0.21.0"))
        assertEquals(false, LoaderVersionConstraints.matches(">=0.20.1 <0.21.0", "0.20.0"))
    }

    @Test
    fun `array alternatives are OR and can combine independent AND ranges`() {
        val alternatives = listOf(">=1.0.0 <1.1.0", ">=2.0.0 <2.1.0")
        assertEquals(true, LoaderVersionConstraints.matchesAny(alternatives, "1.0.5"))
        assertEquals(true, LoaderVersionConstraints.matchesAny(alternatives, "2.0.9"))
        assertEquals(false, LoaderVersionConstraints.matchesAny(alternatives, "1.5.0"))
        assertEquals(false, LoaderVersionConstraints.matchesAny(alternatives, "2.1.0"))
    }

    @Test
    fun `SemVer build metadata is ignored for ordering`() {
        assertEquals(true, LoaderVersionConstraints.matches(">=0.165.2", "0.165.2+26.3"))
        assertEquals(true, LoaderVersionConstraints.matches("0.165.2+26.2", "0.165.2+26.3"))
        assertEquals(false, LoaderVersionConstraints.matches(">0.165.2", "0.165.2+26.3"))
        assertEquals(true, LoaderVersionConstraints.matches("<1.0.0", "1.0.0-beta.1"))
        assertEquals(false, LoaderVersionConstraints.matches("<1.0.0", "1.0.0"))
    }

    @Test
    fun `opaque versions support equality and reject incompatible known numeric versions`() {
        assertEquals(true, LoaderVersionConstraints.matches("future-release", "future-release"))
        assertEquals(false, LoaderVersionConstraints.matches("older", "future-release"))
        assertFalse(LoaderVersionConstraints.matches(">=future-release", "1.0.0") ?: true)
        assertNull(LoaderVersionConstraints.matchesAny(listOf("<future-release", ">old-build"), "1.0.0"))
        assertEquals(true, LoaderVersionConstraints.matchesAny(listOf(">=future-release", "*"), "1.0.0"))
    }

    @Test
    fun `compatible Loader ranges wildcards and opaque exact versions match`() {
        assertEquals(true, LoaderVersionConstraints.matches("^1.2.3", "1.3.0"))
        assertEquals(true, LoaderVersionConstraints.matches("~1.2.3", "1.2.5"))
        assertEquals(true, LoaderVersionConstraints.matches("1.2.*", "1.2.5"))
        assertEquals(true, LoaderVersionConstraints.matches("!=1.2.3", "!=1.2.3"))
        assertEquals(false, LoaderVersionConstraints.matches("!=1.2.3", "1.2.5"))
        assertNull(LoaderVersionConstraints.matches(">=", "1.2.5"))
    }

    @Test
    fun `loader accepts OR when one Minecraft version matches`() {
        val yaml = YamlFixtures.mod(fields = """
            depends:
              minecraft: ["26.2", "26.3"]
              fabricloader: ">=0.20.1 <0.21.0"
        """)
        val model = ModYamlLoader.load(YamlFixtures.write(project, yaml))
        assertEquals(listOf("26.2", "26.3"), model.mod.depends["minecraft"])
        assertEquals(MinecraftProfile.NON_REMAPPING, model.minecraft.profile)
    }

    @Test
    fun `explicit runtime version conflicts cannot be silently overwritten`() {
        val conflict = YamlFixtures.mod(fields = """
            depends:
              fabricloader: ">=0.30.0"
        """)
        val error = YamlFixtures.configError("FA009") {
            ModYamlLoader.load(YamlFixtures.write(project, conflict))
        }
        assertTrue(error.yamlPath.contains("fabricloader"), error.message)
    }

    @Test
    fun `explicit OR with no matching alternatives is an error`() {
        val conflict = YamlFixtures.mod(fields = """
            depends:
              minecraft: ["26.1", "26.2"]
        """)
        YamlFixtures.configError("FA009") {
            ModYamlLoader.load(YamlFixtures.write(project, conflict))
        }
    }

    @Test
    fun `unknown non SemVer ordering must fail validation rather than pass unchecked`() {
        // Regression test for config/ModYamlValidator VersionRequirements:
        // currently its compareVersions(null) fallback returns true for >=opaque.
        val conflict = YamlFixtures.mod(fields = """
            depends:
              fabricloader: ">=future-release"
        """)
        YamlFixtures.configError("FA009") {
            ModYamlLoader.load(YamlFixtures.write(project, conflict))
        }
    }

    @Test
    fun `remapping legacy version may refer to legacy accesswidener resource`() {
        // Regression test: 26.3 must use classTweaker, but 1.21.11 still allows
        // an access widener. Do not apply the 26.3 extension rule to all profiles.
        val legacy = YamlFixtures.mod(fields = "accessWidener: legacy.accesswidener")
            .replace("version: \"26.3\"", "version: \"1.21.11\"")
            .replace("fabricApi: \"0.165.2+26.3\"", "fabricApi: \"0.115.0+1.21.11\"")
        YamlFixtures.resource(project, "legacy.accesswidener", "accessWidener v2 named\n")
        val model = ModYamlLoader.load(YamlFixtures.write(project, legacy))
        assertEquals(MinecraftProfile.REMAPPING, model.minecraft.profile)
        assertEquals("legacy.accesswidener", model.mod.accessWidener)
    }
}
