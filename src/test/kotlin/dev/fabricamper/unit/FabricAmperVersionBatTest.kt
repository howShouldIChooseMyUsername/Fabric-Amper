package dev.fabricamper.unit

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

/** Real Windows CMD checks: version inspection must be read-only and require no Gradle. */
@EnabledOnOs(OS.WINDOWS)
class FabricAmperVersionBatTest {
    @TempDir lateinit var temporary: Path

    private data class Result(val exit: Int, val output: String)

    @Test
    fun `no arguments and version switches emit exactly the release tag`() {
        for (arguments in listOf("", "-V", "-v", "--version", "version", "-C \"$temporary\" -V")) {
            val result = run(arguments)
            assertEquals(0, result.exit, "Unexpected exit for '$arguments': ${result.output}")
            assertEquals("v0.3.1", result.output.trim(), "Unexpected output for '$arguments'")
        }
        assertFalse(Files.exists(temporary.resolve("mod.yaml")))
        assertFalse(Files.exists(temporary.resolve("settings.gradle.kts")))
    }

    @Test
    fun `help remains discoverable and unknown commands still fail`() {
        for (arguments in listOf("help", "--help")) {
            val result = run(arguments)
            assertEquals(0, result.exit, result.output)
            assertTrue(result.output.contains("fabric-amper.bat -V"), result.output)
            assertTrue(result.output.contains("fabric-amper.bat doctor"), result.output)
            assertTrue(result.output.contains("fabric-amper.bat migrate"), result.output)
        }
        val invalid = run("not-a-command")
        assertEquals(2, invalid.exit, invalid.output)
        assertTrue(invalid.output.contains("Unknown command"), invalid.output)
    }

    private fun run(arguments: String): Result {
        val batch = Path.of(System.getProperty("user.dir"))
            .toAbsolutePath().normalize().resolve("fabric-amper.bat")
        assertTrue(Files.isRegularFile(batch), "Missing wrapper: $batch")
        val command = "\"\"$batch\" $arguments\""
        // BAT help output can exceed the Windows anonymous pipe buffer.
        // Redirect before waiting, otherwise cmd.exe blocks writing output.
        val outputFile = temporary.resolve("version-output-${System.nanoTime()}.txt")
        try {
            val process = ProcessBuilder("cmd.exe", "/d", "/s", "/c", command)
                .directory(temporary.toFile())
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw AssertionError("Batch version command did not finish: '$arguments'; output: " +
                    Files.readString(outputFile, StandardCharsets.UTF_8).take(4000))
            }
            return Result(process.exitValue(), Files.readString(outputFile, StandardCharsets.UTF_8))
        } finally {
            Files.deleteIfExists(outputFile)
        }
    }
}
