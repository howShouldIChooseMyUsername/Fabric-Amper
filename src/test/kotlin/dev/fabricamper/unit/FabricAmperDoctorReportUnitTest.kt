package dev.fabricamper.unit

import dev.fabricamper.config.JavaSettings
import dev.fabricamper.config.KotlinSettings
import dev.fabricamper.config.MappingSpec
import dev.fabricamper.config.MavenSpec
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.config.PublishSpec
import dev.fabricamper.loom.LoomPluginSelector
import dev.fabricamper.plugin.FabricAmperDoctorReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class FabricAmperDoctorReportUnitTest {
    @TempDir lateinit var projectRoot: Path

    private fun model() = ModYamlLoader.load(YamlFixtures.write(projectRoot))

    @Test
    fun `checked modern profile report is deterministic and never leaks credentials or root path`() {
        val model = model().copy(
            publish = PublishSpec(
                maven = MavenSpec(
                    url = "https://private.invalid/repository",
                    credentialsFromEnv = listOf("PRIVATE_USERNAME_ENV", "PRIVATE_PASSWORD_ENV"),
                ),
            ),
        )
        val render = {
            FabricAmperDoctorReport.render(
                model = model,
                gradleVersion = "9.7.0",
                jvmVersion = "25.0.4+1",
                projectRoot = projectRoot,
                loomPluginId = LoomPluginSelector.NON_REMAPPING_ID,
                loomVersion = "1.18.4",
                osName = "Windows 11",
            )
        }
        val report = render()
        assertEquals(report, render())
        assertTrue(report.startsWith("Fabric Amper doctor — offline, read-only"), report)
        assertTrue(report.contains("Minecraft: 26.3 (profile: non-remapping; mappings: mojang)"), report)
        assertTrue(report.contains("[CHECKED] This Gradle/Loom pairing"), report)
        assertTrue(report.contains("[CHECK] Running JVM feature 25 meets the configured Java target (25)"), report)
        assertTrue(report.contains("Kotlin: disabled; client source sets: combined"), report)
        assertTrue(report.contains("may still download Kotlin Gradle plugin artifacts"), report)
        assertTrue(report.contains("Amper metadata mirror"), report)
        assertFalse(report.contains(projectRoot.toString()), report)
        for (secret in listOf("PRIVATE_USERNAME_ENV", "PRIVATE_PASSWORD_ENV", "private.invalid")) {
            assertFalse(report.contains(secret), report)
        }
    }

    @Test
    fun `conflicting profile detects Loom mappings Java API version and verified bad variant`() {
        val model = model().let { original ->
            original.copy(
                minecraft = original.minecraft.copy(
                    mappings = MappingSpec(type = "yarn", version = "26.3+build.1"),
                    fabricApi = "0.165.2+26.1",
                ),
                settings = original.settings.copy(java = JavaSettings(release = 21)),
            )
        }
        val longRoot = projectRoot.resolve("nested-" + "x".repeat(175))
        val report = FabricAmperDoctorReport.render(
            model, "9.7.0", "17.0.10", longRoot,
            LoomPluginSelector.REMAPPING_ID, "1.19.4", "Windows 10",
        )
        assertTrue(report.contains("[MISMATCH] Loom plugin flavor"), report)
        assertTrue(report.contains("[MISMATCH] minecraft.mappings"), report)
        assertTrue(report.contains("[MISMATCH] settings.java.release"), report)
        assertTrue(report.contains("[MISMATCH] Running JVM feature 17"), report)
        assertTrue(report.contains("[MISMATCH] minecraft.fabricApi"), report)
        assertTrue(report.contains("[MISMATCH] Gradle 9.7.0 + published Loom 1.19.4"), report)
        assertTrue(report.contains("[WARN] Windows path-length risk"), report)
        assertTrue(report.contains("do not clear global caches"), report)
        assertFalse(report.contains(longRoot.toString()), report)
    }

    @Test
    fun `unknown compatibility and unparseable JVM stay unknown without invented checks`() {
        val report = FabricAmperDoctorReport.render(
            model(), "9.10.0", "unrecognized-jvm", Path.of("relative-root"),
            LoomPluginSelector.NON_REMAPPING_ID, null, "Windows 11",
        )
        assertTrue(report.contains("Loom version: unknown"), report)
        assertTrue(report.contains("[UNKNOWN] Running JVM feature cannot be parsed"), report)
        assertTrue(report.contains("[UNKNOWN] No exact consumer build verification"), report)
        assertTrue(report.contains("[UNKNOWN] Windows project root is relative"), report)
        assertFalse(report.contains("[CHECKED]"), report)
        assertFalse(report.contains("relative-root"), report)
    }

    @Test
    fun `legacy remapping profile and enabled Kotlin remain explicit but not implicitly verified`() {
        val original = model()
        val legacy = original.copy(
            minecraft = original.minecraft.copy(
                version = "1.21.11",
                mappings = MappingSpec(type = "yarn", version = "1.21.11+build.3"),
                fabricApi = null,
            ),
            settings = original.settings.copy(
                java = JavaSettings(release = 21),
                kotlin = KotlinSettings(enabled = true),
            ),
        )
        val report = FabricAmperDoctorReport.render(
            legacy, "9.7.1", "25.0.4", projectRoot,
            LoomPluginSelector.REMAPPING_ID, "1.17.21", "Linux",
        )
        assertTrue(report.contains("Minecraft: 1.21.11 (profile: remapping; mappings: yarn)"), report)
        assertTrue(report.contains("declared Fabric API: not configured"), report)
        assertTrue(report.contains("[CHECK] Loom plugin flavor"), report)
        assertTrue(report.contains("Kotlin: enabled"), report)
        assertTrue(report.contains("[CHECK] Kotlin versions match"), report)
        assertTrue(report.contains("[UNKNOWN] No exact consumer build verification"), report)
        assertTrue(report.contains("Windows-specific path length risk: not applicable"), report)
        assertFalse(report.contains("[MISMATCH]"), report)
    }

    @Test
    fun `Kotlin version mismatch is detected while partial variant verification remains partial`() {
        val original = model()
        val modified = original.copy(settings = original.settings.copy(
            kotlin = KotlinSettings(enabled = true, pluginVersion = "2.1.0"),
        ))
        val report = FabricAmperDoctorReport.render(
            modified, "9.8.0", "25-ea", projectRoot,
            LoomPluginSelector.NON_REMAPPING_ID, "1.19.4", "Linux",
        )
        assertTrue(report.contains("[MISMATCH] settings.kotlin"), report)
        assertTrue(report.contains("[PARTIAL] Gradle 9.8.0 + Loom 1.19.4"), report)
        assertFalse(report.contains("[CHECKED]"), report)
    }

    @Test
    fun `legacy Java eight runtime feature is compared numerically to Java twenty one target`() {
        val original = model()
        val legacy = original.copy(
            minecraft = original.minecraft.copy(version = "1.21.11"),
            settings = original.settings.copy(java = JavaSettings(release = 21)),
        )
        val report = FabricAmperDoctorReport.render(
            legacy, "9.7.1", "1.8.0_442", projectRoot,
            LoomPluginSelector.REMAPPING_ID, null, "Linux",
        )
        assertTrue(report.contains("[MISMATCH] Running JVM feature 8 is older than configured Java target 21"), report)
    }
}
