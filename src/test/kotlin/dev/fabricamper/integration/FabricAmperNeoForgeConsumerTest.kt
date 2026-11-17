package dev.fabricamper.integration

import com.google.gson.JsonParser
import org.gradle.testkit.runner.BuildResult
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
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * NF2/NF5/NF12 and NF17-NF22: real consumer acceptance for the versioned
 * NeoForge 26.3 + ModDevGradle and multi-loader Settings-only contracts.
 *
 * Tests may resolve official game/loader artifacts from public repositories.
 * They never start a game, accept an EULA, run remote publishing, or write a
 * consumer build.gradle(.kts). The fixture creates source Java only.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
class FabricAmperNeoForgeConsumerTest {
    @TempDir lateinit var suiteRoot: Path

    @Test
    fun `single NeoForge 26_3 settings-only MDG builds real annotated class and generated TOML`() {
        val f = TestKitFixture(suiteRoot)
        val yaml = """
            schema: "1.1"
            product: neoforge/mod
            mod:
              id: neoforgeprobe
              version: "0.1.0+26.3"
              group: org.example
              archivesName: neoforgeprobe
              name: NeoForge Probe
              license: MIT
            minecraft:
              version: "26.3"
            settings:
              java:
                release: 25
                sourcesJar: true
              kotlin:
                enabled: false
            neoforge:
              version: "26.3.0.101"
              modLoader: javafml
            repositories:
              - neoforge
              - mavenCentral
            dependencies: []
        """.trimIndent() + "\n"
        val consumer = f.project("neoforge-mdg-263", yaml, alias = "mod.yml")
        f.write(consumer, "src/main/java/org/example/nf/NeoForgeProbe.java", neoForgeEntry("neoforgeprobe", "NeoForgeProbe"))
        f.write(consumer, "src/main/resources/assets/neoforgeprobe/lang/en_us.json", "{\"item.neoforgeprobe.example\":\"Example\"}\n")
        assertSettingsOnly(f, consumer, "mod.yml", yaml, multi = false)

        // MDG, like Loom, can populate its own cache on the first configuration.
        // Warm it before checking the separate Gradle configuration cache.
        val help = f.run(consumer, "help")
        assertEquals(TaskOutcome.SUCCESS, help.task(":help")?.outcome, help.output)
        assertNoPublishing(help)
        val tasks = f.run(consumer, "tasks", "--all")
        assertEquals(TaskOutcome.SUCCESS, tasks.task(":tasks")?.outcome, tasks.output)
        assertTrue(taskListed(tasks, "runClient"), "MDG runClient task missing:\n${tasks.output}")
        assertTrue(taskListed(tasks, "runServer"), "MDG runServer task missing:\n${tasks.output}")
        assertFalse(taskListed(tasks, "generateFabricModJson"),
            "A NeoForge-only consumer must not configure Fabric's metadata generator")

        val first = f.run(consumer, "build", "--configuration-cache")
        assertEquals(TaskOutcome.SUCCESS, first.task(":build")?.outcome, first.output)
        assertNotNull(first.task(":compileJava"), "NeoForge @Mod source must compile through MDG")
        assertNoPublishing(first)
        val jar = productionJars(consumer).single()
        assertEquals("neoforgeprobe-0.1.0+26.3.jar", jar.fileName.toString())
        val toml = assertNeoForgeJar(jar, "neoforgeprobe", "0.1.0+26.3")
        assertTrue(toml.contains("javafml"), "Expected native NeoForge language loader:\n$toml")
        assertTrue(toml.contains("loaderVersion"), "NeoForge language loader range must be present:\n$toml")
        assertTrue(toml.contains("MIT"), "NeoForge requires a license:\n$toml")
        ZipFile(jar.toFile()).use { zip ->
            assertNotNull(zip.getEntry("org/example/nf/NeoForgeProbe.class"))
            assertNotNull(zip.getEntry("assets/neoforgeprobe/lang/en_us.json"))
        }
        val firstHash = sha256(jar)
        val generated = Files.walk(consumer.resolve("build")).use { paths ->
            paths.filter { path ->
                Files.isRegularFile(path) && path.fileName.toString() == "neoforge.mods.toml" &&
                    path.toString().replace('\\', '/').contains("/generated/")
            }.toList()
        }
        assertTrue(generated.isNotEmpty(), "NeoForge TOML needs a managed generated resource: $generated")
        ZipFile(jar.toFile()).use { zip ->
            val packaged = zip.getInputStream(zip.getEntry("META-INF/neoforge.mods.toml"))
                .use { it.readAllBytes() }
            assertTrue(generated.any { Files.readAllBytes(it).contentEquals(packaged) },
                "Packaged NeoForge metadata must match a generated TOML resource: $generated")
        }

        val second = f.run(consumer, "build", "--configuration-cache")
        assertEquals(TaskOutcome.SUCCESS, second.task(":build")?.outcome, second.output)
        assertTrue(second.output.contains("Reusing configuration cache"), second.output)
        assertNoPublishing(second)
        assertArrayEquals(firstHash, sha256(jar), "Same settings-only input must keep final JAR bytes stable")
        assertSettingsOnly(f, consumer, "mod.yml", yaml, multi = false)
    }

    @Test
    fun `multi-product Fabric and NeoForge source roots produce two loader isolated jars`() {
        val f = TestKitFixture(suiteRoot)
        val yaml = multiYaml("fabric/mod", "neoforge/mod")
        val consumer = f.project("neoforge-multi-mod", yaml)
        writeMultiSources(f, consumer)
        assertSettingsOnly(f, consumer, "mod.yaml", yaml, multi = true)

        val help = f.run(consumer, "help")
        assertEquals(TaskOutcome.SUCCESS, help.task(":help")?.outcome, help.output)
        assertNoPublishing(help)
        val projects = f.run(consumer, "projects")
        for (path in listOf(":common", ":fabric", ":neoforge")) {
            assertTrue(projects.output.contains(path), "Missing configured $path project:\n${projects.output}")
        }
        val fabricTasks = f.run(consumer, ":fabric:tasks", "--all")
        val neoForgeTasks = f.run(consumer, ":neoforge:tasks", "--all")
        assertTrue(taskListed(fabricTasks, "runClient"), "Fabric runClient task missing:\n${fabricTasks.output}")
        assertTrue(taskListed(neoForgeTasks, "runClient"), "NeoForge runClient task missing:\n${neoForgeTasks.output}")
        assertFalse(taskListed(neoForgeTasks, "generateFabricModJson"),
            "NeoForge's Gradle project must not inherit Fabric's metadata generator")

        val first = f.run(consumer, "build", "--configuration-cache")
        assertEquals(TaskOutcome.SUCCESS, first.task(":build")?.outcome, first.output)
        assertNotNull(first.task(":fabric:compileJava"), "Fabric source must compile independently")
        assertNotNull(first.task(":neoforge:compileJava"), "NeoForge source must compile independently")
        assertNoPublishing(first)

        val (fabricJar, neoForgeJar) = assertMultiProduction(consumer)
        assertTrue(fabricJar.fileName.toString().contains("-fabric-"), "Fabric artifact needs platform suffix")
        assertTrue(neoForgeJar.fileName.toString().contains("-neoforge-"), "NeoForge artifact needs platform suffix")
        val fabricHash = sha256(fabricJar)
        val neoHash = sha256(neoForgeJar)

        val second = f.run(consumer, "build", "--configuration-cache")
        assertEquals(TaskOutcome.SUCCESS, second.task(":build")?.outcome, second.output)
        // On a clean dual-loader checkout, the first build materializes the
        // outgoing :common JAR. Gradle may have snapshotted that initially
        // absent project-dependency artifact during configuration and thus
        // invalidate the first cached graph exactly once on the next build.
        // Do not mistake this for a failed build or accept repeated misses.
        if (!second.output.contains("Reusing configuration cache")) {
            assertTrue(second.output.contains("common\\build\\libs\\") &&
                second.output.contains("has been created"),
                "Only the initial :common artifact materialization may invalidate configuration cache:\n${second.output}")
        }
        val steadyState = f.run(consumer, "build", "--configuration-cache")
        assertEquals(TaskOutcome.SUCCESS, steadyState.task(":build")?.outcome, steadyState.output)
        assertTrue(steadyState.output.contains("Reusing configuration cache"),
            "The identical third build must reuse the warm multi-loader configuration cache:\n${steadyState.output}")
        assertArrayEquals(fabricHash, sha256(fabricJar), "Fabric artifact changed on unchanged input")
        assertArrayEquals(neoHash, sha256(neoForgeJar), "NeoForge artifact changed on unchanged input")
        assertSettingsOnly(f, consumer, "mod.yaml", yaml, multi = true)

        // Product order is not an artifact identity. Both selected projects
        // and their production archives must stay stable after YAML reordering.
        val reversed = yaml.replace(
            "product: [fabric/mod, neoforge/mod]", "product: [neoforge/mod, fabric/mod]",
        )
        assertFalse(reversed == yaml, "Fixture must actually reverse the target ordering")
        f.write(consumer, "mod.yaml", reversed)
        val reordered = f.run(consumer, "build", "--configuration-cache")
        assertEquals(TaskOutcome.SUCCESS, reordered.task(":build")?.outcome, reordered.output)
        assertArrayEquals(fabricHash, sha256(fabricJar), "Product order changed Fabric JAR bytes")
        assertArrayEquals(neoHash, sha256(neoForgeJar), "Product order changed NeoForge JAR bytes")
        assertMultiProduction(consumer)
        assertSettingsOnly(f, consumer, "mod.yaml", reversed, multi = true)
    }

    @Test
    fun `two loaders without Architectury or common sources build independent entrypoints`() {
        val f = TestKitFixture(suiteRoot)
        // Omitting architectury must not trigger shared Minecraft setup or
        // automatically attempt to reuse Fabric's initializer as NeoForge's.
        val yaml = multiYaml("fabric/mod", "neoforge/mod")
            .replace("architectury:\n  enable: false\n", "")
        assertFalse(yaml.contains("architectury:"))
        val consumer = f.project("neoforge-no-arch-independent", yaml)
        writeSeparateSources(f, consumer)
        assertSettingsOnly(f, consumer, "mod.yaml", yaml, multi = true)

        val build = f.run(consumer, "build")
        assertEquals(TaskOutcome.SUCCESS, build.task(":build")?.outcome, build.output)
        assertNotNull(build.task(":fabric:compileJava"))
        assertNotNull(build.task(":neoforge:compileJava"))
        assertNoPublishing(build)
        assertMultiProduction(consumer, expectCommon = false)
        assertSettingsOnly(f, consumer, "mod.yaml", yaml, multi = true)
    }

    @Test
    fun `dual Architectury uses common API source plus loader-specific runtime metadata`() {
        val f = TestKitFixture(suiteRoot)
        val yaml = multiYaml("fabric/mod", "neoforge/mod")
            .replace("  enable: false", "  enable: true\n  version: \"22.0.5\"")
        val consumer = f.project("neoforge-dual-architectury", yaml)
        writeMultiSources(f, consumer, useArchitectury = true)
        assertSettingsOnly(f, consumer, "mod.yaml", yaml, multi = true)

        val build = f.run(consumer, "build")
        assertEquals(TaskOutcome.SUCCESS, build.task(":build")?.outcome, build.output)
        assertNotNull(build.task(":common:compileJava"), "Architectury common code must compile")
        assertNotNull(build.task(":fabric:compileJava"))
        assertNotNull(build.task(":neoforge:compileJava"))
        assertNoPublishing(build)
        val (fabricJar, neoForgeJar) = assertMultiProduction(consumer)
        ZipFile(fabricJar.toFile()).use { zip ->
            val json = zip.getInputStream(zip.getEntry("fabric.mod.json"))
                .use { JsonParser.parseString(String(it.readAllBytes(), StandardCharsets.UTF_8)).asJsonObject }
            assertEquals(">=22.0.5", json.getAsJsonObject("depends").get("architectury").asString,
                "Fabric should use a Fabric Loader version predicate for Architectury")
        }
        val toml = assertNeoForgeJar(neoForgeJar, "multiloaderprobe", "0.2.0+26.3")
        assertTrue(toml.contains("architectury"), "NeoForge metadata must require its own Architectury mod:\n$toml")
        assertTrue(toml.contains("[22.0.5,)"), "NeoForge must use Maven version ranges, not Fabric predicates:\n$toml")
        assertSettingsOnly(f, consumer, "mod.yaml", yaml, multi = true)
    }

    @Test
    fun `hybrid Fabric API and NeoForge mod publish selection stays loader scoped`() {
        assertApiSelection("fabric/api", "neoforge/mod")
    }

    @Test
    fun `hybrid Fabric mod and NeoForge API publish selection stays loader scoped`() {
        assertApiSelection("fabric/mod", "neoforge/api")
    }

    @Test
    fun `dual API targets expose two independent local Maven publications`() {
        assertApiSelection("fabric/api", "neoforge/api")
    }

    @Test
    fun `dual without Architectury embeds Fabric only Maven Central JIJ without leaking into NeoForge`() {
        val f = TestKitFixture(suiteRoot)
        // Loader-scoped Gradle dependencies must not become shared dependencies.
        // Fabric Loom supports include/JIJ, while NeoForge JarJar is deliberately
        // separate and must not inherit the Fabric include flag or nested bytes.
        val yaml = multiYaml("fabric/mod", "neoforge/mod")
            .replace("architectury:\n  enable: false\n", "")
            .replace(
                "fabric:\n",
                """
                fabric:
                  gradleDependencies:
                    - maven: "org.apache.commons:commons-lang3:3.14.0"
                      kind: library
                      scope: all
                      target: main
                      include: true
                """.trimIndent() + "\n",
            )
        assertFalse(yaml.contains("architectury:"), "No-Architectury fixture must stay loader-independent")
        assertTrue(yaml.contains("      include: true"), "Fixture must exercise Fabric-scoped include")
        val consumer = f.project("neoforge-fabric-only-jij", yaml)
        writeSeparateSources(f, consumer)
        f.write(consumer, "fabric/src/main/java/org/example/fabric/CommonsOnlyOnFabric.java", """
            package org.example.fabric;
            import org.apache.commons.lang3.StringUtils;
            public final class CommonsOnlyOnFabric {
                private CommonsOnlyOnFabric() {}
                public static String capitalize(String text) { return StringUtils.capitalize(text); }
            }
        """.trimIndent() + "\n")
        assertSettingsOnly(f, consumer, "mod.yaml", yaml, multi = true)

        val build = f.run(consumer, "build")
        assertEquals(TaskOutcome.SUCCESS, build.task(":build")?.outcome, build.output)
        assertNotNull(build.task(":fabric:compileJava"), "Maven Central library must be on Fabric's compile classpath")
        assertNotNull(build.task(":neoforge:compileJava"), "NeoForge must compile independently")
        assertNoPublishing(build)
        val (fabricJar, neoForgeJar) = assertMultiProduction(consumer, expectCommon = false)

        ZipFile(fabricJar.toFile()).use { zip ->
            assertNotNull(zip.getEntry("org/example/fabric/CommonsOnlyOnFabric.class"),
                "Fabric-specific Java class must compile against commons-lang3")
            val metadata = zip.getInputStream(requireNotNull(zip.getEntry("fabric.mod.json")))
                .use { JsonParser.parseString(String(it.readAllBytes(), StandardCharsets.UTF_8)).asJsonObject }
            val declared = metadata.getAsJsonArray("jars")
            assertNotNull(declared, "Fabric Loader metadata must declare its included Maven JIJ")
            val nestedPaths = declared!!.map { it.asJsonObject.get("file").asString }
            assertEquals(1, nestedPaths.size, "Exactly one scoped Maven JIJ must be declared: $nestedPaths")
            val nestedPath = nestedPaths.single()
            assertTrue(nestedPath.startsWith("META-INF/jars/"), "Unexpected Fabric JIJ path: $nestedPath")
            assertEquals(1, zip.entries().asSequence().count { it.name == nestedPath },
                "Declared Fabric JIJ must exist exactly once in final JAR")
            val nestedBytes = zip.getInputStream(requireNotNull(zip.getEntry(nestedPath)))
                .use { it.readAllBytes() }
            val nestedEntries = mutableSetOf<String>()
            ZipInputStream(nestedBytes.inputStream()).use { nested ->
                val buffer = ByteArray(8192)
                while (true) {
                    val entry = nested.nextEntry ?: break
                    nestedEntries += entry.name
                    while (nested.read(buffer) != -1) { /* Consume entry and check ZIP CRC. */ }
                    nested.closeEntry()
                }
            }
            assertTrue("org/apache/commons/lang3/StringUtils.class" in nestedEntries,
                "Actual commons-lang3 3.14.0 classes must exist in Fabric nested JAR: $nestedEntries")
        }

        assertNeoForgeJar(neoForgeJar, "multiloaderprobe", "0.2.0+26.3")
        ZipFile(neoForgeJar.toFile()).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertTrue(names.none { it.startsWith("META-INF/jars/") || it.contains("commons-lang3") },
                "Fabric-scoped Maven JIJ must not leak into the NeoForge artifact: $names")
            assertFalse("org/example/fabric/CommonsOnlyOnFabric.class" in names,
                "Fabric-specific commons-lang3 consumer class must not leak into NeoForge")
            assertFalse("org/example/fabric/FabricProbe.class" in names, "Fabric entrypoint must not leak into NeoForge")
            assertFalse("assets/multiloaderprobe/fabric.marker" in names,
                "Fabric-only resources must not leak into NeoForge")
            assertFalse("fabric.mod.json" in names, "Fabric metadata must not leak into NeoForge")
            assertFalse("org/apache/commons/lang3/StringUtils.class" in names,
                "Fabric JIJ must not be expanded into NeoForge classes")
        }
        assertSettingsOnly(f, consumer, "mod.yaml", yaml, multi = true)
    }

    private fun assertApiSelection(fabricProduct: String, neoForgeProduct: String) {
        val f = TestKitFixture(suiteRoot)
        val yaml = multiYaml(fabricProduct, neoForgeProduct)
        val consumer = f.project(
            "neoforge-multi-${fabricProduct.substringAfter('/')}-${neoForgeProduct.substringAfter('/')}", yaml,
        )
        writeMultiSources(f, consumer)
        assertSettingsOnly(f, consumer, "mod.yaml", yaml, multi = true)

        val help = f.run(consumer, "help")
        assertEquals(TaskOutcome.SUCCESS, help.task(":help")?.outcome, help.output)
        val fabricTasks = f.run(consumer, ":fabric:tasks", "--all")
        val neoForgeTasks = f.run(consumer, ":neoforge:tasks", "--all")
        val fabricHasApi = hasLocalMavenPublicationTask(fabricTasks)
        val neoForgeHasApi = hasLocalMavenPublicationTask(neoForgeTasks)
        assertEquals(fabricProduct.endsWith("/api"), fabricHasApi,
            "Fabric publication must match its selected product $fabricProduct:\n${fabricTasks.output}")
        assertEquals(neoForgeProduct.endsWith("/api"), neoForgeHasApi,
            "NeoForge publication must match its selected product $neoForgeProduct:\n${neoForgeTasks.output}")

        // No publishing task is invoked; API selection must not auto-publish.
        val build = f.run(consumer, "build")
        assertEquals(TaskOutcome.SUCCESS, build.task(":build")?.outcome, build.output)
        assertNoPublishing(build)
        assertMultiProduction(consumer)
        assertFalse(Files.walk(consumer).use { files ->
            files.anyMatch { Files.isDirectory(it) && it.fileName.toString() == "maven-repository" }
        }, "A plain build must not write any local Maven publication")
        assertSettingsOnly(f, consumer, "mod.yaml", yaml, multi = true)
    }

    private fun multiYaml(fabric: String, neoForge: String): String = """
        schema: "1.1"
        product: [$fabric, $neoForge]
        mod:
          id: multiloaderprobe
          version: "0.2.0+26.3"
          group: org.example
          archivesName: multiloaderprobe
          name: Multi Loader Probe
          license: MIT
        minecraft:
          version: "26.3"
        settings:
          java:
            release: 25
            sourcesJar: true
          kotlin:
            enabled: false
        architectury:
          enable: false
        fabric:
          loader: "0.20.1"
          api: "0.165.2+26.3"
          mappings: mojang
          environment: "*"
          entrypoints:
            main: ["org.example.fabric.FabricProbe"]
        neoforge:
          version: "26.3.0.101"
          modLoader: javafml
        repositories:
          - mavenCentral
          - fabric
          - neoforge
        dependencies: []
    """.trimIndent() + "\n"

    private fun writeMultiSources(f: TestKitFixture, project: Path, useArchitectury: Boolean = false) {
        val commonApiCall = if (useArchitectury) "dev.architectury.platform.Platform.getMinecraftVersion(); " else ""
        f.write(project, "common/src/main/java/org/example/shared/CommonMarker.java", """
            package org.example.shared;
            public final class CommonMarker {
                private CommonMarker() {}
                public static int generation() { $commonApiCall return 263; }
            }
        """.trimIndent() + "\n")
        f.write(project, "fabric/src/main/java/org/example/fabric/FabricProbe.java", """
            package org.example.fabric;
            import net.fabricmc.api.ModInitializer;
            import org.example.shared.CommonMarker;
            public final class FabricProbe implements ModInitializer {
                @Override public void onInitialize() {
                    if (CommonMarker.generation() != 263) throw new IllegalStateException("common missing");
                }
            }
        """.trimIndent() + "\n")
        f.write(project, "neoforge/src/main/java/org/example/neo/NeoForgeProbe.java", """
            package org.example.neo;
            import net.neoforged.bus.api.IEventBus;
            import net.neoforged.fml.ModContainer;
            import net.neoforged.fml.common.Mod;
            import org.example.shared.CommonMarker;
            @Mod("multiloaderprobe")
            public final class NeoForgeProbe {
                public NeoForgeProbe(IEventBus modBus, ModContainer container) {
                    if (CommonMarker.generation() != 263) throw new IllegalStateException("common missing");
                }
            }
        """.trimIndent() + "\n")
        f.write(project, "fabric/src/main/resources/assets/multiloaderprobe/fabric.marker", "fabric\n")
        f.write(project, "neoforge/src/main/resources/assets/multiloaderprobe/neoforge.marker", "neoforge\n")
    }

    private fun writeSeparateSources(f: TestKitFixture, project: Path) {
        f.write(project, "fabric/src/main/java/org/example/fabric/FabricProbe.java", """
            package org.example.fabric;
            import net.fabricmc.api.ModInitializer;
            public final class FabricProbe implements ModInitializer {
                @Override public void onInitialize() {}
            }
        """.trimIndent() + "\n")
        f.write(project, "neoforge/src/main/java/org/example/neo/NeoForgeProbe.java", """
            package org.example.neo;
            import net.neoforged.bus.api.IEventBus;
            import net.neoforged.fml.ModContainer;
            import net.neoforged.fml.common.Mod;
            @Mod("multiloaderprobe")
            public final class NeoForgeProbe {
                public NeoForgeProbe(IEventBus modBus, ModContainer container) {}
            }
        """.trimIndent() + "\n")
        f.write(project, "fabric/src/main/resources/assets/multiloaderprobe/fabric.marker", "fabric\n")
        f.write(project, "neoforge/src/main/resources/assets/multiloaderprobe/neoforge.marker", "neoforge\n")
    }

    private fun neoForgeEntry(modId: String, type: String): String = """
        package org.example.nf;
        import net.neoforged.bus.api.IEventBus;
        import net.neoforged.fml.ModContainer;
        import net.neoforged.fml.common.Mod;
        @Mod("$modId")
        public final class $type {
            public $type(IEventBus modBus, ModContainer container) {}
        }
    """.trimIndent() + "\n"

    private fun assertMultiProduction(project: Path, expectCommon: Boolean = true): Pair<Path, Path> {
        // :common may legitimately emit an intermediate Java JAR. It is not
        // one of the two platform distributions selected by this YAML.
        val jars = productionJars(project).filter { path ->
            val name = path.fileName.toString()
            "multiloaderprobe-fabric-" in name || "multiloaderprobe-neoforge-" in name
        }
        assertEquals(2, jars.size, "Expected one independently packaged mod JAR per selected loader: $jars")
        val fabric = jars.single { hasEntry(it, "fabric.mod.json") }
        val neoforge = jars.single { hasEntry(it, "META-INF/neoforge.mods.toml") }
        assertFalse(fabric == neoforge, "Both platforms must produce different JARs")

        ZipFile(fabric.toFile()).use { zip ->
            assertEquals(1, zip.entries().asSequence().count { it.name == "fabric.mod.json" })
            assertEquals(0, zip.entries().asSequence().count { it.name == "META-INF/neoforge.mods.toml" })
            assertNotNull(zip.getEntry("org/example/fabric/FabricProbe.class"))
            if (expectCommon) {
                assertNotNull(zip.getEntry("org/example/shared/CommonMarker.class"), "Shared code missing from Fabric JAR")
            } else {
                assertEquals(null, zip.getEntry("org/example/shared/CommonMarker.class"), "No common sources were authored")
            }
            assertNotNull(zip.getEntry("assets/multiloaderprobe/fabric.marker"))
            assertEquals(null, zip.getEntry("org/example/neo/NeoForgeProbe.class"), "NeoForge class leaked into Fabric JAR")
            assertEquals(null, zip.getEntry("assets/multiloaderprobe/neoforge.marker"), "NeoForge resource leaked into Fabric JAR")
            val bytes = zip.getInputStream(zip.getEntry("fabric.mod.json")).use { it.readAllBytes() }
            val json = JsonParser.parseString(String(bytes, StandardCharsets.UTF_8)).asJsonObject
            assertEquals("multiloaderprobe", json.get("id").asString)
            assertEquals("0.2.0+26.3", json.get("version").asString)
        }
        assertNeoForgeJar(neoforge, "multiloaderprobe", "0.2.0+26.3")
        ZipFile(neoforge.toFile()).use { zip ->
            assertNotNull(zip.getEntry("org/example/neo/NeoForgeProbe.class"))
            if (expectCommon) {
                assertNotNull(zip.getEntry("org/example/shared/CommonMarker.class"), "Shared code missing from NeoForge JAR")
            } else {
                assertEquals(null, zip.getEntry("org/example/shared/CommonMarker.class"), "No common sources were authored")
            }
            assertNotNull(zip.getEntry("assets/multiloaderprobe/neoforge.marker"))
            assertEquals(null, zip.getEntry("org/example/fabric/FabricProbe.class"), "Fabric class leaked into NeoForge JAR")
            assertEquals(null, zip.getEntry("assets/multiloaderprobe/fabric.marker"), "Fabric resource leaked into NeoForge JAR")
        }
        return fabric to neoforge
    }

    private fun assertNeoForgeJar(jar: Path, modId: String, version: String): String = ZipFile(jar.toFile()).use { zip ->
        val paths = zip.entries().asSequence().map { it.name }.toList()
        assertEquals(1, paths.count { it == "META-INF/neoforge.mods.toml" }, "Expected exactly one native NeoForge TOML")
        assertEquals(0, paths.count { it == "fabric.mod.json" }, "Fabric Loader JSON must not be packaged in NeoForge")
        assertEquals(0, paths.count { it == "META-INF/mods.toml" }, "Old Forge metadata must not substitute NeoForge TOML")
        val metadata = zip.getInputStream(zip.getEntry("META-INF/neoforge.mods.toml"))
            .use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
        assertTrue(metadata.contains("[[mods]]"), "Missing TOML mod declaration:\n$metadata")
        val escapedId = Regex.escape(modId)
        val escapedVersion = Regex.escape(version)
        assertTrue(Regex("(?m)^\\s*modId\\s*=\\s*\"$escapedId\"\\s*$").containsMatchIn(metadata),
            "Expected NeoForge modId $modId:\n$metadata")
        assertTrue(Regex("(?m)^\\s*version\\s*=\\s*\"$escapedVersion\"\\s*$").containsMatchIn(metadata),
            "Expected selected NeoForge version $version:\n$metadata")
        metadata
    }

    private fun productionJars(project: Path): List<Path> = Files.walk(project).use { paths ->
        paths.filter { path ->
            Files.isRegularFile(path) && path.fileName.toString().endsWith(".jar") &&
                !path.fileName.toString().endsWith("-sources.jar") &&
                !path.fileName.toString().endsWith("-dev.jar") &&
                path.toString().replace('\\', '/').contains("/build/libs/")
        }.toList()
    }

    private fun hasEntry(jar: Path, name: String): Boolean = ZipFile(jar.toFile()).use { it.getEntry(name) != null }

    private fun sha256(jar: Path): ByteArray = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))

    private fun assertSettingsOnly(f: TestKitFixture, project: Path, yamlName: String, yaml: String, multi: Boolean) {
        f.assertNoConsumerBuildScript(project)
        assertEquals(yaml, f.yaml(project, yamlName))
        val folders = if (multi) listOf("common", "fabric", "neoforge") else listOf("")
        for (folder in folders) {
            val root = project.resolve(folder)
            assertFalse(Files.exists(root.resolve("build.gradle")), "Author build.gradle is forbidden: $folder")
            assertFalse(Files.exists(root.resolve("build.gradle.kts")), "Author build.gradle.kts is forbidden: $folder")
            assertFalse(Files.exists(root.resolve("src/main/resources/fabric.mod.json")))
            assertFalse(Files.exists(root.resolve("src/main/resources/META-INF/neoforge.mods.toml")))
        }
    }

    private fun taskListed(result: BuildResult, task: String): Boolean =
        Regex("(?m)^${Regex.escape(task)}\\b").containsMatchIn(result.output)

    private fun hasLocalMavenPublicationTask(result: BuildResult): Boolean =
        Regex("(?m)^publish[A-Za-z0-9]+PublicationToFabricAmperLocalRepository\\b")
            .containsMatchIn(result.output)

    private fun assertNoPublishing(result: BuildResult) {
        assertFalse(result.tasks.any { it.path.substringAfterLast(':').startsWith("publish", ignoreCase = true) },
            "Build, help, and task discovery must not perform local or remote publication:\n${result.output}")
    }
}
