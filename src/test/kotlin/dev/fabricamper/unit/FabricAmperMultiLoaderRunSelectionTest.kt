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

    @Test
    fun dualLoaderValidateVerifyAndBuildDispatchExpectedPlatformTasks() {
        val dual = consumer("Dual Build", """
            schema: "1.1"
            product: [fabric/mod, neoforge/mod]
        """.trimIndent())
        assertTasks(dual, invoke(dual, "dual-validate", "validate"),
            ":fabric:validateFabricAmper", ":neoforge:validateFabricAmper")
        assertTasks(dual, invoke(dual, "dual-verify", "verify"),
            ":fabric:verifyFabricAmperJar", ":neoforge:verifyFabricAmperJar")
        assertTasks(dual, invoke(dual, "dual-build", "build"),
            ":fabric:validateFabricAmper", ":neoforge:validateFabricAmper",
            // The root build task aggregates both child builds; do not force
            // separate :fabric:build/:neoforge:build invocations.
            "build",
            ":fabric:verifyFabricAmperJar", ":neoforge:verifyFabricAmperJar")
    }

    @Test
    fun singleLoaderValidateVerifyAndBuildKeepOriginalUnqualifiedTasks() {
        val consumers = listOf(
            consumer("Single Build Fabric", "schema: 1\nproduct: fabric/mod"),
            consumer("Single Build NeoForge", "schema: \"1.1\"\nproduct: neoforge/mod"),
        )
        for (root in consumers) {
            assertTasks(root, invoke(root, "single-validate", "validate"),
                "validateFabricAmper")
            assertTasks(root, invoke(root, "single-verify", "verify"),
                "verifyFabricAmperJar")
            assertTasks(root, invoke(root, "single-build", "build"),
                "validateFabricAmper", "build", "verifyFabricAmperJar")
        }
    }

    @Test
    fun malformedAndMaliciousYamlNeverInvokesBuildValidationOrVerification() {
        val cases = listOf(
            // Duplicate root product must never be accepted by a superficial
            // first-match scanner, even if the first product is valid.
            "Duplicate Product" to """
                schema: "1.1"
                product: [fabric/mod, neoforge/mod]
                product: fabric/mod
            """.trimIndent(),
            // A malformed flow sequence looks like an intended dual product,
            // but must not be treated as a single loader or raw Gradle syntax.
            "Broken Product Array" to """
                schema: "1.1"
                product: [fabric/mod, neoforge/mod
            """.trimIndent(),
            // YAML floating-point 1.1 is not the quoted schema string.
            "Unquoted Schema" to """
                schema: 1.1
                product: [fabric/mod, neoforge/mod]
            """.trimIndent(),
            "Unknown Product" to """
                schema: "1.1"
                product: [fabric/mod, evil/mod]
            """.trimIndent(),
        )
        for ((name, yaml) in cases) {
            val root = consumer(name, yaml)
            for (command in listOf("validate", "verify", "build")) {
                reject(root, command)
            }
        }

        val invalidUtf8 = consumer("Invalid UTF8", "schema: \"1.1\"\nproduct: fabric/mod")
        Files.write(invalidUtf8.resolve("mod.yaml"), byteArrayOf(
            0x73, 0x63, 0x68, 0x65, 0x6d, 0x61, 0x3a, 0x20,
            0x22, 0x31, 0x2e, 0x31, 0x22, 0x0a,
            0x70, 0x72, 0x6f, 0x64, 0x75, 0x63, 0x74, 0x3a,
            0x20, 0xc3.toByte(), 0x28,
        ))
        for (command in listOf("validate", "verify", "build")) {
            reject(invalidUtf8, command)
        }
    }

    @Test
    fun buildCommandsRejectUntrustedExtraGradleArgumentsBeforeInvokingAnyTask() {
        val dual = consumer("Dual Untrusted Build Args", """
            schema: "1.1"
            product: [fabric/mod, neoforge/mod]
        """.trimIndent())
        val single = consumer("Single Untrusted Build Args", "schema: 1\nproduct: fabric/mod")
        for (root in listOf(dual, single)) {
            for (command in listOf("validate", "verify", "build")) {
                reject(root, command, "--offline")
                reject(root, command, "--init-script", "untrusted.gradle")
                reject(root, command, "unexpected")
            }
        }
    }

    private fun consumer(name: String, yaml: String): Path {
        val root = Files.createDirectories(workspace.resolve(name))
        Files.writeString(root.resolve("settings.gradle.kts"),
            """plugins { id("dev.fabric-amper.settings") version "0.3.1" }""" + "\n")
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

    private fun assertTasks(root: Path, result: Result, vararg expected: String) {
        assertEquals(0, result.code, result.output)
        val invocations = invocationLines(root)
        assertTrue(invocations.isNotEmpty(), "No fake Gradle invocation: ${result.output}")
        val tasks = invocations.flatMap { line -> line.split(Regex("\\s+")) }
        // Gradle accepts a single invocation with many tasks or multiple
        // sequential invocations. The exact dispatched task multiset matters;
        // order is not specified by the wrapper's public command contract.
        assertEquals(expected.toList().sorted(), tasks.sorted(), result.output)
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
