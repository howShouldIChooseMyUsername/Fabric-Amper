package dev.fabricamper.unit

import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.generator.MetadataMapper
import dev.fabricamper.util.LoaderVersionConstraints
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Contract examples from https://docs.fabricmc.net/develop/loader/fabric-mod-json
 * (Semantic Versioning table). These intentionally exercise the extended
 * predicates that the previous conservative evaluator returned as 'unknown'.
 */
class LoaderExtendedVersionRegressionUnitTest {
    @TempDir lateinit var root: Path

    @Test
    fun `caret range permits later minor versions but rejects next major version`() {
        assertEquals(true, LoaderVersionConstraints.matches("^26.2", "26.2"))
        assertEquals(true, LoaderVersionConstraints.matches("^26.2", "26.3"))
        assertEquals(false, LoaderVersionConstraints.matches("^26.2", "26.1"))
        assertEquals(false, LoaderVersionConstraints.matches("^26.2", "27.0"))
    }

    @Test
    fun `tilde range permits later patch versions but rejects next minor version`() {
        assertEquals(true, LoaderVersionConstraints.matches("~26.2", "26.2"))
        assertEquals(true, LoaderVersionConstraints.matches("~26.2", "26.2.7"))
        assertEquals(false, LoaderVersionConstraints.matches("~26.2", "26.3"))
        assertEquals(false, LoaderVersionConstraints.matches("~26.2", "26.1.9"))
    }

    @Test
    fun `tilde prerelease lower bound is inclusive and excludes earlier prereleases`() {
        assertEquals(true, LoaderVersionConstraints.matches("~26.2-rc.2", "26.2-rc.2"))
        assertEquals(true, LoaderVersionConstraints.matches("~26.2-rc.2", "26.2.1"))
        assertEquals(false, LoaderVersionConstraints.matches("~26.2-rc.2", "26.2-rc.1"))
        assertEquals(false, LoaderVersionConstraints.matches("~26.2-rc.2", "26.3"))
    }

    @Test
    fun `wildcard x and star identify one minor version series`() {
        for (range in listOf("26.2.x", "26.2.X", "26.2.*")) {
            assertEquals(true, LoaderVersionConstraints.matches(range, "26.2.0"), range)
            assertEquals(true, LoaderVersionConstraints.matches(range, "26.2.9"), range)
            assertEquals(false, LoaderVersionConstraints.matches(range, "26.3"), range)
        }
    }

    @Test
    fun `extended SemVer compares additional numeric components and pads omitted zeros`() {
        assertEquals(true, LoaderVersionConstraints.matches(">=1.2.3.4", "1.2.3.5"))
        assertEquals(false, LoaderVersionConstraints.matches(">=1.2.3.4", "1.2.3.3"))
        assertEquals(true, LoaderVersionConstraints.matches("1.2.3.0", "1.2.3"))
        assertEquals(true, LoaderVersionConstraints.matches("1.2.3.4+platformA", "1.2.3.4+platformB"))
    }

    @Test
    fun `AND and OR still apply to extended predicates`() {
        val choices = listOf("^26.2 <26.4", "~27.1")
        assertEquals(true, LoaderVersionConstraints.matchesAny(choices, "26.3"))
        assertEquals(false, LoaderVersionConstraints.matchesAny(choices, "26.4"))
        assertEquals(true, LoaderVersionConstraints.matchesAny(choices, "27.1.9"))
        assertEquals(false, LoaderVersionConstraints.matchesAny(choices, "28.0"))
    }

    @Test
    fun `malformed Loader terms cannot be masked by valid AND or OR branches`() {
        assertEquals(null, LoaderVersionConstraints.matchesAny(listOf(">=", "^26.2"), "26.3"))
        assertEquals(null, LoaderVersionConstraints.matchesAny(listOf(">=", "27.0"), "26.3"))
        assertEquals(false, LoaderVersionConstraints.matchesAny(listOf("27.0", "^28.0"), "26.3"))
        assertEquals(null, LoaderVersionConstraints.matches(">= >=26.2", "26.3"))
        assertEquals(null, LoaderVersionConstraints.matches(">= <26.0", "26.3"))
    }

    @Test
    fun `numeric and prerelease range boundaries are ordered correctly`() {
        assertEquals(true, LoaderVersionConstraints.matches("<26.3", "26.3-rc.1"))
        assertEquals(false, LoaderVersionConstraints.matches(">=26.3", "26.3-rc.1"))
        assertEquals(true, LoaderVersionConstraints.matches(">=26.3", "26.3"))
        assertEquals(true, LoaderVersionConstraints.matches(">=26.3-", "26.3-alpha"))
        assertEquals(false, LoaderVersionConstraints.matches("<26.3-", "26.3-alpha"))
    }

    @Test
    fun `all five selected profile version predicates preserve authored values and locate contradictions`() {
        val predicates = linkedMapOf(
            "minecraft" to (">=26.2 <26.4" to ">=27"),
            "fabricloader" to (">=0.20.1 <0.21.0" to "<0.20.1"),
            "java" to (">=25 <26" to "<25"),
            "fabric-api" to (">=0.165.2" to "<0.165.2"),
            "fabric-language-kotlin" to (">=1.14.2" to "<1.14.2"),
        )
        val yaml = YamlFixtures.mod(fields = buildString {
            append("depends:\n")
            predicates.forEach { (name, ranges) -> append("  $name: \"${ranges.first}\"\n") }
        }).replace(
            "settings:\n", "settings:\n  kotlin:\n    enabled: true\n",
        )
        val model = ModYamlLoader.load(YamlFixtures.write(root, yaml))
        val json = MetadataMapper.toJson(model)
        predicates.forEach { (name, ranges) ->
            assertEquals(ranges.first, model.mod.depends[name])
            assertTrue(json.contains("\"$name\": \"${ranges.first}\""), name)
            val conflict = yaml.replace("$name: \"${ranges.first}\"", "$name: \"${ranges.second}\"")
            val error = YamlFixtures.configError("FA009") {
                ModYamlLoader.load(YamlFixtures.write(root, conflict))
            }
            assertEquals("mod.depends.$name", error.yamlPath)
        }
    }

    @Test
    fun `user authored caret predicate is retained and evaluated through YAML loader`() {
        val yaml = YamlFixtures.mod(fields = """
            depends:
              minecraft: "^26.2"
              fabricloader: ">=0.20.1"
        """)
        val model = ModYamlLoader.load(YamlFixtures.write(root, yaml))
        assertEquals("^26.2", model.mod.depends["minecraft"])
        assertTrue(MetadataMapper.toJson(model).contains("\"minecraft\": \"^26.2\""))
        val conflicting = yaml.replace("\"^26.2\"", "\"~26.2\"")
        val error = YamlFixtures.configError("FA009") {
            ModYamlLoader.load(YamlFixtures.write(root, conflicting))
        }
        assertEquals("mod.depends.minecraft", error.yamlPath)
    }

    @Test
    fun `provenance lookup preserves explicit and generated origin when supplied with authored IDs`() {
        val yaml = YamlFixtures.mod(fields = """
            depends:
              minecraft: "^26.2"
              optional_mod: "*"
        """)
        val model = ModYamlLoader.load(YamlFixtures.write(root, yaml))
        val origins = MetadataMapper.dependencyOrigins(model, setOf("minecraft", "optional_mod"))
        assertEquals("mod.depends (user)", origins["minecraft"])
        assertEquals("mod.depends (user)", origins["optional_mod"])
        assertEquals("profile (generated)", origins["java"])
        assertEquals("profile (generated)", origins["fabricloader"])
        assertEquals("profile (generated)", origins["fabric-api"])
        assertFalse(origins.containsKey("fabric-language-kotlin"))
        // The one-argument API reads the original authored keys preserved by
        // ModYamlModel, even after validation merges generated requirements.
        assertEquals("mod.depends (user)",
            MetadataMapper.dependencyOrigins(model)["minecraft"])
        assertEquals("profile (generated)",
            MetadataMapper.dependencyOrigins(model)["java"])
    }
}
