package dev.fabricamper.unit

import dev.fabricamper.util.PlatformReleaseClient
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure local multipart filename validation. Never invoke upload APIs or HTTP.
 * Exercise the same preflight function consumed by upload(), not a copy of its regex.
 */
class PlatformReleaseJarFilenameUnitTest {
    @Test
    fun `Gradle generated archive names with punctuation and leading markers remain valid`() {
        for (filename in listOf(
            "_sample-1.jar",
            "-sample-1.jar",
            ".sample-1.jar",
            "sample-1.0.0+26.3.jar",
            "sample-1.0.0+build.3-rc_5.jar",
            "example.mod-1.0.0+26.3.jar",
            "sample 1.0 (candidate).jar",
            "a".repeat(181) + ".jar", // 185-char maximum leaf name
        )) {
            assertTrue(PlatformReleaseClient.isSafeReleaseJarFilename(filename),
                "A valid final Gradle archive must not be rejected: $filename")
        }
    }

    @Test
    fun `multipart filename rejects path separators traversal and header injection`() {
        for (filename in listOf(
            "",
            ".jar",
            "..jar",
            "...jar",
            "../sample-1.jar",
            "./sample-1.jar",
            "folder/sample-1.jar",
            "folder\\sample-1.jar",
            "..\\sample-1.jar",
            "sample\"injected.jar",
            "sample\rheader.jar",
            "sample\nheader.jar",
            "sample\u0000header.jar",
            "sample\u007fheader.jar",
            "sample\tname.jar",
        )) {
            assertFalse(PlatformReleaseClient.isSafeReleaseJarFilename(filename),
                "Unsafe multipart filename must be rejected: ${filename.toList().map { it.code }}")
        }
    }

    @Test
    fun `only main release jars are valid not dev sources or javadoc artifacts`() {
        for (filename in listOf(
            "sample-1-sources.jar",
            "sample-1-dev.jar",
            "sample-1-javadoc.jar",
            "sample-1-SOURCES.jar",
            "sample-1-DEV.jar",
            "sample-1-JAVADOC.jar",
            "sample-1.zip",
            "sample-1.jar.zip",
            "sample-1.JAR",
            "a".repeat(182) + ".jar", // above maximum leaf-name length
        )) {
            assertFalse(PlatformReleaseClient.isSafeReleaseJarFilename(filename),
                "Main-release file filter must reject: $filename")
        }
    }
}
