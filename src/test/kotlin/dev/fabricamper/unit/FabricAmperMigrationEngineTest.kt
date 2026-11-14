package dev.fabricamper.unit

import dev.fabricamper.config.ModYamlLoader
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
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

/** Real Windows BAT -> actual PS1 migration. All consumer input lives in @TempDir. */
@EnabledOnOs(OS.WINDOWS)
class FabricAmperMigrationEngineTest {
    @TempDir lateinit var temp: Path

    private data class Run(val code: Int, val output: String)
    private data class Fixture(val root: Path, val kotlin: Boolean) {
        val modId = if (kotlin) "kotlin_engine" else "groovy_engine"
        val version = if (kotlin) "1.2.3" else "2.3.4"
        val build = if (kotlin) "build.gradle.kts" else "build.gradle"
        val settings = if (kotlin) "settings.gradle.kts" else "settings.gradle"
        val metadata = "src/main/resources/fabric.mod.json"
        val resource = "src/main/resources/assets/$modId/lang/en_us.json"
        val javaSource = "src/main/java/org/example/ExampleMod.java"
    }

    @Test
    fun `Kotlin DSL backup migration preserves original bytes and sources`() {
        val bat = launcher()
        val fixture = fixture("kotlin backup", true)
        val original = snapshot(fixture.root)
        val savedBuild = Files.readAllBytes(fixture.root.resolve(fixture.build))
        val savedSettings = Files.readAllBytes(fixture.root.resolve(fixture.settings))
        val savedMetadata = Files.readAllBytes(fixture.root.resolve(fixture.metadata))

        val run = run(bat, fixture.root, "migrate")
        assertEquals(0, run.code, run.output)
        assertTrue(run.output.contains("[OK] Created mod.yaml"), run.output)
        val backup = fixture.root.resolve("gradle_backup")
        assertArrayEquals(savedBuild, Files.readAllBytes(backup.resolve(fixture.build)))
        assertArrayEquals(savedSettings, Files.readAllBytes(backup.resolve(fixture.settings)))
        assertArrayEquals(savedMetadata, Files.readAllBytes(backup.resolve("fabric.mod.json")))
        assertFalse(Files.exists(fixture.root.resolve(fixture.build)))
        assertFalse(Files.exists(fixture.root.resolve(fixture.metadata)))
        assertMigrated(fixture)
        assertTrue(Files.readString(fixture.root.resolve("settings.gradle.kts")).contains("Fabric-Amper:v0.1.3"))
        assertPreserved(fixture, original)
        assertNoStage(fixture.root)
    }

    @Test
    fun `Groovy DSL delete migration creates valid consumer without backup`() {
        val bat = launcher()
        val fixture = fixture("groovy project & spaces", false)
        val original = snapshot(fixture.root)
        val run = run(bat, temp, "-C \"${fixture.root}\" migrate --delete")
        assertEquals(0, run.code, run.output)
        assertTrue(run.output.contains("[OK] Created mod.yaml"), run.output)
        assertFalse(Files.exists(fixture.root.resolve("gradle_backup")))
        assertFalse(Files.exists(fixture.root.resolve(fixture.build)))
        assertFalse(Files.exists(fixture.root.resolve(fixture.settings)))
        assertFalse(Files.exists(fixture.root.resolve(fixture.metadata)))
        assertMigrated(fixture)
        assertPreserved(fixture, original)
        assertNoStage(fixture.root)
    }

    @Test
    fun `dry run and dry run with delete are byte for byte immutable`() {
        val bat = launcher()
        val fixture = fixture("dry run consumer", true)
        val before = snapshot(fixture.root)
        val calls = listOf(
            "migrate -C \"${fixture.root}\" --dry-run",
            "--project \"${fixture.root}\" migrate -D --dry-run",
        )
        for (args in calls) {
            val run = run(bat, temp, args)
            assertEquals(0, run.code, "$args: ${run.output}")
            assertTrue(run.output.contains("[preview] Validation complete"), run.output)
            assertEquals(before, snapshot(fixture.root), "Dry-run modified ${fixture.root}")
            assertFalse(Files.exists(fixture.root.resolve("mod.yaml")))
            assertFalse(Files.exists(fixture.root.resolve("gradle_backup")))
            assertNoStage(fixture.root)
        }
    }

    @Test
    fun `preexisting outputs backups and duplicate DSL inputs fail without changes`() {
        val bat = launcher()
        val cases: List<Pair<String, (Fixture) -> Unit>> = listOf(
            "existing-yaml" to { source: Fixture -> source.write("mod.yaml", "DO NOT OVERWRITE") },
            "existing-backup" to { source: Fixture -> source.write("gradle_backup/keep.txt", "DO NOT OVERWRITE") },
            "duplicate-build" to { source: Fixture -> source.write("build.gradle", "// duplicate") },
            "duplicate-settings" to { source: Fixture -> source.write("settings.gradle", "// duplicate") },
        )
        for ((name, mutate) in cases) {
            val fixture = fixture(name, true)
            mutate(fixture)
            val before = snapshot(fixture.root)
            val result = run(bat, temp, "migrate -C \"${fixture.root}\" -D")
            assertNotEquals(0, result.code, "$name must fail: ${result.output}")
            assertTrue(result.output.contains("[ERROR]"), result.output)
            assertEquals(before, snapshot(fixture.root), "$name changed source tree")
            assertNoStage(fixture.root)
        }
    }

    @Test
    fun `unknown Gradle DSL and unsupported Loader metadata fail closed`() {
        val bat = launcher()
        val dynamic = fixture("unsupported DSL", true)
        val build = dynamic.root.resolve(dynamic.build)
        Files.writeString(build, Files.readString(build) +
            "\ntasks.register(\"customBehavior\") { println(\"NOT_EVALUATED\") }\n")
        val unknown = fixture("unsupported metadata", false)
        val meta = unknown.root.resolve(unknown.metadata)
        Files.writeString(meta, Files.readString(meta).replace(
            "\"schemaVersion\": 1,",
            "\"schemaVersion\": 1, \"unrecognizedField\": true,",
        ))
        for (fixture in listOf(dynamic, unknown)) {
            val before = snapshot(fixture.root)
            val result = run(bat, temp, "migrate --project \"${fixture.root}\" --delete")
            assertNotEquals(0, result.code, "Unsupported input accepted: ${result.output}")
            assertTrue(result.output.contains("[ERROR]"), result.output)
            assertEquals(before, snapshot(fixture.root), "Unsupported input mutated original tree")
            assertNoStage(fixture.root)
        }
    }

    private fun fixture(folder: String, kotlin: Boolean): Fixture {
        val fixture = Fixture(Files.createDirectory(temp.resolve(folder)), kotlin)
        val build = if (kotlin) """
            plugins {
                id("net.fabricmc.fabric-loom") version "1.18.4"
            }
            group = "org.example"
            version = "${fixture.version}"
            repositories {
                mavenCentral()
            }
            dependencies {
                minecraft("com.mojang:minecraft:26.3")
                modImplementation("net.fabricmc:fabric-loader:0.20.1")
                modImplementation("net.fabricmc.fabric-api:fabric-api:0.165.2+26.3")
                implementation("org.example:small-library:1.0.0")
            }
            java {
                withSourcesJar()
            }
        """.trimIndent() else """
            plugins {
                id 'net.fabricmc.fabric-loom'
            }
            group = 'org.example'
            version = '${fixture.version}'
            repositories {
                mavenCentral()
            }
            dependencies {
                minecraft 'com.mojang:minecraft:26.3'
                modImplementation 'net.fabricmc:fabric-loader:0.20.1'
                modImplementation 'net.fabricmc.fabric-api:fabric-api:0.165.2+26.3'
            }
        """.trimIndent()
        fixture.write(fixture.build, "$build\n")
        fixture.write(fixture.settings, if (kotlin) "rootProject.name = \"kotlin-engine\"\n"
            else "rootProject.name = 'groovy-engine'\n")
        fixture.write(fixture.metadata, """
            {
              "schemaVersion": 1,
              "id": "${fixture.modId}",
              "version": "${fixture.version}",
              "name": "Migrated Mod",
              "entrypoints": {"main": ["org.example.ExampleMod"]},
              "depends": {"fabricloader": ">=0.20.1", "minecraft": "26.3"}
            }
        """.trimIndent() + "\n")
        fixture.write(fixture.javaSource, "package org.example;\npublic final class ExampleMod {}\n")
        fixture.write(fixture.resource, "{\"message\":\"preserved\"}\n")
        return fixture
    }

    private fun assertMigrated(fixture: Fixture) {
        val yaml = fixture.root.resolve("mod.yaml")
        assertTrue(Files.isRegularFile(yaml))
        val model = ModYamlLoader.load(yaml)
        assertEquals(1, model.schema)
        assertEquals("fabric/mod", model.product)
        assertEquals(fixture.modId, model.mod.id)
        assertEquals(fixture.version, model.mod.version)
        assertEquals("org.example", model.mod.group)
        assertEquals("26.3", model.minecraft.version)
        assertEquals("0.20.1", model.minecraft.loader)
        assertEquals("0.165.2+26.3", model.minecraft.fabricApi)
        assertEquals(25, model.settings.java.release)
        assertEquals(fixture.kotlin, model.settings.java.sourcesJar)
        assertFalse(model.settings.kotlin.enabled)
        assertEquals(if (fixture.kotlin) 1 else 0, model.dependencies.size)
        if (fixture.kotlin) assertEquals("org.example:small-library:1.0.0", model.dependencies.single().coordinate)
    }

    private fun assertPreserved(fixture: Fixture, before: Map<String, String>) {
        val after = snapshot(fixture.root)
        for (name in listOf(fixture.javaSource, fixture.resource)) {
            assertEquals(before[name], after[name], "Unexpected mutation of $name")
        }
    }

    private fun Fixture.write(relative: String, value: String) {
        val target = root.resolve(relative)
        Files.createDirectories(target.parent)
        Files.writeString(target, value, StandardCharsets.UTF_8)
    }

    private fun launcher(): Path {
        val source = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val target = Files.createDirectories(temp.resolve("launcher"))
        for (name in listOf("fabric-amper.bat", "scripts/fabric-amper-migrate.ps1", "fabric-amper.schema.json")) {
            assertTrue(Files.isRegularFile(source.resolve(name)), "Missing migration tool: $name")
            Files.createDirectories(target.resolve(name).parent)
            Files.copy(source.resolve(name), target.resolve(name))
        }
        return target.resolve("fabric-amper.bat")
    }

    private fun run(bat: Path, cwd: Path, args: String): Run {
        val command = "\"\"$bat\" $args\""
        val process = ProcessBuilder("cmd.exe", "/d", "/s", "/c", command)
            .directory(cwd.toFile()).redirectErrorStream(true).start()
        if (!process.waitFor(45, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw AssertionError("Windows migration CLI timed out: $args")
        }
        return Run(process.exitValue(), process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText())
    }

    private fun snapshot(root: Path): Map<String, String> = Files.walk(root).use { walk ->
        walk.iterator().asSequence().filter { it != root }.associate { path ->
            val name = root.relativize(path).toString().replace('\\', '/')
            val marker = if (Files.isDirectory(path)) "directory" else {
                val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
                "file:" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
            }
            name to marker
        }
    }

    private fun assertNoStage(root: Path) {
        Files.list(root).use { paths ->
            assertFalse(paths.anyMatch { it.fileName.toString().startsWith(".fabric-amper-migrate-") },
                "Migration left a staging directory in $root")
        }
    }
}
