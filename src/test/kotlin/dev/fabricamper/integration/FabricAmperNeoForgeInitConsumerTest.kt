package dev.fabricamper.integration

import com.google.gson.JsonParser
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Real BAT init -> local-plugin TestKit acceptance for generated scaffolds.
 * The authored Java/Kotlin sources/YAML come ONLY from fabric-amper.bat; this test
 * replaces the generated public-release settings.gradle.kts with the local
 * TestKit plugin selector, as in FabricAmperArchitecturyInitConsumerTest.
 *
 * No Gradle scripts or Loader metadata are authored by consumers, no game
 * is started, and no Maven/Modrinth/CurseForge upload task is executed.
 */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
class FabricAmperNeoForgeInitConsumerTest {
    @TempDir lateinit var suiteRoot: Path

    private val fixture get() = TestKitFixture(suiteRoot)

    @Test
    fun `init single NeoForge Kotlin template compiles and packages generated TOML`() {
        val f = fixture
        val id = "nfinitkotlin"
        val consumer = initViaRealBat(f, id, "--mod-neoforge", "--kotlin")
        val yaml = Files.readString(consumer.resolve("mod.yaml"))
        assertTrue(yaml.contains("schema: \"1.1\""), yaml)
        assertTrue(yaml.contains("product: neoforge/mod"), yaml)
        assertTrue(yaml.contains("enabled: true"), yaml)
        assertTrue(yaml.contains("version: \"26.3.0.101\""), yaml)
        assertFalse(yaml.contains("fabric:"), "NeoForge-only YAML must not configure Fabric")
        assertFalse(Files.exists(consumer.resolve("fabric")))
        assertFalse(Files.exists(consumer.resolve("common")))

        val neoSource = consumer.resolve("src/main/kotlin/org/example/ExampleNeoForge.kt")
        assertTrue(Files.isRegularFile(neoSource), "The BAT must generate a Kotlin NeoForge entrypoint")
        val kotlin = Files.readString(neoSource)
        assertTrue(kotlin.contains("import net.neoforged.bus.api.IEventBus"), kotlin)
        assertTrue(kotlin.contains("import net.neoforged.fml.ModContainer"), kotlin)
        assertTrue(kotlin.contains("import net.neoforged.fml.common.Mod"), kotlin)
        assertTrue(kotlin.contains("@Mod(\"$id\")"), kotlin)
        assertTrue(kotlin.contains("class ExampleNeoForge(modEventBus: IEventBus, modContainer: ModContainer)"), kotlin)
        assertFalse(kotlin.contains("net.fabricmc"), "NeoForge Kotlin template must not import Fabric APIs")
        assertNoAuthoredBuildOrMetadata(f, consumer)

        val build = f.run(consumer, "build")
        assertSuccess(build, ":build")
        assertCompiled(build, ":compileKotlin")
        assertNoPublishing(build)
        val jar = finalSingleJar(consumer, id, "neoforge")
        assertNeoForgeJar(jar, id, "1.0.0+26.3", "org/example/ExampleNeoForge.class")
        assertNoAuthoredBuildOrMetadata(f, consumer)
        assertEquals(yaml, Files.readString(consumer.resolve("mod.yaml")), "Gradle must not rewrite the generated YAML")
    }

    @Test
    fun `init single NeoForge Architectury Kotlin compiles BAT starter and checks Loader dependencies`() {
        verifySingleNeoForgeArchitectury("nfarchkotlin", kotlin = true)
    }

    @Test
    fun `init single NeoForge Architectury Java compiles BAT starter and checks Loader dependencies`() {
        verifySingleNeoForgeArchitectury("nfarchjava", kotlin = false)
    }

    private fun verifySingleNeoForgeArchitectury(id: String, kotlin: Boolean) {
        val f = fixture
        val consumer = if (kotlin) {
            initViaRealBat(f, id, "--mod-neoforge", "--architectury", "--kotlin")
        } else {
            initViaRealBat(f, id, "--mod-neoforge", "--architectury")
        }
        val yaml = Files.readString(consumer.resolve("mod.yaml"))
        val normalizedYaml = yaml.replace("\r\n", "\n")
        assertTrue(normalizedYaml.contains("schema: \"1.1\""), yaml)
        assertTrue(normalizedYaml.contains("product: neoforge/mod"), yaml)
        assertTrue(normalizedYaml.contains("architectury:\n  enable: true\n  version: \"22.0.5\""), yaml)
        assertTrue(normalizedYaml.contains("  kotlin:\n    enabled: $kotlin"), yaml)
        assertTrue(normalizedYaml.contains("  version: \"26.3.0.101\""), yaml)
        assertTrue(normalizedYaml.contains("  modLoader: javafml"), yaml)
        assertFalse(normalizedYaml.contains("\nfabric:"), "NeoForge-only Architectury cannot add a Fabric target")
        assertFalse(normalizedYaml.contains("fabricLanguageKotlin"), "Fabric Language Kotlin is not a NeoForge dependency")
        assertFalse(normalizedYaml.contains("splitEnvironmentSourceSets"))
        assertFalse(Files.exists(consumer.resolve("fabric")), "NeoForge-only init must not create a Fabric module")
        assertFalse(Files.exists(consumer.resolve("common")), "Single-loader Architectury uses src/main, not common/")
        assertFalse(Files.exists(consumer.resolve("src/client")), "NeoForge-only init must not create a Loom client source set")

        val lang = if (kotlin) "kotlin" else "java"
        val extension = if (kotlin) "kt" else "java"
        val source = consumer.resolve("src/main/$lang/org/example/ExampleNeoForge.$extension")
        assertTrue(Files.isRegularFile(source), "BAT must generate the native NeoForge $lang starter: $source")
        val generated = Files.readString(source)
        assertTrue(generated.contains("import dev.architectury.platform.Platform"), generated)
        assertTrue(generated.contains("Platform.getMinecraftVersion()"),
            "BAT must generate an executable Architectury example, not only an import:\n$generated")
        assertTrue(generated.contains("import net.neoforged.bus.api.IEventBus"), generated)
        assertTrue(generated.contains("import net.neoforged.fml.ModContainer"), generated)
        assertTrue(generated.contains("import net.neoforged.fml.common.Mod"), generated)
        assertTrue(generated.contains("@Mod(\"$id\")"), generated)
        if (kotlin) {
            assertTrue(generated.contains("class ExampleNeoForge(modEventBus: IEventBus, modContainer: ModContainer)"),
                generated)
            assertFalse(Files.exists(consumer.resolve("src/main/java/org/example/ExampleNeoForge.java")))
        } else {
            assertTrue(generated.contains("public ExampleNeoForge(IEventBus modEventBus, ModContainer modContainer)"),
                generated)
            assertFalse(Files.exists(consumer.resolve("src/main/kotlin/org/example/ExampleNeoForge.kt")))
        }
        assertFalse(generated.contains("net.fabricmc"), "NeoForge initializer must not import Fabric APIs")
        assertNoAuthoredBuildOrMetadata(f, consumer)

        val build = f.run(consumer, "build")
        assertSuccess(build, ":build")
        assertCompiled(build, if (kotlin) ":compileKotlin" else ":compileJava")
        assertNoPublishing(build)
        val jar = finalSingleJar(consumer, id, "neoforge")
        val toml = assertNeoForgeJar(jar, id, "1.0.0+26.3", "org/example/ExampleNeoForge.class")
        assertNeoForgeArchitecturyDependencies(toml, id)
        assertNoAuthoredBuildOrMetadata(f, consumer)
        assertEquals(yaml, Files.readString(consumer.resolve("mod.yaml")),
            "Gradle must not rewrite the BAT-generated YAML")
        assertEquals(generated, Files.readString(source),
            "Gradle must compile the unmodified BAT-generated starter")
    }

    private fun assertNeoForgeArchitecturyDependencies(toml: String, modId: String) {
        val sections = toml.split("[[dependencies.$modId]]").drop(1).map { it.substringBefore("[[") }
        fun hasField(section: String, key: String, value: String): Boolean =
            section.lineSequence().any { it.trim() == "$key=\"$value\"" }
        fun required(id: String, versionRange: String) {
            val matching = sections.filter { hasField(it, "modId", id) }
            assertEquals(1, matching.size, "Expected exactly one NeoForge dependency on $id:\n$toml")
            val section = matching.single()
            assertTrue(hasField(section, "type", "required"), "Expected required $id dependency:\n$section")
            assertTrue(hasField(section, "versionRange", versionRange),
                "Expected native NeoForge Maven range $versionRange for $id:\n$section")
            assertTrue(hasField(section, "side", "BOTH"), "Expected BOTH-sided $id dependency:\n$section")
        }
        required("neoforge", "[26.3.0.101,)")
        required("minecraft", "[26.3]")
        required("architectury", "[22.0.5,)")
        for (fabricOnly in listOf("fabricloader", "fabric-api", "fabric-language-kotlin")) {
            assertFalse(sections.any { hasField(it, "modId", fabricOnly) },
                "Fabric-only dependency $fabricOnly leaked into NeoForge Loader TOML:\n$toml")
        }
    }

    @Test
    fun `init dual independent Kotlin templates retain Fabric split and isolate loaders`() {
        val f = fixture
        val id = "dualindependent"
        val consumer = initViaRealBat(f, id, "--mod-fabric", "--mod-neoforge", "--kotlin", "--splitEnvironmentSourceSets")
        val yaml = Files.readString(consumer.resolve("mod.yaml"))
        assertTrue(yaml.contains("schema: \"1.1\""), yaml)
        assertTrue(yaml.contains("product: [fabric/mod, neoforge/mod]"), yaml)
        assertTrue(yaml.contains("enabled: true"), yaml)
        assertTrue(yaml.contains("splitEnvironmentSourceSets: true"), yaml)
        assertTrue(yaml.contains("org.example.ExampleFabricClient"), yaml)
        assertFalse(yaml.contains("architectury:"), "The BAT must not silently enable Architectury")
        assertFalse(Files.exists(consumer.resolve("common/src/main")),
            "Dual no-Architectury must not generate a shared common source project")

        val fabric = consumer.resolve("fabric/src/main/kotlin/org/example/ExampleFabric.kt")
        val client = consumer.resolve("fabric/src/client/kotlin/org/example/ExampleFabricClient.kt")
        val neo = consumer.resolve("neoforge/src/main/kotlin/org/example/ExampleNeoForge.kt")
        assertTrue(Files.isRegularFile(fabric), "Missing generated Fabric Kotlin initializer")
        assertTrue(Files.isRegularFile(client), "The Fabric split flag must generate a client Kotlin initializer")
        assertTrue(Files.isRegularFile(neo), "Missing generated NeoForge Kotlin @Mod entrypoint")
        assertTrue(Files.readString(fabric).contains("import net.fabricmc.api.ModInitializer"))
        assertTrue(Files.readString(client).contains("import net.fabricmc.api.ClientModInitializer"))
        assertTrue(Files.readString(neo).contains("import net.neoforged.fml.common.Mod"))
        assertTrue(Files.readString(neo).contains("@Mod(\"$id\")"))
        assertFalse(Files.readString(fabric).contains("ExampleCommon"))
        assertFalse(Files.readString(neo).contains("ExampleCommon"))
        assertNoAuthoredBuildOrMetadata(f, consumer)

        val build = f.run(consumer, "build")
        assertSuccess(build, ":build")
        assertCompiled(build, ":fabric:compileKotlin")
        assertCompiled(build, ":fabric:compileClientKotlin")
        assertCompiled(build, ":neoforge:compileKotlin")
        assertNoPublishing(build)
        val (fabricJar, neoForgeJar) = dualJars(consumer, id)
        assertFabricJar(fabricJar, id, "1.0.0+26.3", "org/example/ExampleFabric.class",
            "org/example/ExampleFabricClient.class")
        assertNeoForgeJar(neoForgeJar, id, "1.0.0+26.3", "org/example/ExampleNeoForge.class")
        assertAbsent(fabricJar, "org/example/ExampleNeoForge.class")
        assertAbsent(fabricJar, "org/example/ExampleCommon.class")
        assertAbsent(neoForgeJar, "org/example/ExampleFabric.class")
        assertAbsent(neoForgeJar, "org/example/ExampleFabricClient.class")
        assertAbsent(neoForgeJar, "org/example/ExampleCommon.class")
        assertNoAuthoredBuildOrMetadata(f, consumer)
        assertEquals(yaml, Files.readString(consumer.resolve("mod.yaml")))
    }

    @Test
    fun `init dual Architectury Kotlin ignores split and includes compiled common code in both jars`() {
        val f = fixture
        val id = "dualarchitectury"
        val consumer = initViaRealBat(f, id, "--mod-fabric", "--mod-neoforge", "--architectury", "--kotlin",
            "--splitEnvironmentSourceSets")
        val yaml = Files.readString(consumer.resolve("mod.yaml"))
        assertTrue(yaml.contains("schema: \"1.1\""), yaml)
        assertTrue(yaml.contains("product: [fabric/mod, neoforge/mod]"), yaml)
        assertTrue(yaml.contains("enabled: true"), yaml)
        assertTrue(yaml.replace("\r\n", "\n").contains("architectury:\n  enable: true"), yaml)
        assertTrue(yaml.contains("version: \"22.0.5\""), yaml)
        assertFalse(yaml.contains("splitEnvironmentSourceSets"),
            "Dual-loader Architectury must ignore --splitEnvironmentSourceSets, not enable it")
        assertFalse(yaml.contains("org.example.ExampleFabricClient"),
            "Ignored split flag must not add a client Loader entrypoint")
        assertFalse(Files.exists(consumer.resolve("fabric/src/client")),
            "Ignored split flag must not generate Fabric client sources")

        val common = consumer.resolve("common/src/main/kotlin/org/example/ExampleCommon.kt")
        val fabric = consumer.resolve("fabric/src/main/kotlin/org/example/ExampleFabric.kt")
        val neo = consumer.resolve("neoforge/src/main/kotlin/org/example/ExampleNeoForge.kt")
        assertTrue(Files.isRegularFile(common), "Architectury must create common Kotlin source")
        assertTrue(Files.isRegularFile(fabric), "Architectury must create Fabric Kotlin source")
        assertTrue(Files.isRegularFile(neo), "Architectury must create NeoForge Kotlin source")
        val commonSource = Files.readString(common)
        assertTrue(commonSource.contains("import dev.architectury.platform.Platform"), commonSource)
        assertTrue(commonSource.contains("Platform.getMinecraftVersion()"), commonSource)
        assertTrue(Files.readString(fabric).contains("ExampleCommon.initialize()"),
            "Fabric initializer must call shared Architectury code")
        val neoSource = Files.readString(neo)
        assertTrue(neoSource.contains("ExampleCommon.initialize()"),
            "NeoForge initializer must call shared Architectury code")
        assertTrue(neoSource.contains("import net.neoforged.bus.api.IEventBus"), neoSource)
        assertTrue(neoSource.contains("import net.neoforged.fml.ModContainer"), neoSource)
        assertTrue(neoSource.contains("import net.neoforged.fml.common.Mod"), neoSource)
        assertTrue(neoSource.contains("@Mod(\"$id\")"), neoSource)
        assertNoAuthoredBuildOrMetadata(f, consumer)

        val build = f.run(consumer, "build")
        assertSuccess(build, ":build")
        assertCompiled(build, ":common:compileKotlin")
        assertCompiled(build, ":fabric:compileKotlin")
        assertCompiled(build, ":neoforge:compileKotlin")
        assertNoPublishing(build)
        val (fabricJar, neoForgeJar) = dualJars(consumer, id)
        val fabricMetadata = assertFabricJar(fabricJar, id, "1.0.0+26.3", "org/example/ExampleFabric.class",
            "org/example/ExampleCommon.class")
        assertEquals(">=22.0.5", fabricMetadata.getAsJsonObject("depends").get("architectury").asString)
        val neoforgeToml = assertNeoForgeJar(neoForgeJar, id, "1.0.0+26.3",
            "org/example/ExampleNeoForge.class", "org/example/ExampleCommon.class")
        assertTrue(neoforgeToml.contains("architectury"),
            "NeoForge must declare its Architectury runtime dependency:\n$neoforgeToml")
        assertTrue(neoforgeToml.contains("[22.0.5,)"),
            "NeoForge Architectury dependency must use a native Maven version range:\n$neoforgeToml")
        assertAbsent(fabricJar, "org/example/ExampleNeoForge.class")
        assertAbsent(neoForgeJar, "org/example/ExampleFabric.class")
        assertAbsent(fabricJar, "org/example/ExampleFabricClient.class")
        assertAbsent(neoForgeJar, "org/example/ExampleFabricClient.class")
        assertNoAuthoredBuildOrMetadata(f, consumer)
        assertEquals(yaml, Files.readString(consumer.resolve("mod.yaml")))
    }

    private fun initViaRealBat(f: TestKitFixture, id: String, vararg flags: String): Path {
        val producer = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val bat = producer.resolve("fabric-amper.bat")
        assertTrue(Files.isRegularFile(bat), "Missing producer BAT: $bat")
        val consumer = suiteRoot.resolve("$id-project")
        val cmd = "\"\"$bat\" init $id \"$consumer\" mod ${flags.joinToString(" ")}\""
        val process = ProcessBuilder("cmd.exe", "/d", "/s", "/c", cmd)
            .directory(producer.toFile())
            .redirectErrorStream(true)
            .start()
        val completed = process.waitFor(90, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        assertTrue(completed, "BAT init timed out for $id")
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(0, process.exitValue(), "BAT init failed for $id:\n$output")
        assertTrue(Files.isRegularFile(consumer.resolve("mod.yaml")))
        assertTrue(Files.isRegularFile(consumer.resolve("schemas/mod.schema.json")),
            "BAT must supply its pinned editor schema")
        val originalSettings = Files.readString(consumer.resolve("settings.gradle.kts"))
        assertTrue(originalSettings.contains("dev.fabric-amper.settings"), originalSettings)
        assertTrue(originalSettings.contains("v0.3.1"),
            "The generated current prerelease pin must remain visible before replacing it for local TestKit")
        // The only rewritten consumer file is settings.gradle.kts; preserve
        // the BAT's actual YAML, Kotlin examples, schema and folder layout.
        f.write(consumer, "settings.gradle.kts", "plugins { id(\"dev.fabric-amper.settings\") }\n")
        assertNoAuthoredBuildOrMetadata(f, consumer)
        return consumer
    }

    private fun finalSingleJar(project: Path, id: String, loader: String): Path {
        val jars = productionJars(project).filter { it.fileName.toString() == "$id-1.0.0+26.3.jar" }
        assertEquals(1, jars.size, "Single-loader $loader must retain its unsuffixed archive name: $jars")
        return jars.single()
    }

    private fun dualJars(project: Path, id: String): Pair<Path, Path> {
        val jars = productionJars(project)
        val fabric = jars.filter { it.fileName.toString() == "$id-fabric-1.0.0+26.3.jar" }
        val neoforge = jars.filter { it.fileName.toString() == "$id-neoforge-1.0.0+26.3.jar" }
        assertEquals(1, fabric.size, "Exactly one Fabric distribution with its platform suffix: $jars")
        assertEquals(1, neoforge.size, "Exactly one NeoForge distribution with its platform suffix: $jars")
        return fabric.single() to neoforge.single()
    }

    private fun productionJars(root: Path): List<Path> = Files.walk(root).use { files ->
        files.filter { path ->
            Files.isRegularFile(path) &&
                path.toString().replace('\\', '/').contains("/build/libs/") &&
                path.fileName.toString().endsWith(".jar") &&
                !path.fileName.toString().endsWith("-sources.jar") &&
                !path.fileName.toString().endsWith("-dev.jar")
        }.toList()
    }

    private fun assertFabricJar(jar: Path, id: String, version: String, vararg classes: String) =
        ZipFile(jar.toFile()).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertEquals(1, names.count { it == "fabric.mod.json" }, "Exactly one Fabric Loader JSON: $jar")
            assertEquals(0, names.count { it == "META-INF/neoforge.mods.toml" }, "NeoForge TOML leaked into Fabric JAR")
            for (name in classes) assertNotNull(zip.getEntry(name), "Missing compiled $name from $jar")
            val json = zip.getInputStream(zip.getEntry("fabric.mod.json"))
                .use { JsonParser.parseString(String(it.readAllBytes(), StandardCharsets.UTF_8)).asJsonObject }
            assertEquals(id, json.get("id").asString)
            assertEquals(version, json.get("version").asString)
            json
        }

    private fun assertNeoForgeJar(jar: Path, id: String, version: String, vararg classes: String): String =
        ZipFile(jar.toFile()).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertEquals(1, names.count { it == "META-INF/neoforge.mods.toml" },
                "Exactly one native NeoForge TOML must be packaged: $jar")
            assertEquals(0, names.count { it == "fabric.mod.json" }, "Fabric JSON leaked into NeoForge JAR")
            for (name in classes) assertNotNull(zip.getEntry(name), "Missing compiled $name from $jar")
            val toml = zip.getInputStream(zip.getEntry("META-INF/neoforge.mods.toml"))
                .use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
            assertTrue(toml.contains("[[mods]]"), "NeoForge TOML needs [[mods]]:\n$toml")
            assertTrue(Regex("(?m)^\\s*modId\\s*=\\s*\"${Regex.escape(id)}\"\\s*$").containsMatchIn(toml),
                "NeoForge modId $id was not generated:\n$toml")
            assertTrue(Regex("(?m)^\\s*version\\s*=\\s*\"${Regex.escape(version)}\"\\s*$").containsMatchIn(toml),
                "NeoForge mod version $version was not generated:\n$toml")
            assertTrue(toml.contains("javafml"), "NeoForge language loader must remain javafml:\n$toml")
            assertTrue(toml.contains("MIT"), "NeoForge metadata must carry the authored license:\n$toml")
            toml
        }

    private fun assertAbsent(jar: Path, entry: String) {
        ZipFile(jar.toFile()).use { zip ->
            assertEquals(null, zip.getEntry(entry), "Unexpected cross-loader class $entry in $jar")
        }
    }

    private fun assertCompiled(build: BuildResult, path: String) {
        val task = build.task(path)
        assertNotNull(task, "Generated starter source was not compiled ($path):\n${build.output}")
        assertTrue(task!!.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE),
            "Generated compile task $path was skipped or failed: ${task.outcome}\n${build.output}")
    }

    private fun assertSuccess(build: BuildResult, path: String) {
        assertEquals(TaskOutcome.SUCCESS, build.task(path)?.outcome, "Expected real consumer build to finish:\n${build.output}")
    }

    private fun assertNoAuthoredBuildOrMetadata(f: TestKitFixture, project: Path) {
        f.assertNoConsumerBuildScript(project)
        val projectDirs = listOf(project, project.resolve("common"), project.resolve("fabric"), project.resolve("neoforge"))
        for (dir in projectDirs) {
            assertFalse(Files.exists(dir.resolve("build.gradle")), "No consumer build.gradle is allowed: $dir")
            assertFalse(Files.exists(dir.resolve("build.gradle.kts")), "No consumer build.gradle.kts is allowed: $dir")
            assertFalse(Files.exists(dir.resolve("src/main/resources/fabric.mod.json")),
                "Fabric Loader JSON must be generated, not authored: $dir")
            assertFalse(Files.exists(dir.resolve("src/main/resources/META-INF/neoforge.mods.toml")),
                "NeoForge TOML must be generated, not authored: $dir")
        }
        assertEquals("plugins { id(\"dev.fabric-amper.settings\") }\n",
            Files.readString(project.resolve("settings.gradle.kts")),
            "The only TestKit adaptation must be the local Settings plugin declaration")
    }

    private fun assertNoPublishing(build: BuildResult) {
        assertFalse(build.tasks.any { it.path.substringAfterLast(':').startsWith("publish", ignoreCase = true) },
            "Building generated templates must not execute any publishing task:\n${build.output}")
    }
}
