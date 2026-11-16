package dev.fabricamper.unit

import com.google.gson.JsonParser
import dev.fabricamper.config.ArchitecturyDefaults
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.generator.MetadataMapper
import dev.fabricamper.loom.DependencyConfigurer
import dev.fabricamper.loom.GradleDependencyDeclaration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ArchitecturyConvenienceUnitTest {
    @TempDir lateinit var project: Path

    private fun parse(section: String, baseline: String = YamlFixtures.valid): dev.fabricamper.config.ModYamlModel =
        ModYamlLoader.load(YamlFixtures.write(project, baseline + section))

    @Test
    fun `opt in defaults to audited 26_3 Fabric API and registers runtime metadata`() {
        val model = parse("architectury:\n  enable: true\n", YamlFixtures.valid.replace(
            "  fabricApi: \"0.165.2+26.3\"\n", "",
        ))
        assertTrue(model.architectury.enabled)
        assertEquals("22.0.5", model.architectury.version)
        assertEquals("0.165.2+26.3", model.minecraft.fabricApi)
        assertEquals(">=22.0.5", model.mod.depends["architectury"])
        assertEquals(">=0.165.2", model.mod.depends["fabric-api"])
        assertEquals("dev.architectury:architectury-fabric:22.0.5", model.dependencies.single().coordinate)
        val dep = model.dependencies.single()
        assertEquals("mod", dep.kind)
        assertEquals("all", dep.scope)
        assertEquals("main", dep.target)
        assertFalse(dep.include)
        assertFalse(dep.exported)
        assertEquals("implementation", DependencyConfigurer.configurationName(
            GradleDependencyDeclaration(dep.coordinate, dep.kind, dep.scope, dep.target, dep.exported, dep.include),
            remapping = false, test = false,
        ))
        assertEquals(ArchitecturyDefaults.MAVEN_URL, model.repositories.single().url)
        assertEquals(">=22.0.5", JsonParser.parseString(MetadataMapper.toJson(model))
            .asJsonObject.getAsJsonObject("depends").get("architectury").asString)
    }

    @Test
    fun `explicit version and enabled alias preserve authored Fabric API and Loader requirement`() {
        val baseline = YamlFixtures.valid.replace("0.165.2+26.3", "0.165.1+26.3")
        val model = parse("""
            architectury:
              enabled: true
              version: "22.0.4"
        """.trimIndent() + "\n", baseline.replace(
            "        release: 25", "        release: 25",
        ).replace("mod:\n", "mod:\n  depends:\n    architectury: '>=22.0.1 <22.1'\n"))
        assertEquals("22.0.4", model.architectury.version)
        assertEquals("0.165.1+26.3", model.minecraft.fabricApi)
        assertEquals(">=22.0.1 <22.1", model.mod.depends["architectury"])
        assertEquals("dev.architectury:architectury-fabric:22.0.4", model.dependencies.single().coordinate)
    }

    @Test
    fun `disabled or omitted switch adds no repository dependency or metadata`() {
        for (section in listOf("", "architectury:\n  enable: false\n", "architectury:\n  enabled: false\n")) {
            val model = parse(section)
            assertFalse(model.architectury.enabled)
            assertEquals(null, model.architectury.version)
            assertTrue(model.dependencies.isEmpty())
            assertTrue(model.repositories.isEmpty())
            assertFalse(model.mod.depends.containsKey("architectury"))
        }
    }

    @Test
    fun `reusing explicit Architectury Maven repository does not duplicate it`() {
        val model = parse("""
            repositories:
              - maven: "https://maven.architectury.dev/"
            architectury:
              enable: true
        """.trimIndent() + "\n")
        assertEquals(1, model.repositories.size)
    }

    @Test
    fun `bad Architectury config fails with actionable FA003`() {
        val invalid = listOf(
            "architectury:\n  enable: true\n  enabled: false\n",
            "architectury:\n  enabled: false\n  version: \"22.0.5\"\n",
            "architectury:\n  version: \"22.0.5\"\n",
            "architectury:\n  enable: yes\n",
            "architectury:\n  enable: true\n  version: \"latest.release\"\n",
            "architectury:\n  enable: true\n  version: \"22.+\"\n",
            "architectury:\n  enable: true\n  extraneous: 1\n",
        )
        for (yaml in invalid) YamlFixtures.configError("FA003") { parse(yaml) }
        YamlFixtures.configError("FA003") { parse(
            "architectury:\n  enable: true\n",
            YamlFixtures.valid.replace("version: \"26.3\"", "version: \"26.2\""),
        ) }
    }

    @Test
    fun `explicit incompatible Loader requirement and manual Maven duplicate fail closed`() {
        val depends = YamlFixtures.valid.replace("mod:\n", "mod:\n  depends:\n    architectury: '>=99.0.0'\n")
        YamlFixtures.configError("FA009") { parse("architectury:\n  enable: true\n", depends) }
        val duplicate = """
            architectury:
              enable: true
            dependencies:
              - maven: "dev.architectury:architectury-fabric:22.0.4"
                kind: mod
        """.trimIndent() + "\n"
        YamlFixtures.configError("FA012") { parse(duplicate) }
    }
}
