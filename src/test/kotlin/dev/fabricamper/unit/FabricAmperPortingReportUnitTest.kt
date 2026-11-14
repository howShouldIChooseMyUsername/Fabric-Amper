package dev.fabricamper.unit

import dev.fabricamper.config.DependencySpec
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.config.PublishSpec
import dev.fabricamper.config.MavenSpec
import dev.fabricamper.plugin.FabricAmperPortingReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FabricAmperPortingReportUnitTest {
    @TempDir lateinit var project: Path

    private fun modern() = ModYamlLoader.load(YamlFixtures.write(project))

    private fun legacyYaml(): String = YamlFixtures.valid
        .replace("version: \"26.3\"", "version: \"1.21.11\"")
        .replace("release: 25", "release: 21")
        .replace("0.165.2+26.3", "0.100.0+1.21.11")

    @Test
    fun `legacy to modern reports precise Loom artifact Java and mod classpath changes`() {
        YamlFixtures.resource(project, "legacy.accesswidener", "accessWidener v2 named\n")
        val yaml = YamlFixtures.mod(legacyYaml(), """
            accessWidener: legacy.accesswidener
            entrypoints:
              main: [example.Main]
              client: [example.Client]
        """)
        val model = ModYamlLoader.load(YamlFixtures.write(project, yaml)).copy(
            dependencies = listOf(
                DependencySpec("mod", "org.example:mod:1.0"),
                DependencySpec("mod", "org.example:public-mod:1.0", exported = true),
                DependencySpec("mod", "org.example:runtime-mod:1.0", scope = "runtime-only"),
                DependencySpec("library", "org.example:library:1.0", scope = "compile-only"),
                DependencySpec("library", "org.example:embedded:1.0", include = true),
            ),
            testDependencies = listOf(DependencySpec("library", "org.example:test-lib:1.0", scope = "runtime-only")),
        )

        val first = FabricAmperPortingReport.render(model, "26.3")
        assertEquals(first, FabricAmperPortingReport.render(model, "26.3"))
        assertTrue(first.contains("Source Minecraft: 1.21.11 (legacy remapping)"), first)
        assertTrue(first.contains("Requested Minecraft: 26.3 (Mojang official non-remapping)"), first)
        assertTrue(first.contains("net.fabricmc.fabric-loom-remap -> net.fabricmc.fabric-loom"), first)
        assertTrue(first.contains("Production archive task: remapJar -> jar"), first)
        assertTrue(first.contains("configured source release 21 -> target fixture baseline 25"), first)
        assertTrue(first.contains("Loom official Mojang mappings dependency"), first)
        assertTrue(first.contains("modImplementation -> implementation"), first)
        assertTrue(first.contains("modApi -> api"), first)
        assertTrue(first.contains("modRuntimeOnly -> runtimeOnly"), first)
        assertTrue(first.contains("compileOnly -> compileOnly"), first)
        assertTrue(first.contains("testRuntimeOnly -> testRuntimeOnly"), first)
        assertTrue(first.contains("include=true: verify included JAR and Loader metadata in jar output"), first)
        assertTrue(first.contains("main=1, client=1, server=0, preLaunch=0, other=0"), first)
        assertTrue(first.contains("target header classTweaker v1 official"), first)
        assertTrue(first.contains("Manually migrate the access-widener/class-tweaker resource"), first)
        assertTrue(first.contains("target mod/API compatibility is UNKNOWN"), first)
        assertFalse(first.contains("org.example:"), "Coordinates must not be mistaken for target-compatible artifacts")
    }

    @Test
    fun `modern to legacy identifies unsupported client target and legacy format requirements`() {
        val model = modern().copy(
            settings = modern().settings.copy(
                loom = modern().settings.loom.copy(splitEnvironmentSourceSets = true),
            ),
            dependencies = listOf(
                DependencySpec("mod", "maven.modrinth:client-mod:1.0", target = "client"),
                DependencySpec("library", "org.example:local:1.0", exported = true),
            ),
        )
        val report = FabricAmperPortingReport.render(model, "1.21.11")

        assertTrue(report.contains("net.fabricmc.fabric-loom -> net.fabricmc.fabric-loom-remap"), report)
        assertTrue(report.contains("Production archive task: jar -> remapJar"), report)
        assertTrue(report.contains("configured source release 25 -> target fixture baseline 21"), report)
        assertTrue(report.contains("UNSUPPORTED (client dependency with legacy remapping Loom)"), report)
        assertTrue(report.contains("legacy profile (FA014)"), report)
        assertTrue(report.contains("api -> api"), report)
        assertTrue(report.contains("target resource extension .accesswidener"), report)
        assertTrue(report.contains("target header accessWidener v2 named (or v2 intermediary; v1 also accepted)"), report)
        assertTrue(report.contains("select explicit official Mojang or version-matched Yarn mappings dependency"), report)
        assertTrue(report.contains("Recheck the target-specific Fabric Loader and Fabric API artifacts"), report)
    }

    @Test
    fun `same version still requires manual code Mixins Kotlin and runtime review`() {
        val model = modern().copy(
            settings = modern().settings.copy(kotlin = modern().settings.kotlin.copy(enabled = true)),
            mod = modern().mod.copy(
                entrypoints = mapOf("main" to listOf(dev.fabricamper.config.EntrypointSpec("example.Main", "kotlin"))),
                mixins = listOf(dev.fabricamper.config.MixinSpec("example.mixins.json")),
            ),
        )
        val report = FabricAmperPortingReport.render(model, "26.3")

        assertTrue(report.contains("net.fabricmc.fabric-loom -> net.fabricmc.fabric-loom"), report)
        assertTrue(report.contains("Recheck target game and API behavior even when the build profile is unchanged"), report)
        assertTrue(report.contains("Kotlin entrypoint adapters declared: 1"), report)
        assertTrue(report.contains("Kotlin: enabled; review Kotlin Gradle plugin/toolchain"), report)
        assertTrue(report.contains("Mixin configurations: 1; manually review mapping names"), report)
        assertTrue(report.contains("start an isolated client and dedicated server"), report)
        assertTrue(report.contains("https://docs.fabricmc.net/develop/porting/mappings/index"), report)
        assertTrue(report.contains("https://fabricmc.net/2026/09/15/263.html"), report)
        assertFalse(report.contains("automatic fix"), report)
    }

    @Test
    fun `rejects unknown or malformed requested versions and unknown source without reflecting input`() {
        val model = modern()
        listOf("26.1", "26.2", "26.4", "27.1", "1.21.10", "26.3-pre1", "", "26.3\nPRIVATE_TOKEN").forEach { requested ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                FabricAmperPortingReport.render(model, requested)
            }
            assertTrue(error.message.orEmpty().contains("Unsupported target Minecraft version"), error.message)
            assertFalse(error.message.orEmpty().contains("PRIVATE_TOKEN"), "Never repeat untrusted target input")
        }
        val sourceError = assertThrows(IllegalArgumentException::class.java) {
            FabricAmperPortingReport.render(
                model.copy(minecraft = model.minecraft.copy(version = "26.2")), "26.3",
            )
        }
        assertTrue(sourceError.message.orEmpty().contains("Unsupported source Minecraft version"), sourceError.message)
    }

    @Test
    fun `report preserves source bytes and excludes paths publishing secrets and remote target claims`() {
        val yamlFile = YamlFixtures.write(project)
        val before = Files.readAllBytes(yamlFile)
        val model = ModYamlLoader.load(yamlFile).copy(
            publish = PublishSpec(maven = MavenSpec(
                url = "https://private.example.invalid/secret", credentialsFromEnv = listOf("SECRET_USER", "SECRET_PASS"),
            )),
            dependencies = listOf(DependencySpec("library", "private.example:internal-token-artifact:1.0")),
        )

        val report = FabricAmperPortingReport.render(model, "1.21.11")
        assertTrue(Files.readAllBytes(yamlFile).contentEquals(before))
        assertFalse(report.contains(project.toString()), report)
        for (secret in listOf("private.example", "SECRET_USER", "SECRET_PASS", "internal-token-artifact")) {
            assertFalse(report.contains(secret), "The report must not reveal unrelated model fields")
        }
        assertTrue(report.contains("No source, YAML, Gradle settings, caches or remote state were modified."), report)
    }
}
