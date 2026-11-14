package dev.fabricamper.unit

import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.generator.MetadataMapper
import dev.fabricamper.generator.ValidateFabricAmper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SchemaValidationUnitTest {
    @TempDir lateinit var project: Path

    private fun reject(code: String, yaml: String) =
        YamlFixtures.configError(code) {
            ModYamlLoader.load(YamlFixtures.write(project, yaml))
        }

    @Test
    fun `rejects Yarn mappings for 26 point 3 with FA004`() {
        reject("FA004", YamlFixtures.valid.replace("mappings: mojang", "mappings: yarn"))
    }

    @Test
    fun `legacy Yarn requires an explicit mapping version`() {
        val legacy = YamlFixtures.valid
            .replace("version: \"26.3\"", "version: \"1.21.11\"")
            .replace("mappings: mojang", "mappings: yarn")
        reject("FA003", legacy)
        val validLegacy = legacy.replace(
            "mappings: yarn", "mappings:\n    type: yarn\n    version: \"1.21.11+build.1\"",
        )
        assertEquals("yarn", ModYamlLoader.load(YamlFixtures.write(project, validLegacy)).minecraft.mappings.type)
    }

    @Test
    fun `Minecraft 26 point 3 requires Java release 25 with FA005`() {
        reject("FA005", YamlFixtures.valid.replace("release: 25", "release: 21"))
    }

    @Test
    fun `Kotlin entrypoint cannot be enabled without Kotlin runtime settings`() {
        val yaml = YamlFixtures.mod(fields = """
            entrypoints:
              main:
                - adapter: kotlin
                  value: example.KotlinEntrypoint
        """)
        reject("FA006", yaml)
    }

    @Test
    fun `enabled Kotlin requires matching declared Kotlin toolchain and FLK`() {
        val enabled = YamlFixtures.valid.replace(
            "settings:\n", "settings:\n  kotlin:\n    enabled: true\n",
        )
        reject("FA006", enabled.replace("enabled: true", "enabled: true\n    pluginVersion: \"2.3.0\""))
        reject("FA006", enabled.replace("enabled: true", "enabled: true\n    languageVersion: \"2.2\""))
        reject("FA006", enabled.replace(
            "enabled: true", "enabled: true\n    fabricLanguageKotlin: \"1.0.0\"",
        ))
        val valid = ModYamlLoader.load(YamlFixtures.write(project, enabled))
        assertEquals(">=1.14.2", valid.mod.depends["fabric-language-kotlin"])
    }

    @Test
    fun `invalid and duplicate provided IDs cannot escape mod ID checks`() {
        reject("FA009", YamlFixtures.mod(fields = "provides: [bad.ID]"))
        reject("FA009", YamlFixtures.mod(fields = "provides: [example_mod]"))
        reject("FA009", YamlFixtures.mod(fields = "provides: [old_mod, old_mod]"))
    }

    @Test
    fun `metadata environment has only three Loader values`() {
        for (environment in listOf("both", "CLIENT", "none")) {
            reject("FA009", YamlFixtures.mod(fields = "environment: $environment"))
        }
        val client = ModYamlLoader.load(YamlFixtures.write(project,
            YamlFixtures.mod(fields = "environment: client")))
        assertEquals("client", client.mod.environment)
        assertTrue(MetadataMapper.toJson(client).contains("\"environment\": \"client\""))
    }

    @Test
    fun `authors contributors license and icon validate expected JSON shapes`() {
        val person = YamlFixtures.mod(fields = """
            authors:
              - name: Example Author
                contact:
                  homepage: https://example.org
            contributors: [Helper]
            license: [MIT, Apache-2.0]
        """)
        val valid = ModYamlLoader.load(YamlFixtures.write(project, person))
        assertEquals(2, (valid.mod.license as List<*>).size)
        assertEquals(1, valid.mod.authors.size)
        reject("FA003", YamlFixtures.mod(fields = "license: {name: MIT}"))
        reject("FA003", YamlFixtures.mod(fields = """
            authors:
              - email: hello@example.org
        """))
    }

    @Test
    fun `boolean YAML fields are not implicitly coerced from yes or quoted true`() {
        reject("FA003", YamlFixtures.valid.replace("sourcesJar: true", "sourcesJar: yes")
            .replace("release: 25", "release: 25\n    sourcesJar: yes"))
        reject("FA003", YamlFixtures.valid.replace("release: 25", "release: 25\n    sourcesJar: \"true\""))
    }

    @Test
    fun `no custom YAML field may disable generated metadata collision failures`() {
        val yaml = YamlFixtures.valid.replace(
            "settings:\n",
            "settings:\n  resources:\n    failOnExistingFabricModJson: false\n",
        )
        reject("FA003", yaml)
    }

    @Test
    fun `Loader dependency requirement arrays cannot contain numeric or empty members`() {
        reject("FA003", YamlFixtures.mod(fields = """
            depends:
              minecraft: ["26.3", 26.2]
        """))
        reject("FA003", YamlFixtures.mod(fields = """
            depends:
              minecraft: []
        """))
    }

    @Test
    fun `unknown dependency kind and source target are rejected`() {
        val yaml = YamlFixtures.valid + """
            dependencies:
              - maven: "com.example:lib:1.0.0"
                kind: provided
            """.trimIndent() + "\n"
        reject("FA003", yaml)
        reject("FA003", yaml.replace("kind: provided", "target: common-client"))
    }

    @Test
    fun `structural validator reports missing adapter and resource validations separately`() {
        val model = ModYamlLoader.load(YamlFixtures.write(project))
        val invalid = model.copy(mod = model.mod.copy(environment = "both"))
        YamlFixtures.generatorError("FA009") { ValidateFabricAmper.validate(invalid) }
        assertFalse(MetadataMapper.toJson(model).contains("languageAdapters"))
    }
}
