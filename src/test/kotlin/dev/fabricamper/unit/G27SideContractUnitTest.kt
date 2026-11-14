package dev.fabricamper.unit

import com.google.gson.JsonParser
import dev.fabricamper.config.ModYamlLoader
import dev.fabricamper.generator.MetadataMapper
import dev.fabricamper.loom.DependencyConfigurer
import dev.fabricamper.loom.GradleDependencyDeclaration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** G27 side contracts that can be checked without downloading or launching a Minecraft server. */
class G27SideContractUnitTest {
    @TempDir lateinit var root: Path

    private fun load(yaml: String) = ModYamlLoader.load(YamlFixtures.write(root, yaml))

    private fun withClientSources(yaml: String): String = yaml.replace(
        "settings:\n", "settings:\n  loom:\n    splitEnvironmentSourceSets: true\n",
    )

    @Test
    fun `client targeted Gradle mod does not become a Loader mandatory dependency`() {
        val yaml = withClientSources(YamlFixtures.mod(fields = "environment: \"*\"")) + """
            dependencies:
              - maven: "com.example:client-only-mod:1.0.0"
                kind: mod
                scope: compile-only
                target: client
        """.trimIndent() + "\n"
        val model = load(yaml)
        val json = JsonParser.parseString(MetadataMapper.toJson(model)).asJsonObject
        val dependency = model.dependencies.single()
        assertEquals("client", dependency.target)
        assertEquals("mod", dependency.kind)
        assertEquals("clientCompileOnly", DependencyConfigurer.configurationName(
            GradleDependencyDeclaration(dependency.coordinate, dependency.kind, dependency.scope,
                dependency.target, dependency.exported, dependency.include),
            remapping = false, test = false,
        ))
        assertEquals("*", json.get("environment").asString)
        assertFalse(json.getAsJsonObject("depends").has("client-only-mod"))
        assertFalse(json.has("dependencies"), "Gradle-only dependencies must not appear in Loader metadata")
        assertFalse(json.has("client"), "Fabric Loader v1 has no top-level mod.client")
    }

    @Test
    fun `star environment with required client only mod retains dedicated server dependency risk`() {
        YamlFixtures.resource(root, "client.mixins.json", "{}")
        val yaml = withClientSources(YamlFixtures.mod(fields = """
            environment: "*"
            depends:
              client_only_mod: "*"
            entrypoints:
              main: ["example.MainEntrypoint"]
              client: ["example.ClientEntrypoint"]
            mixins:
              - config: client.mixins.json
                environment: client
        """))
        val model = load(yaml)
        val json = JsonParser.parseString(MetadataMapper.toJson(model)).asJsonObject

        // A dependency's remote mod metadata is unavailable in this offline
        // configuration. Keep the user-authored mandatory Loader dependency;
        // never infer that the dedicated server can safely load this mod.
        assertEquals("*", json.get("environment").asString)
        assertEquals("*", json.getAsJsonObject("depends").get("client_only_mod").asString)
        assertEquals("mod.depends (user)", MetadataMapper.dependencyOrigins(model)["client_only_mod"])
        assertFalse(json.has("client"), "mod.client is not a Loader v1 side selector")
        assertTrue(json.getAsJsonObject("depends").get("client_only_mod").isJsonPrimitive,
            "Loader v1 does not support per-dependency environment objects")
        assertEquals("example.MainEntrypoint", json.getAsJsonObject("entrypoints")
            .getAsJsonArray("main")[0].asString)
        assertEquals("example.ClientEntrypoint", json.getAsJsonObject("entrypoints")
            .getAsJsonArray("client")[0].asString)
        val clientMixin = json.getAsJsonArray("mixins")[0].asJsonObject
        assertEquals("client.mixins.json", clientMixin.get("config").asString)
        assertEquals("client", clientMixin.get("environment").asString)
    }

    @Test
    fun `global mod cannot load client only mixin resource without a client side selector`() {
        YamlFixtures.resource(root, "side-only.mixins.json", "{}", sourceSet = "client")
        val incorrectMixins = listOf(
            "mixins: [side-only.mixins.json]",
            """
                mixins:
                  - config: side-only.mixins.json
                    environment: "*"
            """.trimIndent(),
        )
        for (fields in incorrectMixins) {
            val yaml = withClientSources(YamlFixtures.mod(fields = "environment: \"*\"\n$fields"))
            val error = YamlFixtures.configError("FA009") { load(yaml) }
            assertEquals("mod.mixins[0].config", error.yamlPath,
                "global mixin cannot resolve only from src/client/resources")
        }
    }

    @Test
    fun `client scoped mixin or client scoped mod may use client only resource`() {
        YamlFixtures.resource(root, "side-only.mixins.json", "{}", sourceSet = "client")
        val explicitClientMixin = withClientSources(YamlFixtures.mod(fields = """
            environment: "*"
            mixins:
              - config: side-only.mixins.json
                environment: client
        """))
        val explicit = load(explicitClientMixin)
        assertEquals("client", explicit.mod.mixins.single().environment)

        val clientOnlyMod = withClientSources(YamlFixtures.mod(fields = """
            environment: client
            mixins: [side-only.mixins.json]
        """))
        val inferred = load(clientOnlyMod)
        assertEquals("client", inferred.mod.environment)
        assertEquals(null, inferred.mod.mixins.single().environment)
        val generated = JsonParser.parseString(MetadataMapper.toJson(inferred)).asJsonObject
        assertEquals("client", generated.get("environment").asString)
        assertEquals("side-only.mixins.json", generated.getAsJsonArray("mixins")[0].asString)
    }

    @Test
    fun `global mixin in main resources remains valid with client split enabled`() {
        YamlFixtures.resource(root, "global.mixins.json", "{}", sourceSet = "main")
        val yaml = withClientSources(YamlFixtures.mod(fields = """
            environment: "*"
            mixins: [global.mixins.json]
        """))
        val model = load(yaml)
        val json = JsonParser.parseString(MetadataMapper.toJson(model)).asJsonObject
        assertEquals("*", json.get("environment").asString)
        assertEquals("global.mixins.json", json.getAsJsonArray("mixins")[0].asString)
    }
}
