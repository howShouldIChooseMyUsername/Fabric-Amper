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
import java.security.MessageDigest
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
            assertTrue(result.output.contains("STUB Version=0.2.0"), "FA_VERSION for '$args': ${result.output}")
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
    fun `standalone BAT previews missing helper and rejects corrupted helper`() {
        val folder = Files.createDirectories(temporary.resolve("standalone tools"))
        val batch = folder.resolve("fabric-amper.bat")
        Files.copy(producerBatch(), batch)
        val consumer = Files.createDirectory(temporary.resolve("existing consumer"))
        val source = consumer.resolve("build.gradle")
        Files.writeString(source, "// KEEP\n")

        val missingHelper = run(batch, consumer, "migrate --dry-run")
        assertEquals(0, missingHelper.code, missingHelper.output)
        assertTrue(missingHelper.output.contains("Would fetch exactly fabric-amper-migrate.ps1"))
        assertTrue(missingHelper.output.contains("v0.2.0/fabric-amper-migrate.ps1"))
        assertFalse(Files.exists(folder.resolve("scripts")), "Read-only preview must not create scripts directory")
        assertEquals("// KEEP\n", Files.readString(source))

        val scripts = Files.createDirectories(folder.resolve("scripts"))
        val corruptFile = scripts.resolve("fabric-amper-migrate.ps1")
        Files.writeString(corruptFile, "throw 'UNTRUSTED'\n")
        val corruptResult = run(batch, consumer, "migrate --dry-run")
        assertEquals(1, corruptResult.code, corruptResult.output)
        assertTrue(corruptResult.output.contains("SHA-256 mismatch"), corruptResult.output)
        assertEquals("throw 'UNTRUSTED'\n", Files.readString(corruptFile))
        assertEquals("// KEEP\n", Files.readString(source))
    }

    @Test
    fun `matching local helper failure is propagated without changing consumer`() {
        val wrapper = createWrapperWithStub()
        val consumer = Files.createDirectory(temporary.resolve("failure propagation consumer"))
        val source = consumer.resolve("build.gradle.kts")
        Files.writeString(source, "// KEEP\n")
        val result = run(wrapper, consumer, "migrate --dry-run", mapOf("FA_TEST_EXIT_CODE" to "17"))
        assertEquals(17, result.code, result.output)
        assertEquals("// KEEP\n", Files.readString(source))
    }

    @Test
    fun `schema bootstrap remains read only in preview and refuses tampering`() {
        val wrapper = createWrapperWithStub()
        val consumer = Files.createDirectory(temporary.resolve("schema bootstrap consumer"))
        val schema = wrapper.parent.resolve("fabric-amper.schema.json")
        Files.delete(schema)
        val preview = run(wrapper, consumer, "migrate --dry-run")
        assertEquals(0, preview.code, preview.output)
        assertTrue(preview.output.contains("Would fetch exactly fabric-amper.schema.json"), preview.output)
        assertFalse(Files.exists(schema), "Preview must not download a missing schema")
        Files.writeString(schema, "untrusted schema")
        val corrupt = run(wrapper, consumer, "migrate --dry-run")
        assertEquals(1, corrupt.code, corrupt.output)
        assertTrue(corrupt.output.contains("SHA-256 mismatch"), corrupt.output)
        assertEquals("untrusted schema", Files.readString(schema))
    }

    @Test
    fun `migrate helper hash is pinned to exact shipped script`() {
        val root = producerBatch().parent
        val source = root.resolve("scripts/fabric-amper-migrate.ps1")
        val sha = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val batchText = Files.readString(producerBatch(), StandardCharsets.UTF_8)
        assertTrue(batchText.contains("FA_MIGRATE_SHA256=$sha"), "The BAT must pin the actual migration script")
        assertTrue(batchText.contains("$" + "env:FA_HELPER_HASH"), "Download integrity must use the active helper's pinned hash")
    }

    private fun producerBatch(): Path = Path.of(System.getProperty("user.dir"))
        .toAbsolutePath().normalize().resolve("fabric-amper.bat")

    private fun createWrapperWithStub(): Path {
        val folder = Files.createDirectories(temporary.resolve("BAT module with spaces"))
        val batch = folder.resolve("fabric-amper.bat")
        val script = Files.createDirectories(folder.resolve("scripts")).resolve("fabric-amper-migrate.ps1")
        writeStub(script)
        Files.copy(producerBatch().parent.resolve("fabric-amper.schema.json"), folder.resolve("fabric-amper.schema.json"))
        val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(script))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val original = Files.readString(producerBatch(), StandardCharsets.UTF_8)
        val patched = original.replace(Regex("(?m)^set \"FA_MIGRATE_SHA256=[0-9a-f]{64}\""), "set \"FA_MIGRATE_SHA256=$hash\"")
        assertTrue(patched != original, "Test fixture must be able to pin stub hash")
        Files.writeString(batch, patched, StandardCharsets.UTF_8)
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
