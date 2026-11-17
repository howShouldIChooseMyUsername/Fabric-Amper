package dev.fabricamper.unit

import dev.fabricamper.config.StrictYamlReader
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Real Windows BAT init and YAML assertions, without Gradle or network access. */
@EnabledOnOs(OS.WINDOWS)
class FabricAmperNeoForgeInitMatrixTest {
    @TempDir lateinit var workspace: Path

    @Test
    fun `default Fabric remains backwards compatible and settings only`() {
        val root = workspace.resolve("Default Fabric")
        val run = InitBatMatrixProbe.init("defaultmod", root)
        assertEquals(0, run.code, run.output)
        val yaml = InitBatMatrixProbe.yaml(root)
        assertEquals("fabric/mod", yaml["product"])
        assertTrue(yaml["schema"] == null || yaml["schema"] == 1, yaml.toString())
        assertEquals("0.20.1", InitBatMatrixProbe.field(yaml, "minecraft")["loader"])
        assertEquals(false, InitBatMatrixProbe.field(InitBatMatrixProbe.field(yaml, "settings"), "kotlin")["enabled"])
        assertTrue(Files.isRegularFile(root.resolve("src/main/java/org/example/ExampleMod.java")))
        assertTrue(Files.readString(root.resolve("src/main/java/org/example/ExampleMod.java")).contains("ModInitializer"))
        assertFalse(Files.exists(root.resolve("src/client")))
        assertFalse(Files.exists(root.resolve("fabric")))
        assertFalse(Files.exists(root.resolve("neoforge")))
        InitBatMatrixProbe.assertNoAuthoredBuildOrMetadata(root)
        InitBatMatrixProbe.assertBootstrap(root)
    }

    @Test
    fun `explicit Fabric generates Java Kotlin and Architectury single loader variants`() {
        for (architectury in listOf(false, true)) {
            for (kotlin in listOf(false, true)) {
                val root = workspace.resolve("Fabric Kotlin-$kotlin Architectury-$architectury")
                val flags = buildList {
                    add("--mod-fabric")
                    if (architectury) add("--architectury")
                    if (kotlin) add("--kotlin")
                }
                val id = "fabric" + (if (kotlin) "kt" else "java") + (if (architectury) "arch" else "plain")
                val run = InitBatMatrixProbe.init(id, root, flags)
                assertEquals(0, run.code, run.output)
                val yaml = InitBatMatrixProbe.yaml(root)
                assertEquals("fabric/mod", yaml["product"])
                InitBatMatrixProbe.assertFabricProfile(yaml)
                assertEquals(kotlin, InitBatMatrixProbe.field(InitBatMatrixProbe.field(yaml, "settings"), "kotlin")["enabled"])
                assertEquals(architectury, (yaml["architectury"] as? Map<*, *>)?.get("enable") == true)
                val source = InitBatMatrixProbe.oneSource(root, "src/main", kotlin)
                val code = Files.readString(source)
                assertTrue(code.contains("ModInitializer"), "Missing Fabric initializer: $source")
                assertEquals(architectury, code.contains("dev.architectury"), "Fabric Architectury flag mismatch")
                assertFalse(Files.exists(root.resolve("neoforge")))
                assertFalse(Files.exists(root.resolve("common")))
                InitBatMatrixProbe.assertNoAuthoredBuildOrMetadata(root)
                InitBatMatrixProbe.assertBootstrap(root)
            }
        }
    }

    @Test
    fun `single NeoForge emits Java Kotlin and Architectury entrypoints without Fabric imports`() {
        for (architectury in listOf(false, true)) {
            for (kotlin in listOf(false, true)) {
                val root = workspace.resolve("NeoForge Kotlin-$kotlin Architectury-$architectury")
                val flags = buildList {
                    add("--mod-neoforge")
                    if (architectury) add("--architectury")
                    if (kotlin) add("--kotlin")
                }
                val id = "neoforge" + (if (kotlin) "kt" else "java") + (if (architectury) "arch" else "plain")
                val run = InitBatMatrixProbe.init(id, root, flags)
                assertEquals(0, run.code, run.output)
                val yaml = InitBatMatrixProbe.yaml(root)
                assertEquals("1.1", yaml["schema"], "NeoForge init must explicitly select schema v1.1")
                assertEquals("neoforge/mod", yaml["product"])
                assertEquals("26.3", InitBatMatrixProbe.field(yaml, "minecraft")["version"])
                val neo = InitBatMatrixProbe.field(yaml, "neoforge")
                assertTrue((neo["version"] as? String).orEmpty().startsWith("26.3."), neo.toString())
                assertFalse(yaml.containsKey("fabric"), "NeoForge-only YAML has Fabric-only configuration")
                assertEquals(kotlin, InitBatMatrixProbe.field(InitBatMatrixProbe.field(yaml, "settings"), "kotlin")["enabled"])
                assertEquals(architectury, (yaml["architectury"] as? Map<*, *>)?.get("enable") == true)
                val source = InitBatMatrixProbe.oneSource(root, "src/main", kotlin)
                val code = Files.readString(source)
                assertTrue(code.contains("@Mod(\"$id\")"),
                    "NeoForge @Mod annotation must contain the mod ID and closing parenthesis: $source\n$code")
                assertTrue(code.contains("net.neoforged"), "NeoForge import missing: $source")
                assertFalse(code.contains("net.fabricmc"), "Fabric code leaked into NeoForge source: $source")
                assertEquals(architectury, code.contains("dev.architectury"), "NeoForge Architectury flag mismatch")
                assertFalse(Files.exists(root.resolve("fabric")))
                assertFalse(Files.exists(root.resolve("common")))
                InitBatMatrixProbe.assertNoAuthoredBuildOrMetadata(root)
                InitBatMatrixProbe.assertBootstrap(root)
            }
        }
    }

    @Test
    fun `duplicate loader selectors fail before creating any output`() {
        val invalid = listOf(
            listOf("--mod-fabric", "--mod-fabric"),
            listOf("--mod-neoforge", "--mod-neoforge"),
            listOf("--mod-fabric", "--mod-neoforge", "--mod-neoforge"),
            listOf("--architectury", "--architectury", "--mod-neoforge"),
            listOf("--mod-neoforge", "--splitEnvironmentSourceSets"),
        )
        for ((index, flags) in invalid.withIndex()) {
            val root = workspace.resolve("invalid-$index")
            val run = InitBatMatrixProbe.init("invalidmod", root, flags)
            assertEquals(2, run.code, "Unexpected success for $flags: " + run.output)
            assertFalse(Files.exists(root), "Invalid invocation wrote to $root")
        }
    }

    @Test
    fun `NeoForge ID validation rejects hyphens before writing output`() {
        val root = workspace.resolve("invalid-id")
        val run = InitBatMatrixProbe.init("has-hyphen", root, listOf("--mod-neoforge"))
        assertEquals(2, run.code, run.output)
        assertFalse(Files.exists(root))
    }
}

/** Shared helper used by the NeoForge and multi-loader init test matrices. */
internal object InitBatMatrixProbe {
    private val producer: Path get() = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()

    data class Result(val code: Int, val output: String)

    fun init(
        id: String,
        target: Path,
        flags: List<String> = emptyList(),
        profile: String = "mod",
    ): Result {
        val bat = producer.resolve("fabric-amper.bat")
        require(Files.isRegularFile(bat)) { "Missing BAT under $producer" }
        val args = "init $id \"$target\" $profile" +
            (if (flags.isEmpty()) "" else " " + flags.joinToString(" "))
        val command = "\"\"$bat\" $args\""
        val process = ProcessBuilder("cmd.exe", "/d", "/s", "/c", command)
            .directory(producer.toFile())
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(45, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw AssertionError("BAT init timed out: $args")
        }
        return Result(process.exitValue(), process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText())
    }

    fun yaml(root: Path): Map<String, Any?> {
        val path = root.resolve("mod.yaml")
        assertTrue(Files.isRegularFile(path), "Missing generated YAML: $path")
        return StrictYamlReader.read(Files.readString(path, StandardCharsets.UTF_8), path)
    }

    fun field(parent: Map<*, *>, name: String): Map<*, *> {
        val value = parent[name]
        require(value is Map<*, *>) { "Expected YAML mapping $name, got $value" }
        return value
    }

    fun assertFabricProfile(yaml: Map<String, Any?>) {
        if (yaml["schema"] == "1.1") {
            val fabric = field(yaml, "fabric")
            assertEquals("0.20.1", fabric["loader"])
            assertFalse(yaml.containsKey("neoforge"))
        } else {
            assertEquals("0.20.1", field(yaml, "minecraft")["loader"])
        }
    }

    fun oneSource(root: Path, sourceSet: String, kotlin: Boolean): Path {
        val language = if (kotlin) "kotlin" else "java"
        val extension = if (kotlin) ".kt" else ".java"
        val dir = root.resolve("$sourceSet/$language")
        assertTrue(Files.isDirectory(dir), "Missing source directory: $dir")
        val files = Files.walk(dir).use { walk ->
            walk.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(extension) }.toList()
        }
        assertEquals(1, files.size, "Expected one generated source in $dir: $files")
        return files.single()
    }

    fun assertNoAuthoredBuildOrMetadata(root: Path) {
        Files.walk(root).use { walk ->
            val forbidden = walk.filter {
                Files.isRegularFile(it) && it.fileName.toString() in setOf(
                    "build.gradle", "build.gradle.kts", "fabric.mod.json", "neoforge.mods.toml", "mods.toml",
                )
            }.toList()
            assertTrue(forbidden.isEmpty(), "Generated authored Gradle/loader metadata: $forbidden")
        }
    }

    fun assertBootstrap(root: Path) {
        val settings = root.resolve("settings.gradle.kts")
        assertTrue(Files.isRegularFile(settings), "Missing settings bootstrap: $settings")
        val content = Files.readString(settings, StandardCharsets.UTF_8)
        assertTrue(content.contains("id(\"dev.fabric-amper.settings\")"), content)
        assertFalse(content.contains("include(\"fabric\")"), "Platform inclusion belongs to Settings plugin")
        val schema = root.resolve("schemas/mod.schema.json")
        assertTrue(Files.isRegularFile(schema))
        assertArrayEquals(Files.readAllBytes(producer.resolve("fabric-amper.schema.json")), Files.readAllBytes(schema))
    }
}
