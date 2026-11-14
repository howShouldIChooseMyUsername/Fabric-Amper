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
            assertTrue(Files.isRegularFile(consumer.resolve("settings.gradle.kts")))
            assertTrue(Files.isRegularFile(consumer.resolve("src/main/java/org/example/ExampleMod.java")))
            assertFalse(Files.exists(consumer.resolve("build.gradle")))
            assertFalse(Files.exists(consumer.resolve("build.gradle.kts")))
            assertFalse(Files.exists(consumer.resolve("src/main/resources/fabric.mod.json")))

            val secondRun = runInit(batch, id, consumer, profile)
            assertEquals(3, secondRun.first, "init must refuse to overwrite an existing consumer: ${secondRun.second}")
            assertEquals(yaml, Files.readString(yamlFile, StandardCharsets.UTF_8))
            assertArrayEquals(originalSchema, Files.readAllBytes(consumer.resolve("schemas/mod.schema.json")))
        }
    }

    private fun runInit(batch: Path, id: String, target: Path, profile: String): Pair<Int, String> {
        val command = "\"$batch\" init $id \"$target\" $profile"
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
