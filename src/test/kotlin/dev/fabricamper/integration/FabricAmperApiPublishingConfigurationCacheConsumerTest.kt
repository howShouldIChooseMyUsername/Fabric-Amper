package dev.fabricamper.integration

import com.google.gson.JsonParser
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
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
 * Real API local Maven publication configuration-cache acceptance.
 * Four identical publish task invocations: STORE -> HIT -> YAML MISS/STORE
 * -> HIT. An initial unmeasured help primes Loom's external caches.
 * No MavenLocal, remote Maven, Modrinth, CurseForge or game processes.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_API_PUBLISHING", matches = "true")
class FabricAmperApiPublishingConfigurationCacheConsumerTest {
    @TempDir lateinit var suiteRoot: Path

    private val group = "dev.fabricamper.ccacceptance"
    private val artifact = "api-cc-proof"
    private val initialVersion = "1.0.0"
    private val revisedVersion = "1.0.1"
    private val initialDescription = "First configuration cache publication"
    private val revisedDescription = "Updated configuration cache publication"
    private val sourceClass = "dev/fabricamper/ccprobe/ApiCcInitializer"
    private val localPublishTask = "publishMavenJavaPublicationToFabricAmperLocalRepository"

    @Test
    fun realApiLocalPublishStoreHitYamlMissHit() {
        val f = TestKitFixture(suiteRoot)
        val authoredYaml = """
            schema: 1
            product: fabric/api
            mod:
              id: amper_api_cc
              version: "$initialVersion"
              group: $group
              archivesName: $artifact
              name: API Configuration Cache Proof
              description: "$initialDescription"
              environment: "*"
              entrypoints:
                main:
                  - dev.fabricamper.ccprobe.ApiCcInitializer
            minecraft:
              version: "26.3"
              mappings: mojang
              loader: "0.20.1"
            settings:
              java:
                release: 25
                sourcesJar: true
              kotlin:
                enabled: false
              loom:
                splitEnvironmentSourceSets: false
            repositories:
              - fabric
              - mavenCentral
        """.trimIndent() + "\n"
        val project = f.project("api-local-maven-cc", authoredYaml)
        f.write(project, "src/main/java/$sourceClass.java", """
            package dev.fabricamper.ccprobe;
            import net.fabricmc.api.ModInitializer;
            public final class ApiCcInitializer implements ModInitializer {
                @Override public void onInitialize() {}
                public static String contract() { return "api-cc"; }
            }
        """.trimIndent() + "\n")
        f.assertNoConsumerBuildScript(project)
        assertFalse(Files.exists(project.resolve("src/main/resources/fabric.mod.json")))
        assertFalse(Files.exists(project.resolve("build/fabric-amper/maven-repository")))

        val warm = f.run(project, "help")
        assertEquals(TaskOutcome.SUCCESS, warm.task(":help")?.outcome, warm.output)
        assertFalse(Files.exists(project.resolve("build/fabric-amper/maven-repository")),
            "Unmeasured help must not perform a Maven publication")

        val first = publishWithCache(f, project)
        assertRealLocalPublish(first, "first STORE")
        assertFalse(first.output.contains("Reusing configuration cache"), first.output)
        assertStored(first, "first STORE")
        val original = inspectPublication(f, project, initialVersion, initialDescription)

        val second = publishWithCache(f, project)
        assertRealLocalPublish(second, "second HIT")
        assertReused(second, "second HIT")
        val sameOriginal = inspectPublication(f, project, initialVersion, initialDescription)
        assertArrayEquals(original.jar, sameOriginal.jar)
        assertArrayEquals(original.pom, sameOriginal.pom)
        assertArrayEquals(original.loaderJson, sameOriginal.loaderJson)
        assertEquals(original.mirrorPath, sameOriginal.mirrorPath)

        val changedYaml = authoredYaml
            .replace("version: \"$initialVersion\"", "version: \"$revisedVersion\"")
            .replace("description: \"$initialDescription\"", "description: \"$revisedDescription\"")
        assertNotEquals(authoredYaml, changedYaml)
        assertTrue(changedYaml.contains("version: \"$revisedVersion\""))
        assertTrue(changedYaml.contains("description: \"$revisedDescription\""))
        f.write(project, "mod.yaml", changedYaml)

        val third = publishWithCache(f, project)
        assertRealLocalPublish(third, "third YAML MISS")
        assertFalse(third.output.contains("Reusing configuration cache"),
            "YAML edits must invalidate the local Maven publication configuration cache:\n" + third.output)
        assertStored(third, "third YAML MISS/STORE")
        val changed = inspectPublication(f, project, revisedVersion, revisedDescription)
        assertFalse(original.jar.contentEquals(changed.jar), "Version edit must change published JAR bytes")
        assertFalse(original.pom.contentEquals(changed.pom), "Version/description edit must change Maven POM")
        assertFalse(original.loaderJson.contentEquals(changed.loaderJson),
            "Version/description edit must change Loader metadata")
        assertNotEquals(original.mirrorPath, changed.mirrorPath,
            "YAML metadata change must create a distinct content-addressed Loom mirror")
        assertEquals(revisedVersion, loaderVersion(changed.loaderJson))

        // The first GAV remains separately published and unchanged.
        assertTrue(Files.isDirectory(original.gav), "New version may not delete the old GAV")
        assertArrayEquals(original.jar,
            Files.readAllBytes(original.gav.resolve("$artifact-$initialVersion.jar")))
        assertArrayEquals(original.pom,
            Files.readAllBytes(original.gav.resolve("$artifact-$initialVersion.pom")))

        val fourth = publishWithCache(f, project)
        assertRealLocalPublish(fourth, "fourth HIT")
        assertReused(fourth, "fourth HIT")
        val sameChanged = inspectPublication(f, project, revisedVersion, revisedDescription)
        assertArrayEquals(changed.jar, sameChanged.jar)
        assertArrayEquals(changed.pom, sameChanged.pom)
        assertArrayEquals(changed.loaderJson, sameChanged.loaderJson)
        assertEquals(changed.mirrorPath, sameChanged.mirrorPath)

        assertEquals(changedYaml, Files.readString(project.resolve("mod.yaml")))
        assertFalse(Files.exists(project.resolve("src/main/resources/fabric.mod.json")))
        f.assertNoConsumerBuildScript(project)
    }

    private data class Publication(
        val gav: Path,
        val jar: ByteArray,
        val pom: ByteArray,
        val loaderJson: ByteArray,
        val mirrorPath: Path,
    )

    private fun publishWithCache(f: TestKitFixture, project: Path): BuildResult =
        f.run(project, localPublishTask, "--configuration-cache")

    private fun assertRealLocalPublish(result: BuildResult, stage: String) {
        assertEquals(TaskOutcome.SUCCESS, result.task(":$localPublishTask")?.outcome,
            "$stage must execute the real local Maven publication task:\n" + result.output)
        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        assertFalse(result.tasks.any {
            it.path.contains("RemoteRepository") || it.path.contains("Modrinth") ||
                it.path.contains("CurseForge") || it.path == ":publishToMavenLocal"
        }, "$stage must not execute remote/user-wide publishing tasks:\n" + result.output)
    }

    private fun assertStored(result: BuildResult, stage: String) {
        assertTrue(Regex("(?i)configuration cache entry stored").containsMatchIn(result.output),
            "$stage must show a real Gradle configuration cache STORE:\n" + result.output)
    }

    private fun assertReused(result: BuildResult, stage: String) {
        assertTrue(result.output.contains("Reusing configuration cache"),
            "$stage must show an observed Gradle configuration cache HIT:\n" + result.output)
    }

    private fun inspectPublication(
        f: TestKitFixture, project: Path, version: String, description: String,
    ): Publication {
        val repo = project.resolve("build/fabric-amper/maven-repository").toRealPath()
        assertTrue(repo.startsWith(project.toRealPath()),
            "The Maven output must remain inside this isolated consumer's build directory")
        val gav = repo.resolve(group.replace('.', '/') + "/" + artifact + "/" + version)
        assertTrue(Files.isDirectory(gav), "Missing local Maven GAV: " + gav)
        val jarFile = gav.resolve("$artifact-$version.jar")
        val pomFile = gav.resolve("$artifact-$version.pom")
        val moduleFile = gav.resolve("$artifact-$version.module")
        val sourcesFile = gav.resolve("$artifact-$version-sources.jar")
        for (file in listOf(jarFile, pomFile, moduleFile, sourcesFile)) {
            assertTrue(Files.isRegularFile(file) && Files.size(file) > 0,
                "Missing required local Maven publication artifact: " + file)
        }

        val pomRoot = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setXIncludeAware(false)
            isExpandEntityReferences = false
        }.newDocumentBuilder().parse(pomFile.toFile()).documentElement
        fun pomValue(key: String): String? =
            (0 until pomRoot.childNodes.length).map { pomRoot.childNodes.item(it) }
                .filterIsInstance<Element>().firstOrNull { it.localName == key }?.textContent?.trim()
        assertEquals(group, pomValue("groupId"))
        assertEquals(artifact, pomValue("artifactId"))
        assertEquals(version, pomValue("version"))
        assertEquals(description, pomValue("description"),
            "POM description must follow the currently authored YAML")

        val module = JsonParser.parseString(Files.readString(moduleFile)).asJsonObject
            .getAsJsonObject("component")
        assertEquals(group, module.get("group").asString)
        assertEquals(artifact, module.get("module").asString)
        assertEquals(version, module.get("version").asString)
        ZipFile(sourcesFile.toFile()).use { zip ->
            assertTrue(zip.getEntry("$sourceClass.java") != null,
                "Published source archive must contain the consumer Java initializer")
        }

        val jarBytes = Files.readAllBytes(jarFile)
        val (jsonCount, jsonBytes) = f.jarRootJson(jarFile)
        assertEquals(1, jsonCount, "Published API JAR must contain one root fabric.mod.json")
        assertEquals(version, loaderVersion(jsonBytes))
        ZipFile(jarFile.toFile()).use { zip ->
            assertTrue(zip.getEntry("$sourceClass.class") != null,
                "Published API JAR must contain the compiled initializer")
        }
        assertArrayEquals(Files.readAllBytes(f.generatedJson(project)), jsonBytes,
            "Published JAR root Loader JSON must match generated metadata")
        val matches = f.mirrorJson(project).filter {
            Files.readAllBytes(it).contentEquals(jsonBytes)
        }
        assertEquals(1, matches.size,
            "Exactly one content-addressed mirror must match the current Loader JSON")
        val mirror = matches.single()
        assertArrayEquals(jsonBytes, Files.readAllBytes(mirror))
        f.assertNoConsumerBuildScript(project)
        return Publication(gav, jarBytes, Files.readAllBytes(pomFile), jsonBytes, mirror)
    }

    private fun loaderVersion(bytes: ByteArray): String =
        JsonParser.parseString(String(bytes, StandardCharsets.UTF_8))
            .asJsonObject.get("version").asString
}
