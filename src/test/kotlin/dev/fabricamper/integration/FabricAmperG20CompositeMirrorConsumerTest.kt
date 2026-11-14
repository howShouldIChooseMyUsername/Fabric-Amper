package dev.fabricamper.integration

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
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * G20: simultaneous JSON, Mixin and Class Tweaker corruption plus icon
 * deletion, repaired under concurrent real GradleRunner configuration-cache
 * invocations in independently prewarmed homes.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_G20_COMPOSITE", matches = "true")
class FabricAmperG20CompositeMirrorConsumerTest {
    @TempDir lateinit var suiteRoot: Path

    @Test
    fun compositeMirrorRepairSurvivesParallelOfflineCc() {
        val fixture = TestKitFixture(suiteRoot)
        val mixinPath = "example.mixins.json"
        val tweakerPath = "example.classtweaker"
        val iconPath = "assets/testkit_mod/icon.png"
        val jsonPath = "fabric.mod.json"
        val yaml = fixture.resource("minimal-mod.yaml").replace(
            "  entrypoints:",
            "  mixins:\n    - $mixinPath\n  accessWidener: $tweakerPath\n" +
                "  icon: $iconPath\n  entrypoints:",
        )
        assertTrue(yaml.contains("  accessWidener: $tweakerPath"))
        assertTrue(yaml.contains("  mixins:\n    - $mixinPath"))
        assertTrue(yaml.contains("  icon: $iconPath"))
        val root = fixture.project("g20-composite-26-3", yaml)
        val mixinBytes = fixture.resource(mixinPath).toByteArray(StandardCharsets.UTF_8)
        val tweakerBytes = "classTweaker v1 official\n".toByteArray(StandardCharsets.UTF_8)
        val iconBytes = TestKitFixture.onePixelPng
        val sources = linkedMapOf(
            mixinPath to root.resolve("src/main/resources/$mixinPath"),
            tweakerPath to root.resolve("src/main/resources/$tweakerPath"),
            iconPath to root.resolve("src/main/resources/$iconPath"),
        )
        fixture.writeBytes(root, "src/main/resources/$mixinPath", mixinBytes)
        fixture.writeBytes(root, "src/main/resources/$tweakerPath", tweakerBytes)
        fixture.writeBytes(root, "src/main/resources/$iconPath", iconBytes)
        fixture.write(root, "src/main/java/org/example/TestkitEntry.java", """
            package org.example;
            public final class TestkitEntry implements net.fabricmc.api.ModInitializer {
                @Override public void onInitialize() {}
            }
        """.trimIndent() + "\n")
        fixture.assertNoConsumerBuildScript(root)
        assertFalse(Files.exists(root.resolve("src/main/resources/fabric.mod.json")))

        // A real 26.3 build is required before tampering: post-repair builds
        // would not prove preservation of the existing final JAR.
        val initial = fixture.run(root, "build")
        assertSuccessful(initial, ":jar")
        assertEquals(TaskOutcome.SUCCESS, initial.task(":jar")?.outcome, initial.output)
        val jar = fixture.jars(root).single()
        val originalJar = Files.readAllBytes(jar)
        val originalJson = Files.readAllBytes(fixture.generatedJson(root))
        val expected = linkedMapOf(
            jsonPath to originalJson,
            mixinPath to mixinBytes,
            tweakerPath to tweakerBytes,
            iconPath to iconBytes,
        )
        val mirrorJson = fixture.mirrorJson(root).single()
        val mirrorRoot = mirrorJson.parent
        assertUnchanged(fixture, root, mirrorRoot, sources, expected, jar, originalJar)

        // Each TestKit home has its own dependency cache: warm it online
        // before demanding strictly offline configuration-cache HITs.
        val commands = listOf(
            RaceCommand("g20-composite-help", "help"),
            RaceCommand("g20-composite-generate", "generateFabricModJson"),
        )
        for (command in commands) {
            val bootstrap = fixture.runWithIsolatedTestKitHome(
                root, command.home, command.task, "--configuration-cache",
            )
            assertSuccessful(bootstrap, command.taskPath)
            val first = fixture.runWithIsolatedTestKitHome(root, command.home, *command.arguments())
            assertSuccessful(first, command.taskPath)
            val hit = fixture.runWithIsolatedTestKitHome(root, command.home, *command.arguments())
            assertSuccessful(hit, command.taskPath)
            assertTrue(hit.output.contains("Reusing configuration cache"),
                "Configuration cache must be warm before damaging " + command.task + ":\n" + hit.output)
        }
        assertUnchanged(fixture, root, mirrorRoot, sources, expected, jar, originalJar)

        // Tamper ONLY four mirrored files. All edits finish before the two
        // real GradleRunner invocations begin. Sources/JAR/generated JSON stay
        // untouched so successful healing has a meaningful byte oracle.
        Files.write(mirrorJson, "{\"G20_CORRUPTED\":true}\n".toByteArray(StandardCharsets.UTF_8))
        Files.write(mirrorRoot.resolve(mixinPath), "{INVALID_G20_MIXIN".toByteArray(StandardCharsets.UTF_8))
        Files.write(mirrorRoot.resolve(tweakerPath), "invalid G20 tweaker\n".toByteArray(StandardCharsets.UTF_8))
        Files.delete(mirrorRoot.resolve(iconPath))
        for (name in listOf(jsonPath, mixinPath, tweakerPath)) {
            assertFalse(Files.readAllBytes(mirrorRoot.resolve(name)).contentEquals(expected.getValue(name)),
                "Precondition failed: mirrored $name was not corrupted")
        }
        assertFalse(Files.exists(mirrorRoot.resolve(iconPath)),
            "Precondition failed: the mirrored icon must be absent before the race")
        assertSourcesUnchanged(sources, expected)
        assertArrayEquals(originalJson, Files.readAllBytes(fixture.generatedJson(root)))
        assertArrayEquals(originalJar, Files.readAllBytes(jar))

        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = commands.map { command ->
                command to executor.submit(Callable {
                    ready.countDown()
                    check(go.await(1, TimeUnit.MINUTES)) {
                        "G20 parallel workers did not receive the start signal"
                    }
                    fixture.runWithIsolatedTestKitHome(root, command.home, *command.arguments())
                })
            }
            assertTrue(ready.await(1, TimeUnit.MINUTES),
                "Both GradleRunner workers must reach the parallel-start barrier")
            go.countDown()
            for ((command, future) in futures) {
                assertSuccessful(future.get(10, TimeUnit.MINUTES), command.taskPath)
            }
        } finally {
            go.countDown()
            executor.shutdownNow()
        }

        // Both configurations succeeded. Prove that every original byte was
        // restored and no atomic-write temporary file was left behind.
        assertUnchanged(fixture, root, mirrorRoot, sources, expected, jar, originalJar)

        // A final CC HIT on both task graphs proves that the repaired state
        // is reusable, in addition to being byte-identical.
        for (command in commands) {
            val result = fixture.runWithIsolatedTestKitHome(root, command.home, *command.arguments())
            assertSuccessful(result, command.taskPath)
            assertTrue(result.output.contains("Reusing configuration cache"),
                "Final configuration-cache HIT missing for " + command.task + ":\n" + result.output)
        }
        assertUnchanged(fixture, root, mirrorRoot, sources, expected, jar, originalJar)
    }

    private data class RaceCommand(val home: String, val task: String) {
        val taskPath: String get() = ":$task"
        fun arguments(): Array<String> = arrayOf(task, "--configuration-cache", "--offline")
    }

    private fun assertSuccessful(result: BuildResult, taskPath: String) {
        assertTrue(result.output.contains("BUILD SUCCESSFUL"),
            "$taskPath did not complete successfully:\n" + result.output)
        val outcome = result.task(taskPath)?.outcome
        assertNotNull(outcome, "Gradle did not include the real $taskPath task")
        assertTrue(outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE),
            "$taskPath has unexpected outcome $outcome:\n" + result.output)
    }

    private fun assertSourcesUnchanged(sources: Map<String, Path>, expected: Map<String, ByteArray>) {
        sources.forEach { (name, path) ->
            assertArrayEquals(expected.getValue(name), Files.readAllBytes(path),
                "Authored src/main/resources/$name changed during mirror repair")
        }
    }

    private fun assertUnchanged(
        fixture: TestKitFixture,
        root: Path,
        mirrorRoot: Path,
        sources: Map<String, Path>,
        expected: Map<String, ByteArray>,
        jar: Path,
        jarBytes: ByteArray,
    ) {
        val snapshots = fixture.mirrorJson(root)
        assertEquals(1, snapshots.size, "Unchanged authored resources must keep exactly one content hash")
        assertEquals(mirrorRoot.resolve("fabric.mod.json"), snapshots.single(),
            "The snapshot must retain its original content-addressed hash")
        expected.forEach { (name, bytes) ->
            assertArrayEquals(bytes, Files.readAllBytes(mirrorRoot.resolve(name)),
                "Mirror did not restore original bytes for $name")
        }
        assertSourcesUnchanged(sources, expected)
        assertArrayEquals(expected.getValue("fabric.mod.json"), Files.readAllBytes(fixture.generatedJson(root)),
            "Generated task JSON and Loom mirror have different bytes")
        assertArrayEquals(jarBytes, Files.readAllBytes(jar),
            "The initially built JAR was modified by configuration-only repair")
        ZipFile(jar.toFile()).use { zip ->
            expected.forEach { (name, bytes) ->
                val entries = zip.entries().asSequence().filter { it.name == name }.toList()
                assertEquals(1, entries.size, "JAR must contain exactly one $name entry")
                assertArrayEquals(bytes, zip.getInputStream(entries.single()).use { it.readAllBytes() },
                    "JAR resource $name does not match original bytes")
            }
            assertEquals(1, zip.entries().asSequence().count { it.name == "org/example/TestkitEntry.class" },
                "Real Java consumer entrypoint must appear exactly once")
        }
        Files.walk(root.resolve(".gradle/fabric-amper/loom")).use { paths ->
            val remnants = paths.filter { it.fileName.toString().startsWith(".amper-") }.toList()
            assertTrue(remnants.isEmpty(), "Atomic mirror writer left .amper temporary files: $remnants")
        }
        fixture.assertNoConsumerBuildScript(root)
        assertFalse(Files.exists(root.resolve("src/main/resources/fabric.mod.json")))
        assertFalse(Files.exists(root.resolve("src/client/resources/fabric.mod.json")))
    }
}
