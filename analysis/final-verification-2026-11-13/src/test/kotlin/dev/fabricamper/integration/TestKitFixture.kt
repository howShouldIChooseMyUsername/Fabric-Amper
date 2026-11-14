package dev.fabricamper.integration

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/** No consumer build.gradle(.kts), init script, external world, or pre-existing user project. */
internal class TestKitFixture(private val suiteRoot: Path) {
    // Gradle can retain native DLL and cache lock handles on Windows after the
    // test completes. Keep these caches outside JUnit's automatically deleted
    // @TempDir; only disposable consumer projects belong under suiteRoot.
    private val cacheRoot = Path.of(System.getProperty("user.dir"))
        .toAbsolutePath().normalize()
        .resolve("build/fabric-amper-testkit-cache/pid-${ProcessHandle.current().pid()}")
    private val testKitDir = cacheRoot.resolve("testkit-home").also { Files.createDirectories(it) }

    fun project(prefix: String, yaml: String = resource("minimal-mod.yaml"), alias: String = "mod.yaml"): Path {
        val project = Files.createTempDirectory(suiteRoot, "$prefix-")
        write(project, "settings.gradle.kts", "plugins { id(\"dev.fabric-amper.settings\") }\n")
        write(project, alias, yaml)
        check(!project.resolve("build.gradle").exists() && !project.resolve("build.gradle.kts").exists())
        return project
    }

    fun run(project: Path, vararg args: String): BuildResult = runner(project, *args).build()

    fun fail(project: Path, vararg args: String): BuildResult = runner(project, *args).buildAndFail()

    /**
     * Cross-Gradle matrix probe. The TestKit home is separate for every named
     * Gradle/Loom pair, and always outside JUnit's auto-deleted @TempDir.
     * A variant-resolution-only probe must NOT inject the already resolved
     * plugin classpath or it would bypass Gradle's published .module metadata.
     */
    fun runWithGradleVersion(
        project: Path, gradleVersion: String, fixtureKey: String, vararg args: String,
        pluginClasspath: Boolean = true,
    ): BuildResult = matrixRunner(project, gradleVersion, fixtureKey, args, pluginClasspath).build()

    fun failWithGradleVersion(
        project: Path, gradleVersion: String, fixtureKey: String, vararg args: String,
        pluginClasspath: Boolean = true,
    ): BuildResult = matrixRunner(project, gradleVersion, fixtureKey, args, pluginClasspath).buildAndFail()

    /** Parallel G20 runs share only the consumer root, never Gradle TestKit homes. */
    fun runWithIsolatedTestKitHome(
        project: Path, fixtureKey: String, vararg args: String,
    ): BuildResult {
        val variantHome = isolatedTestKitHome("parallel-$fixtureKey")
        return GradleRunner.create()
            .withProjectDir(project.toFile())
            .withPluginClasspath()
            .withTestKitDir(variantHome.toFile())
            .withArguments(*(listOf("--console=plain", "--stacktrace") + args.toList()).toTypedArray())
            .forwardOutput()
            .build()
    }

    private fun matrixRunner(
        project: Path, gradleVersion: String, fixtureKey: String,
        args: Array<out String>, pluginClasspath: Boolean,
    ): GradleRunner {
        require(gradleVersion in setOf("9.7.0", "9.8.0", "9.9.1")) {
            "Unsupported matrix Gradle version: $gradleVersion"
        }
        val variantHome = isolatedTestKitHome("matrix-$fixtureKey")
        val candidate = GradleRunner.create()
            .withProjectDir(project.toFile())
            .withGradleVersion(gradleVersion)
            .withTestKitDir(variantHome.toFile())
            .withArguments(*(listOf("--console=plain", "--stacktrace") + args.toList()).toTypedArray())
            .forwardOutput()
        return if (pluginClasspath) candidate.withPluginClasspath() else candidate
    }

    private fun isolatedTestKitHome(key: String): Path {
        require(Regex("[a-zA-Z0-9_-]{1,72}").matches(key)) {
            "TestKit cache key must be a simple, separate folder name"
        }
        return cacheRoot.resolve("$key/testkit-home").also { Files.createDirectories(it) }
    }

    private fun runner(project: Path, vararg args: String): GradleRunner = GradleRunner.create()
        .withProjectDir(project.toFile())
        .withPluginClasspath()
        .withTestKitDir(testKitDir.toFile())
        .withArguments(
            *(listOf("--console=plain", "--stacktrace") + args.toList()).toTypedArray(),
        )
        .forwardOutput()

    fun write(project: Path, relative: String, text: String) {
        val file = project.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text, StandardCharsets.UTF_8)
    }

    fun writeBytes(project: Path, relative: String, bytes: ByteArray) {
        val file = project.resolve(relative)
        Files.createDirectories(file.parent)
        Files.write(file, bytes)
    }

    fun yaml(project: Path, alias: String = "mod.yaml"): String = project.resolve(alias).readText()

    fun jars(project: Path): List<Path> {
        val folder = project.resolve("build/libs")
        if (!folder.exists()) return emptyList()
        return Files.list(folder).use { paths ->
            paths.filter { it.name.endsWith(".jar") && !it.name.endsWith("-sources.jar") &&
                !it.name.endsWith("-dev.jar") }.toList()
        }
    }

    fun mirrorJson(project: Path): List<Path> {
        val root = project.resolve(".gradle/fabric-amper/loom")
        if (!root.exists()) return emptyList()
        return Files.walk(root).use { paths ->
            paths.filter { it.name == "fabric.mod.json" && Files.isRegularFile(it) }.toList()
        }
    }

    fun generatedJson(project: Path): Path =
        project.resolve("build/generated/fabric-amper/resources/main/fabric.mod.json")

    fun jarRootJson(jar: Path): Pair<Int, ByteArray> = ZipFile(jar.toFile()).use { zip ->
        val entries = zip.entries().asSequence().filter { it.name == "fabric.mod.json" }.toList()
        entries.size to (entries.firstOrNull()?.let {
            zip.getInputStream(it).use { input -> input.readAllBytes() }
        } ?: ByteArray(0))
    }

    fun resource(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/integration/$name")) { "Missing fixture $name" }
            .bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

    fun assertNoConsumerBuildScript(project: Path) {
        check(!project.resolve("build.gradle").exists())
        check(!project.resolve("build.gradle.kts").exists())
        val settings = project.resolve("settings.gradle.kts").readText()
        check(settings.contains("dev.fabric-amper.settings"))
        check(!settings.contains("net.fabricmc.fabric-loom") &&
            !settings.contains("org.jetbrains.kotlin.jvm"))
    }

    companion object {
        // ImageIO writes valid PNG chunks and CRCs; an earlier Base64 fixture
        // had a corrupt IDAT CRC and could make G24 report unrelated failures.
        val onePixelPng: ByteArray = ByteArrayOutputStream().use { output ->
            val pixel = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
            pixel.setRGB(0, 0, 0xFF336699.toInt())
            check(ImageIO.write(pixel, "png", output)) { "A PNG ImageIO writer is required by the test fixture" }
            output.toByteArray()
        }
    }
}
