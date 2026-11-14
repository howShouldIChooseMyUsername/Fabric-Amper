package dev.fabricamper.unit

import com.google.gson.JsonParser
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.config.PublishingSpec
import dev.fabricamper.generator.MetadataMapper
import dev.fabricamper.loom.DependencyConfigurer
import dev.fabricamper.loom.GradleDependencyDeclaration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Strict offline parsing and dependency-scope requirements from README 2026-11-13.
 * Do not assert POM output here: generated POM and .module need isolated TestKit consumers.
 */
class FabricLibraryPublishingContractUnitTest {
    @TempDir lateinit var root: Path

    private fun load(yaml: String) = ModYamlLoader.load(YamlFixtures.write(root, yaml))

    private fun api(tail: String = "", product: String = "fabric/api"): String =
        YamlFixtures.mod(YamlFixtures.valid.replace("product: fabric/mod", "product: $product"),
            "group: org.example.fabric") + tail

    @Test
    fun `API publication is optional and remote credentials are referenced only by env names`() {
        for (product in listOf("fabric/api", "fabric/library")) {
            val withoutPublishing = load(api(product = product))
            assertEquals("org.example.fabric", withoutPublishing.mod.group)
            assertEquals("fabric/api", withoutPublishing.product)
            assertNull(withoutPublishing.publishing)
            val localOnly = load(api("publishing: {}\n", product))
            assertEquals("org.example.fabric", localOnly.mod.group)
            assertEquals(PublishingSpec(), localOnly.publishing)
            val remote = load(api("""
                publishing:
                  repository:
                    url: https://maven.example.org/releases
                    usernameEnv: FABRIC_AMPER_MAVEN_USERNAME
                    passwordEnv: FABRIC_AMPER_MAVEN_PASSWORD
            """.trimIndent() + "\n", product))
            val metadata = JsonParser.parseString(MetadataMapper.toJson(remote)).asJsonObject
            assertFalse(metadata.has("publishing"))
            assertFalse(metadata.has("usernameEnv"))
            assertFalse(metadata.has("passwordEnv"))
            assertEquals("org.example.fabric", remote.mod.group)
            val repo = remote.publishing?.repository
            assertEquals("https://maven.example.org/releases", repo?.url)
            assertEquals("FABRIC_AMPER_MAVEN_USERNAME", repo?.usernameEnv)
            assertEquals("FABRIC_AMPER_MAVEN_PASSWORD", repo?.passwordEnv)
            val anonymous = load(api("""
                publishing:
                  repository:
                    url: https://maven.example.org/public
            """.trimIndent() + "\n", product))
            assertEquals("https://maven.example.org/public", anonymous.publishing?.repository?.url)
            assertNull(anonymous.publishing?.repository?.usernameEnv)
            assertNull(anonymous.publishing?.repository?.passwordEnv)
            val upperScheme = load(api("""
                publishing:
                  repository:
                    url: HTTPS://maven.example.org/releases
            """.trimIndent() + "\n", product))
            assertEquals("HTTPS://maven.example.org/releases", upperScheme.publishing?.repository?.url,
                "URI schemes are case-insensitive; preserve original declared URL")
        }
    }

    @Test
    fun `remote publication forbids non HTTPS credentials in URLs and missing authority`() {
        val badUrls = listOf(
            "http://maven.example.org/releases",
            "ftp://maven.example.org/releases",
            "https://user:password@maven.example.org/releases",
            "https:///releases",
            "https://maven.example.org/releases#fragment",
            "https://maven.example.org/releases?token=example-placeholder",
            "https://maven.example.org/releases?mirror=release",
            "file:///tmp/maven-repository",
        )
        for (url in badUrls) {
            val yaml = api("publishing:\n  repository:\n    url: \"$url\"\n")
            val error = YamlFixtures.configError("FA003") { load(yaml) }
            assertEquals("publishing.repository.url", error.yamlPath, url)
        }
    }

    @Test
    fun `remote publication environment variable identifiers must be names not inline secrets`() {
        val invalidNames = listOf("1STARTS_WITH_DIGIT", "HAS-DASH", "HAS SPACE", "\${SECRET_TOKEN}")
        for (field in listOf("usernameEnv", "passwordEnv")) {
            for (invalid in invalidNames) {
                val counterpart = if (field == "usernameEnv")
                    "passwordEnv: SAFE_PASSWORD" else "usernameEnv: SAFE_USERNAME"
                val yaml = api("""
                    publishing:
                      repository:
                        url: https://maven.example.org/releases
                        $counterpart
                        $field: "$invalid"
                """.trimIndent() + "\n")
                val error = YamlFixtures.configError("FA003") { load(yaml) }
                assertEquals("publishing.repository.$field", error.yamlPath, "$field=$invalid")
            }
        }
    }

    @Test
    fun `legacy Maven repository credentials require either a complete environment pair or neither`() {
        for (product in listOf("fabric/api", "fabric/library")) {
            val repository = "publishing:\n  repository:\n    url: https://maven.example.org/releases\n"
            val none = load(api(repository, product)).publishing?.repository
            assertEquals("https://maven.example.org/releases", none?.url)
            assertNull(none?.usernameEnv)
            assertNull(none?.passwordEnv)

            val both = load(api(repository +
                "    usernameEnv: MAVEN_USERNAME\n    passwordEnv: MAVEN_PASSWORD\n", product))
                .publishing?.repository
            assertEquals("MAVEN_USERNAME", both?.usernameEnv)
            assertEquals("MAVEN_PASSWORD", both?.passwordEnv)

            for (single in listOf(
                "    usernameEnv: MAVEN_USERNAME\n",
                "    passwordEnv: MAVEN_PASSWORD\n",
            )) {
                val failure = YamlFixtures.configError("FA003") { load(api(repository + single, product)) }
                assertTrue(failure.yamlPath.startsWith("publishing.repository"),
                    "Partial legacy Maven credentials must fail at publishing.repository: ${failure.yamlPath}")
            }
        }
    }

    @Test
    fun `publishing rejects unknown keys and direct plaintext credential properties`() {
        val invalid = listOf(
            "publishing:\n  unexpected: value\n" to "publishing.unexpected",
            "publishing:\n  repository:\n    url: https://maven.example.org/releases\n    password: inline-password\n" to
                "publishing.repository.password",
            "publishing:\n  repository:\n    url: https://maven.example.org/releases\n    username: somebody\n" to
                "publishing.repository.username",
        )
        for ((tail, path) in invalid) {
            val error = YamlFixtures.configError("FA003") { load(api(tail)) }
            assertEquals(path, error.yamlPath)
        }
    }

    @Test
    fun `API dependency model keeps exported implementation runtime compileOnly and include scopes distinct`() {
        val model = load(api("""
            dependencies:
              - maven: "org.example:public-contract:1.2.3"
                exported: true
              - maven: "org.example:internal-helper:2.0.0"
                scope: all
              - maven: "org.example:runtime-hook:3.0.0"
                scope: runtime-only
              - maven: "org.example:compile-annotations:4.0.0"
                scope: compile-only
              - maven: "org.example:embedded:5.0.0"
                include: true
            test-dependencies:
              - "org.junit.jupiter:junit-jupiter:6.0.0": all
        """.trimIndent() + "\n"))
        val expectedConfigurations = listOf("api", "implementation", "runtimeOnly", "compileOnly", "implementation")
        assertEquals(expectedConfigurations, model.dependencies.map { dep ->
            DependencyConfigurer.configurationName(
                GradleDependencyDeclaration(dep.coordinate, dep.kind, dep.scope,
                    dep.target, dep.exported, dep.include), remapping = false, test = false,
            )
        })
        assertEquals(listOf(true, false, false, false, false), model.dependencies.map { it.exported })
        assertEquals(listOf(false, false, false, false, true), model.dependencies.map { it.include })
        assertEquals("testImplementation", DependencyConfigurer.configurationName(
            GradleDependencyDeclaration(model.testDependencies.single().coordinate),
            remapping = false, test = true,
        ))
        val json = JsonParser.parseString(MetadataMapper.toJson(model)).asJsonObject
        assertFalse(json.has("dependencies"), "Gradle dependency scopes must not enter Loader metadata")
        assertFalse(json.has("test-dependencies"), "test dependencies must not enter Loader metadata")
        assertTrue(json.getAsJsonObject("depends").has("minecraft"))
    }

    @Test
    fun `client targeted dependency cannot be promoted into common Maven API or Loader depends`() {
        val yaml = api("""
            dependencies:
              - maven: "org.example:client-helper:1.0.0"
                kind: mod
                scope: all
                target: client
        """.trimIndent() + "\n").replace(
            "settings:\n", "settings:\n  loom:\n    splitEnvironmentSourceSets: true\n",
        )
        val model = load(yaml)
        val client = model.dependencies.single()
        assertEquals("client", client.target)
        assertFalse(client.exported)
        assertFalse(client.include)
        assertEquals("clientImplementation", DependencyConfigurer.configurationName(
            GradleDependencyDeclaration(client.coordinate, client.kind, client.scope,
                client.target, client.exported, client.include), remapping = false, test = false,
        ))
        val metadata = JsonParser.parseString(MetadataMapper.toJson(model)).asJsonObject
        assertFalse(metadata.getAsJsonObject("depends").has("client-helper"),
            "client Gradle dependency is not an automatic Loader required mod")
    }
}
