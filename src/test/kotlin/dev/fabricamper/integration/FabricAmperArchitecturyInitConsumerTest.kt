package dev.fabricamper.integration

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText

/** Compile real Windows init starter sources against the in-development settings plugin.
 * The generated v0.3.1 plugin pin is replaced with TestKit's local classpath;
 * this test does not depend on the prerelease having been published.
 */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
class FabricAmperArchitecturyInitConsumerTest {
    @TempDir lateinit var root: Path

    @Test
    fun `init Architectury Java and Kotlin examples build with and without split source sets`() {
        val f = TestKitFixture(root)
        val producer = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val bat = producer.resolve("fabric-amper.bat")
        data class Case(val id: String, val kotlin: Boolean, val split: Boolean)
        for (case in listOf(
            Case("arch_java_single", kotlin = false, split = false),
            Case("arch_java_split", kotlin = false, split = true),
            Case("arch_kotlin_single", kotlin = true, split = false),
            Case("arch_kotlin_split", kotlin = true, split = true),
        )) {
            val consumer = root.resolve(case.id)
            val flags = listOfNotNull(
                "--architectury",
                if (case.kotlin) "--kotlin" else null,
                if (case.split) "--splitEnvironmentSourceSets" else null,
            ).joinToString(" ")
            val command = "\"\"$bat\" init ${case.id} \"$consumer\" mod $flags\""
            val process = ProcessBuilder("cmd.exe", "/d", "/s", "/c", command)
                .directory(producer.toFile()).redirectErrorStream(true).start()
            assertTrue(process.waitFor(40, TimeUnit.SECONDS), "init timed out: ${case.id}")
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "init failed: $output")
            val generated = consumer.resolve("settings.gradle.kts").readText()
            assertTrue(generated.contains("v0.3.1"), "BAT must pin the current v0.3.1 prerelease")
            // Exercise the current local plugin without downloading the prerelease.
            // No build.gradle file.
            f.write(consumer, "settings.gradle.kts", "plugins { id(\"dev.fabric-amper.settings\") }\n")
            f.assertNoConsumerBuildScript(consumer)
            assertFalse(Files.exists(consumer.resolve("src/main/resources/fabric.mod.json")))
            val build = f.run(consumer, "build")
            assertTrue(build.output.contains("BUILD SUCCESSFUL"), "${case.id}: ${build.output}")
            val depends = JsonParser.parseString(f.generatedJson(consumer).readText())
                .asJsonObject.getAsJsonObject("depends")
            assertEquals(">=22.0.5", depends.get("architectury").asString)
            assertEquals(1, f.jarRootJson(f.jars(consumer).single()).first)
            if (case.kotlin) assertTrue(build.output.contains("compileKotlin"), build.output)
            if (case.split) {
                assertTrue(build.output.contains(if (case.kotlin) "compileClientKotlin" else "compileClientJava"),
                    "Split client code was not compiled: ${build.output}")
            }
            f.assertNoConsumerBuildScript(consumer)
        }
    }
}
