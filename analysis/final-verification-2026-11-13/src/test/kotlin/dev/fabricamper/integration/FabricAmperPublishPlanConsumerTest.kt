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
import java.util.zip.ZipFile

/**
 * README 2026-11-13 unified publish contract: configuration and task-plan
 * acceptance only. NO remote Maven publish or Modrinth/CurseForge HTTP call.
 * A Gradle --dry-run does not execute a publishing task's action.
 *
 * The test deliberately uses an .invalid Maven endpoint and dummy remote
 * project identifiers. Environment variable names are not secret values.
 * Standard Gradle/Loom dependency downloads may still occur for real builds.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_PUBLISH_PLAN", matches = "true")
class FabricAmperPublishPlanConsumerTest {
    @TempDir lateinit var suiteRoot: Path

    private val targets = listOf(
        "publishFabricAmperMaven",
        "publishFabricAmperModrinth",
        "publishFabricAmperCurseForge",
    )
    private val allPublishTasks = targets + "publishFabricAmper"

    @Test
    fun modTasksSafeWithoutTokens() = verifyProduct("fabric/mod", "mod.yaml")

    @Test
    fun apiTasksSafeWithoutTokens() = verifyProduct("fabric/api", "mod.yaml")

    @Test
    fun libraryAliasTasksSafeWithoutTokens() = verifyProduct("fabric/library", "mod.yml")

    @Test
    fun modPlatformOnlyWithoutMavenGroupExposesSafePublishLifecycle() {
        val f = TestKitFixture(suiteRoot)
        val yaml = """
            schema: 1
            product: fabric/mod
            mod:
              id: amper_platform_only
              version: "0.1.0+26.3"
              name: Platform-only Publish Acceptance
              environment: "*"
              entrypoints:
                main:
                  - org.example.platformonly.PlatformOnlyEntry
            minecraft:
              version: "26.3"
              mappings: mojang
              loader: "0.20.1"
            settings:
              java:
                release: 25
                sourcesJar: false
              kotlin:
                enabled: false
            repositories:
              - fabric
              - mavenCentral
            publish:
              modrinth:
                projectId: "offline-platform-plan-only"
                tokenEnv: FABRIC_AMPER_PLATFORM_ONLY_MODRINTH_TOKEN_NOT_SET
                changelog: "Task graph fixture. Never upload."
              curseforge:
                projectId: 99999999
                tokenEnv: FABRIC_AMPER_PLATFORM_ONLY_CURSEFORGE_TOKEN_NOT_SET
                changelog: "Task graph fixture. Never upload."
        """.trimIndent() + "\n"
        assertFalse(yaml.contains("group:"), "Platform-only mod must not author mod.group")
        assertFalse(yaml.contains("maven:"), "Platform-only mod must not author publish.maven")
        val root = f.project("publish-plan-platform-only", yaml)
        f.write(root, "src/main/java/org/example/platformonly/PlatformOnlyEntry.java", """
            package org.example.platformonly;
            import net.fabricmc.api.ModInitializer;
            public final class PlatformOnlyEntry implements ModInitializer {
                @Override public void onInitialize() {}
            }
        """.trimIndent() + "\n")
        assertOnlySettingsAndYaml(f, root, "mod.yaml")

        val help = f.run(root, "help")
        assertEquals(TaskOutcome.SUCCESS, help.task(":help")?.outcome, help.output)
        assertNoPublishTaskExecuted(help, "platform-only help")
        assertNoLocalPublication(root)

        val listing = f.run(root, "tasks", "--all")
        assertEquals(TaskOutcome.SUCCESS, listing.task(":tasks")?.outcome, listing.output)
        assertNoPublishTaskExecuted(listing, "platform-only tasks --all")
        val platformTasks = listOf("publishFabricAmperModrinth", "publishFabricAmperCurseForge")
        val selectedTargets = platformTasks + listOf("publishFabricAmper", "publish")
        for (name in selectedTargets) {
            assertTrue(listing.output.lineSequence().any { line ->
                val item = line.trimStart()
                item == name || item.startsWith("$name ") || item.startsWith("$name\t")
            }, "The platform-only mod must expose $name:\n" + listing.output)
        }
        val unwantedMavenTasks = Regex(
            """(?m)^\s*(publishFabricAmperMaven|publishMavenJava|publishToMavenLocal|generatePomFileForMavenJavaPublication)\b"""
        )
        assertFalse(unwantedMavenTasks.containsMatchIn(listing.output),
            "Platform-only publishing must not apply any Maven publish task:\n" + listing.output)
        assertNoLocalPublication(root)

        val build = f.run(root, "build")
        assertEquals(TaskOutcome.SUCCESS, build.task(":build")?.outcome, build.output)
        assertEquals(TaskOutcome.SUCCESS, build.task(":compileJava")?.outcome, build.output)
        assertEquals(TaskOutcome.SUCCESS, build.task(":jar")?.outcome, build.output)
        assertNotNull(build.task(":verifyFabricAmperJar"))
        assertNoPublishTaskExecuted(build, "platform-only build")
        val jar = f.jars(root).single()
        val (count, bytes) = f.jarRootJson(jar)
        assertEquals(1, count, "The real final mod JAR must have exactly one root Loader JSON")
        assertArrayEquals(Files.readAllBytes(f.generatedJson(root)), bytes)
        val loader = JsonParser.parseString(String(bytes, StandardCharsets.UTF_8)).asJsonObject
        assertEquals("amper_platform_only", loader.get("id").asString)
        assertFalse(loader.has("publish"), "External upload settings must not leak into Loader JSON")
        ZipFile(jar.toFile()).use { zip ->
            assertNotNull(zip.getEntry("org/example/platformonly/PlatformOnlyEntry.class"))
        }
        assertNoLocalPublication(root)

        // All platform publishing and lifecycle tasks are invoked ONLY with
        // --dry-run. Check the actual console graph, because GradleRunner
        // BuildResult.tasks can be empty in dry-run mode.
        for (target in selectedTargets) {
            val plan = f.run(root, target, "--dry-run")
            assertDryRunGraph(plan, target, "fabric/mod without Maven")
            val paths = dryRunTaskPaths(plan)
            val verify = paths.indexOf(":verifyFabricAmperJar")
            val targetIndex = paths.indexOf(":$target")
            if (target == "publishFabricAmper" || target == "publish") {
                for (platform in platformTasks) {
                    val index = paths.indexOf(":$platform")
                    assertTrue(index > verify && index < targetIndex,
                        "$target must include verified, skipped $platform upload:\n" + plan.output)
                }
            }
            if (target == "publish") {
                val aggregate = paths.indexOf(":publishFabricAmper")
                assertTrue(aggregate > verify && aggregate < targetIndex,
                    "The lifecycle publish task must depend on the aggregate:\n" + plan.output)
            }
            assertFalse(paths.any {
                it.contains("Maven", ignoreCase = true) || it.contains("ToMavenLocal")
            }, "No Maven publication task belongs in the platform-only graph:\n" + plan.output)
            assertNoLocalPublication(root)
            assertOnlySettingsAndYaml(f, root, "mod.yaml")
        }
        assertEquals(yaml, Files.readString(root.resolve("mod.yaml")))
    }

    private fun verifyProduct(product: String, yamlAlias: String) {
        val f = TestKitFixture(suiteRoot)
        val yaml = f.resource("runtime/publish-plan-only.yaml").replace("__PRODUCT__", product)
        assertTrue(yaml.contains("product: $product"))
        assertTrue(yaml.contains("https://example.invalid/"),
            "The test may not use a production Maven upload URL")
        val root = f.project("publish-plan-" + product.substringAfter('/'), yaml, alias = yamlAlias)
        f.write(root, "src/main/java/org/example/publishplan/PublishPlanMod.java", """
            package org.example.publishplan;
            import net.fabricmc.api.ModInitializer;
            public final class PublishPlanMod implements ModInitializer {
                @Override public void onInitialize() {}
            }
        """.trimIndent() + "\n")
        assertOnlySettingsAndYaml(f, root, yamlAlias)

        // The normal configuration commands must work without real author
        // credentials and must NOT invoke a Gradle publishing task.
        val help = f.run(root, "help")
        assertEquals(TaskOutcome.SUCCESS, help.task(":help")?.outcome, help.output)
        assertTrue(help.output.contains("BUILD SUCCESSFUL"), help.output)
        assertNoPublishTaskExecuted(help, "help")
        assertNoLocalPublication(root)

        val listing = f.run(root, "tasks", "--all")
        assertEquals(TaskOutcome.SUCCESS, listing.task(":tasks")?.outcome, listing.output)
        assertTrue(listing.output.contains("BUILD SUCCESSFUL"), listing.output)
        for (name in allPublishTasks) {
            assertTrue(listing.output.lines().any { line ->
                val trimmed = line.trimStart()
                trimmed == name || trimmed.startsWith(name + " ") || trimmed.startsWith(name + "\t")
            }, "Missing expected task $name in the $product tasks --all output:\n" + listing.output)
        }
        assertNoPublishTaskExecuted(listing, "tasks --all")
        assertNoLocalPublication(root)
        assertOnlySettingsAndYaml(f, root, yamlAlias)

        // This is a real build, NOT a publish. The compiled Java entrypoint,
        // final distribution, generated Loader JSON and verifier must work.
        val build = f.run(root, "build")
        assertEquals(TaskOutcome.SUCCESS, build.task(":build")?.outcome, build.output)
        assertEquals(TaskOutcome.SUCCESS, build.task(":compileJava")?.outcome, build.output)
        assertEquals(TaskOutcome.SUCCESS, build.task(":jar")?.outcome, build.output)
        assertNotNull(build.task(":verifyFabricAmperJar"),
            "Real build must verify the produced Fabric distribution")
        assertTrue(build.output.contains("BUILD SUCCESSFUL"), build.output)
        assertNoPublishTaskExecuted(build, "build")
        val jar = f.jars(root).single()
        val (count, actualJson) = f.jarRootJson(jar)
        assertEquals(1, count, "Final JAR must contain exactly one root fabric.mod.json")
        assertArrayEquals(Files.readAllBytes(f.generatedJson(root)), actualJson)
        ZipFile(jar.toFile()).use { zip ->
            assertNotNull(zip.getEntry("org/example/publishplan/PublishPlanMod.class"),
                "The real Java entrypoint must be compiled and packaged")
        }
        val metadata = JsonParser.parseString(String(actualJson, StandardCharsets.UTF_8)).asJsonObject
        assertEquals("amper_publish_plan", metadata.get("id").asString)
        assertFalse(metadata.has("publish"), "No remote publishing metadata belongs in Loader JSON")
        assertNoLocalPublication(root)
        assertOnlySettingsAndYaml(f, root, yamlAlias)

        // Never execute publishFabricAmper* without --dry-run. Inspect each
        // target, plus the aggregate, for final JAR -> verifier -> publisher.
        for (target in allPublishTasks) {
            val plan = f.run(root, target, "--dry-run")
            assertDryRunGraph(plan, target, product)
            if (target == "publishFabricAmper") {
                val planned = dryRunTaskPaths(plan)
                for (child in targets) {
                    assertTrue(":$child" in planned,
                        "Aggregate dry-run output must include the configured $child publisher:\n" +
                            plan.output)
                }
            }
            assertNoLocalPublication(root)
            assertOnlySettingsAndYaml(f, root, yamlAlias)
        }
    }

    private fun assertDryRunGraph(result: BuildResult, target: String, product: String) {
        assertTrue(result.output.contains("BUILD SUCCESSFUL"),
            "Dry-run plan for $product/$target must configure without tokens:\n" + result.output)
        // GradleRunner's BuildResult.tasks may be EMPTY for --dry-run. Gradle
        // prints the authoritative ordered task plan as ':task SKIPPED' lines.
        val paths = dryRunTaskPaths(result)
        val jar = paths.indexOf(":jar")
        val verify = paths.indexOf(":verifyFabricAmperJar")
        val publishing = paths.indexOf(":$target")
        assertTrue(jar >= 0, "Final :jar missing from $product/$target dry-run:\n" + result.output)
        assertTrue(verify > jar, "Verifier must be after final :jar:\n" + result.output)
        assertTrue(publishing > verify, "Publishing must be after verifier:\n" + result.output)
        assertTrue(":$target" in paths,
            "The remote publishing target must appear in --dry-run output as SKIPPED:\n" + result.output)
    }

    private fun dryRunTaskPaths(result: BuildResult): List<String> {
        val skipped = Regex("""^(:\S+)\s+SKIPPED$""")
        val displayed = result.output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith(":") }
            .toList()
        assertTrue(displayed.isNotEmpty(),
            "Gradle --dry-run must print actual ordered task lines:\n" + result.output)
        assertTrue(displayed.all { skipped.matches(it) },
            "EVERY displayed task must be exactly ':taskName SKIPPED'; no task action may run:\n" +
                displayed.joinToString("\n") + "\n" + result.output)
        return displayed.map { requireNotNull(skipped.matchEntire(it)).groupValues[1] }
    }

    private fun assertNoPublishTaskExecuted(result: BuildResult, phase: String) {
        assertFalse(result.tasks.any { task ->
            task.path.substringAfterLast(':').startsWith("publish", ignoreCase = true)
        }, "An ordinary $phase command must never execute any publish task:\n" + result.output)
        assertFalse(result.output.contains("BUILD FAILED"), result.output)
    }

    private fun assertNoLocalPublication(root: Path) {
        assertFalse(Files.exists(root.resolve("build/fabric-amper/maven-repository")),
            "Help, task listing, build and publish dry-run cannot create the local publication repository")
    }

    private fun assertOnlySettingsAndYaml(f: TestKitFixture, root: Path, yamlAlias: String) {
        f.assertNoConsumerBuildScript(root)
        assertTrue(Files.isRegularFile(root.resolve(yamlAlias)))
        assertEquals(1, listOf("mod.yaml", "mod.yml").count { Files.exists(root.resolve(it)) },
            "Exactly one YAML file must be provided")
        assertFalse(Files.exists(root.resolve("src/main/resources/fabric.mod.json")))
        assertEquals("plugins { id(\"dev.fabric-amper.settings\") }\n",
            Files.readString(root.resolve("settings.gradle.kts")),
            "Only the single Settings plugin may be authored in the settings script")
    }
}
