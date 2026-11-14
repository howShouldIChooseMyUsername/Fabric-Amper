package dev.fabricamper.unit

import com.google.gson.JsonParser
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.generator.MetadataMapper
import dev.fabricamper.loom.DependencyConfigurer
import dev.fabricamper.loom.GradleDependencyDeclaration
import dev.fabricamper.util.LoaderVersionConstraints
import net.fabricmc.loader.api.Version
import net.fabricmc.loader.api.metadata.version.VersionPredicate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Path

/**
 * Offline G26 regressions: selected Gradle artifacts use fixed versions, while
 * Loader metadata dependency maps retain only syntactically valid predicates.
 * Unknown sibling mods cannot be version-evaluated without a running Loader;
 * these tests assert syntax and metadata preservation, not installation.
 */
class VersionSelectorAndSiblingPredicateUnitTest {
    @TempDir lateinit var project: Path

    private fun load(yaml: String) = ModYamlLoader.load(YamlFixtures.write(project, yaml))

    @Test
    fun `selected Fabric Loader version rejects Gradle dynamic selectors with FA003 path`() {
        for (selector in listOf("0.20.+", "0.20.*", "latest.release", "[0.20,0.21)")) {
            val yaml = YamlFixtures.valid.replace("loader: \"0.20.1\"", "loader: \"$selector\"")
            val error = YamlFixtures.configError("FA003") { load(yaml) }
            assertEquals("minecraft.loader", error.yamlPath, selector)
        }
    }

    @Test
    fun `selected Fabric API version rejects Gradle dynamic selectors with FA003 path`() {
        for (selector in listOf("0.165.+", "0.165.*", "latest.release", "[0.165,0.166)")) {
            val yaml = YamlFixtures.valid.replace(
                "fabricApi: \"0.165.2+26.3\"", "fabricApi: \"$selector\"",
            )
            val error = YamlFixtures.configError("FA003") { load(yaml) }
            assertEquals("minecraft.fabricApi", error.yamlPath, selector)
        }
    }

    @Test
    fun `fixed Loader and Fabric API coordinates retain exact authored build metadata`() {
        val model = load(YamlFixtures.valid)
        assertEquals("0.20.1", model.minecraft.loader)
        assertEquals("0.165.2+26.3", model.minecraft.fabricApi)
        assertEquals(">=0.20.1", model.mod.depends["fabricloader"])
        assertEquals(">=0.165.2", model.mod.depends["fabric-api"])
        val depends = JsonParser.parseString(MetadataMapper.toJson(model))
            .asJsonObject.getAsJsonObject("depends")
        assertEquals(">=0.20.1", depends.get("fabricloader").asString)
        assertEquals(">=0.165.2", depends.get("fabric-api").asString)
    }

    @Test
    fun `invalid optional Loader predicates fail at each sibling dependency path`() {
        for ((group, id) in listOf(
            "depends" to "optional_mod",
            "recommends" to "suggested_mod",
            "conflicts" to "legacy_mod",
        )) {
            for (predicate in listOf(">=", "<=")) {
                val yaml = YamlFixtures.mod(fields = "$group:\n  $id: \"$predicate\"")
                val error = YamlFixtures.configError("FA009") { load(yaml) }
                assertEquals("mod.$group.$id", error.yamlPath, "$group: $predicate")
            }
        }
    }

    @Test
    fun `invalid OR arm cannot hide behind valid alternative in any Loader sibling map`() {
        for ((group, id) in listOf(
            "depends" to "optional_mod",
            "recommends" to "suggested_mod",
            "conflicts" to "legacy_mod",
        )) {
            val yaml = YamlFixtures.mod(fields =
                "$group:\n  $id: [\">=\", \"26.3\"]")
            val error = YamlFixtures.configError("FA009") { load(yaml) }
            assertEquals("mod.$group.$id[0]", error.yamlPath, group)
        }
    }

    @Test
    fun `invalid sibling predicate types fail FA003 at their exact map or list member`() {
        val invalid = listOf(
            Triple("depends:\n  optional_mod: {operator: \">=\"}", "mod.depends.optional_mod", "mapping"),
            Triple("recommends:\n  suggested_mod: []", "mod.recommends.suggested_mod", "empty OR"),
            Triple("conflicts:\n  legacy_mod: [\">=1.0\", 42]", "mod.conflicts.legacy_mod[1]", "numeric OR member"),
        )
        for ((fields, expectedPath, description) in invalid) {
            val error = YamlFixtures.configError("FA003") { load(YamlFixtures.mod(fields = fields)) }
            assertEquals(expectedPath, error.yamlPath, description)
        }
    }

    @Test
    fun `valid Loader sibling predicates preserve wildcard AND and OR forms in generated JSON`() {
        val yaml = YamlFixtures.mod(fields = """
            depends:
              optional_mod: [">=1.2 <2.0", "^3.0"]
            recommends:
              suggested_mod: "~1.4"
            conflicts:
              legacy_mod: "<2.0"
        """)
        val model = load(yaml)
        val json = JsonParser.parseString(MetadataMapper.toJson(model)).asJsonObject

        assertEquals(listOf(">=1.2 <2.0", "^3.0"),
            json.getAsJsonObject("depends").getAsJsonArray("optional_mod").map { it.asString })
        assertEquals("~1.4", json.getAsJsonObject("recommends").get("suggested_mod").asString)
        assertEquals("<2.0", json.getAsJsonObject("conflicts").get("legacy_mod").asString)
        assertEquals("mod.depends (user)", MetadataMapper.dependencyOrigins(model)["optional_mod"])
        assertFalse(model.dependencies.any { it.coordinate.contains("optional_mod") })
        assertTrue(model.mod.depends.containsKey("fabricloader"))
    }

    @Test
    fun `Loader accepted inline operator sequence is preserved in every sibling metadata map`() {
        // Real Fabric Loader 0.20.1 accepts this string verbatim; its evaluation
        // semantics are checked against the official API in the parity suite.
        val expression = ">=1.0 || <2.0"
        val model = load(YamlFixtures.mod(fields = """
            depends:
              optional_mod: "$expression"
            recommends:
              suggested_mod: "$expression"
            conflicts:
              legacy_mod: "$expression"
        """))
        val json = JsonParser.parseString(MetadataMapper.toJson(model)).asJsonObject
        assertEquals(expression, model.mod.depends["optional_mod"])
        assertEquals(expression, model.mod.recommends["suggested_mod"])
        assertEquals(expression, model.mod.conflicts["legacy_mod"])
        assertEquals(expression, json.getAsJsonObject("depends").get("optional_mod").asString)
        assertEquals(expression, json.getAsJsonObject("recommends").get("suggested_mod").asString)
        assertEquals(expression, json.getAsJsonObject("conflicts").get("legacy_mod").asString)
    }

    @Test
    fun `Loader accepted non trailing star opaque version survives optional metadata maps`() {
        // Loader 0.20.1 accepts this literal as an opaque exact version.
        val expression = "1.2.*.3"
        val model = load(YamlFixtures.mod(fields = """
            depends:
              optional_mod: "$expression"
            recommends:
              suggested_mod: "$expression"
            conflicts:
              legacy_mod: "$expression"
        """))
        val json = JsonParser.parseString(MetadataMapper.toJson(model)).asJsonObject
        assertEquals(expression, model.mod.depends["optional_mod"])
        assertEquals(expression, model.mod.recommends["suggested_mod"])
        assertEquals(expression, model.mod.conflicts["legacy_mod"])
        assertEquals(expression, json.getAsJsonObject("depends").get("optional_mod").asString)
        assertEquals(expression, json.getAsJsonObject("recommends").get("suggested_mod").asString)
        assertEquals(expression, json.getAsJsonObject("conflicts").get("legacy_mod").asString)
    }

    @Test
    fun `all optional Loader sibling predicates retain JSON shape and match official 0 point 20 point 1`() {
        val model = load(YamlFixtures.mod(fields = """
            depends:
              optional_mod: [">=26.2 <26.4", "1.2.*.3"]
            recommends:
              suggested_mod: ["x", "X"]
            suggests:
              extra_mod: ">=1.0 || <2.0"
            conflicts:
              legacy_mod: "<26.3"
            breaks:
              obsolete_mod: "!=1.2.3"
        """))
        val json = JsonParser.parseString(MetadataMapper.toJson(model)).asJsonObject
        val siblings = listOf(
            "depends" to ("optional_mod" to model.mod.depends.getValue("optional_mod")),
            "recommends" to ("suggested_mod" to model.mod.recommends.getValue("suggested_mod")),
            "suggests" to ("extra_mod" to model.mod.suggests.getValue("extra_mod")),
            "conflicts" to ("legacy_mod" to model.mod.conflicts.getValue("legacy_mod")),
            "breaks" to ("obsolete_mod" to model.mod.breaks.getValue("obsolete_mod")),
        )
        for ((group, entry) in siblings) {
            val (id, authored) = entry
            val expressions = when (authored) {
                is String -> listOf(authored)
                is List<*> -> authored.map { it as String }
                else -> error("unexpected $group.$id predicate type: $authored")
            }
            val generated = json.getAsJsonObject(group).get(id)
            if (authored is String) {
                assertEquals(authored, generated.asString, "$group.$id must retain its scalar form")
            } else {
                assertEquals(expressions, generated.asJsonArray.map { it.asString },
                    "$group.$id must retain its OR array form")
            }
            val official = VersionPredicate.parse(expressions)
            // Compare versions whose ordering is defined for the predicate.
            // Unknown external mod versions need not be SemVer-comparable.
            val versions = when (group) {
                "depends" -> listOf("26.3", "26.4", "1.2.*.3")
                "recommends" -> listOf("26.3", "x", "X")
                "suggests" -> listOf("1.5", "26.3")
                "conflicts" -> listOf("26.2", "26.3")
                else -> listOf("26.3", "!=1.2.3")
            }
            for (version in versions) {
                val expected = official.any { it.test(Version.parse(version)) }
                assertEquals(expected, LoaderVersionConstraints.matchesAny(expressions, version),
                    "official Loader parity for $group.$id at $version")
            }
        }
    }

    @Test
    fun `valid Loader wildcard predicates and SemVer build metadata survive all sibling maps`() {
        val yaml = YamlFixtures.mod(fields = """
            depends:
              optional_mod: ["26.3.x", "1.2.3+fabric.7"]
            recommends:
              suggested_mod: ["1.2.*", ">=1.4.0+build.5"]
            conflicts:
              legacy_mod: "<2.0.0+release.1"
        """)
        val model = load(yaml)
        val json = JsonParser.parseString(MetadataMapper.toJson(model)).asJsonObject
        assertEquals(listOf("26.3.x", "1.2.3+fabric.7"),
            json.getAsJsonObject("depends").getAsJsonArray("optional_mod").map { it.asString })
        assertEquals(listOf("1.2.*", ">=1.4.0+build.5"),
            json.getAsJsonObject("recommends").getAsJsonArray("suggested_mod").map { it.asString })
        assertEquals("<2.0.0+release.1",
            json.getAsJsonObject("conflicts").get("legacy_mod").asString)
    }

    @Test
    fun `Loader opaque bang-prefix exact version survives each sibling metadata map`() {
        // Loader 0.20.1 parses !=1.2.3 as an opaque version, NOT a != operator.
        val opaqueVersion = "!=1.2.3"
        val model = load(YamlFixtures.mod(fields = """
            depends:
              optional_mod: "$opaqueVersion"
            recommends:
              suggested_mod: "$opaqueVersion"
            conflicts:
              legacy_mod: "$opaqueVersion"
        """))
        val json = JsonParser.parseString(MetadataMapper.toJson(model)).asJsonObject
        assertEquals(opaqueVersion, json.getAsJsonObject("depends").get("optional_mod").asString)
        assertEquals(opaqueVersion, json.getAsJsonObject("recommends").get("suggested_mod").asString)
        assertEquals(opaqueVersion, json.getAsJsonObject("conflicts").get("legacy_mod").asString)
        assertEquals("mod.depends (user)", MetadataMapper.dependencyOrigins(model)["optional_mod"])
    }

    @Test
    fun `ProjectBuilder wires explicit build dependencies without turning Loader siblings into Gradle modules`() {
        val yaml = YamlFixtures.mod(fields = """
            depends:
              optional_mod: ">=1.2"
            recommends:
              suggested_mod: "*"
            conflicts:
              legacy_mod: "<2.0"
        """) + """
            dependencies:
              - "com.example:compile-lib:1.0.0": compile-only
        """.trimIndent() + "\n"
        val model = load(yaml)
        val gradle = ProjectBuilder.builder().withProjectDir(project.toFile()).build()
        gradle.pluginManager.apply("java-library")
        val declarations = model.dependencies.map { dep ->
            GradleDependencyDeclaration(dep.coordinate, dep.kind, dep.scope,
                dep.target, dep.exported, dep.include)
        }
        DependencyConfigurer.configure(gradle, model.minecraft.version, declarations, emptyList())

        assertEquals(listOf("compile-lib"),
            gradle.configurations.getByName("compileOnly").dependencies.map { it.name })
        assertTrue(gradle.configurations.getByName("implementation").dependencies.isEmpty())
        assertEquals("*", model.mod.recommends["suggested_mod"])
        assertEquals("<2.0", model.mod.conflicts["legacy_mod"])
        assertTrue(gradle.configurations.flatMap { it.dependencies }
            .none { it.name in setOf("optional_mod", "suggested_mod", "legacy_mod") })
    }
}
