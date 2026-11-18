package dev.fabricamper.integration

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Real Gradle configuration wiring for schema 1.1 root dependency.loader. */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
class FabricAmperRootLoaderDependenciesConsumerTest {
    @TempDir lateinit var root: Path

    @Test
    fun `root loader versions stay isolated across Fabric NeoForge and optional common Architectury`() {
        val fixture = TestKitFixture(root)
        for (withArchitectury in listOf(false, true)) {
            val yaml = dualYaml(withArchitectury = withArchitectury, dependencies = """
                - maven: "org.apache.commons:commons-lang3:3.14.0"
                  loader: fabric
                - maven: "org.apache.commons:commons-lang3:3.15.0"
                  loader: neoforge
                - maven: "org.apache.commons:commons-text:1.12.0"
            """)
            val project = fixture.project("root-deps-arch-$withArchitectury", yaml)
            Files.createDirectories(project.resolve("fabric/src/main/java"))
            Files.createDirectories(project.resolve("neoforge/src/main/java"))
            if (withArchitectury) Files.createDirectories(project.resolve("common/src/main/java"))
            fixture.assertNoConsumerBuildScript(project)

            val fabric = report(fixture, project, ":fabric", "implementation")
            assertContains(fabric, "org.apache.commons:commons-lang3:3.14.0")
            assertAbsent(fabric, "org.apache.commons:commons-lang3:3.15.0")
            assertContains(fabric, "org.apache.commons:commons-text:1.12.0")

            val neo = report(fixture, project, ":neoforge", "implementation")
            assertContains(neo, "org.apache.commons:commons-lang3:3.15.0")
            assertAbsent(neo, "org.apache.commons:commons-lang3:3.14.0")
            assertContains(neo, "org.apache.commons:commons-text:1.12.0")

            if (withArchitectury) {
                val common = report(fixture, project, ":common", "implementation")
                assertContains(common, "org.apache.commons:commons-text:1.12.0")
                assertAbsent(common, "org.apache.commons:commons-lang3:3.14.0")
                assertAbsent(common, "org.apache.commons:commons-lang3:3.15.0")
                assertContains(report(fixture, project, ":common", "api"),
                    "dev.architectury:architectury:22.0.5")
            } else {
                assertFalse(Files.exists(project.resolve("common")), "Independent dual has no common module")
            }
            fixture.assertNoConsumerBuildScript(project)
        }
    }

    @Test
    fun `root Fabric include is Loom only and not NeoForge JarJar or common`() {
        val fixture = TestKitFixture(root)
        val yaml = dualYaml(withArchitectury = false, dependencies = """
            - maven: "org.apache.commons:commons-lang3:3.14.0"
              loader: fabric
              kind: library
              include: true
            - maven: "org.apache.commons:commons-text:1.12.0"
        """)
        val project = fixture.project("root-deps-fabric-jij", yaml, alias = "mod.yml")
        Files.createDirectories(project.resolve("fabric/src/main/java"))
        Files.createDirectories(project.resolve("neoforge/src/main/java"))
        fixture.assertNoConsumerBuildScript(project)

        val fabricInclude = report(fixture, project, ":fabric", "include")
        assertContains(fabricInclude, "org.apache.commons:commons-lang3:3.14.0")
        val neoJarJar = report(fixture, project, ":neoforge", "jarJar")
        assertAbsent(neoJarJar, "org.apache.commons:commons-lang3:3.14.0")
        assertFalse(Files.exists(project.resolve("common")), "Independent dual must not create :common")
        fixture.assertNoConsumerBuildScript(project)
    }

    private fun report(fixture: TestKitFixture, project: Path, path: String, configuration: String): String {
        val result: BuildResult = fixture.run(project, "$path:dependencies", "--configuration", configuration)
        assertEquals(TaskOutcome.SUCCESS, result.task("$path:dependencies")?.outcome, result.output)
        return result.output
    }

    private fun assertContains(report: String, coordinate: String) =
        assertTrue(report.contains(coordinate), "Expected $coordinate in the selected configuration:\n$report")

    private fun assertAbsent(report: String, coordinate: String) =
        assertFalse(report.contains(coordinate), "Unexpected $coordinate in another loader configuration:\n$report")

    private fun dualYaml(withArchitectury: Boolean, dependencies: String): String = buildString {
        appendLine("schema: \"1.1\"")
        appendLine("product: [fabric/mod, neoforge/mod]")
        appendLine("mod:")
        appendLine("  id: rootloaderprobe")
        appendLine("  version: \"1.0.0+26.3\"")
        appendLine("  group: org.example")
        appendLine("  name: Root Loader Dependency Probe")
        appendLine("  license: MIT")
        appendLine("minecraft:")
        appendLine("  version: \"26.3\"")
        appendLine("settings:")
        appendLine("  java:")
        appendLine("    release: 25")
        appendLine("fabric:")
        appendLine("  loader: \"0.20.1\"")
        appendLine("  api: \"0.165.2+26.3\"")
        appendLine("  mappings: mojang")
        appendLine("neoforge:")
        appendLine("  version: \"26.3.0.101\"")
        appendLine("  modLoader: javafml")
        if (withArchitectury) {
            appendLine("architectury:")
            appendLine("  enable: true")
        }
        appendLine("repositories: [mavenCentral, fabric, neoforge]")
        appendLine("dependencies:")
        append(dependencies.trimIndent().prependIndent("  "))
        appendLine()
    }
}
