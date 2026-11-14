package dev.fabricamper.unit

import com.google.gson.JsonParser
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.plugin.FabricAmperReleasePlan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Pure offline previews of real validated YAML and final ZIP fixtures; no Gradle or HTTP. */
class FabricAmperReleasePlanUnitTest {
    @TempDir lateinit var project: Path

    private fun load(yaml: String) = ModYamlLoader.load(YamlFixtures.write(project, yaml))

    private fun makeJar(vararg items: Pair<String, String>): Path {
        val output = project.resolve("sample-release.jar")
        ZipOutputStream(Files.newOutputStream(output)).use { archive ->
            items.forEach { (name, content) ->
                archive.putNextEntry(ZipEntry(name))
                archive.write(content.toByteArray(StandardCharsets.UTF_8))
                archive.closeEntry()
            }
        }
        return output
    }

    private fun jar(metadata: String = validMetadata()) = makeJar(
        "fabric.mod.json" to metadata,
        "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\n",
    )

    private fun validMetadata() =
        """{"schemaVersion":1,"id":"example_mod","version":"1.0.0+26.3"}"""

    private fun projectFiles() = Files.list(project).use { stream ->
        stream.map { it.fileName.toString() }.sorted().toArray().toList()
    }

    @Test
    fun `offline plan renders inherited platform metadata and final archive checksum without secrets`() {
        val yaml = YamlFixtures.mod(fields = "group: org.example") + """
            publish:
              common:
                releaseType: beta
                name: Shared 1.0.0
                gameVersions: ["26.3", "26.3.1"]
                changelog: NEVER_PRINT_PRIVATE_RELEASE_NOTES
                dependencies:
                  - slug: fabric-api
                    modrinthId: fabric-api
                    curseforgeId: "306612"
                    type: required
              maven:
                url: https://maven.example.org/releases
                credentialsFromEnv: [MAVEN_PREVIEW_USER, MAVEN_PREVIEW_PASSWORD]
              modrinth:
                projectId: mr-example
                tokenEnv: MR_PREVIEW_TOKEN
              curseforge:
                projectId: 987654
                tokenEnv: CF_PREVIEW_TOKEN
                gameVersionNames: ["26.3", "Fabric", "Client", "Server"]
        """.trimIndent() + "\n"
        val model = load(yaml)
        val selectedJar = jar()
        val before = projectFiles()

        val prepared = FabricAmperReleasePlan.prepare(model)
        val first = FabricAmperReleasePlan.render(prepared, selectedJar)
        val second = FabricAmperReleasePlan.render(prepared, selectedJar)
        assertEquals(first, second, "release plan must be deterministic")
        assertEquals(before, projectFiles(), "preview cannot create or edit any project files")

        val report = JsonParser.parseString(first).asJsonObject
        assertEquals("Fabric Amper RELEASE PLAN", report.get("title").asString)
        assertEquals("OFFLINE_PREVIEW_ONLY", report.get("mode").asString)
        assertEquals(0, report.get("remoteRequests").asInt)
        val artifact = report.getAsJsonObject("artifact")
        assertEquals("sample-release.jar", artifact.get("filename").asString)
        assertEquals(Files.size(selectedJar), artifact.get("sizeBytes").asLong)
        val expectedDigest = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(selectedJar))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(expectedDigest, artifact.get("sha256").asString)
        assertEquals(1, artifact.get("rootFabricModJsonEntries").asInt)
        assertEquals("example_mod", artifact.get("loaderId").asString)
        assertEquals("1.0.0+26.3", artifact.get("loaderVersion").asString)

        val targets = report.getAsJsonArray("targets").map { it.asJsonObject }
        assertEquals(listOf("maven-remote", "modrinth", "curseforge"),
            targets.map { it.get("platform").asString })
        assertEquals("ENVIRONMENT_VARIABLES_NOT_READ", targets[0].get("credentialSource").asString)
        assertEquals("org.example", targets[0].get("groupId").asString)
        assertEquals("example_mod", targets[0].get("artifactId").asString)

        val modrinth = targets[1].getAsJsonObject("metadataWithoutChangelog")
        assertEquals("mr-example", modrinth.get("project_id").asString)
        assertEquals("Shared 1.0.0", modrinth.get("name").asString)
        assertEquals("beta", modrinth.get("version_type").asString)
        assertEquals(listOf("26.3", "26.3.1"),
            modrinth.getAsJsonArray("game_versions").map { it.asString })
        assertEquals("required", modrinth.getAsJsonArray("dependencies")[0]
            .asJsonObject.get("dependency_type").asString)
        assertFalse(targets[1].get("externalProjectAndVersionsVerified").asBoolean)

        val curseforge = targets[2].getAsJsonObject("metadataWithoutChangelog")
        assertEquals("Shared 1.0.0", curseforge.get("displayName").asString)
        assertEquals(listOf("26.3", "Fabric", "Client", "Server"),
            curseforge.getAsJsonArray("gameVersionNames").map { it.asString })
        assertEquals("306612", curseforge.getAsJsonObject("relations")
            .getAsJsonArray("projects")[0].asJsonObject.get("projectID").asString)
        assertEquals("INLINE_CONTENT_OMITTED", targets[1].get("changelogSource").asString)
        assertTrue(report.getAsJsonArray("limitations").any { it.asString.contains("UNVERIFIED") })

        for (forbidden in listOf(
            "MR_PREVIEW_TOKEN", "CF_PREVIEW_TOKEN", "MAVEN_PREVIEW_PASSWORD",
            "MAVEN_PREVIEW_USER", "NEVER_PRINT_PRIVATE_RELEASE_NOTES", project.toString(),
        )) {
            assertFalse(prepared.contains(forbidden), "prepared task input exposed private value: $forbidden")
            assertFalse(first.contains(forbidden), "preview exposed private value: $forbidden")
        }
    }

    @Test
    fun `library local Maven and legacy remote targets are explicit while plain mods have no target`() {
        val libraryYaml = YamlFixtures.mod(
            YamlFixtures.valid.replace("product: fabric/mod", "product: fabric/api"),
            "group: org.example.library",
        ) + """
            publishing:
              repository:
                url: https://packages.example.org/maven
                usernameEnv: LEGACY_MAVEN_USERNAME
                passwordEnv: LEGACY_MAVEN_PASSWORD
        """.trimIndent() + "\n"
        val libraryPlan = JsonParser.parseString(FabricAmperReleasePlan.prepare(load(libraryYaml))).asJsonObject
        val targets = libraryPlan.getAsJsonArray("targets").map { it.asJsonObject }
        assertEquals(listOf("maven-local", "maven-remote"), targets.map { it.get("platform").asString })
        assertEquals("build/fabric-amper/maven-repository", targets[0].get("repository").asString)
        assertEquals("https://packages.example.org/maven", targets[1].get("repository").asString)
        assertEquals("ENVIRONMENT_VARIABLES_NOT_READ", targets[1].get("credentialSource").asString)
        assertFalse(libraryPlan.toString().contains("LEGACY_MAVEN_PASSWORD"))

        val plainModPlan = JsonParser.parseString(
            FabricAmperReleasePlan.render(load(YamlFixtures.valid), jar()),
        ).asJsonObject
        assertEquals(0, plainModPlan.getAsJsonArray("targets").size())
        assertEquals(1, plainModPlan.getAsJsonObject("artifact").get("rootFabricModJsonEntries").asInt)
    }

    @Test
    fun `release plan refuses missing root metadata or metadata from another release`() {
        val plan = FabricAmperReleasePlan.prepare(load(YamlFixtures.valid))
        val withoutRoot = makeJar("META-INF/fabric.mod.json" to validMetadata())
        val missing = assertThrows(IllegalArgumentException::class.java) {
            FabricAmperReleasePlan.render(plan, withoutRoot)
        }
        assertTrue(missing.message!!.contains("exactly one root fabric.mod.json"))

        val stale = jar("""{"schemaVersion":1,"id":"other_mod","version":"0.1.0"}""")
        val wrong = assertThrows(IllegalArgumentException::class.java) {
            FabricAmperReleasePlan.render(plan, stale)
        }
        assertTrue(wrong.message!!.contains("differs from the validated YAML"))

        for (trailing in listOf(" junk", " {\"other\":true}")) {
            val invalid = jar(validMetadata() + trailing)
            val malformed = assertThrows(IllegalArgumentException::class.java) {
                FabricAmperReleasePlan.render(plan, invalid)
            }
            assertEquals(
                "Release plan requires valid Loader JSON in root fabric.mod.json",
                malformed.message,
                "Trailing JSON or arbitrary text must be rejected as invalid Loader metadata without echoing content",
            )
        }
    }

    @Test
    fun `two physical root Loader metadata entries cannot be mistaken for one`() {
        val original = "fabric.mod.jsoX".toByteArray(StandardCharsets.US_ASCII)
        val replacement = "fabric.mod.json".toByteArray(StandardCharsets.US_ASCII)
        val output = makeJar(
            "fabric.mod.json" to validMetadata(),
            "fabric.mod.jsoX" to validMetadata(),
        )
        val bytes = Files.readAllBytes(output)
        var replaced = 0
        for (offset in 0..bytes.size - original.size) {
            if (original.indices.all { bytes[offset + it] == original[it] }) {
                replacement.copyInto(bytes, destinationOffset = offset)
                replaced++
            }
        }
        assertEquals(2, replaced, "must alter both ZIP local and central directory entry names")
        Files.write(output, bytes)
        val plan = FabricAmperReleasePlan.prepare(load(YamlFixtures.valid))
        val failure = assertThrows(IllegalArgumentException::class.java) {
            FabricAmperReleasePlan.render(plan, output)
        }
        assertTrue(failure.message!!.contains("exactly one root fabric.mod.json"))
    }
}
