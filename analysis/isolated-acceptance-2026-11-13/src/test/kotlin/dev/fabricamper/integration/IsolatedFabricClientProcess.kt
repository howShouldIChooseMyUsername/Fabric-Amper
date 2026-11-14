package dev.fabricamper.integration

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText

/**
 * Native, bounded Gradle launcher for a new consumer inside Fabric Amper's
 * build directory. Never invokes an installed Minecraft profile or server.
 *
 * Gradle TestKit build() is unsuitable for an open-ended runClient: it does not
 * expose an owned client process tree to terminate after the Loader proof.
 * This class tracks the one ProcessBuilder root and its observed descendants;
 * it NEVER kills Java/Gradle processes by name or uses global gradle --stop.
 */
internal class IsolatedFabricClientProcess(
    private val project: Path,
    gradleExecutable: Path,
    private val timeout: Duration = Duration.ofMinutes(7),
) {
    private val root = project.toRealPath()
    private val executable = gradleExecutable.toRealPath()
    private val owned = LinkedHashMap<Long, OwnedHandle>()
    private var launched: Process? = null

    private data class OwnedHandle(
        val handle: ProcessHandle,
        val started: Instant?,
    ) {
        fun stillSameProcess(): Boolean {
            if (!handle.isAlive) return false
            val currentStart = handle.info().startInstant().orElse(null)
            // Do not signal a reused PID (or a PID without a stable identity).
            return started != null && currentStart == started
        }
    }

    init {
        require(Files.isRegularFile(executable)) { "Set an actual absolute Gradle executable: $executable" }
        require(executable.isAbsolute) { "Gradle executable must be absolute" }
        require(executable.fileName.toString().lowercase() in setOf("gradle", "gradle.bat", "gradle.cmd")) {
            "Only an explicit Gradle executable is permitted, not an arbitrary command: $executable"
        }
        require(timeout > Duration.ZERO && timeout <= Duration.ofMinutes(12))
    }

    /**
     * For finite tasks such as build. A nonzero process exit always fails;
     * BUILD SUCCESSFUL must be backed by exit code 0, never by a log match.
     */
    fun completedTask(task: String): String = launch(task, emptyMap(), untilMarkers = false)

    /**
     * For runClient. Marker content is a per-test nonce; the process is
     * terminated immediately after all independently required markers exist.
     * A process exiting before those markers, including with code 0, fails.
     */
    fun clientUntilMarkers(expected: Map<Path, String>): String {
        require(expected.isNotEmpty())
        expected.keys.forEach { file ->
            val normalized = file.toAbsolutePath().normalize()
            require(normalized.startsWith(root)) { "Marker outside isolated consumer: $file" }
            require(!Files.exists(file)) { "Stale marker would produce a false PASS: $file" }
        }
        return launch("runClient", expected, untilMarkers = true)
    }

    private fun launch(task: String, markers: Map<Path, String>, untilMarkers: Boolean): String {
        require(task in setOf("build", "runClient")) { "Only approved isolated tasks may be launched" }
        require(launched == null) { "One native Gradle process per fixture supervisor" }
        val home = Files.createDirectories(root.resolve("sandbox-home"))
        val appData = Files.createDirectories(root.resolve("sandbox-appdata"))
        val localAppData = Files.createDirectories(root.resolve("sandbox-localappdata"))
        val gradleHome = Files.createDirectories(root.resolve("sandbox-gradle-home"))
        val logs = Files.createDirectories(root.resolve("logs"))
        val log = logs.resolve("$task.log")
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val gradleCommand = listOf("--no-daemon", "--console=plain", "--stacktrace", task)
        val command = if (isWindows) {
            require(executable.fileName.toString().endsWith(".bat", true) ||
                executable.fileName.toString().endsWith(".cmd", true)) {
                "Windows needs an explicit Gradle .bat or .cmd launcher"
            }
            val batch = "\"${executable.toAbsolutePath()}\" ${gradleCommand.joinToString(" ")}"
            listOf(System.getenv("ComSpec") ?: "cmd.exe", "/d", "/s", "/c", "\"${batch}\"")
        } else {
            require(executable.fileName.toString() == "gradle") { "Expected native Gradle launcher" }
            listOf(executable.toString()) + gradleCommand
        }
        val builder = ProcessBuilder(command)
            .directory(root.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
        builder.environment().apply {
            this["GRADLE_USER_HOME"] = gradleHome.toString()
            this["HOME"] = home.toString()
            this["USERPROFILE"] = home.toString()
            this["APPDATA"] = appData.toString()
            this["LOCALAPPDATA"] = localAppData.toString()
            this["XDG_CONFIG_HOME"] = Files.createDirectories(home.resolve(".config")).toString()
            this["XDG_DATA_HOME"] = Files.createDirectories(home.resolve(".local/share")).toString()
            // Propagates to Gradle and its game JVM: never consult the actual
            // account's ~/.minecraft or OS profile via Java's user.home.
            this["JAVA_TOOL_OPTIONS"] = "-Duser.home=\"${home.toAbsolutePath()}\""
        }

        val process = builder.start()
        launched = process
        record(process.toHandle())
        val shutdownHook = Thread({ terminateOwnedTree(process) }, "fabric-amper-isolated-client-cleanup")
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        try {
            val deadline = System.nanoTime() + timeout.toNanos()
            while (System.nanoTime() < deadline) {
                captureDescendants(process)
                if (untilMarkers && markers.all { (file, value) ->
                        Files.isRegularFile(file) && Files.readString(file, StandardCharsets.UTF_8) == value
                    }) {
                    return logTail(log)
                }
                if (!process.isAlive) {
                    val exit = process.exitValue()
                    if (!untilMarkers && exit == 0) return logTail(log)
                    throw AssertionError(
                        "$task finished before required proof (exit=$exit, markers=${markers.keys}):\n${logTail(log)}",
                    )
                }
                Thread.sleep(150)
            }
            throw AssertionError("$task exceeded the isolated runtime deadline:\n${logTail(log)}")
        } finally {
            terminateOwnedTree(process)
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook)
            } catch (_: IllegalStateException) {
                // JVM shutdown hook may already be executing.
            }
        }
    }

    private fun record(handle: ProcessHandle) {
        owned.putIfAbsent(
            handle.pid(),
            OwnedHandle(handle, handle.info().startInstant().orElse(null)),
        )
    }

    private fun captureDescendants(process: Process) {
        process.descendants().use { descendants -> descendants.forEach(::record) }
    }

    /** Signal children before their tracked parent; force only confirmed owned PIDs. */
    @Synchronized
    private fun terminateOwnedTree(process: Process) {
        captureDescendants(process)
        val children = owned.values.toList().asReversed()
        for (child in children) if (child.stillSameProcess()) child.handle.destroy()
        if (process.isAlive) process.destroy()
        try {
            process.waitFor(2, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        // Do not signal a PID which has exited/reused; no process-name matching.
        for (child in children) if (child.stillSameProcess()) child.handle.destroyForcibly()
        if (process.isAlive) process.destroyForcibly()
        try {
            process.waitFor(2, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun logTail(path: Path): String {
        if (!Files.isRegularFile(path)) return "(no Gradle output captured)"
        val text = path.readText(StandardCharsets.UTF_8)
        return text.takeLast(24_000)
    }
}
