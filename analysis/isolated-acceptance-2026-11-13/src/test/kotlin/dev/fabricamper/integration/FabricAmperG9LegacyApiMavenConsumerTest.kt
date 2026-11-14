package dev.fabricamper.integration

import com.google.gson.JsonParser
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import org.w3c.dom.Element
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Separate legacy API Maven publication gate. The producer uses Minecraft
 * 1.21.11, Java21 sources and the real Loom remap task, not the 26.3 fixtures.
 * It never publishes remotely or touches a user's Maven home, game or EULA.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_LEGACY", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_G9_API_PUBLISH", matches = "true")
class FabricAmperG9LegacyApiMavenConsumerTest {
    @TempDir lateinit var suiteRoot: Path

    private val group = "dev.fabricamper.acceptance.legacy"
    private val artifact = "legacy-api-published"
    private val version = "0.1.0+1.21.11"
    private val javaClass = "org/example/legacy/LegacyPublishedApi.class"
    private val javaSource = "org/example/legacy/LegacyPublishedApi.java"

    @Test
    fun legacyApiPublishesActualRemapJarAndRemappedSources() {
        val fixture = TestKitFixture(suiteRoot)
        val producerYaml = """
            schema: 1
            product: fabric/api
            mod:
              id: amper_legacy_api
              version: "0.1.0+1.21.11"
              group: dev.fabricamper.acceptance.legacy
              archivesName: legacy-api-published
              environment: "*"
              entrypoints:
                main:
                  - org.example.legacy.LegacyPublishedApi
            minecraft:
              version: "1.21.11"
              mappings: mojang
              loader: "0.20.1"
            settings:
              java:
                release: 21
                sourcesJar: true
              kotlin:
                enabled: false
              loom:
                splitEnvironmentSourceSets: false
              resources:
                failOnExistingFabricModJson: true
            repositories:
              - fabric
              - mavenCentral
        """.trimIndent() + "\n"
        val a = fixture.project("g9-legacy-api-producer", producerYaml)
        fixture.write(a, "src/main/java/" + javaSource, """
            package org.example.legacy;
            import net.fabricmc.api.ModInitializer;
            import net.minecraft.world.level.block.Block;
            import net.minecraft.world.level.block.Blocks;
            public final class LegacyPublishedApi implements ModInitializer {
                @Override public void onInitialize() {}
                public static Block exposedBlock() { return Blocks.STONE; }
            }
        """.trimIndent() + "\n")
        fixture.write(a, "g9-archive-provider.init.gradle", """
            gradle.projectsEvaluated {
                ['jar', 'remapJar', 'sourcesJar', 'remapSourcesJar'].each { taskName ->
                    def t = rootProject.tasks.findByName(taskName)
                    def value = t == null ? '<absent>' : t.archiveFile.get().asFile.absolutePath
                    println('G9_LEGACY_ARCHIVE_' + taskName + '=' + value)
                }
            }
        """.trimIndent() + "\n")
        fixture.assertNoConsumerBuildScript(a)
        assertFalse(Files.exists(a.resolve("build/fabric-amper/maven-repository")),
            "Local Maven directory must not exist before an explicit publish")

        val published = fixture.run(
            a, "publishMavenJavaPublicationToFabricAmperLocalRepository",
            "--init-script", a.resolve("g9-archive-provider.init.gradle").toString(),
        )
        assertEquals(TaskOutcome.SUCCESS,
            published.task(":publishMavenJavaPublicationToFabricAmperLocalRepository")?.outcome,
            "Local Maven publication must be a real executed task:\n" + published.output)
        assertNotNull(published.task(":compileJava"), "Real Java21 producer must be compiled")
        assertNotNull(published.task(":remapJar"), "Legacy 1.21.11 must run production remapJar")
        assertNotNull(published.task(":verifyFabricAmperJar"), "Final Fabric metadata must be verified")
        val sequence = published.tasks.map { it.path }
        assertTrue(sequence.indexOf(":remapJar") >= 0 &&
            sequence.indexOf(":remapJar") < sequence.indexOf(":verifyFabricAmperJar") &&
            sequence.indexOf(":verifyFabricAmperJar") <
            sequence.indexOf(":publishMavenJavaPublicationToFabricAmperLocalRepository"),
            "Mandatory remapJar -> verifier -> local publication order:\n" + published.output)
        assertFalse(published.tasks.any { it.path.contains("RemoteRepository") ||
            it.path == ":publishToMavenLocal" }, "No user-wide Maven or remote upload is authorized")
        assertTrue(published.output.contains("BUILD SUCCESSFUL"), published.output)

        // These are the actual task archiveFile provider outputs, rather than
        // conventional filename guesses that could accidentally select a dev JAR.
        val remapJar = taskArchive(published.output, "remapJar")
        val devJar = taskArchive(published.output, "jar")
        assertTrue(Files.isRegularFile(remapJar), "Missing actual remapJar archive: " + remapJar)
        assertTrue(Files.isRegularFile(devJar), "Missing actual development jar: " + devJar)
        assertFalse(remapJar.toRealPath() == devJar.toRealPath(),
            "Loom's remapped and development JAR archiveFile providers must differ")
        assertFalse(Files.readAllBytes(remapJar).contentEquals(Files.readAllBytes(devJar)),
            "Remapped release JAR bytes must differ from the development JAR")
        assertFalse(zipEntryBytes(remapJar, javaClass).contentEquals(zipEntryBytes(devJar, javaClass)),
            "Actual Minecraft Block/Blocks references must be remapped to intermediary names")

        val repository = a.resolve("build/fabric-amper/maven-repository").toRealPath()
        assertTrue(repository.startsWith(a.toRealPath()), "Publication escaped consumer A's build dir")
        val gavDir = repository.resolve("dev/fabricamper/acceptance/legacy/legacy-api-published/" + version)
        assertTrue(Files.isDirectory(gavDir), "Published Maven GAV directory missing: " + gavDir)
        val jar = gavDir.resolve(artifact + "-" + version + ".jar")
        val sources = gavDir.resolve(artifact + "-" + version + "-sources.jar")
        val pom = gavDir.resolve(artifact + "-" + version + ".pom")
        val module = gavDir.resolve(artifact + "-" + version + ".module")
        for (file in listOf(jar, sources, pom, module)) {
            assertTrue(Files.isRegularFile(file) && Files.size(file) > 0,
                "Missing published G9 Maven file: " + file)
        }
        assertArrayEquals(Files.readAllBytes(remapJar), Files.readAllBytes(jar),
            "The published main Maven artifact MUST be Loom's final remapJar archive")
        assertFalse(Files.readAllBytes(jar).contentEquals(Files.readAllBytes(devJar)),
            "The development JAR must never be published as the main archive")
        val publishedNames = Files.list(gavDir).use { files ->
            files.map { it.fileName.toString() }.toList()
        }
        assertEquals(1, publishedNames.count { it == jar.fileName.toString() })
        assertEquals(1, publishedNames.count { it == sources.fileName.toString() },
            "Java sources must be attached exactly once")
        assertFalse(publishedNames.any { it.endsWith("-dev.jar") || it.endsWith("-remapped.jar") },
            "No extra developer/remap classifier may be published")

        val remapSources = taskArchiveOrNull(published.output, "remapSourcesJar")
        if (remapSources != null) {
            assertNotNull(published.task(":remapSourcesJar"),
                "If remapSourcesJar exists, the Maven sources component must execute it")
            assertTrue(Files.isRegularFile(remapSources), "Remapped sources task output missing")
            assertArrayEquals(Files.readAllBytes(remapSources), Files.readAllBytes(sources),
                "Published sources must equal remapSourcesJar when Loom exposes that task")
        } else {
            val plainSources = taskArchive(published.output, "sourcesJar")
            assertTrue(Files.isRegularFile(plainSources), "Java21 sources JAR not generated")
            assertArrayEquals(Files.readAllBytes(plainSources), Files.readAllBytes(sources))
        }
        ZipFile(sources.toFile()).use { zip ->
            assertNotNull(zip.getEntry(javaSource), "Actual authored Java21 source must be published")
        }
        verifyGav(pom, module)
        val (count, jsonBytes) = fixture.jarRootJson(jar)
        assertEquals(1, count, "Exactly one root Fabric Loader metadata file in Maven artifact")
        val json = JsonParser.parseString(String(jsonBytes, StandardCharsets.UTF_8)).asJsonObject
        assertEquals("amper_legacy_api", json.get("id").asString)
        assertEquals(version, json.get("version").asString)
        assertFalse(json.has("product"), "Publishing profile must not leak into Loader metadata")
        fixture.assertNoConsumerBuildScript(a)
        assertEquals(producerYaml, Files.readString(a.resolve("mod.yaml")), "Do not rewrite YAML")

        // A second independent settings-only consumer resolves the genuinely
        // published intermediary JAR as a Fabric mod. Loom must remap it back
        // to the consumer's official development namespace for Java21 compile.
        val bYaml = """
            schema: 1
            product: fabric/mod
            mod:
              id: amper_legacy_downstream
              version: "0.1.0+1.21.11"
            minecraft:
              version: "1.21.11"
              mappings: mojang
              loader: "0.20.1"
            settings:
              java:
                release: 21
                sourcesJar: false
              kotlin:
                enabled: false
            repositories:
              - fabric
              - mavenCentral
            dependencies:
              - maven: "dev.fabricamper.acceptance.legacy:legacy-api-published:0.1.0+1.21.11"
                kind: mod
                scope: all
        """.trimIndent() + "\n"
        val b = fixture.project("g9-legacy-api-downstream", bYaml)
        val repositoryUrl = repository.toUri().toASCIIString()
        fixture.write(b, "settings.gradle.kts", """
            plugins { id("dev.fabric-amper.settings") }
            gradle.beforeProject {
                repositories {
                    maven { url = uri("$repositoryUrl") }
                }
            }
        """.trimIndent() + "\n")
        fixture.write(b, "src/main/java/org/example/legacy/LegacyConsumer.java", """
            package org.example.legacy;
            import net.minecraft.world.level.block.Block;
            public final class LegacyConsumer {
                public static Block exposedBlock() {
                    return LegacyPublishedApi.exposedBlock();
                }
            }
        """.trimIndent() + "\n")
        fixture.assertNoConsumerBuildScript(b)
        val consumer = fixture.run(b, "compileJava")
        assertEquals(TaskOutcome.SUCCESS, consumer.task(":compileJava")?.outcome,
            "Independent Java21 consumer B must compile against published remapped API:\n" + consumer.output)
        assertTrue(Files.isRegularFile(
            b.resolve("build/classes/java/main/org/example/legacy/LegacyConsumer.class")),
            "Real downstream Java21 source did not compile")
        fixture.assertNoConsumerBuildScript(b)
    }

    private fun archiveValue(output: String, taskName: String): String {
        val prefix = "G9_LEGACY_ARCHIVE_" + taskName + "="
        val values = output.lineSequence().filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix).trim() }.toList()
        assertEquals(1, values.size,
            "Must observe exactly one real Gradle archiveFile provider for " + taskName + ":\n" + output)
        return values.single()
    }

    private fun taskArchiveOrNull(output: String, task: String): Path? {
        val value = archiveValue(output, task)
        return if (value == "<absent>") null else Path.of(value).toAbsolutePath().normalize()
    }

    private fun taskArchive(output: String, task: String): Path =
        requireNotNull(taskArchiveOrNull(output, task)) { "Required Gradle archive task absent: " + task }

    private fun zipEntryBytes(path: Path, entryName: String): ByteArray =
        ZipFile(path.toFile()).use { zip ->
            val entry = requireNotNull(zip.getEntry(entryName)) {
                "Missing " + entryName + " in " + path
            }
            zip.getInputStream(entry).use { it.readAllBytes() }
        }

    private fun verifyGav(pom: Path, module: Path) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setXIncludeAware(false)
            isExpandEntityReferences = false
        }
        val root = factory.newDocumentBuilder().parse(pom.toFile()).documentElement
        fun direct(name: String): String? =
            (0 until root.childNodes.length).map { root.childNodes.item(it) }
                .filterIsInstance<Element>()
                .firstOrNull { it.localName == name }?.textContent?.trim()
        assertEquals(group, direct("groupId"), "Maven POM groupId")
        assertEquals(artifact, direct("artifactId"), "Maven POM artifactId")
        assertEquals(version, direct("version"), "Maven POM version")
        val metadata = JsonParser.parseString(Files.readString(module)).asJsonObject
        val component = metadata.getAsJsonObject("component")
        assertEquals(group, component.get("group").asString)
        assertEquals(artifact, component.get("module").asString)
        assertEquals(version, component.get("version").asString)
        val variants = metadata.getAsJsonArray("variants")
        assertTrue(variants.any { it.asJsonObject.get("name").asString.contains("apiElements") },
            "Maven module must contain Java API variant")
        assertTrue(variants.any { it.asJsonObject.get("name").asString.contains("runtimeElements") },
            "Maven module must contain Java runtime variant")
    }
}
