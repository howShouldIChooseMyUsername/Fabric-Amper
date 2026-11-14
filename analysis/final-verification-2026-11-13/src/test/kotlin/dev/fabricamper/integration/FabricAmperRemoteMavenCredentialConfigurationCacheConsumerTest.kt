package dev.fabricamper.integration

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * A deliberately limited remote-Maven configuration-cache acceptance probe.
 *
 * BOTH Gradle executions are --offline AND --dry-run: Gradle must configure,
 * serialize/reuse the remote task graph with its missing-credential Providers,
 * but must never execute the doFirst credential action or HTTP upload. This
 * does NOT validate actual HTTPS authentication/credential mutation on replay.
 *
 * No user ~/.m2, remote endpoint, Minecraft world, client, server or EULA.
 * Run only when FULL + REMOTE_MAVEN_CC is explicitly opted in.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_REMOTE_MAVEN_CC", matches = "true")
class FabricAmperRemoteMavenCredentialConfigurationCacheConsumerTest {
    @TempDir lateinit var suiteRoot: Path

    private val remoteTask = "publishMavenJavaPublicationToFabricAmperRemoteRepository"
    private val usernameEnv = "FABRIC_AMPER_TEST_REMOTE_MAVEN_CC_USERNAME_INTENTIONALLY_UNSET"
    private val passwordEnv = "FABRIC_AMPER_TEST_REMOTE_MAVEN_CC_PASSWORD_INTENTIONALLY_UNSET"

    @Test
    fun remoteMavenDryRunConfigCacheStoreThenHitWithMissingEnv() {
        // Verify the test really exercises absent environment variables, not
        // an accidentally populated credential on the worker machine.
        assertFalse(System.getenv().containsKey(usernameEnv),
            "Isolated remote Maven fixture requires its dummy username environment variable to be absent")
        assertFalse(System.getenv().containsKey(passwordEnv),
            "Isolated remote Maven fixture requires its dummy password environment variable to be absent")
        val f = TestKitFixture(suiteRoot)
        val yaml = """
            schema: 1
            product: fabric/api
            mod:
              id: amper_remote_cc
              version: "0.1.0+26.3"
              group: dev.fabricamper.ccprobe
              archivesName: remote-cc-probe
              name: Remote Maven Configuration Cache Graph
              environment: "*"
              entrypoints:
                main:
                  - dev.fabricamper.ccprobe.RemoteCcEntry
            minecraft:
              version: "26.3"
              mappings: mojang
              loader: "0.20.1"
              fabricApi: "0.165.2+26.3"
            settings:
              java:
                release: 25
                sourcesJar: true
              kotlin:
                enabled: false
              loom:
                splitEnvironmentSourceSets: false
            repositories:
              - fabric
              - mavenCentral
            publish:
              maven:
                url: https://example.invalid/fabric-amper-never-upload
                credentialsFromEnv:
                  - $usernameEnv
                  - $passwordEnv
        """.trimIndent() + "\n"
        assertTrue(yaml.contains("https://example.invalid/"),
            "Never test this configuration-cache graph against an actual publishing endpoint")
        val project = f.project("remote-maven-offline-cc", yaml)
        f.write(project, "src/main/java/dev/fabricamper/ccprobe/RemoteCcEntry.java", """
            package dev.fabricamper.ccprobe;
            public final class RemoteCcEntry implements net.fabricmc.api.ModInitializer {
                @Override public void onInitialize() {}
            }
        """.trimIndent() + "\n")
        assertSettingsOnly(f, project, yaml)
        assertNoOutputArchives(project)

        // An isolated TestKit home has neither Mojang metadata nor actual
        // compileClasspath JARs. Warm normal Minecraft/Loom classpath without
        // any publishing task, so Gradle's offline configuration-cache snapshot
        // later cannot fail merely because its JavaCompile input files are cold.
        // ACTUAL remote publishing probes remain --offline AND --dry-run.
        val warm = f.run(project, "compileJava")
        assertEquals(TaskOutcome.SUCCESS, warm.task(":compileJava")?.outcome, warm.output)
        assertTrue(warm.output.contains("BUILD SUCCESSFUL"), warm.output)
        assertNoOutputArchives(project)

        val first = f.run(project, remoteTask, "--dry-run", "--configuration-cache", "--offline")
        assertDryRun(first, "first STORE")
        assertFalse(first.output.contains("Reusing configuration cache"),
            "The first remote Maven dry-run must not start from an existing task-graph cache:\n" +
                first.output)
        assertTrue(Regex("(?i)configuration cache entry stored").containsMatchIn(first.output),
            "The remote-Maven Provider task graph must STORE a configuration cache entry:\n" +
                first.output)
        assertNoOutputArchives(project)
        assertSettingsOnly(f, project, yaml)

        val second = f.run(project, remoteTask, "--dry-run", "--configuration-cache", "--offline")
        assertDryRun(second, "second HIT")
        assertTrue(second.output.contains("Reusing configuration cache"),
            "The same remote-Maven credential-provider task graph must HIT the cache:\n" +
                second.output)
        assertNoOutputArchives(project)
        assertSettingsOnly(f, project, yaml)

        // Missing credentials are valid during task configuration and dry-run;
        // actual action-time rejection/HTTP semantics remain NOT VERIFIED.
        // There are no artifact generation or task actions in either run.
        assertFalse(Files.exists(f.generatedJson(project)),
            "Neither dry-run may execute generateFabricModJson")
    }

    private fun assertDryRun(result: BuildResult, stage: String) {
        assertTrue(result.output.contains("BUILD SUCCESSFUL"),
            "$stage must configure a remote HTTPS Maven publication without credentials:\n" + result.output)

        // Gradle TestKit's BuildResult.tasks is empty for --dry-run in this
        // project; parse the ACTUAL ordered Gradle stdout task lines instead.
        val skipped = Regex("""^(?:> Task )?(:\S+)\s+SKIPPED$""")
        val displayed = result.output.lineSequence()
            .map(String::trim)
            .filter { it.startsWith(":") || it.startsWith("> Task :") }
            .toList()
        assertTrue(displayed.isNotEmpty(), "$stage has no observable Gradle task graph:\n" + result.output)
        assertTrue(displayed.all { skipped.matches(it) },
            "$stage must SKIP every task, including remote doFirst/HTTP actions:\n" + result.output)
        val paths = displayed.map { requireNotNull(skipped.matchEntire(it)).groupValues[1] }
        assertEquals(paths.size, paths.toSet().size,
            "$stage must not contain duplicate task invocations:\n" + result.output)
        val jarIndex = paths.indexOf(":jar")
        val verifyIndex = paths.indexOf(":verifyFabricAmperJar")
        val publishIndex = paths.indexOf(":$remoteTask")
        assertTrue(jarIndex >= 0 && verifyIndex > jarIndex && publishIndex > verifyIndex,
            "$stage must order final JAR -> verifier -> skipped remote Maven publisher:\n" +
                result.output)
        assertFalse(paths.any {
            it == ":publishToMavenLocal" ||
                it.contains("FabricAmperModrinth") ||
                it.contains("FabricAmperCurseForge") ||
                it.contains("FabricAmperLocalRepository")
        }, "$stage may only graph the explicitly requested REMOTE Maven target:\n" + result.output)
        assertFalse(result.output.contains("https://example.invalid/fabric-amper-never-upload"),
            "$stage must not log the configured remote target URL as an HTTP request:\n" +
                result.output)
        assertFalse(result.output.contains(usernameEnv) || result.output.contains(passwordEnv),
            "$stage must not echo credential variable names in routine logs:\n" + result.output)
    }

    private fun assertSettingsOnly(f: TestKitFixture, project: Path, yaml: String) {
        f.assertNoConsumerBuildScript(project)
        assertEquals("plugins { id(\"dev.fabric-amper.settings\") }\n",
            Files.readString(project.resolve("settings.gradle.kts")))
        assertEquals(yaml, Files.readString(project.resolve("mod.yaml")))
        assertFalse(Files.exists(project.resolve("mod.yml")))
        assertFalse(Files.exists(project.resolve("src/main/resources/fabric.mod.json")))
    }

    private fun assertNoOutputArchives(project: Path) {
        assertFalse(Files.exists(project.resolve("build/fabric-amper/maven-repository")),
            "Remote dry-run must never publish a local Maven repository")
        assertFalse(Files.exists(project.resolve("build/libs")),
            "Remote dry-run must not execute jar/sourcesJar or materialize a distribution")
        assertFalse(Files.exists(project.resolve("build/generated/fabric-amper/resources/main/fabric.mod.json")),
            "Remote dry-run must not execute the JSON generator")
    }
}
