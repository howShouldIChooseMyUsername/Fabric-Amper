package dev.fabricamper.unit

import dev.fabricamper.config.ModYamlLoader
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Real Windows batch parser and source templates; does not download or execute Gradle. */
@EnabledOnOs(OS.WINDOWS)
class FabricAmperArchitecturyInitBatTest {
    private val producer = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    private val bat = producer.resolve("fabric-amper.bat")

    private data class Example(val args: String, val folder: Path, val kotlin: Boolean, val split: Boolean, val api: Boolean = false)

    @Test
    fun `architectury init generates distinct standalone and split examples for Java and Kotlin`() {
        val root = Files.createTempDirectory(Files.createDirectories(producer.resolve("build")), "fabric-amper-init-architectury-")
        val javaSingle = root.resolve("Java Single")
        val javaSplit = root.resolve("Java Split")
        val kotlinSingle = root.resolve("Kotlin Single")
        val kotlinSplit = root.resolve("Kotlin Split")
        val here = Files.createDirectory(root.resolve("Here API"))
        val cases = listOf(
            Example("init archjava \"$javaSingle\" mod --architectury", javaSingle, false, false),
            Example("init archsplit \"$javaSplit\" mod --architectury --splitEnvironmentSourceSets", javaSplit, false, true),
            Example("init archkotlin \"$kotlinSingle\" mod --kotlin --architectury", kotlinSingle, true, false),
            Example("init archktas \"$kotlinSplit\" mod --splitEnvironmentSourceSets --architectury --kotlin", kotlinSplit, true, true),
            Example("-C \"$here\" init archhere --here api --architectury --kotlin --splitEnvironmentSourceSets", here, true, true, true),
        )
        val schema = Files.readAllBytes(producer.resolve("fabric-amper.schema.json"))
        for (case in cases) {
            val (code, output) = run(case.args)
            assertEquals(0, code, "Failed ${case.args}: $output")
            val yaml = case.folder.resolve("mod.yaml")
            val content = Files.readString(yaml, StandardCharsets.UTF_8)
            val model = ModYamlLoader.load(yaml)
            assertEquals(if (case.api) "fabric/api" else "fabric/mod", model.product)
            assertTrue(content.replace("\r\n", "\n").contains("architectury:\n  enable: true"), content)
            assertTrue(model.architectury.enabled)
            assertEquals("22.0.5", model.architectury.version)
            assertEquals(case.split, model.settings.loom.splitEnvironmentSourceSets)
            assertEquals(case.kotlin, model.settings.kotlin.enabled)
            assertEquals(">=22.0.5", model.mod.depends["architectury"])
            assertArrayEquals(schema, Files.readAllBytes(case.folder.resolve("schemas/mod.schema.json")))
            val ext = if (case.kotlin) "kt" else "java"
            val language = if (case.kotlin) "kotlin" else "java"
            val main = case.folder.resolve("src/main/$language/org/example/ExampleMod.$ext")
            assertTrue(Files.isRegularFile(main), "Missing main example: $main")
            val mainCode = Files.readString(main)
            assertTrue(mainCode.contains("dev.architectury.platform.Platform"), mainCode)
            assertTrue(mainCode.contains("Platform.getMinecraftVersion()"), mainCode)
            assertTrue(mainCode.contains(if (case.split) "Architectury common source set" else "Architectury single source set"), mainCode)
            val client = case.folder.resolve("src/client/$language/org/example/ExampleModClient.$ext")
            if (case.split) {
                assertTrue(Files.isRegularFile(client), "Missing split client example: $client")
                val clientCode = Files.readString(client)
                assertTrue(clientCode.contains("import dev.architectury.platform.Platform"), clientCode)
                assertTrue(clientCode.contains("Platform.getMinecraftVersion()"), clientCode)
                assertTrue(clientCode.contains("Architectury client source set"), clientCode)
                assertTrue(clientCode.contains("ClientModInitializer"), clientCode)
                assertEquals("org.example.ExampleModClient", model.mod.entrypoints.getValue("client").single().value)
            } else {
                assertFalse(Files.exists(case.folder.resolve("src/client")))
                assertFalse(model.mod.entrypoints.containsKey("client"))
            }
            assertFalse(Files.exists(case.folder.resolve("build.gradle.kts")))
            assertFalse(Files.exists(case.folder.resolve("build.gradle")))
            assertFalse(Files.exists(case.folder.resolve("src/main/resources/fabric.mod.json")))
            assertTrue(output.contains("Architectury API: enabled"), output)
        }
    }

    @Test
    fun `architectury option rejects duplicates misplaced flags and extra arguments before creating files`() {
        val root = Files.createTempDirectory(Files.createDirectories(producer.resolve("build")), "fabric-amper-init-architectury-invalid-")
        val cases = listOf(
            "init invalid_arc \"${root.resolve("duplicate")}\" mod --architectury --architectury",
            "init invalid_arc \"${root.resolve("before-profile")}\" --architectury mod",
            "init invalid_arc \"${root.resolve("after-three")}\" mod --architectury --kotlin --splitEnvironmentSourceSets extra",
            "init invalid_arc --architectury \"${root.resolve("before-folder")}\"",
        )
        for (command in cases) {
            val (code, output) = run(command)
            assertEquals(2, code, "Unexpected init success: $command\n$output")
        }
        assertTrue(Files.list(root).use { it.findAny().isEmpty })
    }

    @Test
    fun `plain init remains Architectury-free`() {
        val root = Files.createTempDirectory(Files.createDirectories(producer.resolve("build")), "fabric-amper-init-no-architectury-")
        val project = root.resolve("Legacy Java")
        val (code, output) = run("init plain_mod \"$project\" mod")
        assertEquals(0, code, output)
        val model = ModYamlLoader.load(project.resolve("mod.yaml"))
        assertFalse(model.architectury.enabled)
        assertFalse(Files.readString(project.resolve("mod.yaml")).contains("architectury:"))
        assertFalse(Files.readString(project.resolve("src/main/java/org/example/ExampleMod.java")).contains("dev.architectury"))
    }

    private fun run(args: String): Pair<Int, String> {
        val command = "\"\"$bat\" $args\""
        val process = ProcessBuilder("cmd.exe", "/d", "/s", "/c", command)
            .directory(producer.toFile()).redirectErrorStream(true).start()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw AssertionError("BAT init timed out: $args")
        }
        return process.exitValue() to process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
    }
}
