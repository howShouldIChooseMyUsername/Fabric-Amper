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

/**
 * The Windows BAT may select a Gradle *task*, but it must not launch Gradle
 * during this test. Every consumer owns an inert gradlew.bat that only records
 * its arguments. FABRIC_AMPER_GRADLE additionally pins the launcher to this
 * stub so no cached/system Gradle is ever selected.
 */
@EnabledOnOs(OS.WINDOWS)
class FabricAmperMultiLoaderRunSelectionTest {
    @TempDir lateinit var workspace: Path

    private val wrapper: Path
        get() = Path.of(System.getProperty("user.dir"))
            .toAbsolutePath().normalize().resolve("fabric-amper.bat")

    @Test
    fun dualLoaderRequiresExplicitSelectorAndRoutesEachSideToOnlyOneTask() {
        val consumer = consumer("Dual Consumers", """
            schema: "1.1"
            product: [fabric/mod, neoforge/mod]
        """.trimIndent())

        for (command in listOf("run", "server")) {
            reject(consumer, command)
            val suffix = if (command == "run") "Client" else "Server"
            invoke(consumer, "run-selector-$command-fabric", command, "--mod-fabric")
                .also { assertTask(consumer, it, "runFabric$suffix") }
            invoke(consumer, "run-selector-$command-neoforge", command, "--mod-neoforge")
                .also { assertTask(consumer, it, "runNeoForge$suffix") }
        }
    }

    @Test
    fun singleLoaderKeepsLegacyTaskWithNoSelectorAndAcceptsOnlyMatchingFlag() {
        val fabric = consumer("Fabric Consumer", "product: fabric/mod")
        val neo = consumer("NeoForge Consumer", """
            schema: "1.1"
            product: neoforge/mod
        """.trimIndent())

        for (command in listOf("run", "server")) {
            val task = if (command == "run") "runClient" else "runServer"
            assertTask(fabric, invoke(fabric, command, command), task)
            assertTask(fabric, invoke(fabric, "matching-$command", command, "--mod-fabric"), task)
            assertTask(neo, invoke(neo, command, command), task)
            assertTask(neo, invoke(neo, "matching-$command", command, "--mod-neoforge"), task)
            reject(fabric, command, "--mod-neoforge")
            reject(neo, command, "--mod-fabric")
        }
    }

    @Test
    fun arbitraryFlagsDuplicateSelectorsAndExtraArgumentsCannotReachGradleStub() {
        val dual = consumer("Strict Dual", """
            schema: "1.1"
            product: [fabric/mod, neoforge/mod]
        """.trimIndent())
        for (command in listOf("run", "server")) {
            reject(dual, command, "--offline")
            reject(dual, command, "--mod-fabric", "--offline")
            reject(dual, command, "--mod-neoforge", "unexpected")
            reject(dual, command, "--mod-fabric", "--mod-neoforge")
            reject(dual, command, "--mod-fabric", "--mod-fabric")
            reject(dual, command, "unexpected")
        }
    }

    private fun consumer(name: String, yaml: String): Path {
        val root = Files.createDirectories(workspace.resolve(name))
        Files.writeString(root.resolve("settings.gradle.kts"),
            """plugins { id("dev.fabric-amper.settings") version "0.1.3" }""" + "\n")
        Files.writeString(root.resolve("mod.yaml"), yaml + "\n", StandardCharsets.UTF_8)
        Files.writeString(root.resolve("gradlew.bat"), """
            @echo off
            >>"%~dp0gradle-invocations.txt" echo %*
            exit /b 0
        """.trimIndent().replace("\n", "\r\n") + "\r\n", StandardCharsets.US_ASCII)
        return root
    }

    private data class Result(val code: Int, val output: String)

    private fun invoke(root: Path, label: String, vararg args: String): Result {
        require(Files.isRegularFile(wrapper)) { "Missing BAT: $wrapper" }
        val cli = args.joinToString(" ")
        // All argument tokens here are fixed test literals; the consumer path
        // is quoted, including intentionally spaced directory names.
        val command = "\"\"$wrapper\" -C \"$root\" $cli\""
        val process = ProcessBuilder("cmd.exe", "/d", "/s", "/c", command)
            .directory(workspace.toFile())
            .redirectErrorStream(true)
            .apply {
                environment()["FABRIC_AMPER_GRADLE"] = root.resolve("gradlew.bat").toString()
            }.start()
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw AssertionError("$label timed out: $command")
        }
        return Result(process.exitValue(), process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText())
    }

    private fun assertTask(root: Path, result: Result, expected: String) {
        assertEquals(0, result.code, result.output)
        val invocations = invocationLines(root)
        assertEquals(listOf(expected), invocations, result.output)
        Files.delete(root.resolve("gradle-invocations.txt"))
    }

    private fun reject(root: Path, vararg args: String) {
        assertFalse(Files.exists(root.resolve("gradle-invocations.txt")),
            "Previous invocation file must be cleared before negative case")
        val result = invoke(root, "rejected-${args.joinToString("-")}", *args)
        assertEquals(2, result.code, result.output)
        assertTrue(invocationLines(root).isEmpty(),
            "Rejected selection invoked the Gradle stub: ${invocationLines(root)}\n${result.output}")
    }

    private fun invocationLines(root: Path): List<String> {
        val record = root.resolve("gradle-invocations.txt")
        return if (Files.isRegularFile(record)) {
            Files.readAllLines(record, StandardCharsets.US_ASCII).map { it.trim() }
                .filter { it.isNotEmpty() }
        } else emptyList()
    }
}
