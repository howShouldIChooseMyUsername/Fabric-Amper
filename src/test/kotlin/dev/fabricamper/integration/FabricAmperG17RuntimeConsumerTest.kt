package dev.fabricamper.integration

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.zip.ZipFile

/**
 * G17 runtime acceptance, independent from the custom-only compile fixture.
 *
 * The YAML-supplied loom:injected_interfaces must make direct Java calls
 * compilable. At runtime, the separately declared official Class Tweaker is
 * responsible for actually implementing the interface on Minecraft's class.
 * Only a fresh isolated runClient is permitted; the supervised process kills
 * only its own process tree after Loader main/client/tick nonce markers.
 *
 * Deliberately requires an additional G17 runtime opt-in: existing G15/G10
 * runtime selections must never accidentally start this client.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_G17", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_RUNTIME", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_RUNTIME_G17", matches = "true")
class FabricAmperG17RuntimeConsumerTest {
    private val producerRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    private val buildRoot: Path get() = producerRoot.resolve("build").toRealPath()

    @Test
    fun `G17 actual Loader applies Class Tweaker injection and invokes default method on game tick`() {
        val consumer = newConsumer()
        val nonce = UUID.randomUUID().toString()
        val target = "net/minecraft/world/item/ItemStack"
        val injectedInterface = "org/example/runtime/G17Injected"
        val tweak = "g17.classtweaker"
        val tweakContents = """
            classTweaker v1 official
            inject-interface $target $injectedInterface
        """.trimIndent() + "\n"
        write(consumer, "src/main/resources/$tweak", tweakContents)
        installSources(consumer, nonce)
        assertFalse(Files.exists(consumer.resolve("src/main/resources/fabric.mod.json")))
        assertFalse(Files.exists(consumer.resolve("build/generated/fabric-amper/resources/main/fabric.mod.json")))

        val buildLog = process(consumer, Duration.ofMinutes(7)).completedTask("build")
        assertTrue(buildLog.contains("BUILD SUCCESSFUL"), buildLog)

        val mirrorRoot = consumer.resolve(".gradle/fabric-amper/loom")
        val mirrors = Files.walk(mirrorRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString() == "fabric.mod.json" }.toList()
        }
        assertEquals(1, mirrors.size, "A single content-addressed Loom metadata snapshot is expected")
        val mirrorBytes = Files.readAllBytes(mirrors.single())
        val generatedBytes = Files.readAllBytes(
            consumer.resolve("build/generated/fabric-amper/resources/main/fabric.mod.json"),
        )
        assertArrayEquals(mirrorBytes, generatedBytes, "Loom mirror and generated JSON must match")
        assertInjectionMetadata(mirrorBytes, target, injectedInterface)

        val distribution = consumer.resolve("build/libs/amper-rt-g17-0.1.0+26.3.jar")
        assertTrue(Files.isRegularFile(distribution), "G17 distribution JAR is missing: $distribution")
        ZipFile(distribution.toFile()).use { zip ->
            val metadata = zip.entries().asSequence().filter { it.name == "fabric.mod.json" }.toList()
            assertEquals(1, metadata.size, "Exactly one Loader root metadata entry")
            val shipped = zip.getInputStream(metadata.single()).use { it.readAllBytes() }
            assertArrayEquals(generatedBytes, shipped)
            assertInjectionMetadata(shipped, target, injectedInterface)
            for (name in listOf(
                "org/example/runtime/G17Injected.class",
                "org/example/runtime/G17Main.class",
                "org/example/runtime/G17Client.class",
                "org/example/runtime/G17Proof.class",
                tweak,
            )) {
                assertTrue(zip.getEntry(name) != null, "Missing production JAR entry: $name")
            }
            assertEquals(tweakContents,
                zip.getInputStream(zip.getEntry(tweak)).use {
                    String(it.readAllBytes(), StandardCharsets.UTF_8)
                },
                "Official Class Tweaker must be packaged unchanged",
            )
        }
        val proofs = consumer.resolve("runtime-proof")
        val expected = mapOf(
            proofs.resolve("$nonce.main") to "$nonce:loader-main-interface-default-ok",
            proofs.resolve("$nonce.client") to "$nonce:loader-client-initialized",
            proofs.resolve("$nonce.tick") to "$nonce:real-client-tick-interface-default-ok",
        )
        process(consumer, Duration.ofMinutes(3)).clientUntilMarkers(expected)
        for ((file, content) in expected) {
            assertEquals(content, Files.readString(file, StandardCharsets.UTF_8),
                "Real Loader/client tick marker does not match its nonce: $file")
        }
        assertNoConsumerBuildScript(consumer)
    }

    private fun assertInjectionMetadata(bytes: ByteArray, target: String, injectedInterface: String) {
        val json = JsonParser.parseString(String(bytes, StandardCharsets.UTF_8)).asJsonObject
        assertEquals("amper_rt_g17", json.get("id").asString)
        assertEquals("g17.classtweaker", json.get("accessWidener").asString)
        assertFalse(json.has("mixins"), "G17 runtime must not rely on Mixin for this default method")
        val custom = json.getAsJsonObject("custom")
        assertEquals(setOf("loom:injected_interfaces"), custom.keySet())
        val mappings = custom.getAsJsonObject("loom:injected_interfaces")
        assertEquals(setOf(target), mappings.keySet())
        assertEquals(listOf(injectedInterface), mappings.getAsJsonArray(target).map { it.asString })
    }

    private fun installSources(consumer: Path, nonce: String) {
        val expectedHome = literal(consumer.resolve("sandbox-home").toAbsolutePath().normalize().toString())
        val outputFolder = literal(consumer.resolve("runtime-proof").toAbsolutePath().normalize().toString())
        write(consumer, "src/main/java/org/example/runtime/G17Injected.java", """
            package org.example.runtime;
            public interface G17Injected {
                default String amper_rt_g17${'$'}ping() { return "G17_DEFAULT_OK"; }
            }
        """.trimIndent() + "\n")
        write(consumer, "src/main/java/org/example/runtime/G17Proof.java", """
            package org.example.runtime;
            import java.nio.charset.StandardCharsets;
            import java.nio.file.*;
            public final class G17Proof {
                private G17Proof() {}
                public static void mark(String type, String value) {
                    Path userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
                    if (!userHome.equals(Path.of("$expectedHome"))) {
                        throw new IllegalStateException("Unexpected game home: " + userHome);
                    }
                    try {
                        Path folder = Path.of("$outputFolder");
                        Files.createDirectories(folder);
                        Files.writeString(folder.resolve("$nonce." + type), value, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    } catch (java.io.IOException error) {
                        throw new IllegalStateException("Could not record isolated Loader proof", error);
                    }
                }
            }
        """.trimIndent() + "\n")
        write(consumer, "src/main/java/org/example/runtime/G17Main.java", """
            package org.example.runtime;
            import net.fabricmc.api.ModInitializer;
            import net.fabricmc.loader.api.FabricLoader;
            import net.minecraft.world.item.ItemStack;
            public final class G17Main implements ModInitializer {
                @Override public void onInitialize() {
                    if (!FabricLoader.getInstance().isModLoaded("amper_rt_g17")) {
                        throw new IllegalStateException("Fabric Loader did not discover G17");
                    }
                    if (!((Object) ItemStack.EMPTY instanceof G17Injected)) {
                        throw new IllegalStateException("Class Tweaker failed to implement G17Injected");
                    }
                    if (!"G17_DEFAULT_OK".equals(ItemStack.EMPTY.amper_rt_g17${'$'}ping())) {
                        throw new IllegalStateException("G17 default method not available in Loader");
                    }
                    G17Proof.mark("main", "$nonce:loader-main-interface-default-ok");
                }
            }
        """.trimIndent() + "\n")
        write(consumer, "src/client/java/org/example/runtime/G17Client.java", """
            package org.example.runtime;
            import net.fabricmc.api.ClientModInitializer;
            import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
            import net.minecraft.world.item.ItemStack;
            import java.util.concurrent.atomic.AtomicBoolean;
            public final class G17Client implements ClientModInitializer {
                private static final AtomicBoolean FIRST_TICK = new AtomicBoolean();
                @Override public void onInitializeClient() {
                    G17Proof.mark("client", "$nonce:loader-client-initialized");
                    ClientTickEvents.END_CLIENT_TICK.register(client -> {
                        if (FIRST_TICK.compareAndSet(false, true)) {
                            if (!((Object) ItemStack.EMPTY instanceof G17Injected) ||
                                    !"G17_DEFAULT_OK".equals(ItemStack.EMPTY.amper_rt_g17${'$'}ping())) {
                                throw new IllegalStateException("Interface injection missing during actual game tick");
                            }
                            G17Proof.mark("tick", "$nonce:real-client-tick-interface-default-ok");
                        }
                    });
                }
            }
        """.trimIndent() + "\n")
    }

    private fun newConsumer(): Path {
        val parent = Files.createDirectories(buildRoot.resolve("fabric-amper-runtime-consumers"))
        val consumer = Files.createTempDirectory(parent, "g17-loader-").toRealPath()
        require(consumer.startsWith(buildRoot)) { "G17 fixture escaped the approved build root" }
        val published = buildRoot.resolve("g29-probe/maven-repo").toRealPath()
        require(published.startsWith(buildRoot)) { "Published plugin repository escaped build root" }
        val marker = published.resolve(
            "dev/fabric-amper/settings/dev.fabric-amper.settings.gradle.plugin/0.3.1/" +
                "dev.fabric-amper.settings.gradle.plugin-0.3.1.pom",
        )
        val moduleJar = published.resolve("dev/fabricamper/fabric-amper/0.3.1/fabric-amper-0.3.1.jar")
        require(Files.isRegularFile(marker) && Files.isRegularFile(moduleJar)) {
            "Prime must publish the G29 local plugin marker/POM/JAR before running isolated G17"
        }
        val repoUri = published.toUri().toASCIIString()
        write(consumer, "settings.gradle.kts", """
            pluginManagement {
                repositories {
                    maven { url = uri("$repoUri") }
                    maven("https://maven.fabricmc.net/")
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            plugins { id("dev.fabric-amper.settings") version "0.3.1" }
        """.trimIndent() + "\n")
        val yaml = requireNotNull(
            javaClass.getResourceAsStream("/integration/runtime/g17-loader.yaml"),
        ) { "G17 Loader YAML fixture is missing" }
            .bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        write(consumer, "mod.yaml", yaml)
        assertNoConsumerBuildScript(consumer)
        return consumer
    }

    private fun process(consumer: Path, timeout: Duration): IsolatedFabricClientProcess {
        val executable = System.getenv("FABRIC_AMPER_RUNTIME_GRADLE_BIN")
            ?: error("FABRIC_AMPER_RUNTIME_GRADLE_BIN must explicitly select an absolute Gradle launcher")
        val path = Path.of(executable)
        require(path.isAbsolute) { "G17 runtime Gradle launcher must be an absolute path" }
        return IsolatedFabricClientProcess(consumer, path, timeout)
    }

    private fun write(root: Path, relative: String, content: String) {
        val target = root.resolve(relative).normalize()
        require(target.startsWith(root)) { "G17 fixture attempted to write outside its consumer root" }
        Files.createDirectories(target.parent)
        Files.writeString(target, content, StandardCharsets.UTF_8)
    }

    private fun assertNoConsumerBuildScript(root: Path) {
        assertFalse(Files.exists(root.resolve("build.gradle")))
        assertFalse(Files.exists(root.resolve("build.gradle.kts")))
        val bootstrap = Files.readString(root.resolve("settings.gradle.kts"))
        assertTrue(bootstrap.contains("dev.fabric-amper.settings"))
        assertFalse(bootstrap.contains("net.fabricmc.fabric-loom"))
    }

    private fun literal(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"")
}
