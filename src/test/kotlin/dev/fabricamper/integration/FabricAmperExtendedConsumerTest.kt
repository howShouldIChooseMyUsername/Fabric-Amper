package dev.fabricamper.integration

import com.google.gson.JsonParser
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/**
 * Additional real consumer acceptance gates. No build.gradle, EULA changes,
 * runClient or runServer. These cases still resolve Gradle/Loom/MC artifacts,
 * so they remain explicit opt-in and are NOT part of the fast diagnostic suite.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FabricAmperExtendedConsumerTest {
    @TempDir lateinit var suiteRoot: Path
    private val fixture get() = TestKitFixture(suiteRoot)

    @Test
    fun `G10 include packs one valid nested Maven jar and declares its exact path in Loader metadata`() {
        val f = fixture
        val yaml = f.resource("minimal-mod.yaml") + """

            dependencies:
              - "org.apache.commons:commons-lang3:3.17.0": include
        """.trimIndent() + "\n"
        val root = f.project("g10", yaml)
        f.write(root, "src/main/java/org/example/IncludedLibraryConsumer.java", """
            package org.example;
            import org.apache.commons.lang3.StringUtils;
            public final class IncludedLibraryConsumer {
                public static String example() { return StringUtils.capitalize("included"); }
            }
        """.trimIndent() + "\n")

        f.run(root, "build")
        val archive = f.jars(root).single()
        val (rootJsonCount, metadataBytes) = f.jarRootJson(archive)
        assertEquals(1, rootJsonCount, "Exactly one root fabric.mod.json is required")
        val metadata = JsonParser.parseString(String(metadataBytes, StandardCharsets.UTF_8)).asJsonObject
        val jarsField = metadata.get("jars")
        assertNotNull(jarsField, "Loom must add jars[] for the included library")
        assertTrue(jarsField!!.isJsonArray, "Loader jars metadata must be an array")
        val included = jarsField.asJsonArray.map { it.asJsonObject.get("file").asString }
        assertEquals(1, included.size, "One YAML :include must produce exactly one nested archive")

        ZipFile(archive.toFile()).use { zip ->
            val nestedPath = included.single()
            assertTrue(nestedPath.startsWith("META-INF/jars/"), "Unexpected nested JAR path: $nestedPath")
            assertEquals(
                1,
                zip.entries().asSequence().count { it.name == nestedPath },
                "Declared nested path must exist exactly once in ZIP",
            )
            val nestedEntry = zip.getEntry(nestedPath)
            assertNotNull(nestedEntry, "Declared nested archive is missing")
            val contents = zip.getInputStream(nestedEntry!!).use { it.readAllBytes() }
            val nestedFiles = mutableSetOf<String>()
            ZipInputStream(contents.inputStream()).use { nested ->
                val buffer = ByteArray(8192)
                while (true) {
                    val entry = nested.nextEntry ?: break
                    nestedFiles += entry.name
                    while (nested.read(buffer) != -1) { /* consume data and verify ZIP CRC */ }
                    nested.closeEntry()
                }
            }
            assertTrue(nestedFiles.isNotEmpty(), "Included library must be a readable ZIP")
            assertTrue(
                "org/apache/commons/lang3/StringUtils.class" in nestedFiles,
                "Included Maven Central library classes missing from nested archive: $nestedPath",
            )
            assertNotNull(
                zip.getEntry("org/example/IncludedLibraryConsumer.class"),
                "YAML :include must also expose the library to javac",
            )
        }
        f.assertNoConsumerBuildScript(root)
    }

    @Test
    fun `G18 client-only dependencies are declared on split client configurations not common ones`() {
        val f = fixture
        val yaml = f.resource("minimal-mod.yaml")
            .replace("splitEnvironmentSourceSets: false", "splitEnvironmentSourceSets: true") + """

            dependencies:
              - maven: "org.apache.commons:commons-lang3:3.17.0"
                target: client
                scope: all
              - maven: "com.google.code.gson:gson:2.14.0"
                target: client
                scope: compile-only
        """.trimIndent() + "\n"
        val root = f.project("g18", yaml)
        // Read declared configuration members rather than resolving Minecraft or
        // trying to run a real client/server. This deliberately does not claim
        // source-set compile isolation without a separate compiled-code fixture.
        f.write(root, "inspect-client-configurations.gradle", """
            gradle.projectsEvaluated {
                def coordinates = { name ->
                    rootProject.configurations.getByName(name).allDependencies
                        .collect { dep -> dep.group + ':' + dep.name + ':' + dep.version }
                        .sort().join(',')
                }
                println('AMPER_CLIENT_IMPLEMENTATION=' + coordinates('clientImplementation'))
                println('AMPER_CLIENT_COMPILE_ONLY=' + coordinates('clientCompileOnly'))
                println('AMPER_COMMON_IMPLEMENTATION=' + coordinates('implementation'))
                println('AMPER_COMMON_COMPILE_ONLY=' + coordinates('compileOnly'))
            }
        """.trimIndent() + "\n")
        val result = f.run(root, "help", "--init-script", root.resolve("inspect-client-configurations.gradle").toString())
        val expectedAll = "org.apache.commons:commons-lang3:3.17.0"
        val expectedCompileOnly = "com.google.code.gson:gson:2.14.0"
        val lines = result.output.lines()
        fun declared(label: String): String {
            val marker = "$label="
            val line = lines.singleOrNull { it.startsWith(marker) }
            assertNotNull(line, "Missing $label inspection marker:\n${result.output}")
            return line!!.removePrefix(marker)
        }
        assertTrue(expectedAll in declared("AMPER_CLIENT_IMPLEMENTATION"))
        assertTrue(expectedCompileOnly in declared("AMPER_CLIENT_COMPILE_ONLY"))
        assertFalse(expectedAll in declared("AMPER_COMMON_IMPLEMENTATION"))
        assertFalse(expectedCompileOnly in declared("AMPER_COMMON_COMPILE_ONLY"))
        f.assertNoConsumerBuildScript(root)
    }

    @Test
    fun `G18 client-only compileOnly library compiles in client Java but fails in common Java`() {
        val f = fixture
        val yaml = f.resource("minimal-mod.yaml")
            .replace("splitEnvironmentSourceSets: false", "splitEnvironmentSourceSets: true") + """

            dependencies:
              - maven: "org.apache.commons:commons-collections4:4.5.0"
                target: client
                scope: compile-only
        """.trimIndent() + "\n"

        // Two immutable consumer projects avoid changing source files between
        // builds and make the common classpath failure independent of the
        // successful client compilation.
        val client = f.project("g18-client-javac", yaml)
        f.write(client, "src/client/java/org/example/ClientClasspathProbe.java", """
            package org.example;
            import org.apache.commons.collections4.bag.HashBag;
            public final class ClientClasspathProbe {
                public static Class<?> value() { return HashBag.class; }
            }
        """.trimIndent() + "\n")
        val clientResult = f.run(client, "compileClientJava")
        assertEquals(TaskOutcome.SUCCESS, clientResult.task(":compileClientJava")?.outcome, clientResult.output)
        assertTrue(
            Files.isRegularFile(client.resolve("build/classes/java/client/org/example/ClientClasspathProbe.class")),
            "The split client source must really compile against its clientCompileOnly dependency",
        )
        f.assertNoConsumerBuildScript(client)

        val common = f.project("g18-common-javac", yaml)
        f.write(common, "src/main/java/org/example/CommonClasspathProbe.java", """
            package org.example;
            import org.apache.commons.collections4.bag.HashBag;
            public final class CommonClasspathProbe {
                public static Class<?> value() { return HashBag.class; }
            }
        """.trimIndent() + "\n")
        val commonResult = f.fail(common, "compileJava")
        assertEquals(TaskOutcome.FAILED, commonResult.task(":compileJava")?.outcome, commonResult.output)
        assertTrue(commonResult.output.contains("CommonClasspathProbe.java"), commonResult.output)
        assertTrue(
            commonResult.output.contains("package org.apache.commons.collections4.bag does not exist"),
            "The common compilation must fail specifically because the client-only library is absent:\n${commonResult.output}",
        )
        f.assertNoConsumerBuildScript(common)
    }

    @Test
    fun `G16 Loom validateAccessWidener accepts valid Class Tweaker and rejects missing method`() {
        val f = fixture
        val yaml = f.resource("minimal-mod.yaml").replace(
            "  entrypoints:",
            "  accessWidener: example.classtweaker\n  entrypoints:",
        )
        // Fabric's documented validateAccessWidener task checks actual
        // vanilla targets, not merely the Class Tweaker header. Both projects
        // are independent; an invalid member cannot contaminate the valid run.
        val valid = f.project("g16-valid-member", yaml)
        f.write(valid, "src/main/resources/example.classtweaker", """
            classTweaker v1 official
            accessible class net/minecraft/world/level/block/Blocks
        """.trimIndent() + "\n")
        val success = f.run(valid, "validateAccessWidener")
        assertEquals(
            TaskOutcome.SUCCESS, success.task(":validateAccessWidener")?.outcome,
            "Loom must execute and validate an actual vanilla class target:\n${success.output}",
        )
        f.assertNoConsumerBuildScript(valid)

        val invalid = f.project("g16-missing-method", yaml)
        val missingMethod = "fabricAmperMissingMethodForG16"
        f.write(invalid, "src/main/resources/example.classtweaker", """
            classTweaker v1 official
            accessible method net/minecraft/world/level/block/Blocks $missingMethod ()V
        """.trimIndent() + "\n")
        val failure = f.fail(invalid, "validateAccessWidener")
        assertEquals(
            TaskOutcome.FAILED, failure.task(":validateAccessWidener")?.outcome,
            "The real Loom validation task must reject an absent vanilla member:\n${failure.output}",
        )
        assertTrue(
            failure.output.contains(missingMethod),
            "Failure must identify the deliberately nonexistent method, not an unrelated build error:\n${failure.output}",
        )
        f.assertNoConsumerBuildScript(invalid)
    }

    @Test
    fun `G22 YAML group version and archivesName control project and jar and sourcesJar`() {
        val f = fixture
        val root = f.project("g22")
        f.write(root, "src/main/java/org/example/TestkitEntry.java", """
            package org.example;
            public final class TestkitEntry implements net.fabricmc.api.ModInitializer {
                @Override public void onInitialize() {}
            }
        """.trimIndent() + "\n")
        val properties = f.run(root, "properties").output
        assertTrue(Regex("(?m)^group: org\\.example\\s*$").containsMatchIn(properties), properties)
        assertTrue(Regex("(?m)^version: 1\\.0\\.0\\+26\\.3\\s*$").containsMatchIn(properties), properties)
        assertTrue(Regex("(?m)^name: testkit-mod\\s*$").containsMatchIn(properties), properties)

        f.run(root, "jar", "sourcesJar")
        val archive = root.resolve("build/libs/testkit-mod-1.0.0+26.3.jar")
        val sources = root.resolve("build/libs/testkit-mod-1.0.0+26.3-sources.jar")
        assertTrue(Files.isRegularFile(archive), "YAML archivesName/version not reflected in jar name")
        assertTrue(Files.isRegularFile(sources), "sourcesJar not produced under YAML archive name")
        val (count, bytes) = f.jarRootJson(archive)
        assertEquals(1, count)
        val metadata = JsonParser.parseString(String(bytes, StandardCharsets.UTF_8)).asJsonObject
        assertEquals("testkit_mod", metadata.get("id").asString)
        assertEquals("1.0.0+26.3", metadata.get("version").asString)
        assertFalse(metadata.has("group"), "Gradle group must not leak to Loader JSON")
        assertFalse(metadata.has("archivesName"), "Gradle archive name must not leak to Loader JSON")
        ZipFile(sources.toFile()).use { zip ->
            assertNotNull(zip.getEntry("org/example/TestkitEntry.java"), "sourcesJar should contain the consumer Java source")
        }
        f.assertNoConsumerBuildScript(root)
    }

    @Test
    fun `G23 YAML JUnit Jupiter dependency discovers and executes a real consumer test`() {
        val f = fixture
        val yaml = f.resource("minimal-mod.yaml") + """

            test-dependencies:
              - "org.junit.jupiter:junit-jupiter:6.0.0": all
        """.trimIndent() + "\n"
        val root = f.project("g23", yaml)
        f.write(root, "src/test/java/org/example/JupiterSmokeTest.java", """
            package org.example;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;
            public class JupiterSmokeTest {
                @Test public void actuallyRuns() { assertEquals(42, 40 + 2); }
            }
        """.trimIndent() + "\n")
        val result = f.run(root, "test", "--tests", "org.example.JupiterSmokeTest")
        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome, result.output)
        val xml = root.resolve("build/test-results/test/TEST-org.example.JupiterSmokeTest.xml")
        assertTrue(Files.isRegularFile(xml), "JUnit XML missing; discovery may not have happened")
        val suite = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.toFile()).documentElement
        assertEquals("1", suite.getAttribute("tests"), "Exactly one actual JUnit Jupiter test must run")
        assertEquals("0", suite.getAttribute("failures"))
        assertEquals("0", suite.getAttribute("errors"))
        assertEquals("0", suite.getAttribute("skipped"))
        f.assertNoConsumerBuildScript(root)
    }

    @Test
    fun `G30 switching mod yml to mod yaml invalidates and then reuses configuration cache`() {
        val f = fixture
        val root = f.project("g30-rename", alias = "mod.yml")
        f.run(root, "help") // Unmeasured Loom cold-cache bootstrap (see G5).
        f.run(root, "help", "--configuration-cache")
        val second = f.run(root, "help", "--configuration-cache")
        assertTrue(second.output.contains("Reusing configuration cache"), second.output)
        val changedYaml = f.yaml(root, "mod.yml").replace(
            "version: \"1.0.0+26.3\"",
            "version: \"2.0.0+26.3\"",
        )
        f.write(root, "mod.yaml", changedYaml)
        Files.delete(root.resolve("mod.yml"))
        val third = f.run(root, "help", "--configuration-cache")
        assertFalse(third.output.contains("Reusing configuration cache"), third.output)
        assertTrue(
            f.mirrorJson(root).any { mirror ->
                JsonParser.parseString(mirror.readText()).asJsonObject.get("version").asString == "2.0.0+26.3"
            },
            "Renamed YAML must update the configuration-time metadata snapshot",
        )
        val fourth = f.run(root, "help", "--configuration-cache")
        assertTrue(fourth.output.contains("Reusing configuration cache"), fourth.output)
        f.assertNoConsumerBuildScript(root)
    }
}
