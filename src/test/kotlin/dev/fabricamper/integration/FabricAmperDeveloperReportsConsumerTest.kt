package dev.fabricamper.integration

import com.google.gson.JsonParser
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Real settings-only TestKit consumers of the opt-in developer reports. The
 * release fixture only PREVIEWS a fake Modrinth destination. No task in this
 * test may publish/upload, launch Minecraft, or change a global Maven cache.
 * Normal Loom/Fabric game build resolution is permitted during initial warm-up;
 * every report invocation is strictly --offline.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
class FabricAmperDeveloperReportsConsumerTest {
    @TempDir lateinit var suiteRoot: Path

    @Test
    fun `modern settings-only reports reuse configuration cache and preview verified real jar`() {
        val f = TestKitFixture(suiteRoot)
        val yaml = f.resource("minimal-mod.yaml")
            .replace(
                "repositories:\n",
                """
                dependencies:
                  - maven: "com.google.code.gson:gson:2.14.0"
                    scope: compile-only
                repositories:
                """.trimIndent() + "\n",
            ) + """
            publish:
              modrinth:
                projectId: "offline-preview-only"
                tokenEnv: FABRIC_AMPER_REPORT_FAKE_TOKEN_ENV
                releaseType: beta
                gameVersions: ["26.3"]
        """.trimIndent() + "\n"
        val root = f.project("developer-report-modern", yaml)
        f.write(root, "src/main/java/org/example/TestkitEntry.java", """
            package org.example;
            public final class TestkitEntry implements net.fabricmc.api.ModInitializer {
                @Override public void onInitialize() {}
            }
        """.trimIndent() + "\n")
        assertSettingsOnly(f, root, yaml)

        // A separate normal help/configuration run primes Loom's local files so
        // report actions can be checked with --offline and strict CC reuse.
        val warm = f.run(root, "help")
        assertEquals(TaskOutcome.SUCCESS, warm.task(":help")?.outcome, warm.output)
        assertNoPublishActions(warm)

        val reportArgs = arrayOf(
            "fabricAmperDoctor", "fabricAmperExplain", "fabricAmperPortingReport",
            "-PfabricAmperPortTarget=1.21.11", "--configuration-cache", "--offline",
        )
        val first = f.run(root, *reportArgs)
        assertReportTasks(first)
        assertTrue(first.output.contains("Fabric Amper doctor"), first.output)
        assertTrue(first.output.contains("Minecraft: 26.3 (profile: non-remapping"), first.output)
        assertTrue(first.output.contains("Fabric Amper dependency report"), first.output)
        assertTrue(first.output.contains("coordinate=\"com.google.code.gson:gson:2.14.0\""), first.output)
        assertTrue(first.output.contains("scope=compile-only, target=main"), first.output)
        assertTrue(first.output.contains("fabric-api: \">=0.165.2\"; origin=generated"), first.output)
        assertTrue(first.output.contains("Fabric Amper porting report"), first.output)
        assertTrue(first.output.contains("Source Minecraft: 26.3"), first.output)
        assertTrue(first.output.contains("Requested Minecraft: 1.21.11"), first.output)
        assertTrue(first.output.contains("Production archive task: jar -> remapJar"), first.output)
        assertTrue(first.output.contains("target fixture baseline 21"), first.output)
        assertFalse(first.output.contains("FABRIC_AMPER_REPORT_FAKE_TOKEN_ENV"), first.output)

        val second = f.run(root, *reportArgs)
        assertReportTasks(second)
        assertTrue(second.output.contains("Reusing configuration cache"), second.output)
        assertTrue(second.output.contains("Requested Minecraft: 1.21.11"), second.output)

        // A real consumer build warms the compiled game artifacts. The preview
        // itself must not attempt any repository access or publishing action.
        val build = f.run(root, "build")
        assertEquals(TaskOutcome.SUCCESS, build.task(":build")?.outcome, build.output)
        assertEquals(TaskOutcome.SUCCESS, build.task(":compileJava")?.outcome, build.output)
        assertNoPublishActions(build)
        val jar = f.jars(root).single()
        assertEquals("testkit-mod-1.0.0+26.3.jar", jar.fileName.toString())
        val (rootCount, loaderBytes) = f.jarRootJson(jar)
        assertEquals(1, rootCount, "Final JAR must have one root fabric.mod.json")
        assertArrayEquals(Files.readAllBytes(f.generatedJson(root)), loaderBytes)
        val metadata = JsonParser.parseString(String(loaderBytes, StandardCharsets.UTF_8)).asJsonObject
        assertEquals("testkit_mod", metadata.get("id").asString)
        assertEquals("1.0.0+26.3", metadata.get("version").asString)
        ZipFile(jar.toFile()).use { zip ->
            assertNotNull(zip.getEntry("org/example/TestkitEntry.class"), "Real compiled consumer class is missing")
        }

        val preview = f.run(root, "fabricAmperReleasePlan", "--offline")
        assertEquals(TaskOutcome.SUCCESS, preview.task(":fabricAmperReleasePlan")?.outcome, preview.output)
        assertNotNull(preview.task(":verifyFabricAmperJar"),
            "Release plan must depend on real JAR verification:\n${preview.output}")
        assertNoPublishActions(preview)
        val release = parseReleasePlan(preview)
        assertEquals("OFFLINE_PREVIEW_ONLY", release.get("mode").asString)
        assertEquals(0, release.get("remoteRequests").asInt)
        val artifact = release.getAsJsonObject("artifact")
        assertEquals(jar.fileName.toString(), artifact.get("filename").asString)
        assertEquals(Files.size(jar), artifact.get("sizeBytes").asLong)
        assertEquals(sha256(jar), artifact.get("sha256").asString)
        assertEquals(1, artifact.get("rootFabricModJsonEntries").asInt)
        assertEquals(metadata.get("id").asString, artifact.get("loaderId").asString)
        assertEquals(metadata.get("version").asString, artifact.get("loaderVersion").asString)
        assertEquals("26.3", release.getAsJsonObject("mod").get("minecraftVersion").asString)
        val targets = release.getAsJsonArray("targets")
        assertEquals(1, targets.size(), "Only the fake opt-in Modrinth preview is configured")
        val target = targets[0].asJsonObject
        assertEquals("modrinth", target.get("platform").asString)
        assertEquals("CONFIGURED_NOT_EXECUTED", target.get("status").asString)
        assertFalse(target.get("externalProjectAndVersionsVerified").asBoolean)
        assertEquals("offline-preview-only", target.getAsJsonObject("metadataWithoutChangelog")
            .get("project_id").asString)
        assertFalse(preview.output.contains("FABRIC_AMPER_REPORT_FAKE_TOKEN_ENV"), preview.output)
        assertFalse(Files.exists(root.resolve("build/fabric-amper/maven-repository")),
            "A release preview must not publish locally")
        assertSettingsOnly(f, root, yaml)
    }

    @Test
    fun `valid Minecraft 26_2 can show ordinary tasks but explicit unsupported port report fails`() {
        val f = TestKitFixture(suiteRoot)
        // This source version is valid for Amper's modern/non-remap profile,
        // while FabricAmperPortingReport is deliberately scoped to 1.21.11/26.3.
        // Use an actually published 26.2 Fabric API artifact rather than a
        // made-up 26.3 artifact with its suffix changed.
        val yaml = f.resource("minimal-mod.yaml")
            .replace("version: \"1.0.0+26.3\"", "version: \"1.0.0+26.2\"")
            .replace("version: \"26.3\"", "version: \"26.2\"")
            .replace("fabricApi: \"0.165.2+26.3\"", "fabricApi: \"0.161.0+26.2\"")
        val root = f.project("developer-report-unscoped-26-2", yaml)
        assertSettingsOnly(f, root, yaml)

        val help = f.run(root, "help")
        assertEquals(TaskOutcome.SUCCESS, help.task(":help")?.outcome, help.output)
        assertNoPublishActions(help)
        val tasks = f.run(root, "tasks", "--all", "--offline")
        assertEquals(TaskOutcome.SUCCESS, tasks.task(":tasks")?.outcome, tasks.output)
        for (name in listOf("fabricAmperDoctor", "fabricAmperExplain", "fabricAmperPortingReport", "fabricAmperReleasePlan")) {
            assertTrue(Regex("(?m)^${Regex.escape(name)}\\b").containsMatchIn(tasks.output),
                "Ordinary task listing must expose $name:\n${tasks.output}")
        }
        assertNoPublishActions(tasks)

        val unsupported = f.fail(root, "fabricAmperPortingReport", "-PfabricAmperPortTarget=26.3", "--offline")
        assertTrue(unsupported.output.contains("Unsupported source Minecraft version for the porting report"),
            unsupported.output)
        assertTrue(unsupported.output.contains("Only 1.21.11 and 26.3"), unsupported.output)
        assertTrue(unsupported.output.contains("BUILD FAILED"), unsupported.output)
        assertNoPublishActions(unsupported)
        assertSettingsOnly(f, root, yaml)
    }

    private fun assertReportTasks(result: BuildResult) {
        for (task in listOf("fabricAmperDoctor", "fabricAmperExplain", "fabricAmperPortingReport")) {
            assertEquals(TaskOutcome.SUCCESS, result.task(":$task")?.outcome, result.output)
        }
        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        assertNoPublishActions(result)
    }

    private fun assertNoPublishActions(result: BuildResult) {
        assertFalse(result.tasks.any { it.path.substringAfterLast(':').startsWith("publish", ignoreCase = true) },
            "A developer report, help, or build must never run a publishing task:\n${result.output}")
    }

    private fun parseReleasePlan(result: BuildResult) = run {
        val output = result.output.replace("\r\n", "\n")
        val start = output.indexOf("{\n  \"title\": \"Fabric Amper RELEASE PLAN\"")
        assertTrue(start >= 0, "Release preview must print a JSON report:\n$output")
        val end = output.indexOf("\n}\n", start)
        assertTrue(end > start, "Release JSON closing brace is missing:\n$output")
        JsonParser.parseString(output.substring(start, end + 2)).asJsonObject
    }

    private fun sha256(file: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(file))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun assertSettingsOnly(f: TestKitFixture, project: Path, originalYaml: String) {
        f.assertNoConsumerBuildScript(project)
        assertEquals(originalYaml, f.yaml(project), "Report tasks must not change the YAML input")
        assertFalse(Files.exists(project.resolve("src/main/resources/fabric.mod.json")),
            "Loader metadata must remain generated")
        assertFalse(Files.exists(project.resolve("src/client/resources/fabric.mod.json")))
    }
}
