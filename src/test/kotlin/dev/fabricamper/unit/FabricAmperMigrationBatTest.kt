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

/** Real Windows cmd.exe -> BAT -> PowerShell smoke, without Gradle or remote access. */
@EnabledOnOs(OS.WINDOWS)
class FabricAmperMigrationBatTest {
    @TempDir lateinit var temporary: Path

    private data class Run(val code: Int, val output: String)

    @Test
    fun `migration resolves current directory, project aliases, deletion and dry run`() {
        val wrapper = createWrapperWithStub()
        val project = Files.createDirectory(temporary.resolve("consumer folder & name"))
        val other = Files.createDirectory(temporary.resolve("another consumer"))
        val original = project.resolve("build.gradle.kts")
        Files.writeString(original, "// user Gradle configuration - must survive wrapper routing\n")

        val combinations = listOf(
            "migrate" to Triple(project, false, false),
            "migrate -D" to Triple(project, true, false),
            "migrate --delete" to Triple(project, true, false),
            "migrate --dry-run" to Triple(project, false, true),
            "migrate --dry-run --delete" to Triple(project, true, true),
            "migrate -C \"$other\" -D" to Triple(other, true, false),
            "migrate -C \"$other\" --delete" to Triple(other, true, false),
            "-C \"$other\" migrate -D" to Triple(other, true, false),
            "--project \"$other\" migrate --delete" to Triple(other, true, false),
            "migrate --project \"$other\" --dry-run" to Triple(other, false, true),
        )
        for ((args, expected) in combinations) {
            val result = run(wrapper, project, args)
            assertEquals(0, result.code, "Expected success for '$args': ${result.output}")
            assertTrue(result.output.contains("STUB Project=${expected.first}"), "Project forwarded for '$args': ${result.output}")
            assertTrue(result.output.contains("STUB Consumer=${expected.first}"), "FA_CONSUMER forwarded for '$args': ${result.output}")
            assertTrue(result.output.contains("STUB Delete=${if (expected.second) "True" else "False"}"), "Delete flag for '$args': ${result.output}")
            assertTrue(result.output.contains("STUB DryRun=${if (expected.third) "True" else "False"}"), "DryRun flag for '$args': ${result.output}")
            assertTrue(result.output.contains("STUB Version=0.1.3"), "FA_VERSION for '$args': ${result.output}")
            assertTrue(result.output.contains("STUB Root=${wrapper.parent}\\"), "FA_ROOT for '$args': ${result.output}")
            assertEquals("// user Gradle configuration - must survive wrapper routing\n", Files.readString(original))
            assertFalse(Files.exists(project.resolve("mod.yaml")), "Only the migration script may create output")
        }
    }

    @Test
    fun `migration rejects invalid option combinations before invoking helper`() {
        val wrapper = createWrapperWithStub()
        val project = Files.createDirectory(temporary.resolve("consumer"))
        val invalid = listOf(
            "migrate -D -D",
            "migrate -D --delete",
            "migrate --dry-run --dry-run",
            "migrate -C",
            "migrate --project",
            "migrate -C --dry-run",
            "migrate --project -D",
            "migrate -C \"$project\" --project \"$project\"",
            "-C \"$project\" migrate -C \"$project\"",
            "migrate --unknown",
            "migrate extra",
            "migrate -C \"${temporary.resolve("does-not-exist-987")}\" -D",
        )
        for (args in invalid) {
            val result = run(wrapper, project, args)
            assertEquals(2, result.code, "Expected argument rejection for '$args': ${result.output}")
            assertFalse(result.output.contains("STUB Project="), "Helper should not execute for '$args'")
        }
    }

    @Test
    fun `standalone BAT fails clearly without helper and propagates helper errors`() {
        val folder = Files.createDirectories(temporary.resolve("standalone tools"))
        val batch = folder.resolve("fabric-amper.bat")
        Files.copy(producerBatch(), batch)
        val consumer = Files.createDirectory(temporary.resolve("existing consumer"))
        val source = consumer.resolve("build.gradle")
        Files.writeString(source, "// KEEP\n")

        val missingHelper = run(batch, consumer, "migrate --dry-run")
        assertEquals(1, missingHelper.code, missingHelper.output)
        assertTrue(missingHelper.output.contains("Missing fabric-amper-migrate.ps1"))
        assertEquals("// KEEP\n", Files.readString(source))

        writeStub(folder.resolve("fabric-amper-migrate.ps1"))
        val failedHelper = run(batch, consumer, "migrate --dry-run", mapOf("FA_TEST_EXIT_CODE" to "17"))
        assertEquals(17, failedHelper.code, "Script failure must propagate: ${failedHelper.output}")
        assertEquals("// KEEP\n", Files.readString(source))
    }

    private fun producerBatch(): Path = Path.of(System.getProperty("user.dir"))
        .toAbsolutePath().normalize().resolve("fabric-amper.bat")

    private fun createWrapperWithStub(): Path {
        val folder = Files.createDirectories(temporary.resolve("BAT module with spaces"))
        val batch = folder.resolve("fabric-amper.bat")
        Files.copy(producerBatch(), batch)
        writeStub(folder.resolve("fabric-amper-migrate.ps1"))
        return batch
    }

    private fun writeStub(script: Path) {
        Files.writeString(script, listOf(
            "param([switch]\$Preview, [switch]\$Delete)",
            "if (\$env:FA_TEST_EXIT_CODE) { exit [int]\$env:FA_TEST_EXIT_CODE }",
            "Write-Output ('STUB Project=' + \$env:FA_CONSUMER)",
            "Write-Output ('STUB Consumer=' + \$env:FA_CONSUMER)",
            "Write-Output ('STUB Delete=' + \$Delete.IsPresent)",
            "Write-Output ('STUB DryRun=' + \$Preview.IsPresent)",
            "Write-Output ('STUB Version=' + \$env:FA_VERSION)",
            "Write-Output ('STUB Root=' + \$env:FA_ROOT)",
        ).joinToString("\n", postfix = "\n"), StandardCharsets.UTF_8)
    }

    private fun run(batch: Path, directory: Path, arguments: String, environment: Map<String, String> = emptyMap()): Run {
        val command = "\"\"$batch\" $arguments\""
        val builder = ProcessBuilder("cmd.exe", "/d", "/s", "/c", command)
            .directory(directory.toFile())
            .redirectErrorStream(true)
        builder.environment().putAll(environment)
        val process = builder.start()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw AssertionError("Migration BAT did not finish in 30 seconds: $arguments")
        }
        return Run(process.exitValue(), process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText())
    }
}
