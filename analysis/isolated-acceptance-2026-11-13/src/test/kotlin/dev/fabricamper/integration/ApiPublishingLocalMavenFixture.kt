package dev.fabricamper.integration

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.tools.ToolProvider

/**
 * Minimal, genuinely compiled, fixed-version Maven modules. All output is
 * confined to the JUnit-owned fixture root. In particular, the test never
 * publishes to ~/.m2, writes a user Gradle init script, or contacts a remote
 * Maven repository for the synthetic modules under this unique test group.
 */
internal object ApiPublishingLocalMavenFixture {
    const val GROUP = "dev.fabricamper.testfixtures"
    const val VERSION = "1.0.0"

    const val PUBLIC = "public-token"
    const val INTERNAL = "internal-helper"
    const val RUNTIME = "runtime-only-helper"
    const val COMPILE_ONLY = "compile-only-helper"
    const val NESTED = "nested-helper"
    const val TEST_ONLY = "test-helper"

    private data class Module(val artifact: String, val sourcePath: String, val source: String)

    private val modules = listOf(
        Module(PUBLIC, "dev/fabricamper/testfixtures/PublicToken.java", """
            package dev.fabricamper.testfixtures;
            public final class PublicToken {
                public String value() { return "public-fixture"; }
            }
        """.trimIndent()),
        Module(INTERNAL, "dev/fabricamper/testfixtures/InternalHelper.java", """
            package dev.fabricamper.testfixtures;
            public final class InternalHelper {
                public static String value() { return "internal-fixture"; }
            }
        """.trimIndent()),
        Module(RUNTIME, "dev/fabricamper/testfixtures/RuntimeHelper.java", """
            package dev.fabricamper.testfixtures;
            public final class RuntimeHelper {
                public static String value() { return "runtime-fixture"; }
            }
        """.trimIndent()),
        Module(COMPILE_ONLY, "dev/fabricamper/testfixtures/CompileMarker.java", """
            package dev.fabricamper.testfixtures;
            public @interface CompileMarker {}
        """.trimIndent()),
        Module(NESTED, "dev/fabricamper/testfixtures/NestedHelper.java", """
            package dev.fabricamper.testfixtures;
            public final class NestedHelper {
                public static String value() { return "included-fixture"; }
            }
        """.trimIndent()),
        Module(TEST_ONLY, "dev/fabricamper/testfixtures/TestHelper.java", """
            package dev.fabricamper.testfixtures;
            public final class TestHelper {}
        """.trimIndent()),
    )

    fun create(ownedRoot: Path): Path {
        val root = ownedRoot.toAbsolutePath().normalize()
        check(Files.isDirectory(root)) { "JUnit fixture root not found: $root" }
        val repo = Files.createDirectories(root.resolve("local-module-repository"))
        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler()) {
            "The API publishing consumer TestKit requires a real JDK (Java 25), not a JRE"
        }

        for (module in modules) {
            val source = root.resolve("fixture-source/${module.artifact}/${module.sourcePath}")
            Files.createDirectories(source.parent)
            Files.writeString(source, module.source + "\n", StandardCharsets.UTF_8)
            val classes = Files.createDirectories(root.resolve("fixture-classes/${module.artifact}"))
            val diagnostics = ByteArrayOutputStream()
            val exitCode = compiler.run(null, null, diagnostics,
                "--release", "25", "-encoding", "UTF-8", "-d", classes.toString(), source.toString())
            check(exitCode == 0) {
                "Unable to compile local Maven fixture '${module.artifact}':\n" +
                    diagnostics.toString(StandardCharsets.UTF_8)
            }
            val publishDir = Files.createDirectories(repo.resolve(
                "${GROUP.replace('.', '/')}/${module.artifact}/$VERSION"))
            val artifactJar = publishDir.resolve("${module.artifact}-$VERSION.jar")
            ZipOutputStream(Files.newOutputStream(artifactJar)).use { zip ->
                Files.walk(classes).use { paths ->
                    paths.filter { Files.isRegularFile(it) }.sorted().forEach { file ->
                        val entryName = classes.relativize(file).toString().replace('\\', '/')
                        val entry = ZipEntry(entryName)
                        entry.time = 0L
                        zip.putNextEntry(entry)
                        Files.copy(file, zip)
                        zip.closeEntry()
                    }
                }
            }
            Files.writeString(publishDir.resolve("${module.artifact}-$VERSION.pom"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>$GROUP</groupId>
                  <artifactId>${module.artifact}</artifactId>
                  <version>$VERSION</version>
                  <packaging>jar</packaging>
                </project>
            """.trimIndent() + "\n", StandardCharsets.UTF_8)
        }
        return repo
    }

    fun gav(artifact: String): String = "$GROUP:$artifact:$VERSION"
}
