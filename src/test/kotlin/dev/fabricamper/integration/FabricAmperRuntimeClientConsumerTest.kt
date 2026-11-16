package dev.fabricamper.integration

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Real client Loader acceptance. Explicit environment opt-in ONLY.
 *
 * - Consumer roots live under the producer's build/fabric-amper-runtime-consumers.
 * - The exact published G29 plugin marker/POM/JAR must already exist locally.
 * - Native Gradle is launched by a supervised ProcessBuilder, never the
 *   blocking GradleRunner.runClient path.
 * - Only runClient, never runServer, no user Minecraft installation, no EULA,
 *   no auth token injection, no server/world joins.
 *
 * To enable, Prime supplies FABRIC_AMPER_TESTKIT_FULL=true,
 * FABRIC_AMPER_TESTKIT_RUNTIME=true, and an absolute
 * FABRIC_AMPER_RUNTIME_GRADLE_BIN pointing to a Gradle 9.7.1+ launcher.
 * Absence of any required runtime input when enabled is FAIL, not PASS.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_RUNTIME", matches = "true")
class FabricAmperRuntimeClientConsumerTest {
    private val producerRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    private val buildRoot: Path get() = producerRoot.resolve("build").toRealPath()

    @Test
    fun `G15 published settings-only mod reaches real Fabric Loader main client and game tick`() {
        val consumer = newConsumer("g15-loader", "runtime/g15-loader.yaml")
        val nonce = UUID.randomUUID().toString()
        installProofWriter(consumer, nonce)
        write(consumer, "src/main/java/org/example/runtime/ProbeMain.java", """
            package org.example.runtime;
            import net.fabricmc.api.ModInitializer;
            import net.fabricmc.loader.api.FabricLoader;
            public final class ProbeMain implements ModInitializer {
                @Override public void onInitialize() {
                    if (!FabricLoader.getInstance().isModLoaded("amper_rt_g15")) {
                        throw new IllegalStateException("Loader did not discover amper_rt_g15");
                    }
                    ProofWriter.mark("main", "$nonce:loader-main");
                }
            }
        """.trimIndent() + "\n")
        write(consumer, "src/client/java/org/example/runtime/ProbeClient.java", """
            package org.example.runtime;
            import net.fabricmc.api.ClientModInitializer;
            import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
            import java.util.concurrent.atomic.AtomicBoolean;
            public final class ProbeClient implements ClientModInitializer {
                private static final AtomicBoolean FIRST_TICK = new AtomicBoolean();
                @Override public void onInitializeClient() {
                    ProofWriter.mark("client", "$nonce:loader-client");
                    ClientTickEvents.END_CLIENT_TICK.register(client -> {
                        if (FIRST_TICK.compareAndSet(false, true)) {
                            ProofWriter.mark("tick", "$nonce:actual-client-tick");
                        }
                    });
                }
            }
        """.trimIndent() + "\n")

        val buildLog = newProcess(consumer, Duration.ofMinutes(7)).completedTask("build")
        assertTrue(buildLog.contains("BUILD SUCCESSFUL"), buildLog)
        val jar = consumer.resolve("build/libs/amper-rt-g15-0.1.0+26.3.jar")
        assertTrue(Files.isRegularFile(jar), "G15 isolated distribution missing: $jar")
        ZipFile(jar.toFile()).use { zip ->
            assertNotNull(zip.getEntry("org/example/runtime/ProbeMain.class"))
            assertNotNull(zip.getEntry("org/example/runtime/ProbeClient.class"))
            val entries = zip.entries().asSequence().filter { it.name == "fabric.mod.json" }.toList()
            assertEquals(1, entries.size, "Loader distribution must contain exactly one root metadata entry")
            val bytes = zip.getInputStream(entries.single()).use { it.readAllBytes() }
            val metadata = JsonParser.parseString(String(bytes, StandardCharsets.UTF_8)).asJsonObject
            assertEquals("amper_rt_g15", metadata.get("id").asString)
            assertEquals("26.3", metadata.getAsJsonObject("depends").get("minecraft").asString)
        }
        val proofs = consumer.resolve("runtime-proof")
        val expected = mapOf(
            proofs.resolve("$nonce.main") to "$nonce:loader-main",
            proofs.resolve("$nonce.client") to "$nonce:loader-client",
            proofs.resolve("$nonce.tick") to "$nonce:actual-client-tick",
        )
        newProcess(consumer, Duration.ofMinutes(3)).clientUntilMarkers(expected)
        for ((path, value) in expected) {
            assertEquals(value, Files.readString(path), "Missing authenticated-by-nonce real Loader proof: $path")
        }
        assertNoConsumerBuildScript(consumer)
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_RUNTIME_JIJ", matches = "true")
    fun `G10 published Loader discovers external mod and invokes library from its nested JAR`() {
        // The host has NO Gradle dependency on commons-collections4. A negative
        // host run without the external mod must establish classpath absence
        // before the positive Loader run with only the production JAR installed.
        val baseline = newConsumer("g10-host-negative", "runtime/g10-host.yaml")
        val positive = newConsumer("g10-host-positive", "runtime/g10-host.yaml")
        val producer = newConsumer("g10-producer", "runtime/g10-producer.yaml")
        val baselineNonce = UUID.randomUUID().toString()
        val positiveNonce = UUID.randomUUID().toString()

        // The producer's real ModInitializer, not the host's dev classpath,
        // calls the embedded library. Its marker path belongs to positive.
        val embeddedProof = positive.resolve("runtime-proof/$positiveNonce.embedded")
        val expectedHome = literal(positive.resolve("sandbox-home").toAbsolutePath().normalize().toString())
        write(producer, "src/main/java/org/example/runtime/EmbeddedEntrypoint.java", """
            package org.example.runtime;
            import net.fabricmc.api.ModInitializer;
            import net.fabricmc.loader.api.FabricLoader;
            import org.apache.commons.collections4.bag.HashBag;
            import java.nio.file.*;
            import java.nio.charset.StandardCharsets;
            public final class EmbeddedEntrypoint implements ModInitializer {
                @Override public void onInitialize() {
                    if (!FabricLoader.getInstance().isModLoaded("amper_rt_g10_embedded")) {
                        throw new IllegalStateException("External JAR not discovered by Loader");
                    }
                    HashBag<String> bag = new HashBag<>();
                    bag.add("loaded");
                    if (bag.getCount("loaded") != 1) throw new IllegalStateException("Nested HashBag failure");
                    if (!Path.of(System.getProperty("user.home")).toAbsolutePath().normalize()
                            .equals(Path.of("$expectedHome"))) {
                        throw new IllegalStateException("External mod escaped the isolated home");
                    }
                    try {
                        Path marker = Path.of("${literal(embeddedProof.toAbsolutePath().normalize().toString())}");
                        Files.createDirectories(marker.getParent());
                        Files.writeString(marker, "$positiveNonce:embedded-library-invoked",
                            StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                    } catch (java.io.IOException error) { throw new IllegalStateException(error); }
                }
            }
        """.trimIndent() + "\n")
        val producerBuild = newProcess(producer, Duration.ofMinutes(7)).completedTask("build")
        assertTrue(producerBuild.contains("BUILD SUCCESSFUL"), producerBuild)
        val distribution = producer.resolve("build/libs/amper-rt-g10-embedded-0.1.0+26.3.jar")
        assertTrue(Files.isRegularFile(distribution), "Missing actual external distribution")
        val nestedId = nestedLoaderId(distribution)
        assertTrue(nestedId.isNotBlank(), "Nested JIJ must carry an actual Fabric Loader mod ID")

        val baselineExpected = installG10HostSources(baseline, baselineNonce, nestedId, expectNested = false)
        newProcess(baseline, Duration.ofMinutes(7)).completedTask("build")
        newProcess(baseline, Duration.ofMinutes(3)).clientUntilMarkers(baselineExpected)
        assertEquals(
            "$baselineNonce:loader-saw-absent-external-and-nested-library",
            Files.readString(baseline.resolve("runtime-proof/$baselineNonce.host")),
        )

        val positiveExpected = installG10HostSources(positive, positiveNonce, nestedId, expectNested = true)
        newProcess(positive, Duration.ofMinutes(7)).completedTask("build")
        val modsFolder = Files.createDirectories(positive.resolve("run/mods"))
        Files.copy(distribution, modsFolder.resolve(distribution.fileName))
        newProcess(positive, Duration.ofMinutes(3)).clientUntilMarkers(
            positiveExpected + (embeddedProof to "$positiveNonce:embedded-library-invoked"),
        )
        assertEquals("$positiveNonce:embedded-library-invoked", Files.readString(embeddedProof))
        for (consumer in listOf(producer, baseline, positive)) assertNoConsumerBuildScript(consumer)
    }

    private fun nestedLoaderId(distribution: Path): String = ZipFile(distribution.toFile()).use { zip ->
        val metadata = zip.getEntry("fabric.mod.json")
            ?: error("Production JAR has no root fabric.mod.json")
        val json = JsonParser.parseString(
            String(zip.getInputStream(metadata).use { it.readAllBytes() }, StandardCharsets.UTF_8),
        ).asJsonObject
        val names = json.getAsJsonArray("jars")
            ?: error("Production JAR is missing Loader jars[]")
        assertEquals(1, names.size(), "One include must produce one nested library JAR")
        val nestedPath = names[0].asJsonObject.get("file").asString
        assertTrue(nestedPath.startsWith("META-INF/jars/"), nestedPath)
        val entry = zip.getEntry(nestedPath) ?: error("Loader nested JAR path does not exist: $nestedPath")
        val nestedBytes = zip.getInputStream(entry).use { it.readAllBytes() }
        ZipInputStream(nestedBytes.inputStream()).use { nested ->
            while (true) {
                val inside = nested.nextEntry ?: break
                if (inside.name == "fabric.mod.json") {
                    return@use JsonParser.parseString(
                        String(nested.readAllBytes(), StandardCharsets.UTF_8),
                    ).asJsonObject.get("id").asString
                }
            }
            error("Loom nested library JAR is not recognized as a Loader mod")
        }
    }

    private fun installG10HostSources(
        consumer: Path, nonce: String, nestedId: String, expectNested: Boolean,
    ): Map<Path, String> {
        installProofWriter(consumer, nonce)
        val status = if (expectNested) {
            "loader-saw-external-and-nested-library"
        } else {
            "loader-saw-absent-external-and-nested-library"
        }
        write(consumer, "src/main/java/org/example/runtime/HostMain.java", """
            package org.example.runtime;
            import net.fabricmc.api.ModInitializer;
            import net.fabricmc.loader.api.FabricLoader;
            public final class HostMain implements ModInitializer {
                @Override public void onInitialize() {
                    boolean producer = FabricLoader.getInstance().isModLoaded("amper_rt_g10_embedded");
                    boolean nested = FabricLoader.getInstance().isModLoaded("$nestedId");
                    boolean classFound;
                    try {
                        Class.forName("org.apache.commons.collections4.bag.HashBag", false,
                            HostMain.class.getClassLoader());
                        classFound = true;
                    } catch (ClassNotFoundException missing) {
                        classFound = false;
                    }
                    if (producer != $expectNested || nested != $expectNested ||
                            classFound != $expectNested) {
                        throw new IllegalStateException("Unexpected Loader JIJ visibility: " +
                            producer + "/" + nested + "/" + classFound);
                    }
                    ProofWriter.mark("host", "$nonce:$status");
                }
            }
        """.trimIndent() + "\n")
        write(consumer, "src/client/java/org/example/runtime/HostClient.java", """
            package org.example.runtime;
            import net.fabricmc.api.ClientModInitializer;
            import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
            import java.util.concurrent.atomic.AtomicBoolean;
            public final class HostClient implements ClientModInitializer {
                private static final AtomicBoolean FIRST = new AtomicBoolean();
                @Override public void onInitializeClient() {
                    ClientTickEvents.END_CLIENT_TICK.register(client -> {
                        if (FIRST.compareAndSet(false, true)) {
                            ProofWriter.mark("tick", "$nonce:host-client-tick");
                        }
                    });
                }
            }
        """.trimIndent() + "\n")
        return mapOf(
            consumer.resolve("runtime-proof/$nonce.host") to "$nonce:$status",
            consumer.resolve("runtime-proof/$nonce.tick") to "$nonce:host-client-tick",
        )
    }

    private fun newProcess(consumer: Path, timeout: Duration): IsolatedFabricClientProcess {
        val executable = System.getenv("FABRIC_AMPER_RUNTIME_GRADLE_BIN")
            ?: error("Explicit FABRIC_AMPER_RUNTIME_GRADLE_BIN is required for native process supervision")
        val path = Path.of(executable)
        require(path.isAbsolute) { "FABRIC_AMPER_RUNTIME_GRADLE_BIN must be absolute" }
        return IsolatedFabricClientProcess(consumer, path, timeout)
    }

    private fun newConsumer(prefix: String, resource: String): Path {
        val parent = Files.createDirectories(buildRoot.resolve("fabric-amper-runtime-consumers"))
        val root = Files.createTempDirectory(parent, "$prefix-").toRealPath()
        require(root.startsWith(buildRoot)) { "Runtime consumer escaped producer build: $root" }
        val published = buildRoot.resolve("g29-probe/maven-repo").toRealPath()
        require(published.startsWith(buildRoot)) { "The published plugin repo must stay inside build/" }
        val marker = published.resolve(
            "dev/fabric-amper/settings/dev.fabric-amper.settings.gradle.plugin/0.2.0/" +
                "dev.fabric-amper.settings.gradle.plugin-0.2.0.pom",
        )
        val moduleJar = published.resolve("dev/fabricamper/fabric-amper/0.2.0/fabric-amper-0.2.0.jar")
        require(Files.isRegularFile(marker) && Files.isRegularFile(moduleJar)) {
            "Prime must first publish the existing G29 local marker, POM and plugin JAR; no substitute or remote publish"
        }
        val uri = published.toUri().toASCIIString()
        write(root, "settings.gradle.kts", """
            pluginManagement {
                repositories {
                    maven { url = uri("$uri") }
                    maven("https://maven.fabricmc.net/")
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            plugins { id("dev.fabric-amper.settings") version "0.2.0" }
        """.trimIndent() + "\n")
        val yaml = TestKitFixture(root).resource(resource)
        write(root, "mod.yaml", yaml)
        assertNoConsumerBuildScript(root)
        return root
    }

    private fun installProofWriter(consumer: Path, nonce: String) {
        val home = consumer.resolve("sandbox-home").toAbsolutePath().normalize()
        val location = consumer.resolve("runtime-proof").toAbsolutePath().normalize()
        val javaHome = literal(home.toString())
        val javaOutput = literal(location.toString())
        write(consumer, "src/main/java/org/example/runtime/ProofWriter.java", """
            package org.example.runtime;
            import java.nio.charset.StandardCharsets;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.nio.file.StandardOpenOption;
            public final class ProofWriter {
                private ProofWriter() {}
                public static void mark(String name, String evidence) {
                    Path userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
                    if (!userHome.equals(Path.of("$javaHome"))) {
                        throw new IllegalStateException("Runtime must not use the user's Minecraft home: " + userHome);
                    }
                    try {
                        Path folder = Path.of("$javaOutput");
                        Files.createDirectories(folder);
                        Files.writeString(folder.resolve("$nonce." + name), evidence, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    } catch (java.io.IOException e) { throw new IllegalStateException(e); }
                }
            }
        """.trimIndent() + "\n")
    }

    private fun assertNoConsumerBuildScript(root: Path) {
        assertFalse(Files.exists(root.resolve("build.gradle")))
        assertFalse(Files.exists(root.resolve("build.gradle.kts")))
        val settings = Files.readString(root.resolve("settings.gradle.kts"))
        assertTrue(settings.contains("dev.fabric-amper.settings"))
        assertFalse(settings.contains("net.fabricmc.fabric-loom"))
    }

    private fun write(root: Path, path: String, text: String) {
        val target = root.resolve(path).normalize()
        require(target.startsWith(root)) { "Fixture attempted write outside isolated project: $target" }
        Files.createDirectories(target.parent)
        Files.writeString(target, text, StandardCharsets.UTF_8)
    }

    private fun literal(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
