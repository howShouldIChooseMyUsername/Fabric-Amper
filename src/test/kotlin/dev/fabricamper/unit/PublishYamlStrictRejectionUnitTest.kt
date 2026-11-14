package dev.fabricamper.unit

import dev.fabricamper.config.ModYamlLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Negative-only YAML schema / security surface for the new publish contract.
 * No network, environment-secret reads, Gradle publishing tasks or API calls.
 */
class PublishYamlStrictRejectionUnitTest {
    @TempDir lateinit var project: Path

    private fun basic(extraMod: String = ""): String =
        if (extraMod.isEmpty()) YamlFixtures.valid else YamlFixtures.mod(YamlFixtures.valid, extraMod)

    private fun reject(tail: String, expectedPath: String, base: String = basic(), root: Path = project) {
        val source = YamlFixtures.write(root, base + tail)
        val error = YamlFixtures.configError("FA003") { ModYamlLoader.load(source) }
        assertTrue(error.yamlPath == expectedPath ||
            error.yamlPath.startsWith("$expectedPath.") ||
            error.yamlPath.startsWith("$expectedPath["),
            "Expected failure at $expectedPath; actual ${error.yamlPath}: ${error.reason}")
    }

    @Test
    fun `unknown publish keys and explicit null values are rejected at their authored paths`() {
        val invalid = listOf(
            "publish: null\n" to "publish",
            "publish: {}\n" to "publish",
            "publish:\n  unknown: true\n" to "publish.unknown",
            "publish:\n  maven: null\n" to "publish.maven",
            "publish:\n  modrinth: null\n" to "publish.modrinth",
            "publish:\n  curseforge: null\n" to "publish.curseforge",
            "publish:\n  maven:\n    url: https://maven.example.org/repo\n    unexpected: true\n" to "publish.maven.unexpected",
            "publish:\n  modrinth:\n    projectId: example\n    tokenEnv: MODRINTH_TOKEN\n    unsupported: true\n" to "publish.modrinth.unsupported",
            "publish:\n  curseforge:\n    projectId: 100\n    tokenEnv: CURSEFORGE_TOKEN\n    unsupported: true\n" to "publish.curseforge.unsupported",
        )
        for ((tail, path) in invalid) reject(tail, path)
    }

    @Test
    fun `Maven URL rejects protocols credentials query fragment and malformed host`() {
        for (url in listOf(
            "http://maven.example.org/releases",
            "file:///tmp/repository",
            "https://user:pass@maven.example.org/releases",
            "https://maven.example.org/releases?token=placeholder",
            "https://maven.example.org/releases#secret",
            "https:///releases",
            "https://:443/releases",
        )) {
            reject("publish:\n  maven:\n    url: \"$url\"\n",
                "publish.maven.url", basic("group: org.example"))
        }
    }

    @Test
    fun `Maven credentialsFromEnv requires exactly two valid environment variable names`() {
        for (pair in listOf(
            "[]",
            "[ONLY_ONE]",
            "[A, B, C]",
            "[GITHUB_TOKEN, GITHUB_TOKEN]",
            "[1STARTS_WITH_DIGIT, GITHUB_TOKEN]",
            "[BAD-NAME, GITHUB_TOKEN]",
            "[GITHUB_ACTOR, \"has whitespace\"]",
            "[GITHUB_ACTOR, null]",
        )) {
            reject("publish:\n  maven:\n    url: https://maven.example.org/releases\n    credentialsFromEnv: $pair\n",
                "publish.maven.credentialsFromEnv", basic("group: org.example"))
        }
    }

    @Test
    fun `Modrinth project ID must be a nonblank string and platform token env name must be safe`() {
        for (value in listOf("123456", "\"\"", "null", "[]")) {
            reject("publish:\n  modrinth:\n    projectId: $value\n    tokenEnv: MODRINTH_TOKEN\n",
                "publish.modrinth.projectId")
        }
        for (value in listOf("\"TOKEN WITH SPACE\"", "1BAD", "BAD-DASH", "null")) {
            reject("publish:\n  modrinth:\n    projectId: sample-project\n    tokenEnv: $value\n",
                "publish.modrinth.tokenEnv")
        }
    }

    @Test
    fun `CurseForge project ID must be a positive integer and token env name must be safe`() {
        for (value in listOf("0", "-5", "1.5", "\"123456\"", "null", "[]")) {
            reject("publish:\n  curseforge:\n    projectId: $value\n    tokenEnv: CURSEFORGE_TOKEN\n",
                "publish.curseforge.projectId")
        }
        for (value in listOf("\"TOKEN WITH SPACE\"", "1BAD", "BAD-DASH", "null")) {
            reject("publish:\n  curseforge:\n    projectId: 123456\n    tokenEnv: $value\n",
                "publish.curseforge.tokenEnv")
        }
    }

    @Test
    fun `release changelog loader environment and game version selectors are strictly typed`() {
        val invalid = listOf(
            "modrinth:\n    projectId: sample\n    tokenEnv: MR_TOKEN\n    releaseType: snapshot" to "publish.modrinth.releaseType",
            "modrinth:\n    projectId: sample\n    tokenEnv: MR_TOKEN\n    gameVersions: []" to "publish.modrinth.gameVersions",
            "modrinth:\n    projectId: sample\n    tokenEnv: MR_TOKEN\n    gameVersions: [26.3]" to "publish.modrinth.gameVersions",
            "modrinth:\n    projectId: sample\n    tokenEnv: MR_TOKEN\n    loaders: []" to "publish.modrinth.loaders",
            "modrinth:\n    projectId: sample\n    tokenEnv: MR_TOKEN\n    environment: everywhere" to "publish.modrinth.environment",
            "curseforge:\n    projectId: 123456\n    tokenEnv: CF_TOKEN\n    releaseType: nightly" to "publish.curseforge.releaseType",
            "curseforge:\n    projectId: 123456\n    tokenEnv: CF_TOKEN\n    changelogType: yaml" to "publish.curseforge.changelogType",
            "curseforge:\n    projectId: 123456\n    tokenEnv: CF_TOKEN\n    manualRelease: yes-please" to "publish.curseforge.manualRelease",
            "curseforge:\n    projectId: 123456\n    tokenEnv: CF_TOKEN\n    gameVersionNames: []" to "publish.curseforge.gameVersionNames",
            "curseforge:\n    projectId: 123456\n    tokenEnv: CF_TOKEN\n    gameVersions: [0]" to "publish.curseforge.gameVersions",
            "curseforge:\n    projectId: 123456\n    tokenEnv: CF_TOKEN\n    gameVersions: [\"7499\"]" to "publish.curseforge.gameVersions",
        )
        for ((body, path) in invalid) reject("publish:\n  " + body.replace("\n", "\n  ") + "\n", path)
    }

    @Test
    fun `platform dependency and relation IDs and types cannot silently become Loader dependencies`() {
        val invalid = listOf(
            "modrinth:\n    projectId: sample\n    tokenEnv: MR_TOKEN\n    dependencies:\n      - projectId: required\n        type: unsupported" to "publish.modrinth.dependencies",
            "modrinth:\n    projectId: sample\n    tokenEnv: MR_TOKEN\n    dependencies:\n      - type: required" to "publish.modrinth.dependencies",
            "curseforge:\n    projectId: 123456\n    tokenEnv: CF_TOKEN\n    relations:\n      - slug: dependency\n        type: required" to "publish.curseforge.relations",
            "curseforge:\n    projectId: 123456\n    tokenEnv: CF_TOKEN\n    relations:\n      - slug: dependency\n        type: requiredDependency\n        inlineToken: unsafe" to "publish.curseforge.relations",
        )
        for ((body, path) in invalid) reject("publish:\n  " + body.replace("\n", "\n  ") + "\n", path)
    }

    @Test
    fun `changelog text and file are mutually exclusive for both release platforms`() {
        Files.writeString(project.resolve("CHANGELOG.md"), "Fixture release\n")
        for ((section, header) in listOf(
            "modrinth" to "projectId: sample\n    tokenEnv: MR_TOKEN",
            "curseforge" to "projectId: 123456\n    tokenEnv: CF_TOKEN",
        )) {
            reject("publish:\n  $section:\n    $header\n" +
                "    changelog: \"inline notes\"\n    changelogFile: CHANGELOG.md\n", "publish.$section")
        }
    }

    @Test
    fun `changelog file accepts contained nested file but rejects parent and absolute paths`() {
        Files.createDirectories(project.resolve("docs"))
        Files.writeString(project.resolve("docs/CHANGELOG.md"), "Fixture release\n")
        for ((section, header) in listOf(
            "modrinth" to "projectId: sample\n    tokenEnv: MR_TOKEN",
            "curseforge" to "projectId: 123456\n    tokenEnv: CF_TOKEN",
        )) {
            val valid = "publish:\n  $section:\n    $header\n    changelogFile: docs/CHANGELOG.md\n"
            assertEquals("fabric/mod", ModYamlLoader.load(YamlFixtures.write(project, basic() + valid)).product)
            for (candidate in listOf("../outside.md", project.resolve("docs/CHANGELOG.md").toAbsolutePath().toString())) {
                val bad = valid.replace("docs/CHANGELOG.md", candidate.replace('\\', '/'))
                reject(bad, "publish.$section.changelogFile")
            }
        }
    }

    @Test
    fun `changelog symlink cannot escape consumer project even when target exists`() {
        val consumer = Files.createDirectory(project.resolve("consumer"))
        val outside = Files.writeString(project.resolve("outside.md"), "Outside consumer root\n")
        val link = consumer.resolve("CHANGELOG.md")
        try {
            Files.createSymbolicLink(link, outside)
        } catch (_: UnsupportedOperationException) {
            assumeTrue(false, "Symlinks not supported by this filesystem")
        } catch (_: SecurityException) {
            assumeTrue(false, "Symlinks unavailable in restricted environment")
        } catch (_: java.nio.file.FileSystemException) {
            assumeTrue(false, "Symlink privileges not granted")
        }
        for ((section, header) in listOf(
            "modrinth" to "projectId: sample\n    tokenEnv: MR_TOKEN",
            "curseforge" to "projectId: 123456\n    tokenEnv: CF_TOKEN",
        )) {
            reject("publish:\n  $section:\n    $header\n    changelogFile: CHANGELOG.md\n",
                "publish.$section.changelogFile", root = consumer)
        }
    }
}
