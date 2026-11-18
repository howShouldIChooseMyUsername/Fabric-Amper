package dev.fabricamper.unit

import dev.fabricamper.config.FabricAmperSchemaVersion
import dev.fabricamper.config.ModYamlLoader
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Real Windows batch init; cached-schema tests do not need Gradle or network. */
@EnabledOnOs(OS.WINDOWS)
class FabricAmperInitSchemaUnitTest {
    @Test
    fun `init copies current producer schema and explicitly pins legacy Fabric starter for mod and api`() {
        val producer = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val batch = producer.resolve("fabric-amper.bat")
        val originalSchema = Files.readAllBytes(producer.resolve("fabric-amper.schema.json"))
        val testRoot = Files.createTempDirectory(Files.createDirectories(producer.resolve("build")), "fabric-amper-init-schema-")

        for ((profile, id) in listOf("mod" to "autocompletemod", "api" to "autocompleteapi")) {
            val consumer = testRoot.resolve(profile)
            val firstRun = runInit(batch, id, consumer, profile)
            assertEquals(0, firstRun.first, "init $profile must succeed: ${firstRun.second}")

            val yamlFile = consumer.resolve("mod.yaml")
            val yaml = Files.readString(yamlFile, StandardCharsets.UTF_8)
            assertEquals("# yaml-language-server: \$schema=./schemas/mod.schema.json", yaml.lineSequence().first())
            assertEquals("# \$schema: ./schemas/mod.schema.json", yaml.lineSequence().drop(1).first(),
                "IntelliJ IDEA must discover the same local schema without manual mapping")
            val reference = yaml.lineSequence().first().substringAfter("\$schema=")
            assertEquals(
                consumer.resolve("schemas/mod.schema.json").toAbsolutePath().normalize(),
                yamlFile.parent.resolve(reference).toAbsolutePath().normalize(),
                "YAML language-server directive must resolve to the actual generated schema",
            )
            assertTrue(Files.isRegularFile(yamlFile.parent.resolve(reference)), "YAML comment must not point to a missing file")
            val effective = ModYamlLoader.load(yamlFile)
            assertEquals(FabricAmperSchemaVersion.LATEST, effective.schema)
            assertEquals("fabric/$profile", effective.product)
            assertEquals(id, effective.mod.id)
            assertTrue(Regex("(?m)^schema: 1$").containsMatchIn(yaml),
                "legacy Fabric starter must pin schema: 1 now that omission means schema 1.1")
            assertEquals("1", effective.schemaVersion)
            assertArrayEquals(originalSchema, Files.readAllBytes(consumer.resolve("schemas/mod.schema.json")))
            assertTrue(Files.isRegularFile(consumer.resolve(".vscode/settings.json")))
            assertTrue(
                Files.readString(consumer.resolve(".vscode/settings.json")).contains("./schemas/mod.schema.json"),
                "init must provide a VS Code mapping in addition to the YAML language-server comment",
            )
            assertTrue(Files.isRegularFile(consumer.resolve("settings.gradle.kts")))
            val settings = Files.readString(consumer.resolve("settings.gradle.kts"), StandardCharsets.UTF_8)
            assertTrue(settings.contains("maven(\"https://jitpack.io\")"))
            assertTrue(settings.contains("maven(\"https://maven.fabricmc.net/\")"))
            assertTrue(settings.contains("gradlePluginPortal()"))
            assertTrue(settings.contains("mavenCentral()"))
            assertTrue(settings.contains("resolutionStrategy {"))
            assertTrue(settings.contains("if (requested.id.id == \"dev.fabric-amper.settings\") {"))
            assertTrue(settings.contains("useModule(\"com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.3.1\")"))
            assertTrue(settings.contains("id(\"dev.fabric-amper.settings\") version \"0.3.1\""))
            assertFalse(settings.contains("build/g29-probe"), "init must not rely on local Maven setup")
            assertFalse(settings.contains("https://fabricmc.net\""), "Fabric Maven requires maven.fabricmc.net")
            assertTrue(Files.isRegularFile(consumer.resolve("src/main/java/org/example/ExampleMod.java")))
            assertFalse(Files.exists(consumer.resolve("src/main/kotlin/org/example/ExampleMod.kt")),
                "Java remains the default; Kotlin must be explicitly requested")
            assertFalse(effective.settings.kotlin.enabled, "Java init must not enable Kotlin")
            assertFalse(effective.settings.loom.splitEnvironmentSourceSets,
                "Split source sets must remain opt-in for the Java starter")
            assertFalse(Files.exists(consumer.resolve("src/client")), "Unflagged init must not create client sources")
            assertFalse(effective.mod.entrypoints.containsKey("client"), "Unflagged init must not register a client starter")
            assertFalse(Regex("(?m)^  loom:").containsMatchIn(yaml),
                "Unflagged init should not add a redundant settings.loom block")
            assertFalse(Files.exists(consumer.resolve("build.gradle")))
            assertFalse(Files.exists(consumer.resolve("build.gradle.kts")))
            assertFalse(Files.exists(consumer.resolve("src/main/resources/fabric.mod.json")))

            val secondRun = runInit(batch, id, consumer, profile)
            assertEquals(3, secondRun.first, "init must refuse to overwrite an existing consumer: ${secondRun.second}")
            assertEquals(yaml, Files.readString(yamlFile, StandardCharsets.UTF_8))
            assertArrayEquals(originalSchema, Files.readAllBytes(consumer.resolve("schemas/mod.schema.json")))
        }

        val removedSetup = runBat(batch, "setup")
        assertEquals(2, removedSetup.first, "setup must no longer run a producer publication")
        assertTrue(removedSetup.second.contains("Unknown command"))
        val help = runBat(batch, "help")
        assertEquals(0, help.first)
        assertFalse(Regex("(?m)^\\s*fabric-amper\\.bat setup\\s*$").containsMatchIn(help.second))
    }

    @Test
    fun `init Kotlin flag generates Kotlin source and settings for mod api and here`() {
        val producer = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val batch = producer.resolve("fabric-amper.bat")
        val workspace = Files.createTempDirectory(Files.createDirectories(producer.resolve("build")), "fabric-amper-init-kotlin-")
        val standalone = workspace.resolve("Test Mode")
        val api = workspace.resolve("API Mode")
        val compact = workspace.resolve("Compact Mode")
        val here = Files.createDirectory(workspace.resolve("Here Mode"))
        val runs = listOf(
            "init test_mode \"$standalone\" mod --kotlin" to (standalone to "fabric/mod"),
            "init api_mode \"$api\" api --kotlin" to (api to "fabric/api"),
            "init compact_mode \"$compact\" --kotlin" to (compact to "fabric/mod"),
            "-C \"$here\" init here_mode --here mod --kotlin" to (here to "fabric/mod"),
        )
        for ((args, expected) in runs) {
            val result = runBat(batch, args)
            assertEquals(0, result.first, "Kotlin init failed for '$args': ${result.second}")
            val (consumer, profile) = expected
            val file = consumer.resolve("mod.yaml")
            val yaml = Files.readString(file, StandardCharsets.UTF_8)
            val model = ModYamlLoader.load(file)
            assertEquals(profile, model.product)
            assertTrue(model.settings.kotlin.enabled, "Kotlin flag should enable Fabric Language Kotlin")
            assertEquals("# yaml-language-server: \$schema=./schemas/mod.schema.json", yaml.lineSequence().first())
            assertEquals("# \$schema: ./schemas/mod.schema.json", yaml.lineSequence().drop(1).first())
            assertTrue(Files.isRegularFile(consumer.resolve("schemas/mod.schema.json")))
            assertEquals("org.example.ExampleMod", model.mod.entrypoints.getValue("main").single().value)
            val source = consumer.resolve("src/main/kotlin/org/example/ExampleMod.kt")
            assertTrue(Files.isRegularFile(source), "Missing Kotlin ModInitializer at $source")
            val kotlin = Files.readString(source, StandardCharsets.UTF_8)
            assertTrue(kotlin.contains("class ExampleMod : ModInitializer"), kotlin)
            assertTrue(kotlin.contains("override fun onInitialize()"), kotlin)
            assertFalse(Files.exists(consumer.resolve("src/main/java/org/example/ExampleMod.java")),
                "Kotlin init must not generate the Java example as well")
            assertFalse(Files.exists(consumer.resolve("src/client")), "Kotlin-only init must not enable split sources")
            assertFalse(model.mod.entrypoints.containsKey("client"))
            assertFalse(Files.exists(consumer.resolve("build.gradle.kts")))
            assertFalse(Files.exists(consumer.resolve("src/main/resources/fabric.mod.json")))
        }
    }

    @Test
    fun `init splitEnvironmentSourceSets creates client source entrypoint and roots for Java Kotlin mod api and here`() {
        val producer = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val batch = producer.resolve("fabric-amper.bat")
        val root = Files.createTempDirectory(Files.createDirectories(producer.resolve("build")), "fabric-amper-init-split-")
        val mod = root.resolve("Java Mod")
        val api = root.resolve("Java API")
        val noProfile = root.resolve("Default Profile")
        val kotlin = root.resolve("Kotlin Mod")
        val reverse = root.resolve("Reverse Flags")
        val here = Files.createDirectory(root.resolve("Here Mod"))
        val hereKotlin = Files.createDirectory(root.resolve("Here Kotlin API"))
        data class Case(val args: String, val folder: Path, val product: String, val kotlin: Boolean)
        val cases = listOf(
            Case("init split_mod \"$mod\" mod --splitEnvironmentSourceSets", mod, "fabric/mod", false),
            Case("init split_api \"$api\" api --splitEnvironmentSourceSets", api, "fabric/api", false),
            Case("init split_default \"$noProfile\" --splitEnvironmentSourceSets", noProfile, "fabric/mod", false),
            Case("init split_kotlin \"$kotlin\" mod --kotlin --splitEnvironmentSourceSets", kotlin, "fabric/mod", true),
            Case("init split_reverse \"$reverse\" mod --splitEnvironmentSourceSets --kotlin", reverse, "fabric/mod", true),
            Case("-C \"$here\" init split_here --here mod --splitEnvironmentSourceSets", here, "fabric/mod", false),
            Case("-C \"$hereKotlin\" init split_here_api --here api --splitEnvironmentSourceSets --kotlin", hereKotlin, "fabric/api", true),
        )
        for ((args, folder, product, isKotlin) in cases) {
            val (status, output) = runBat(batch, args)
            assertEquals(0, status, "Split init failed for '$args': $output")
            val yamlFile = folder.resolve("mod.yaml")
            val yaml = Files.readString(yamlFile, StandardCharsets.UTF_8)
            val normalizedYaml = yaml.replace("\r\n", "\n")
            val model = ModYamlLoader.load(yamlFile)
            assertEquals(product, model.product, args)
            assertTrue(model.settings.loom.splitEnvironmentSourceSets, "Loom split not enabled: $args")
            assertEquals(1, Regex("(?m)^    splitEnvironmentSourceSets: true$").findAll(normalizedYaml).count(),
                "Expected one indented Loom setting in YAML: $args")
            assertTrue(normalizedYaml.contains("  loom:\n    splitEnvironmentSourceSets: true"),
                "YAML nesting incorrect: $yaml")
            assertEquals(isKotlin, model.settings.kotlin.enabled, args)
            assertTrue(Files.isRegularFile(folder.resolve("schemas/mod.schema.json")))
            assertEquals("# yaml-language-server: \$schema=./schemas/mod.schema.json", yaml.lineSequence().first())
            assertEquals("# \$schema: ./schemas/mod.schema.json", yaml.lineSequence().drop(1).first())
            assertTrue(Files.isRegularFile(folder.resolve(
                if (isKotlin) "src/main/kotlin/org/example/ExampleMod.kt" else "src/main/java/org/example/ExampleMod.java"
            )), "Entrypoint source missing: $args")
            assertEquals("org.example.ExampleMod", model.mod.entrypoints.getValue("main").single().value, args)
            assertEquals("org.example.ExampleModClient", model.mod.entrypoints.getValue("client").single().value, args)
            assertTrue(normalizedYaml.contains("    client:\n      - org.example.ExampleModClient"),
                "Client entrypoint must be included in generated YAML: $args")
            assertTrue(Files.isDirectory(folder.resolve("src/main/resources")), args)
            assertTrue(Files.isDirectory(folder.resolve("src/client/resources")), args)
            val clientSource = folder.resolve(if (isKotlin)
                "src/client/kotlin/org/example/ExampleModClient.kt"
            else
                "src/client/java/org/example/ExampleModClient.java")
            assertTrue(Files.isRegularFile(clientSource), "Client initializer source missing: $args")
            val code = Files.readString(clientSource, StandardCharsets.UTF_8)
            assertTrue(code.contains("import net.fabricmc.api.ClientModInitializer"), code)
            assertTrue(code.contains(if (isKotlin)
                "class ExampleModClient : ClientModInitializer"
            else
                "class ExampleModClient implements ClientModInitializer"), code)
            assertTrue(code.contains(if (isKotlin)
                "override fun onInitializeClient()"
            else
                "@Override public void onInitializeClient()"), code)
            assertFalse(Files.exists(folder.resolve(if (isKotlin)
                "src/client/java/org/example/ExampleModClient.java"
            else
                "src/client/kotlin/org/example/ExampleModClient.kt")), "Do not create both languages: $args")
            assertFalse(Files.exists(folder.resolve("build.gradle.kts")), args)
            assertFalse(Files.exists(folder.resolve("src/main/resources/fabric.mod.json")), args)
            assertFalse(Files.exists(folder.resolve("src/client/resources/fabric.mod.json")), args)
        }
    }

    @Test
    fun `init refuses duplicate and misplaced split or kotlin flags without creating outputs`() {
        val producer = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val batch = producer.resolve("fabric-amper.bat")
        val root = Files.createTempDirectory(Files.createDirectories(producer.resolve("build")), "fabric-amper-init-split-reject-")
        val cases = listOf(
            "init invalid_split \"${root.resolve("duplicate-split")}\" mod --splitEnvironmentSourceSets --splitEnvironmentSourceSets",
            "init invalid_split \"${root.resolve("split-before-profile")}\" --splitEnvironmentSourceSets mod",
            "init invalid_split \"${root.resolve("split-before-folder")}\" --kotlin --splitEnvironmentSourceSets mod",
            "init invalid_split \"${root.resolve("duplicate-kotlin")}\" mod --kotlin --kotlin",
            "init invalid_split \"${root.resolve("unknown")}\" mod --unknown",
            "init invalid_split \"${root.resolve("too-many")}\" mod --kotlin --splitEnvironmentSourceSets extra",
        )
        for (args in cases) {
            val (status, output) = runBat(batch, args)
            assertEquals(2, status, "Unexpected success for '$args': $output")
        }
        assertTrue(Files.list(root).use { stream -> stream.findAny().isEmpty },
            "Bad flag syntax must not create any consumer directories")
    }

    @Test
    fun `init Kotlin option rejects duplicate misplaced and unexpected arguments before creating a project`() {
        val producer = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val batch = producer.resolve("fabric-amper.bat")
        val workspace = Files.createTempDirectory(Files.createDirectories(producer.resolve("build")), "fabric-amper-init-kotlin-invalid-")
        val invalid = listOf(
            "init wrong_kotlin \"${workspace.resolve("one")}\" mod --kotlin --kotlin",
            "init wrong_kotlin \"${workspace.resolve("two")}\" --kotlin mod",
            "init wrong_kotlin \"${workspace.resolve("three")}\" --kotlin --kotlin",
            "init wrong_kotlin \"${workspace.resolve("four")}\" mod --unknown",
            "init wrong_kotlin \"${workspace.resolve("five")}\" unknown --kotlin",
        )
        for (args in invalid) {
            val result = runBat(batch, args)
            assertEquals(2, result.first, "Should reject '$args': ${result.second}")
        }
        for (name in listOf("one", "two", "three", "four", "five")) {
            assertFalse(Files.exists(workspace.resolve(name)), "Invalid Kotlin flags must not create $name")
        }
    }

    @Test
    fun `standalone init enforces the main branch schema hash and refuses existing corruption`() {
        val producer = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val batchSource = Files.readString(producer.resolve("fabric-amper.bat"), StandardCharsets.UTF_8)
        val schema = Files.readAllBytes(producer.resolve("fabric-amper.schema.json"))
        val hash = MessageDigest.getInstance("SHA-256").digest(schema)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertTrue(batchSource.contains("FA_SCHEMA_SHA256=$hash"), "Standalone BAT must pin main schema bytes")
        assertTrue(
            batchSource.contains("https://raw.githubusercontent.com/howShouldIChooseMyUsername/Fabric-Amper/main/fabric-amper.schema.json"),
            "init must fetch from the exact GitHub main branch path",
        )

        val root = Files.createTempDirectory(Files.createDirectories(producer.resolve("build")), "fabric-amper-init-standalone-")
        val batch = root.resolve("fabric-amper.bat")
        Files.writeString(batch, batchSource, StandardCharsets.UTF_8)
        Files.writeString(root.resolve("fabric-amper.schema.json"), "UNTRUSTED SCHEMA")
        val candidate = root.resolve("Must Not Exist")
        val failed = runBat(batch, "init schema_guard \"$candidate\" mod")
        assertEquals(1, failed.first, failed.second)
        assertTrue(failed.second.contains("SHA-256 mismatch"), failed.second)
        assertFalse(Files.exists(candidate), "Bad schema must be refused before any starter files are created")
        assertEquals("UNTRUSTED SCHEMA", Files.readString(root.resolve("fabric-amper.schema.json")))
        Files.write(root.resolve("fabric-amper.schema.json"), schema)
        val success = runBat(batch, "init schema_guard \"$candidate\" mod")
        assertEquals(0, success.first, success.second)
        val yaml = Files.readString(candidate.resolve("mod.yaml"))
        val directive = yaml.lineSequence().first().substringAfter("\$schema=")
        assertTrue(Files.isRegularFile(candidate.resolve(directive)), "Standalone YAML directive must resolve locally")
        assertArrayEquals(schema, Files.readAllBytes(candidate.resolve(directive)))
    }

    @Test
    fun `init here preserves tools and refuses any existing consumer project`() {
        val root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val batch = root.resolve("fabric-amper.bat")
        val originalSchema = Files.readAllBytes(root.resolve("fabric-amper.schema.json"))
        val temp = Files.createTempDirectory(Files.createDirectories(root.resolve("build")), "fabric-amper-init-here-")

        for (profile in listOf("mod", "api")) {
            val folder = Files.createDirectory(temp.resolve("tool-only-$profile"))
            val tool = folder.resolve("fabric-amper.bat")
            Files.writeString(tool, "existing batch tools")
            Files.writeString(folder.resolve("FabricAmperSKILL.md"), "existing agent skill")
            Files.writeString(folder.resolve(".gitignore"), "existing ignore rules")
            Files.createDirectory(folder.resolve(".vscode"))
            val editorSettings = folder.resolve(".vscode/settings.json")
            Files.writeString(editorSettings, "{\"editor.tabSize\":4}")

            val first = runBat(batch, "-C \"$folder\" init night_vision --here $profile")
            assertEquals(0, first.first, "init --here $profile must succeed: ${first.second}")
            assertEquals("existing batch tools", Files.readString(tool))
            assertEquals("existing agent skill", Files.readString(folder.resolve("FabricAmperSKILL.md")))
            assertEquals("existing ignore rules", Files.readString(folder.resolve(".gitignore")))
            assertEquals("{\"editor.tabSize\":4}", Files.readString(editorSettings), "do not overwrite VS Code settings")
            assertEquals("# yaml-language-server: \$schema=./schemas/mod.schema.json", Files.readAllLines(folder.resolve("mod.yaml"))[0])
            assertArrayEquals(originalSchema, Files.readAllBytes(folder.resolve("schemas/mod.schema.json")))
            assertEquals("fabric/$profile", ModYamlLoader.load(folder.resolve("mod.yaml")).product)

            val second = runBat(batch, "-C \"$folder\" init night_vision --here $profile")
            assertEquals(3, second.first, "init --here must refuse an existing project")
        }

        for (conflict in listOf("mod.yaml", "mod.yml", "settings.gradle.kts", "build.gradle.kts", "build.gradle", "src", "schemas/mod.schema.json")) {
            val folder = Files.createDirectory(temp.resolve("conflict-${conflict.replace('/', '-').replace('.', '-')}"))
            val existing = folder.resolve(conflict)
            Files.createDirectories(existing.parent)
            if (conflict == "src") Files.createDirectory(existing) else Files.writeString(existing, "KEEP")
            val result = runBat(batch, "-C \"$folder\" init night_vision --here")
            assertEquals(3, result.first, "Expected hard refusal for $conflict: ${result.second}")
            if (conflict != "src") assertEquals("KEEP", Files.readString(existing))
            assertFalse(Files.exists(folder.resolve("src/main/java/org/example/ExampleMod.java")))
            assertFalse(Files.exists(folder.resolve(".vscode/settings.json")))
        }
        assertEquals(2, runBat(batch, "init night_vision --here mod extra").first)
        assertEquals(2, runBat(batch, "init night_vision other-folder --here").first)
    }

    @Test
    fun `doctor fix dry-run previews repair without installing or writing`() {
        val root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val batch = root.resolve("fabric-amper.bat")
        val folder = Files.createTempDirectory(Files.createDirectories(root.resolve("build")), "fabric-amper-fix-preview-")
        Files.writeString(folder.resolve("mod.yaml"), "product: fabric/mod\n")
        val preview = runBat(batch, "-C \"$folder\" doctor --fix --dry-run")
        assertEquals(0, preview.first, "Dry-run should not need network/winget: ${preview.second}")
        assertTrue(preview.second.contains("READ-ONLY PREVIEW"))
        assertTrue(preview.second.contains("Would create missing settings.gradle.kts"))
        assertTrue(preview.second.contains("Would copy missing schemas/mod.schema.json"))
        assertTrue(preview.second.contains("Would create missing .vscode/settings.json"))
        assertFalse(Files.exists(folder.resolve("settings.gradle.kts")))
        assertFalse(Files.exists(folder.resolve("schemas/mod.schema.json")))
        assertFalse(Files.exists(folder.resolve(".vscode/settings.json")))

        val powershell = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-File", root.resolve("scripts/fabric-amper-doctor-fix.ps1").toString(),
            "-Preview", "-AssumeMissing",
        ).directory(root.toFile()).redirectErrorStream(true)
        powershell.environment()["FA_ROOT"] = root.toString()
        powershell.environment()["FA_CONSUMER"] = folder.toString()
        powershell.environment()["FA_VERSION"] = "0.3.1"
        val process = powershell.start()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Forced-missing preview must not start a download")
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.exitValue(), output)
        assertTrue(output.contains("winget EclipseAdoptium.Temurin.25.JDK"), output)
        assertTrue(output.contains("official Gradle 9.7.1 ZIP with SHA-256"), output)
        assertFalse(Files.exists(folder.resolve("settings.gradle.kts")))
    }

    @Test
    fun `doctor accepts project selection before or after command including repeated doctor`() {
        val root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val batch = root.resolve("fabric-amper.bat")
        val testRoot = Files.createTempDirectory(Files.createDirectories(root.resolve("build")), "doctor-options-")
        val consumer = Files.createDirectory(testRoot.resolve("consumer with spaces"))
        val relative = ".\\${root.relativize(consumer)}"

        val invocations = listOf(
            "-C \"$relative\" doctor --paths",
            "doctor -C \"$relative\" --paths",
            "doctor --paths --project \"$relative\"",
            "doctor -C \"$relative\" doctor --paths",
            "doctor -C \"$relative\" doctor",
        )
        for (args in invocations) {
            val result = runBat(batch, args)
            assertEquals(0, result.first, "doctor must accept '$args': ${result.second}")
            assertTrue(result.second.contains("[doctor] Consumer: \"$consumer\""), result.second)
        }
        val preview = runBat(batch, "doctor --fix --dry-run -C \"$relative\"")
        assertEquals(0, preview.first, preview.second)
        assertTrue(preview.second.contains("READ-ONLY PREVIEW"), preview.second)
        assertFalse(Files.exists(consumer.resolve("settings.gradle.kts")))

        for (invalid in listOf(
            "doctor -C",
            "doctor --dry-run",
            "doctor --full --paths",
            "doctor --fix --full",
            "doctor -C \"$relative\" doctor doctor --paths",
            "doctor -C \"$relative\" --fix --dry-run --unknown",
        )) {
            val result = runBat(batch, invalid)
            assertEquals(2, result.first, "doctor must reject '$invalid': ${result.second}")
        }
    }

    @Test
    fun `doctor fix standalone BAT previews exact release download and rejects corrupted helper`() {
        val root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val batSource = root.resolve("fabric-amper.bat")
        val scriptSource = root.resolve("scripts/fabric-amper-doctor-fix.ps1")
        val expectedHash = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(scriptSource))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val batchSource = Files.readString(batSource, StandardCharsets.UTF_8)
        assertTrue(batchSource.contains("FA_DOCTOR_SHA256=$expectedHash"), "BAT must pin the exact release PS1")
        assertTrue(
            batchSource.contains("releases/download/v'+\$env:FA_VERSION+'/'+\$name"),
            "Downloader must use this BAT version's GitHub Release asset",
        )

        val folder = Files.createTempDirectory(Files.createDirectories(root.resolve("build")), "fabric-amper-remote-helper-")
        val batch = folder.resolve("fabric-amper.bat")
        Files.copy(batSource, batch)
        val missingPreview = runBat(batch, "doctor --fix --dry-run")
        assertEquals(0, missingPreview.first, "Missing helper must preview without fetching: ${missingPreview.second}")
        assertTrue(missingPreview.second.contains("Would fetch exactly fabric-amper-doctor-fix.ps1"))
        assertTrue(missingPreview.second.contains("/releases/download/v0.3.1/fabric-amper-doctor-fix.ps1"))
        assertFalse(Files.exists(folder.resolve("scripts/fabric-amper-doctor-fix.ps1")))
        assertEquals(2, runBat(batch, "doctor --fix --unknown").first)

        val script = Files.createDirectories(folder.resolve("scripts")).resolve("fabric-amper-doctor-fix.ps1")
        val corrupted = "throw 'UNTRUSTED SCRIPT'\n"
        Files.writeString(script, corrupted)
        val corruptPreview = runBat(batch, "doctor --fix --dry-run")
        assertEquals(1, corruptPreview.first, "Existing unexpected PS1 must never execute: ${corruptPreview.second}")
        assertTrue(corruptPreview.second.contains("SHA-256 mismatch"))
        assertEquals(corrupted, Files.readString(script), "Corrupted helper must not be silently overwritten")
    }

    private fun runInit(batch: Path, id: String, target: Path, profile: String): Pair<Int, String> {
        return runBat(batch, "init $id \"$target\" $profile")
    }

    private fun runBat(batch: Path, args: String): Pair<Int, String> {
        // cmd /s /c strips the outer quote pair. Keep another pair around the
        // entire command so an executable path and arguments containing spaces
        // are both preserved when Windows parses them.
        val command = "\"\"$batch\" $args\""
        // Drain stdout concurrently through an OS file. Waiting for the child
        // *before* reading its pipe can deadlock if Windows PowerShell/Gradle
        // emits more diagnostic text than the small process pipe can hold.
        val output = Files.createTempFile("fabric-amper-init-stdout-", ".log")
        try {
            val process = ProcessBuilder("cmd.exe", "/d", "/s", "/c", command)
                .directory(batch.parent.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start()
            val finished = process.waitFor(90, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                throw AssertionError("Windows init did not terminate in 90 seconds; output: " +
                    Files.readString(output, StandardCharsets.UTF_8).take(4000))
            }
            return process.exitValue() to Files.readString(output, StandardCharsets.UTF_8)
        } finally {
            Files.deleteIfExists(output)
        }
    }
}
