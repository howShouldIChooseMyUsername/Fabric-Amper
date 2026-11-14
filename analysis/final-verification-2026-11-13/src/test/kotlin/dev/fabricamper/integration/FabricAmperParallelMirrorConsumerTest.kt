package dev.fabricamper.integration

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
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
import kotlin.io.path.readBytes

/** Real parallel GradleRunner builds against one isolated consumer, not a mock of the mirror writer. */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
class FabricAmperParallelMirrorConsumerTest {
    @TempDir lateinit var suiteRoot: Path

    @Test
    fun `G20 parallel configurations restore corrupted JSON and deleted referenced resource atomically`() {
        val f = TestKitFixture(suiteRoot)
        val yaml = f.resource("minimal-mod.yaml").replace(
            "  entrypoints:",
            "  mixins:\n    - example.mixins.json\n  icon: assets/testkit_mod/icon.png\n  entrypoints:",
        )
        val root = f.project("g20-parallel", yaml)
        val mixin = f.resource("example.mixins.json")
        val icon = TestKitFixture.onePixelPng
        f.write(root, "src/main/resources/example.mixins.json", mixin)
        f.writeBytes(root, "src/main/resources/assets/testkit_mod/icon.png", icon)
        f.assertNoConsumerBuildScript(root)

        // The unmeasured bootstrap initializes Loom's own version-manifest
        // cache; the mirror under test is managed by Fabric Amper.
        f.run(root, "help")
        // Prewarm BOTH distinct TestKit caches before the parallel race. The
        // concurrent builds should share the consumer root and snapshot only,
        // not contend for a PID-wide TestKit home on Windows.
        f.runWithIsolatedTestKitHome(root, "g20-thread-0", "help")
        f.runWithIsolatedTestKitHome(root, "g20-thread-1", "help")
        val mirror = f.mirrorJson(root).single()
        val mirrorRoot = mirror.parent
        val originalJson = mirror.readBytes()
        val originalMixin = mixin.toByteArray(StandardCharsets.UTF_8)
        assertArrayEquals(originalMixin, mirrorRoot.resolve("example.mixins.json").readBytes())
        assertArrayEquals(icon, mirrorRoot.resolve("assets/testkit_mod/icon.png").readBytes())

        Files.writeString(mirror, "{\"corrupted\":true}\n", StandardCharsets.UTF_8)
        Files.delete(mirrorRoot.resolve("assets/testkit_mod/icon.png"))

        // Both Gradle builds start at one barrier. They must independently
        // complete successfully despite racing to materialize the same
        // content-addressed snapshot and resource. Never use runClient/server.
        val start = CountDownLatch(1)
        val ready = CountDownLatch(2)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val commands = listOf(
                "g20-thread-0" to listOf("help", "--configuration-cache"),
                "g20-thread-1" to listOf("generateFabricModJson", "--configuration-cache"),
            )
            val results = commands.map { (testKitKey, args) ->
                workers.submit(Callable {
                    ready.countDown()
                    check(start.await(1, TimeUnit.MINUTES)) { "Parallel TestKit start barrier expired" }
                    f.runWithIsolatedTestKitHome(root, testKitKey, *args.toTypedArray())
                })
            }
            assertTrue(ready.await(1, TimeUnit.MINUTES), "Both Gradle TestKit workers must reach the barrier")
            start.countDown()
            for (result in results) {
                val output = result.get(8, TimeUnit.MINUTES).output
                assertTrue(output.contains("BUILD SUCCESSFUL"), output)
            }
        } finally {
            start.countDown()
            workers.shutdownNow()
        }

        assertEquals(1, f.mirrorJson(root).size, "Concurrent writes must retain exactly one content hash snapshot")
        assertArrayEquals(originalJson, mirror.readBytes(), "Concurrent repair must restore metadata bytes")
        assertArrayEquals(originalMixin, mirrorRoot.resolve("example.mixins.json").readBytes())
        assertArrayEquals(icon, mirrorRoot.resolve("assets/testkit_mod/icon.png").readBytes())
        assertArrayEquals(originalJson, f.generatedJson(root).readBytes(), "Task JSON and repaired mirror must agree")
        Files.walk(mirrorRoot).use { paths ->
            assertEquals(
                0,
                paths.filter { it.fileName.toString().startsWith(".amper-") }.count(),
                "Atomic mirror writes must leave no temporary files",
            )
        }
        f.assertNoConsumerBuildScript(root)
    }
}
