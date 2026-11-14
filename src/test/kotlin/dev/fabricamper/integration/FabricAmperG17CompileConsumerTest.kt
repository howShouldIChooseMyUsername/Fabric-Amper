package dev.fabricamper.integration

import com.google.gson.JsonParser
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * G17 compile-time contract. A direct call on the Mojang Minecraft type must
 * compile from YAML custom metadata alone. No Class Tweaker, Mixin or manually
 * authored fabric.mod.json is provided in either independent consumer.
 *
 * The negative consumer runs first so a cached transformed Minecraft JAR from
 * the positive project cannot turn a false-positive into a passing test.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_G17", matches = "true")
class FabricAmperG17CompileConsumerTest {
    @TempDir lateinit var suiteRoot: Path

    @Test
    fun `G17 custom-only injected interface enables direct ItemStack call and removal breaks javac`() {
        val fixture = TestKitFixture(suiteRoot)
        val withInjection = fixture.resource("runtime/g17-compile.yaml")
        val withoutInjection = fixture.resource("runtime/g17-no-custom.yaml")
        val customKey = "loom:injected_interfaces"
        val target = "net/minecraft/world/item/ItemStack"
        val injectedInterface = "org/example/runtime/G17Injected"
        val method = "amper_rt_g17${'$'}ping"

        assertTrue(withInjection.contains("\"$customKey\""))
        assertFalse(withoutInjection.contains(customKey))
        for (yaml in listOf(withInjection, withoutInjection)) {
            assertFalse(yaml.contains("accessWidener:"), "G17 compile test must never enable Class Tweaker")
            assertFalse(yaml.contains("mixins:"), "G17 compile test must never enable Mixins")
        }
        // The negative fixture is byte-for-byte equivalent, apart from one
        // custom metadata stanza. This avoids accidentally testing a different
        // dependency, mapping, source-set or Java profile.
        val injectedStanza = listOf(
            "  custom:",
            "    \"loom:injected_interfaces\":",
            "      \"net/minecraft/world/item/ItemStack\":",
            "        - \"org/example/runtime/G17Injected\"",
        ).joinToString("\n", postfix = "\n")
        assertTrue(withInjection.contains(injectedStanza))
        assertEquals(withoutInjection, withInjection.replace(injectedStanza, ""))

        val javaInterface = """
            package org.example.runtime;
            public interface G17Injected {
                default String amper_rt_g17${'$'}ping() { return "G17_DEFAULT_OK"; }
            }
        """.trimIndent() + "\n"
        val javaCaller = """
            package org.example.runtime;
            import net.fabricmc.api.ModInitializer;
            import net.minecraft.world.item.ItemStack;
            public final class G17CompileProbe implements ModInitializer {
                public static String directCall() {
                    return ItemStack.EMPTY.amper_rt_g17${'$'}ping();
                }
                @Override public void onInitialize() {
                    if (!"G17_DEFAULT_OK".equals(directCall())) {
                        throw new IllegalStateException("G17 default method returned an unexpected value");
                    }
                }
            }
        """.trimIndent() + "\n"

        fun writeIdenticalJava(root: Path) {
            fixture.write(root, "src/main/java/org/example/runtime/G17Injected.java", javaInterface)
            fixture.write(root, "src/main/java/org/example/runtime/G17CompileProbe.java", javaCaller)
            assertFalse(Files.exists(root.resolve("src/main/resources/fabric.mod.json")))
        }

        val negative = fixture.project("g17-no-custom", withoutInjection)
        writeIdenticalJava(negative)
        val missing = fixture.fail(negative, "compileJava")
        assertEquals(TaskOutcome.FAILED, missing.task(":compileJava")?.outcome,
            "The negative must fail at javac, not during plugin setup or dependency resolution:\n${missing.output}")
        assertTrue(missing.output.contains("G17CompileProbe.java"), missing.output)
        assertTrue(missing.output.contains("cannot find symbol"), missing.output)
        assertTrue(missing.output.contains(method),
            "Javac must specifically reject the absent injected method:\n${missing.output}")
        fixture.assertNoConsumerBuildScript(negative)

        val positive = fixture.project("g17-yaml-only", withInjection)
        writeIdenticalJava(positive)
        assertFalse(Files.exists(fixture.generatedJson(positive)), "No task output before compilation")
        // Explicitly observe Loom's configured source before any generator or
        // Java task runs. Merely finding a snapshot on disk would not prove that
        // Loom consumed it instead of an unrelated metadata location.
        val probeScript = positive.resolve("inspect-g17-loom.gradle")
        fixture.write(positive, "inspect-g17-loom.gradle", """
            gradle.projectsEvaluated {
                def loom = rootProject.extensions.getByName('loom')
                println('FABRIC_AMPER_G17_LOOM_PATH=' + loom.fabricModJsonPath.get().asFile.absolutePath)
            }
        """.trimIndent() + "\n")
        val configured = fixture.run(positive, "help", "--init-script", probeScript.toString())
        val mirror = fixture.mirrorJson(positive).single()
        assertTrue(configured.output.contains("FABRIC_AMPER_G17_LOOM_PATH=${mirror.toAbsolutePath()}"),
            "Loom must point at the content-addressed, custom-bearing YAML mirror:\n${configured.output}")
        assertFalse(Files.exists(fixture.generatedJson(positive)),
            "Configuration-only help must not produce task-owned metadata")
        assertInterfaceMetadata(Files.readAllBytes(mirror), "amper_rt_g17_compile",
            customKey, target, injectedInterface)
        val built = fixture.run(positive, "build")
        assertEquals(TaskOutcome.SUCCESS, built.task(":compileJava")?.outcome,
            "Loom must make the custom-injected method available to Java compilation:\n${built.output}")
        assertTrue(Files.isRegularFile(positive.resolve(
            "build/classes/java/main/org/example/runtime/G17CompileProbe.class")))
        assertTrue(Files.isRegularFile(positive.resolve(
            "build/classes/java/main/org/example/runtime/G17Injected.class")))

        val mirrorBytes = Files.readAllBytes(mirror)
        val generatedBytes = Files.readAllBytes(fixture.generatedJson(positive))
        assertArrayEquals(mirrorBytes, generatedBytes, "Loom configuration mirror must match generated JSON")
        assertInterfaceMetadata(mirrorBytes, "amper_rt_g17_compile", customKey, target, injectedInterface)

        val distribution = fixture.jars(positive).single()
        ZipFile(distribution.toFile()).use { zip ->
            val roots = zip.entries().asSequence().filter { it.name == "fabric.mod.json" }.toList()
            assertEquals(1, roots.size, "Exactly one generated root metadata entry is required")
            val shippedBytes = zip.getInputStream(roots.single()).use { it.readAllBytes() }
            assertArrayEquals(generatedBytes, shippedBytes)
            assertInterfaceMetadata(shippedBytes, "amper_rt_g17_compile", customKey, target, injectedInterface)
            assertTrue(zip.getEntry("org/example/runtime/G17Injected.class") != null)
            assertTrue(zip.getEntry("org/example/runtime/G17CompileProbe.class") != null)
            assertFalse(zip.entries().asSequence().any { it.name.endsWith(".classtweaker") })
            assertFalse(zip.entries().asSequence().any { it.name.endsWith(".mixins.json") })
        }
        fixture.assertNoConsumerBuildScript(positive)
    }

    private fun assertInterfaceMetadata(
        bytes: ByteArray, modId: String, customKey: String, target: String, injectedInterface: String,
    ) {
        val json = JsonParser.parseString(String(bytes, StandardCharsets.UTF_8)).asJsonObject
        assertEquals(modId, json.get("id").asString)
        assertFalse(json.has("accessWidener"), "The compile fixture must not rely on Class Tweaker")
        assertFalse(json.has("mixins"), "The compile fixture must not rely on Mixin")
        val custom = json.getAsJsonObject("custom")
        assertEquals(setOf(customKey), custom.keySet())
        val mappings = custom.getAsJsonObject(customKey)
        assertEquals(setOf(target), mappings.keySet())
        assertEquals(listOf(injectedInterface), mappings.getAsJsonArray(target).map { it.asString })
    }
}
