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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * G27 non-server acceptance: the YAML-only consumer compiles real common and
 * client entrypoints, isolates a clientCompileOnly library, and ships a
 * client-scoped Mixin sourced solely from src/client/resources.
 *
 * No client/server/game process is started. Dedicated-server Loader behavior
 * remains NOT VERIFIED until a separately authorized isolated EULA-approved
 * server can be exercised.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_G27", matches = "true")
class FabricAmperG27SideConsumerTest {
    @TempDir lateinit var suiteRoot: Path

    @Test
    fun `G27 star environment preserves client source dependency and mixin boundary in distribution`() {
        val fixture = TestKitFixture(suiteRoot)
        val yaml = fixture.resource("runtime/g27-side.yaml")
        val mixinSource = fixture.resource("runtime/g27.client.mixins.json")
        val mixinName = "g27.client.mixins.json"
        val libraryCoordinate = "org.apache.commons:commons-collections4:4.5.0"
        assertTrue(yaml.contains("environment: \"*\""))
        assertTrue(yaml.contains("splitEnvironmentSourceSets: true"))
        assertTrue(yaml.contains("target: client"))
        assertTrue(yaml.contains("scope: compile-only"))

        fun installCommonAndClientSources(project: Path) {
            fixture.write(project, "src/main/java/org/example/g27/G27Main.java", """
                package org.example.g27;
                import net.fabricmc.api.ModInitializer;
                public final class G27Main implements ModInitializer {
                    @Override public void onInitialize() {}
                }
            """.trimIndent() + "\n")
            fixture.write(project, "src/client/java/org/example/g27/G27Client.java", """
                package org.example.g27;
                import net.fabricmc.api.ClientModInitializer;
                public final class G27Client implements ClientModInitializer {
                    @Override public void onInitializeClient() {}
                }
            """.trimIndent() + "\n")
            fixture.write(project, "src/client/java/org/example/g27/G27ClientLibraryProbe.java", """
                package org.example.g27;
                import org.apache.commons.collections4.bag.HashBag;
                public final class G27ClientLibraryProbe {
                    private G27ClientLibraryProbe() {}
                    public static int count() {
                        HashBag<String> bag = new HashBag<>();
                        bag.add("client");
                        return bag.getCount("client");
                    }
                }
            """.trimIndent() + "\n")
            fixture.write(project, "src/client/java/org/example/g27/mixin/G27ClientMixin.java", """
                package org.example.g27.mixin;
                import net.minecraft.client.Minecraft;
                import org.spongepowered.asm.mixin.Mixin;
                @Mixin(Minecraft.class)
                public abstract class G27ClientMixin {}
            """.trimIndent() + "\n")
            fixture.write(project, "src/client/resources/$mixinName", mixinSource)
            assertFalse(Files.exists(project.resolve("src/main/resources/$mixinName")),
                "The Mixin must exist only in the client resource source set")
            assertFalse(Files.exists(project.resolve("src/main/resources/fabric.mod.json")))
            assertFalse(Files.exists(project.resolve("src/client/resources/fabric.mod.json")))
            fixture.assertNoConsumerBuildScript(project)
        }

        // Existing G27SideContractUnitTest also requires FA009 when a global
        // mod refers to a client-only mixin without environment: client.
        // Exercise that boundary through a real settings-only consumer parser.
        val globallyScopedYaml = yaml.replace("      environment: client\n", "      environment: \"*\"\n")
        assertFalse(globallyScopedYaml == yaml, "The invalid fixture must change only the Mixin side")
        val invalidMixin = fixture.project("g27-global-mixin-rejected", globallyScopedYaml)
        installCommonAndClientSources(invalidMixin)
        val rejected = fixture.fail(invalidMixin, "help")
        assertTrue(rejected.output.contains("FA009"), rejected.output)
        assertTrue(rejected.output.contains("mod.mixins[0].config"),
            "FA009 must identify the client-only Mixin resource path:\n${rejected.output}")
        assertFalse(Files.exists(fixture.generatedJson(invalidMixin)))

        // Fresh consumer with identical YAML and client sources, but a forbidden
        // common-source import. A compileJava failure specifically about HashBag
        // proves the target:client library was not leaked into the main classpath.
        val negative = fixture.project("g27-common-no-client-library", yaml)
        installCommonAndClientSources(negative)
        fixture.write(negative, "src/main/java/org/example/g27/G27IllegalCommonLibraryProbe.java", """
            package org.example.g27;
            import org.apache.commons.collections4.bag.HashBag;
            public final class G27IllegalCommonLibraryProbe {
                public static Class<?> illegalReference() { return HashBag.class; }
            }
        """.trimIndent() + "\n")
        val missing = fixture.fail(negative, "compileJava")
        assertEquals(TaskOutcome.FAILED, missing.task(":compileJava")?.outcome,
            "The negative must reach javac and fail on the client-only library:\n${missing.output}")
        assertTrue(missing.output.contains("G27IllegalCommonLibraryProbe.java"), missing.output)
        assertTrue(missing.output.contains("package org.apache.commons.collections4.bag does not exist"),
            "Expected the missing client-only HashBag package, not a configuration failure:\n${missing.output}")
        fixture.assertNoConsumerBuildScript(negative)

        val positive = fixture.project("g27-client-only-side", yaml)
        installCommonAndClientSources(positive)
        assertFalse(Files.exists(fixture.generatedJson(positive)), "Metadata must be task-generated")
        val built = fixture.run(positive, "build")
        assertEquals(TaskOutcome.SUCCESS, built.task(":compileJava")?.outcome, built.output)
        assertEquals(TaskOutcome.SUCCESS, built.task(":compileClientJava")?.outcome,
            "The client source set must compile a real HashBag use plus the Minecraft client Mixin:\n${built.output}")
        for ((sourceSet, file) in listOf(
            "main" to "org/example/g27/G27Main.class",
            "client" to "org/example/g27/G27Client.class",
            "client" to "org/example/g27/G27ClientLibraryProbe.class",
            "client" to "org/example/g27/mixin/G27ClientMixin.class",
        )) {
            assertTrue(Files.isRegularFile(positive.resolve("build/classes/java/$sourceSet/$file")),
                "Expected compiled $sourceSet class: $file")
        }

        val mirrorBytes = Files.readAllBytes(fixture.mirrorJson(positive).single())
        val generatedBytes = Files.readAllBytes(fixture.generatedJson(positive))
        assertArrayEquals(mirrorBytes, generatedBytes, "Loom metadata mirror must match the generated Loader JSON")
        assertLoaderSideMetadata(generatedBytes, mixinName, libraryCoordinate)

        val jar = fixture.jars(positive).single()
        ZipFile(jar.toFile()).use { zip ->
            val metadataEntries = zip.entries().asSequence().filter { it.name == "fabric.mod.json" }.toList()
            assertEquals(1, metadataEntries.size, "Only one Loader root JSON may ship")
            val shippedBytes = zip.getInputStream(metadataEntries.single()).use { it.readAllBytes() }
            assertArrayEquals(generatedBytes, shippedBytes, "Packaged Loader JSON must match YAML-generated JSON")
            assertLoaderSideMetadata(shippedBytes, mixinName, libraryCoordinate)
            val clientMixinEntries = zip.entries().asSequence().filter { it.name == mixinName }.toList()
            assertEquals(1, clientMixinEntries.size, "Client-only Mixin resource must occur once")
            val packagedMixin = zip.getInputStream(clientMixinEntries.single()).use { it.readAllBytes() }
            assertArrayEquals(mixinSource.toByteArray(StandardCharsets.UTF_8), packagedMixin,
                "Client Mixin JSON must retain its exact authored bytes")
            val mixinJson = JsonParser.parseString(String(packagedMixin, StandardCharsets.UTF_8)).asJsonObject
            assertEquals("org.example.g27.mixin", mixinJson.get("package").asString)
            assertEquals(listOf("G27ClientMixin"),
                mixinJson.getAsJsonArray("client").map { it.asString })
            assertFalse(mixinJson.has("mixins"), "The Mixin class must not be declared on both sides")
            for (compiled in listOf(
                "org/example/g27/G27Main.class",
                "org/example/g27/G27Client.class",
                "org/example/g27/G27ClientLibraryProbe.class",
                "org/example/g27/mixin/G27ClientMixin.class",
            )) {
                assertNotNull(zip.getEntry(compiled), "Split source-set class missing in JAR: $compiled")
            }
            assertFalse(zip.getEntry("org/apache/commons/collections4/bag/HashBag.class") != null,
                "A clientCompileOnly library must not be bundled into the mod JAR")
        }
        fixture.assertNoConsumerBuildScript(positive)
    }

    private fun assertLoaderSideMetadata(bytes: ByteArray, mixinName: String, libraryCoordinate: String) {
        val metadata = JsonParser.parseString(String(bytes, StandardCharsets.UTF_8)).asJsonObject
        assertEquals("amper_rt_g27", metadata.get("id").asString)
        assertEquals("*", metadata.get("environment").asString)
        val entrypoints = metadata.getAsJsonObject("entrypoints")
        assertEquals(listOf("org.example.g27.G27Main"),
            entrypoints.getAsJsonArray("main").map { it.asString })
        assertEquals(listOf("org.example.g27.G27Client"),
            entrypoints.getAsJsonArray("client").map { it.asString })
        val mixins = metadata.getAsJsonArray("mixins")
        assertEquals(1, mixins.size())
        val mixin = mixins[0].asJsonObject
        assertEquals(mixinName, mixin.get("config").asString)
        assertEquals("client", mixin.get("environment").asString)
        val depends = metadata.getAsJsonObject("depends")
        assertEquals(setOf("minecraft", "fabricloader", "java", "fabric-api"), depends.keySet(),
            "Client Gradle dependency must not become a Loader depends entry")
        assertFalse(metadata.toString().contains(libraryCoordinate))
        assertFalse(metadata.has("dependencies"), "Gradle-only dependencies must not leak into Loader JSON")
        assertFalse(metadata.has("jars"), "compile-only must not generate a nested runtime library")
    }
}
