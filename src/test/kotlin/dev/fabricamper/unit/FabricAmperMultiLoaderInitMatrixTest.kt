package dev.fabricamper.unit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * A dual-loader init generates two independent loader entrypoints. Architectury
 * additionally gets a loader-neutral common source set. All paths are asserted
 * against the consumer output, never by evaluating Gradle scripts.
 */
@EnabledOnOs(OS.WINDOWS)
class FabricAmperMultiLoaderInitMatrixTest {
    @TempDir lateinit var workspace: Path

    @Test
    fun `dual API profile retains both independent API products`() {
        val target = workspace.resolve("dual API")
        val run = InitBatMatrixProbe.init(
            "dualapi", target, listOf("--mod-fabric", "--mod-neoforge"), profile = "api",
        )
        assertEquals(0, run.code, run.output)
        val yaml = InitBatMatrixProbe.yaml(target)
        assertEquals("1.1", yaml["schema"])
        assertEquals(listOf("fabric/api", "neoforge/api"), yaml["product"])
        assertEquals("org.example", InitBatMatrixProbe.field(yaml, "mod")["group"])
        assertTrue(Files.readString(InitBatMatrixProbe.oneSource(target, "fabric/src/main", false))
            .contains("ModInitializer"))
        assertTrue(Files.readString(InitBatMatrixProbe.oneSource(target, "neoforge/src/main", false))
            .contains("@Mod(\"dualapi\")"))
        InitBatMatrixProbe.assertNoAuthoredBuildOrMetadata(target)
        InitBatMatrixProbe.assertBootstrap(target)
    }

    @Test
    fun `dual loader Java and Kotlin matrix respects Architectury isolation`() {
        for (architectury in listOf(false, true)) {
            for (kotlin in listOf(false, true)) {
                val label = "architectury-$architectury-kotlin-$kotlin"
                val target = workspace.resolve(label)
                val id = "multi" + (if (architectury) "arch" else "plain") +
                    (if (kotlin) "kt" else "java")
                val flags = buildList {
                    add("--mod-fabric")
                    add("--mod-neoforge")
                    if (architectury) add("--architectury")
                    if (kotlin) add("--kotlin")
                }
                val result = InitBatMatrixProbe.init(id, target, flags)
                assertEquals(0, result.code, "$label: " + result.output)
                verifyDual(target, id, kotlin, architectury, expectSplit = false)
            }
        }
    }

    @Test
    fun `without Architectury split applies exclusively to Fabric client sources`() {
        for (kotlin in listOf(false, true)) {
            val target = workspace.resolve(if (kotlin) "split Kotlin" else "split Java")
            val id = if (kotlin) "dualsplitkt" else "dualsplitjava"
            // Deliberately reverse the two platform flags to test order independence.
            val flags = listOf("--mod-neoforge", "--mod-fabric", "--splitEnvironmentSourceSets") +
                if (kotlin) listOf("--kotlin") else emptyList()
            val result = InitBatMatrixProbe.init(id, target, flags)
            assertEquals(0, result.code, result.output)
            verifyDual(target, id, kotlin, architectury = false, expectSplit = true)
            val client = InitBatMatrixProbe.oneSource(target, "fabric/src/client", kotlin)
            val code = Files.readString(client)
            assertTrue(code.contains("ClientModInitializer"), "Fabric split client entrypoint missing: $client")
            assertFalse(Files.exists(target.resolve("neoforge/src/client")), "Loom split leaked into NeoForge")
            assertFalse(Files.exists(target.resolve("common")))
        }
    }

    @Test
    fun `Architectury multi loader ignores Loom split flag with either language`() {
        for (kotlin in listOf(false, true)) {
            val name = if (kotlin) "botharchkt" else "botharchjava"
            val base = workspace.resolve("$name-base")
            val requestedSplit = workspace.resolve("$name-split")
            val standardFlags = listOf("--mod-fabric", "--mod-neoforge", "--architectury") +
                if (kotlin) listOf("--kotlin") else emptyList()
            val splitFlags = listOf("--architectury", "--splitEnvironmentSourceSets",
                "--mod-neoforge", "--mod-fabric") +
                if (kotlin) listOf("--kotlin") else emptyList()

            val first = InitBatMatrixProbe.init(name, base, standardFlags)
            assertEquals(0, first.code, first.output)
            val second = InitBatMatrixProbe.init(name, requestedSplit, splitFlags)
            assertEquals(0, second.code, second.output)
            verifyDual(base, name, kotlin, architectury = true, expectSplit = false)
            verifyDual(requestedSplit, name, kotlin, architectury = true, expectSplit = false)
            assertEquals(InitBatMatrixProbe.yaml(base), InitBatMatrixProbe.yaml(requestedSplit),
                "Loom split flag must not rewrite Architectury multi-loader YAML")
            assertEquals(relativeFiles(base), relativeFiles(requestedSplit),
                "Loom split flag must not add client or duplicate source files to Architectury")
            for (relative in relativeFiles(base)) {
                val left = Files.readAllBytes(base.resolve(relative))
                val right = Files.readAllBytes(requestedSplit.resolve(relative))
                assertTrue(left.contentEquals(right), "Unexpected content drift in $relative")
            }
        }
    }

    private fun verifyDual(
        root: Path,
        id: String,
        kotlin: Boolean,
        architectury: Boolean,
        expectSplit: Boolean,
    ) {
        val yaml = InitBatMatrixProbe.yaml(root)
        assertEquals("1.1", yaml["schema"], "A multi-loader project must explicitly use schema v1.1")
        assertEquals(listOf("fabric/mod", "neoforge/mod"), yaml["product"],
            "Both loaders must be present in stable order and never collapsed into one product")
        assertEquals(id, InitBatMatrixProbe.field(yaml, "mod")["id"])
        assertEquals("26.3", InitBatMatrixProbe.field(yaml, "minecraft")["version"])
        assertEquals(kotlin, InitBatMatrixProbe.field(InitBatMatrixProbe.field(yaml, "settings"), "kotlin")["enabled"])

        val fabric = InitBatMatrixProbe.field(yaml, "fabric")
        val neoforge = InitBatMatrixProbe.field(yaml, "neoforge")
        assertEquals("0.20.1", fabric["loader"])
        assertTrue((neoforge["version"] as? String).orEmpty().startsWith("26.3."), neoforge.toString())
        assertFalse(InitBatMatrixProbe.field(yaml, "minecraft").containsKey("loader"),
            "Fabric Loader must live under fabric:, not shared minecraft:")
        assertFalse(InitBatMatrixProbe.field(yaml, "mod").containsKey("entrypoints"),
            "Fabric entrypoints must live under fabric:, not shared mod:")
        assertEquals(architectury, (yaml["architectury"] as? Map<*, *>)?.get("enable") == true)
        val entrypoints = InitBatMatrixProbe.field(fabric, "entrypoints")
        assertTrue(entrypoints.containsKey("main"), "Fabric platform needs its own main entrypoint")
        assertEquals(expectSplit, entrypoints.containsKey("client"),
            "Fabric client entrypoint must track its source-set layout")

        val fabricCode = Files.readString(InitBatMatrixProbe.oneSource(root, "fabric/src/main", kotlin))
        assertTrue(fabricCode.contains("ModInitializer"), "Fabric platform source lacks ModInitializer")
        assertTrue(fabricCode.contains("net.fabricmc"), "Fabric imports missing")
        assertFalse(fabricCode.contains("net.neoforged"), "NeoForge leaked into Fabric source")
        val neoforgeCode = Files.readString(InitBatMatrixProbe.oneSource(root, "neoforge/src/main", kotlin))
        assertTrue(neoforgeCode.contains("@Mod(\"$id\")"),
            "NeoForge @Mod annotation must contain its mod ID and closing parenthesis")
        assertTrue(neoforgeCode.contains("net.neoforged"), "NeoForge imports missing")
        assertFalse(neoforgeCode.contains("net.fabricmc"), "Fabric leaked into NeoForge source")
        assertEquals(architectury, fabricCode.contains("ExampleCommon.initialize"),
            "Fabric platform must call shared initializer exactly in Architectury mode")
        assertEquals(architectury, neoforgeCode.contains("ExampleCommon.initialize"),
            "NeoForge platform must call shared initializer exactly in Architectury mode")
        assertTrue(Files.isDirectory(root.resolve("fabric/src/main/resources")),
            "Fabric resources must be kept in the Fabric project")
        assertTrue(Files.isDirectory(root.resolve("neoforge/src/main/resources")),
            "NeoForge resources must be kept in the NeoForge project")

        if (architectury) {
            val common = Files.readString(InitBatMatrixProbe.oneSource(root, "common/src/main", kotlin))
            assertFalse(common.contains("net.fabricmc"), "Common source must be Fabric-independent")
            assertFalse(common.contains("net.neoforged"), "Common source must be NeoForge-independent")
            assertTrue(common.contains("dev.architectury"), "Architectury common source must use common API")
            assertTrue(Files.isDirectory(root.resolve("common/src/main/resources")),
                "Architectury shared resources must have their own source directory")
            assertFalse(Files.exists(root.resolve("fabric/src/client")),
                "Architectury dual-loader path ignores Loom split option")
            assertFalse(Files.exists(root.resolve("neoforge/src/client")))
        } else {
            assertFalse(Files.exists(root.resolve("common")),
                "Independent loader projects must not require an Architectury common module")
            val client = root.resolve("fabric/src/client")
            assertEquals(expectSplit, Files.isDirectory(client),
                "Fabric client source-set split selection mismatch")
        }
        val loom = fabric["loom"] as? Map<*, *>
        assertEquals(expectSplit, loom?.get("splitEnvironmentSourceSets") == true,
            "Fabric's Loom split setting does not match generated layout")
        assertFalse(Files.exists(root.resolve("src/main")),
            "Multi-loader init must use per-loader roots instead of a single-loader src/main")
        InitBatMatrixProbe.assertNoAuthoredBuildOrMetadata(root)
        InitBatMatrixProbe.assertBootstrap(root)
    }

    private fun relativeFiles(root: Path): Set<String> = Files.walk(root).use { walk ->
        walk.filter { Files.isRegularFile(it) }
            .map { root.relativize(it).toString().replace('\\', '/') }
            .toList()
            .toSet()
    }
}
