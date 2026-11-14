package dev.fabricamper.unit

import dev.fabricamper.config.FabricAmperSchemaVersion
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
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Real Windows batch init without Gradle builds, downloads or remote publishing. */
@EnabledOnOs(OS.WINDOWS)
class FabricAmperInitSchemaUnitTest {
    @Test
    fun `init copies current producer schema and omits pinned YAML schema for mod and api`() {
        val producer = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val batch = producer.resolve("fabric-amper.bat")
        val originalSchema = Files.readAllBytes(producer.resolve("fabric-amper.schema.json"))
        val testRoot = Files.createTempDirectory(Files.createDirectories(producer.resolve("build")), "fabric-amper-init-schema-")

        for ((profile, id) in listOf("mod" to "autocompletemod", "api" to "autocompleteapi")) {
            val consumer = testRoot.resolve(profile)
            val firstRun = runInit(batch, id, consumer, profile)
            assertEquals(0, firstRun.first, "init $profile must succeed: ${firstRun.second}")

            val yamlFile = consumer.resolve("mod.yaml")
            val yaml = Files.readString(yamlFile, StandardCharsets.UTF_8)
            assertEquals("# yaml-language-server: \$schema=./schemas/mod.schema.json", yaml.lineSequence().first())
            val effective = ModYamlLoader.load(yamlFile)
            assertEquals(FabricAmperSchemaVersion.LATEST, effective.schema)
            assertEquals("fabric/$profile", effective.product)
            assertEquals(id, effective.mod.id)
            assertFalse(Regex("(?m)^schema:").containsMatchIn(yaml), "init must not pin schema: 1")
            assertArrayEquals(originalSchema, Files.readAllBytes(consumer.resolve("schemas/mod.schema.json")))
            assertTrue(Files.isRegularFile(consumer.resolve(".vscode/settings.json")))
            assertTrue(
                Files.readString(consumer.resolve(".vscode/settings.json")).contains("./schemas/mod.schema.json"),
                "init must provide a VS Code mapping in addition to the YAML language-server comment",
            )
            assertTrue(Files.isRegularFile(consumer.resolve("settings.gradle.kts")))
            val settings = Files.readString(consumer.resolve("settings.gradle.kts"), StandardCharsets.UTF_8)
            assertTrue(settings.contains("maven(\"https://jitpack.io\")"))
            assertTrue(settings.contains("maven(\"https://maven.fabricmc.net/\")"))
            assertTrue(settings.contains("gradlePluginPortal()"))
            assertTrue(settings.contains("mavenCentral()"))
            assertTrue(settings.contains("resolutionStrategy {"))
            assertTrue(settings.contains("if (requested.id.id == \"dev.fabric-amper.settings\") {"))
            assertTrue(settings.contains("useModule(\"com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.2\")"))
            assertTrue(settings.contains("id(\"dev.fabric-amper.settings\") version \"0.1.2\""))
            assertFalse(settings.contains("build/g29-probe"), "init must not rely on local Maven setup")
            assertFalse(settings.contains("https://fabricmc.net\""), "Fabric Maven requires maven.fabricmc.net")
            assertTrue(Files.isRegularFile(consumer.resolve("src/main/java/org/example/ExampleMod.java")))
            assertFalse(Files.exists(consumer.resolve("build.gradle")))
            assertFalse(Files.exists(consumer.resolve("build.gradle.kts")))
            assertFalse(Files.exists(consumer.resolve("src/main/resources/fabric.mod.json")))

            val secondRun = runInit(batch, id, consumer, profile)
            assertEquals(3, secondRun.first, "init must refuse to overwrite an existing consumer: ${secondRun.second}")
            assertEquals(yaml, Files.readString(yamlFile, StandardCharsets.UTF_8))
            assertArrayEquals(originalSchema, Files.readAllBytes(consumer.resolve("schemas/mod.schema.json")))
        }

        val removedSetup = runBat(batch, "setup")
        assertEquals(2, removedSetup.first, "setup must no longer run a producer publication")
        assertTrue(removedSetup.second.contains("Unknown command"))
        val help = runBat(batch, "help")
        assertEquals(0, help.first)
        assertFalse(Regex("(?m)^\\s*fabric-amper\\.bat setup\\s*$").containsMatchIn(help.second))
    }

    @Test
    fun `init here preserves tools and refuses any existing consumer project`() {
        val root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val batch = root.resolve("fabric-amper.bat")
        val originalSchema = Files.readAllBytes(root.resolve("fabric-amper.schema.json"))
        val temp = Files.createTempDirectory(Files.createDirectories(root.resolve("build")), "fabric-amper-init-here-")

        for (profile in listOf("mod", "api")) {
            val folder = Files.createDirectory(temp.resolve("tool-only-$profile"))
            val tool = folder.resolve("fabric-amper.bat")
            Files.writeString(tool, "existing batch tools")
            Files.writeString(folder.resolve("FabricAmperSKILL.md"), "existing agent skill")
            Files.writeString(folder.resolve(".gitignore"), "existing ignore rules")
            Files.createDirectory(folder.resolve(".vscode"))
            val editorSettings = folder.resolve(".vscode/settings.json")
            Files.writeString(editorSettings, "{\"editor.tabSize\":4}")

            val first = runBat(batch, "-C \"$folder\" init night_vision --here $profile")
            assertEquals(0, first.first, "init --here $profile must succeed: ${first.second}")
            assertEquals("existing batch tools", Files.readString(tool))
            assertEquals("existing agent skill", Files.readString(folder.resolve("FabricAmperSKILL.md")))
            assertEquals("existing ignore rules", Files.readString(folder.resolve(".gitignore")))
            assertEquals("{\"editor.tabSize\":4}", Files.readString(editorSettings), "do not overwrite VS Code settings")
            assertEquals("# yaml-language-server: \$schema=./schemas/mod.schema.json", Files.readAllLines(folder.resolve("mod.yaml"))[0])
            assertArrayEquals(originalSchema, Files.readAllBytes(folder.resolve("schemas/mod.schema.json")))
            assertEquals("fabric/$profile", ModYamlLoader.load(folder.resolve("mod.yaml")).product)

            val second = runBat(batch, "-C \"$folder\" init night_vision --here $profile")
            assertEquals(3, second.first, "init --here must refuse an existing project")
        }

        for (conflict in listOf("mod.yaml", "mod.yml", "settings.gradle.kts", "build.gradle.kts", "build.gradle", "src", "schemas/mod.schema.json")) {
            val folder = Files.createDirectory(temp.resolve("conflict-${conflict.replace('/', '-').replace('.', '-')}"))
            val existing = folder.resolve(conflict)
            Files.createDirectories(existing.parent)
            if (conflict == "src") Files.createDirectory(existing) else Files.writeString(existing, "KEEP")
            val result = runBat(batch, "-C \"$folder\" init night_vision --here")
            assertEquals(3, result.first, "Expected hard refusal for $conflict: ${result.second}")
            if (conflict != "src") assertEquals("KEEP", Files.readString(existing))
            assertFalse(Files.exists(folder.resolve("src/main/java/org/example/ExampleMod.java")))
            assertFalse(Files.exists(folder.resolve(".vscode/settings.json")))
        }
        assertEquals(2, runBat(batch, "init night_vision --here mod extra").first)
        assertEquals(2, runBat(batch, "init night_vision other-folder --here").first)
    }

    @Test
    fun `doctor fix dry-run previews repair without installing or writing`() {
        val root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val batch = root.resolve("fabric-amper.bat")
        val folder = Files.createTempDirectory(Files.createDirectories(root.resolve("build")), "fabric-amper-fix-preview-")
        Files.writeString(folder.resolve("mod.yaml"), "product: fabric/mod\n")
        val preview = runBat(batch, "-C \"$folder\" doctor --fix --dry-run")
        assertEquals(0, preview.first, "Dry-run should not need network/winget: ${preview.second}")
        assertTrue(preview.second.contains("READ-ONLY PREVIEW"))
        assertTrue(preview.second.contains("Would create missing settings.gradle.kts"))
        assertTrue(preview.second.contains("Would copy missing schemas/mod.schema.json"))
        assertTrue(preview.second.contains("Would create missing .vscode/settings.json"))
        assertFalse(Files.exists(folder.resolve("settings.gradle.kts")))
        assertFalse(Files.exists(folder.resolve("schemas/mod.schema.json")))
        assertFalse(Files.exists(folder.resolve(".vscode/settings.json")))

        val powershell = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-File", root.resolve("fabric-amper-doctor-fix.ps1").toString(),
            "-Preview", "-AssumeMissing",
        ).directory(root.toFile()).redirectErrorStream(true)
        powershell.environment()["FA_ROOT"] = root.toString()
        powershell.environment()["FA_CONSUMER"] = folder.toString()
        powershell.environment()["FA_VERSION"] = "0.1.2"
        val process = powershell.start()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Forced-missing preview must not start a download")
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.exitValue(), output)
        assertTrue(output.contains("winget EclipseAdoptium.Temurin.25.JDK"), output)
        assertTrue(output.contains("official Gradle 9.7.1 ZIP with SHA-256"), output)
        assertFalse(Files.exists(folder.resolve("settings.gradle.kts")))
    }

    @Test
    fun `doctor fix standalone BAT previews exact release download and rejects corrupted helper`() {
        val root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val batSource = root.resolve("fabric-amper.bat")
        val scriptSource = root.resolve("fabric-amper-doctor-fix.ps1")
        val expectedHash = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(scriptSource))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val batchSource = Files.readString(batSource, StandardCharsets.UTF_8)
        assertTrue(batchSource.contains("FA_DOCTOR_SHA256=$expectedHash"), "BAT must pin the exact release PS1")
        assertTrue(
            batchSource.contains("releases/download/v'+\$env:FA_VERSION+'/fabric-amper-doctor-fix.ps1"),
            "Downloader must use this BAT version's GitHub Release asset",
        )

        val folder = Files.createTempDirectory(Files.createDirectories(root.resolve("build")), "fabric-amper-remote-helper-")
        val batch = folder.resolve("fabric-amper.bat")
        Files.copy(batSource, batch)
        val missingPreview = runBat(batch, "doctor --fix --dry-run")
        assertEquals(0, missingPreview.first, "Missing helper must preview without fetching: ${missingPreview.second}")
        assertTrue(missingPreview.second.contains("Would fetch exactly fabric-amper-doctor-fix.ps1"))
        assertTrue(missingPreview.second.contains("/releases/download/v0.1.2/fabric-amper-doctor-fix.ps1"))
        assertFalse(Files.exists(folder.resolve("fabric-amper-doctor-fix.ps1")))
        assertEquals(2, runBat(batch, "doctor --fix --unknown").first)

        val script = folder.resolve("fabric-amper-doctor-fix.ps1")
        val corrupted = "throw 'UNTRUSTED SCRIPT'\n"
        Files.writeString(script, corrupted)
        val corruptPreview = runBat(batch, "doctor --fix --dry-run")
        assertEquals(1, corruptPreview.first, "Existing unexpected PS1 must never execute: ${corruptPreview.second}")
        assertTrue(corruptPreview.second.contains("SHA-256 mismatch"))
        assertEquals(corrupted, Files.readString(script), "Corrupted helper must not be silently overwritten")
    }

    private fun runInit(batch: Path, id: String, target: Path, profile: String): Pair<Int, String> {
        return runBat(batch, "init $id \"$target\" $profile")
    }

    private fun runBat(batch: Path, args: String): Pair<Int, String> {
        val command = "\"$batch\" $args"
        val process = ProcessBuilder("cmd.exe", "/d", "/s", "/c", command)
            .directory(batch.parent.toFile())
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(30, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw AssertionError("Windows init did not terminate in 30 seconds")
        }
        return process.exitValue() to process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
    }
}
