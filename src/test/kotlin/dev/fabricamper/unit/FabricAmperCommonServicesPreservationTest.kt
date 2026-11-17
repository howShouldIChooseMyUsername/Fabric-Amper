package dev.fabricamper.unit

import dev.fabricamper.plugin.UnpackFabricAmperCommon
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Offline ProjectBuilder coverage for the real common-JAR unpack task. */
class FabricAmperCommonServicesPreservationTest {
    @TempDir lateinit var root: Path

    @Test
    fun `keeps common service providers and ordinary META-INF resources`() {
        val service = "com.example.impl.CommonProvider\n".toByteArray(StandardCharsets.UTF_8)
        val license = "Common license\n".toByteArray(StandardCharsets.UTF_8)
        val jar = archive(
            "META-INF/services/com.example.Service" to service,
            "META-INF/native-image/com.example/common/reflect-config.json" to "[]".toByteArray(),
            "META-INF/LICENSE" to license,
            "com/example/Common.class" to byteArrayOf(0, 1, 2, 3),
        )

        val output = unpack(jar)
        assertArrayEquals(service, Files.readAllBytes(output.resolve("META-INF/services/com.example.Service")))
        assertArrayEquals(license, Files.readAllBytes(output.resolve("META-INF/LICENSE")))
        assertTrue(Files.isRegularFile(output.resolve("META-INF/native-image/com.example/common/reflect-config.json")))
        assertArrayEquals(byteArrayOf(0, 1, 2, 3), Files.readAllBytes(output.resolve("com/example/Common.class")))
    }

    @Test
    fun `strips manifest signing and loader metadata but retains services`() {
        val jar = archive(
            "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\n".toByteArray(),
            "META-INF/OLD.SF" to byteArrayOf(1),
            "META-INF/old.RSA" to byteArrayOf(1),
            "META-INF/OLD.DSA" to byteArrayOf(1),
            "META-INF/OLD.EC" to byteArrayOf(1),
            "META-INF/SIG-OLD" to byteArrayOf(1),
            "fabric.mod.json" to "{}".toByteArray(),
            "quilt.mod.json" to "{}".toByteArray(),
            "META-INF/neoforge.mods.toml" to "license=\"MIT\"".toByteArray(),
            "META-INF/mods.toml" to "license=\"MIT\"".toByteArray(),
            "META-INF/jarjar/metadata.json" to "{}".toByteArray(),
            "META-INF/services/com.example.Service" to "com.example.Provider\n".toByteArray(),
        )

        val output = unpack(jar)
        for (excluded in listOf(
            "META-INF/MANIFEST.MF", "META-INF/OLD.SF", "META-INF/old.RSA",
            "META-INF/OLD.DSA", "META-INF/OLD.EC", "META-INF/SIG-OLD",
            "fabric.mod.json", "quilt.mod.json", "META-INF/neoforge.mods.toml",
            "META-INF/mods.toml", "META-INF/jarjar/metadata.json",
        )) {
            assertFalse(Files.exists(output.resolve(excluded)), "foreign metadata leaked: $excluded")
        }
        assertTrue(Files.isRegularFile(output.resolve("META-INF/services/com.example.Service")))
    }

    @Test
    fun `rejects traversal malformed paths and case insensitive collisions`() {
        for (unsafe in listOf("../outside.txt", "a/../outside.txt", "a//outside.txt", "a\\outside.txt", "C:/outside.txt")) {
            val failure = assertThrows(GradleException::class.java) {
                unpack(archive(unsafe to byteArrayOf(1)))
            }
            assertTrue(failure.message.orEmpty().contains("FA013"), "unsafe path $unsafe: ${failure.message}")
        }

        val collision = archive(
            "META-INF/services/com.example.Service" to byteArrayOf(1),
            "meta-inf/services/COM.EXAMPLE.SERVICE" to byteArrayOf(2),
        )
        val failure = assertThrows(GradleException::class.java) { unpack(collision) }
        assertTrue(failure.message.orEmpty().contains("FA013"), failure.message)
    }

    @Test
    fun `rejects bytewise duplicate ZIP entry names`() {
        // ZipOutputStream rejects duplicate names, so make a valid archive with
        // equal-length entries and rewrite both local and central ZIP records.
        val jar = archive(
            "common/a.txt" to byteArrayOf(1),
            "common/b.txt" to byteArrayOf(2),
        )
        val bytes = Files.readAllBytes(jar)
        val from = "common/b.txt".toByteArray(StandardCharsets.US_ASCII)
        val to = "common/a.txt".toByteArray(StandardCharsets.US_ASCII)
        var replacements = 0
        for (offset in 0..bytes.size - from.size) {
            if (from.indices.all { index -> bytes[offset + index] == from[index] }) {
                to.copyInto(bytes, offset)
                replacements++
            }
        }
        assertTrue(replacements == 2, "ZIP fixture must rewrite local and central filenames")
        Files.write(jar, bytes)

        val failure = assertThrows(GradleException::class.java) { unpack(jar) }
        assertTrue(failure.message.orEmpty().contains("FA013"), failure.message)
    }

    private fun archive(vararg entries: Pair<String, ByteArray>): Path {
        val jar = root.resolve("common.jar")
        ZipOutputStream(Files.newOutputStream(jar)).use { output ->
            for ((name, bytes) in entries) {
                output.putNextEntry(ZipEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return jar
    }

    private fun unpack(jar: Path): Path {
        val projectDir = root.resolve("consumer")
        Files.createDirectories(projectDir)
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        val task = project.tasks.create("unpackCommon", UnpackFabricAmperCommon::class.java)
        val output = root.resolve("unpacked")
        task.commonArchive.from(jar.toFile())
        task.classesDirectory.set(output.toFile())
        task.unpack()
        return output
    }
}
