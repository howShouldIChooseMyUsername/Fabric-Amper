package dev.fabricamper.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Real repository-resolution acceptance for the supplemental FA011 diagnostic.
 * The Gradle native failure must remain intact; network/TLS/offline failures
 * are not evidence that the declared artifact is absent.
 */
@EnabledIfEnvironmentVariable(named = "FABRIC_AMPER_TESTKIT_FULL", matches = "true")
class FabricAmperMissingDependencyConsumerTest {
    @TempDir lateinit var suiteRoot: Path

    @Test
    fun `FA011 nonexistent Maven artifact preserves native compile failure and precise YAML source`() {
        val f = TestKitFixture(suiteRoot)
        // A fixed, deliberately unregistered group/artifact/version on real Maven
        // repositories. No fake repository or local substitute is installed.
        val coordinate = "dev.fabricamper.fixtures.nonexistent:fa011-missing-module-20261111:0.0.0"
        val declaration = "  - \"$coordinate\""
        val yaml = f.resource("minimal-mod.yaml").trimEnd() +
            "\ndependencies:\n$declaration\n"
        val expectedLine = yaml.lines().indexOf(declaration) + 1
        assertTrue(expectedLine > 0, "Expected the dependency declaration in the fixture YAML")
        val root = f.project("fa011-missing-maven", yaml)
        val source = """
            package org.example;
            public final class MissingArtifactCompileProbe {
                public static int value() { return 42; }
            }
        """.trimIndent() + "\n"
        val sourcePath = "src/main/java/org/example/MissingArtifactCompileProbe.java"
        f.write(root, sourcePath, source)
        f.assertNoConsumerBuildScript(root)

        // Request a real compile rather than help: Loom eagerly resolves
        // mainRuntimeDependencyResolve during project configuration, before
        // Gradle can construct or execute the compileJava task graph.
        val result = f.fail(root, "compileJava")
        assertTrue(
            result.output.contains("A problem occurred configuring root project"),
            "Missing Maven artifact must fail during Loom project configuration:\n${result.output}",
        )
        assertTrue(
            result.output.contains("Could not resolve all files for configuration ':mainRuntimeDependencyResolve'"),
            "The failure must be the actual Loom runtime dependency resolution:\n${result.output}",
        )
        assertTrue(result.tasks.isEmpty(), "Configuration failed before any task graph could run:\n${result.output}")
        assertTrue(
            result.output.contains("Could not find $coordinate."),
            "Gradle's own missing-module diagnostic must survive unchanged:\n${result.output}",
        )
        val expectedFa011 =
            "FabricAmper FA011 mod.yaml:$expectedLine:5 dependencies[0]: " +
                "Maven/Modrinth artifact not found: $coordinate"
        assertTrue(
            result.output.contains(expectedFa011),
            "FA011 must identify exactly this coordinate and its YAML line/column/path:\n${result.output}",
        )
        for (networkFailure in listOf(
            "offline mode", "Offline mode", "SSLHandshakeException",
            "PKIX path building failed", "ConnectException", "Read timed out",
        )) {
            assertFalse(
                result.output.contains(networkFailure),
                "Network/TLS/offline failure cannot be accepted as missing Maven artifact: $networkFailure",
            )
        }
        assertEquals(source, Files.readString(root.resolve(sourcePath)), "The consumer source must remain unmodified")
        assertEquals(yaml, f.yaml(root), "The YAML must remain unmodified after failed resolution")
        f.assertNoConsumerBuildScript(root)
    }
}
