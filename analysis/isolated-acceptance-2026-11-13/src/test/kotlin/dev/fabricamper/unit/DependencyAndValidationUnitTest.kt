package dev.fabricamper.unit

import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.loom.DependencyConfigurer
import dev.fabricamper.loom.GradleDependencyDeclaration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.gradle.api.GradleException
import java.nio.file.Path

class DependencyAndValidationUnitTest {
    @TempDir lateinit var project: Path

    private fun load(yaml: String) = ModYamlLoader.load(YamlFixtures.write(project, yaml))

    @Test
    fun `normalizes Maven short forms without conflating compile and jar inclusion`() {
        val model = load(
            YamlFixtures.valid + """
                dependencies:
                  - "com.example:plain:1.0.0"
                  - "com.example:provided:1.1.0": compile-only
                  - "com.example:runtime:1.2.0": runtime-only
                  - "com.example:exported:1.3.0": exported
                  - "com.example:embedded:1.4.0": include
            """.trimIndent() + "\n",
        )
        assertEquals(5, model.dependencies.size)
        val (plain, provided, runtime, exported, embedded) = model.dependencies
        assertEquals("library", plain.kind)
        assertEquals("all", plain.scope)
        assertEquals("main", plain.target)
        assertFalse(plain.include)
        assertEquals("compile-only", provided.scope)
        assertFalse(provided.exported)
        assertEquals("runtime-only", runtime.scope)
        assertTrue(exported.exported)
        assertTrue(embedded.include)
        assertEquals("all", embedded.scope)
    }

    @Test
    fun `normalizes full Maven and Modrinth objects and client target`() {
        val yaml = YamlFixtures.valid.replace(
            "        release: 25", "        release: 25",
        ) + """
            dependencies:
              - maven: "net.fabricmc.fabric-api:fabric-api:0.165.2+26.3"
                kind: mod
                scope: all
                exported: true
              - modrinth:
                  project: sodium
                  version: mc26.3-0.8.0
                target: client
                scope: compile-only
            """.trimIndent() + "\n"
        val splitYaml = yaml.replace(
            "settings:\n", "settings:\n  loom:\n    splitEnvironmentSourceSets: true\n",
        )
        val model = load(splitYaml)
        assertEquals("net.fabricmc.fabric-api:fabric-api:0.165.2+26.3", model.dependencies[0].coordinate)
        assertEquals("mod", model.dependencies[0].kind)
        assertTrue(model.dependencies[0].exported)
        assertEquals("maven.modrinth:sodium:mc26.3-0.8.0", model.dependencies[1].coordinate)
        assertEquals("mod", model.dependencies[1].kind)
        assertEquals("compile-only", model.dependencies[1].scope)
        assertEquals("client", model.dependencies[1].target)
    }

    @Test
    fun `test dependencies are normalized and cannot be exported included or client targeted`() {
        val correct = load(
            YamlFixtures.valid + """
                test-dependencies:
                  - "org.junit.jupiter:junit-jupiter:6.0.0": all
            """.trimIndent() + "\n",
        )
        assertEquals("org.junit.jupiter:junit-jupiter:6.0.0", correct.testDependencies.single().coordinate)
        for (body in listOf(
            "  - \"com.example:x:1.0\": include",
            "  - \"com.example:x:1.0\": exported",
            "  - maven: \"com.example:x:1.0\"\n    target: client",
        )) {
            val yaml = YamlFixtures.valid + "test-dependencies:\n$body\n"
            YamlFixtures.configError("FA003") { load(yaml) }
        }
    }

    @Test
    fun `rejects invalid dependency scope combination with FA007`() {
        val bad = YamlFixtures.valid + """
            dependencies:
              - maven: "com.example:bad:1.0.0"
                scope: runtime-only
                include: true
            """.trimIndent() + "\n"
        YamlFixtures.configError("FA007") { load(bad) }
        val alsoBad = bad.replace("include: true", "exported: true")
        YamlFixtures.configError("FA007") { load(alsoBad) }
    }

    @Test
    fun `rejects conflicting declarations of one coordinate and target`() {
        val yaml = YamlFixtures.valid + """
            dependencies:
              - "com.example:dupe:1.0.0": all
              - "com.example:dupe:1.0.0": compile-only
            """.trimIndent() + "\n"
        val error = YamlFixtures.configError("FA012") { load(yaml) }
        assertTrue(error.reason.contains("conflicting"))
    }

    @Test
    fun `rejects invalid or dynamic Maven coordinate versions`() {
        for (coordinate in listOf(
            "com.example:missing",
            "com.example:dynamic:1.+",
            "com.example:dynamic:latest.release",
            "https://example.org/artifact.jar",
            "com.example:extra:1.0:classifier",
        )) {
            YamlFixtures.configError("FA003") {
                load(YamlFixtures.valid + "dependencies:\n  - \"$coordinate\"\n")
            }
        }
    }

    @Test
    fun `client dependency without source splitting is FA014`() {
        val yaml = YamlFixtures.valid + """
            dependencies:
              - maven: "com.example:client:1.0.0"
                target: client
            """.trimIndent() + "\n"
        YamlFixtures.configError("FA014") { load(yaml) }
    }

    @Test
    fun `client split forbids exporting and embedding client only dependencies`() {
        val base = YamlFixtures.valid.replace(
            "settings:\n", "settings:\n  loom:\n    splitEnvironmentSourceSets: true\n",
        )
        for (unsupported in listOf("exported: true", "include: true")) {
            val yaml = base + "dependencies:\n" +
                "  - maven: \"com.example:client:1.0.0\"\n    target: client\n    $unsupported\n"
            YamlFixtures.configError("FA014") { load(yaml) }
        }
    }

    @Test
    fun `Maven custom repositories require HTTPS URLs and preserve named repositories`() {
        val validYaml = YamlFixtures.valid + """
            repositories:
              - mavenCentral
              - fabric
              - modrinth
              - maven: https://example.org/maven
              - fabric
            """.trimIndent() + "\n"
        val model = load(validYaml)
        assertEquals(listOf("mavenCentral", "fabric", "modrinth", "maven"),
            model.repositories.map { it.kind })
        YamlFixtures.configError("FA003") {
            load(validYaml.replace("https://example.org/maven", "http://example.org/maven"))
        }
        YamlFixtures.configError("FA003") {
            load(YamlFixtures.valid + "repositories:\n  - unknown\n")
        }
    }

    @Test
    fun `Gradle configuration names respect kind scope source set and remapping`() {
        fun mapped(kind: String, scope: String = "all", client: Boolean = false, remap: Boolean = false) =
            DependencyConfigurer.configurationName(
                GradleDependencyDeclaration(
                    coordinate = "com.example:mod:1.0",
                    kind = kind,
                    scope = scope,
                    target = if (client) "client" else "main",
                ),
                remapping = remap,
                test = false,
            )
        assertEquals("implementation", mapped("library"))
        assertEquals("api", DependencyConfigurer.configurationName(
            GradleDependencyDeclaration("com.example:lib:1.0", exported = true),
            remapping = false, test = false,
        ))
        assertEquals("compileOnly", mapped("library", "compile-only"))
        assertEquals("runtimeOnly", mapped("library", "runtime-only"))
        assertEquals("clientImplementation", mapped("mod", client = true))
        assertEquals("clientCompileOnly", mapped("library", "compile-only", client = true))
        assertEquals("modImplementation", mapped("mod", remap = true))
        assertEquals("modCompileOnly", mapped("mod", "compile-only", remap = true))
        assertEquals("implementation", mapped("library", remap = true))
        assertEquals("testImplementation", DependencyConfigurer.configurationName(
            GradleDependencyDeclaration("com.example:test:1.0"), remapping = false, test = true,
        ))
        assertThrows(GradleException::class.java) {
            mapped("mod", client = true, remap = true)
        }
    }
}
