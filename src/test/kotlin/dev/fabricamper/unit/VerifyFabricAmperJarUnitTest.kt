package dev.fabricamper.unit

import dev.fabricamper.generator.VerifyFabricAmperJar
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Offline FA013 checks of the finished-archive verifier; no Gradle or Minecraft download. */
class VerifyFabricAmperJarUnitTest {
    @TempDir lateinit var project: Path

    private fun jarOf(vararg entries: Pair<String, String>): Path = jarOfBytes(
        *entries.map { (name, contents) ->
            name to contents.toByteArray(StandardCharsets.UTF_8)
        }.toTypedArray(),
    )

    private fun jarOfBytes(vararg entries: Pair<String, ByteArray>): Path {
        val path = project.resolve("fixture.jar")
        ZipOutputStream(Files.newOutputStream(path)).use { archive ->
            entries.forEach { (name, bytes) ->
                archive.putNextEntry(ZipEntry(name))
                archive.write(bytes)
                archive.closeEntry()
            }
        }
        return path
    }

    private fun nestedJarBytes(): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { nested ->
            nested.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            nested.write("Manifest-Version: 1.0\n".toByteArray(StandardCharsets.UTF_8))
            nested.closeEntry()
        }
        output.toByteArray()
    }

    private fun storedNestedJarBytes(): ByteArray = ByteArrayOutputStream().use { output ->
        val payload = "Manifest-Version: 1.0\n".toByteArray(StandardCharsets.UTF_8)
        val entry = ZipEntry("META-INF/MANIFEST.MF").apply {
            method = ZipEntry.STORED
            size = payload.size.toLong()
            compressedSize = payload.size.toLong()
            crc = CRC32().apply { update(payload) }.value
        }
        ZipOutputStream(output).use { nested ->
            nested.putNextEntry(entry)
            nested.write(payload)
            nested.closeEntry()
        }
        output.toByteArray()
    }

    private fun byteOffset(bytes: ByteArray, sought: ByteArray): Int =
        (0..bytes.size - sought.size).firstOrNull { offset ->
            sought.indices.all { i -> bytes[offset + i] == sought[i] }
        } ?: -1

    private fun nestedArchive(metadata: String, bytes: ByteArray) = jarOfBytes(
        "fabric.mod.json" to metadata.toByteArray(StandardCharsets.UTF_8),
        "META-INF/jars/lib.jar" to bytes,
    )

    private fun metadataWithJars(jarsJson: String): String =
        """{"schemaVersion":1,"id":"sample_mod","version":"1.0","jars":$jarsJson}"""

    private fun assertFa013(location: String, path: Path) {
        val failure = YamlFixtures.generatorError("FA013") {
            VerifyFabricAmperJar.verifyJar(path)
        }
        assertEquals(location, failure.diagnostic.location)
    }

    /** ZipOutputStream forbids duplicate names; rewrite both ZIP name records. */
    private fun jarWithDuplicateRootMetadata(first: String, second: String): Path {
        val originalName = "fabric.mod.jsoX".toByteArray(StandardCharsets.US_ASCII)
        val duplicateName = "fabric.mod.json".toByteArray(StandardCharsets.US_ASCII)
        assertEquals(originalName.size, duplicateName.size)
        val zip = jarOf(
            "fabric.mod.json" to first,
            "fabric.mod.jsoX" to second,
        )
        val bytes = Files.readAllBytes(zip)
        var replacements = 0
        for (offset in 0..bytes.size - originalName.size) {
            if (originalName.indices.all { i -> bytes[offset + i] == originalName[i] }) {
                duplicateName.copyInto(bytes, destinationOffset = offset)
                replacements++
            }
        }
        assertEquals(2, replacements, "must rewrite local and central directory names")
        Files.write(zip, bytes)
        return zip
    }

    @Test
    fun `jar without root metadata is rejected even if subdirectory metadata exists`() {
        assertFa013("fabric.mod.json", jarOf(
            "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\n",
            "subdirectory/fabric.mod.json" to "{}",
        ))
    }

    @Test
    fun `exactly one root metadata document is accepted`() {
        val jar = jarOf(
            "fabric.mod.json" to """{"schemaVersion":1,"id":"sample_mod","version":"1.0.0"}""",
            "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\n",
        )
        assertDoesNotThrow { VerifyFabricAmperJar.verifyJar(jar) }
    }

    @Test
    fun `malformed root metadata JSON fails FA013 instead of throwing parser details`() {
        assertFa013("fabric.mod.json", jarOf("fabric.mod.json" to """{"jars":[}"""))
    }

    @Test
    fun `nonobject root metadata JSON fails FA013`() {
        assertFa013("fabric.mod.json", jarOf("fabric.mod.json" to "[]"))
    }

    @Test
    fun `unreadable ZIP archive fails FA013`() {
        val path = project.resolve("malformed.jar")
        Files.writeString(path, "not a ZIP file", StandardCharsets.UTF_8)
        assertFa013("jar", path)
    }

    @Test
    fun `declared nested jar is accepted only when its exact path exists`() {
        val metadata = metadataWithJars("""[{"file":"META-INF/jars/lib.jar"}]""")
        val valid = jarOfBytes(
            "fabric.mod.json" to metadata.toByteArray(StandardCharsets.UTF_8),
            "META-INF/jars/lib.jar" to nestedJarBytes(),
        )
        assertDoesNotThrow { VerifyFabricAmperJar.verifyJar(valid) }

        val missing = jarOf("fabric.mod.json" to metadata)
        assertFa013("fabric.mod.json.jars[0].file", missing)
    }

    @Test
    fun `nested jar reference with non-ZIP bytes fails FA013`() {
        val metadata = metadataWithJars("""[{"file":"META-INF/jars/lib.jar"}]""")
        val invalid = jarOf(
            "fabric.mod.json" to metadata,
            "META-INF/jars/lib.jar" to "not a ZIP archive",
        )
        assertFa013("fabric.mod.json.jars[0].file", invalid)
    }

    @Test
    fun `nested local ZIP header without central directory and EOCD fails FA013`() {
        val original = nestedJarBytes()
        val centralDirectory = byteOffset(original, byteArrayOf(0x50, 0x4b, 0x01, 0x02))
        assertTrue(centralDirectory > 0, "valid ZIP fixture must contain a central directory")
        val incomplete = original.copyOf(centralDirectory)
        val metadata = metadataWithJars("""[{"file":"META-INF/jars/lib.jar"}]""")
        assertFa013("fabric.mod.json.jars[0].file", nestedArchive(metadata, incomplete))
    }

    @Test
    fun `nested ZIP with corrupted stored-entry CRC fails FA013`() {
        val original = storedNestedJarBytes()
        val corrupted = original.copyOf()
        val payload = "Manifest-Version: 1.0\n".toByteArray(StandardCharsets.UTF_8)
        val payloadOffset = byteOffset(corrupted, payload)
        assertTrue(payloadOffset > 0, "stored ZIP fixture must contain uncompressed payload")
        corrupted[payloadOffset] = (corrupted[payloadOffset].toInt() xor 1).toByte()
        val metadata = metadataWithJars("""[{"file":"META-INF/jars/lib.jar"}]""")
        assertFa013("fabric.mod.json.jars[0].file", nestedArchive(metadata, corrupted))
    }

    @Test
    fun `root metadata missing mandatory Loader schema id or version is rejected`() {
        for (metadata in listOf(
            "{}",
            """{"id":"sample_mod","version":"1.0"}""",
            """{"schemaVersion":1,"version":"1.0"}""",
            """{"schemaVersion":1,"id":"sample_mod"}""",
            """{"schemaVersion":"1","id":"sample_mod","version":"1.0"}""",
            """{"schemaVersion":2,"id":"sample_mod","version":"1.0"}""",
            """{"schemaVersion":1.5,"id":"sample_mod","version":"1.0"}""",
            """{"schemaVersion":1,"id":"","version":"1.0"}""",
        )) {
            assertFa013("fabric.mod.json", jarOf("fabric.mod.json" to metadata))
        }
    }

    @Test
    fun `invalid UTF8 in root Loader metadata is rejected as FA013`() {
        val corruptedUtf8 = byteArrayOf(0x7b, 0x22, 0xc3.toByte(), 0x28, 0x22, 0x3a, 0x31, 0x7d)
        val zip = jarOfBytes("fabric.mod.json" to corruptedUtf8)
        YamlFixtures.generatorError("FA013") { VerifyFabricAmperJar.verifyJar(zip) }
    }

    @Test
    fun `trailing document after root JSON is rejected as FA013`() {
        val zip = jarOf("fabric.mod.json" to
            """{"schemaVersion":1,"id":"sample_mod","version":"1.0"} {"extra":true}""")
        assertFa013("fabric.mod.json", zip)
    }

    @Test
    fun `two physical root metadata ZIP entries trigger FA013`() {
        val metadata = """{"schemaVersion":1,"id":"sample_mod","version":"1.0"}"""
        val zip = jarWithDuplicateRootMetadata(metadata, metadata)
        assertFa013("fabric.mod.json", zip)
    }

    @Test
    fun `duplicate root entry fails before accepting a valid document or a malformed duplicate`() {
        val metadata = """{"schemaVersion":1,"id":"sample_mod","version":"1.0"}"""
        val zip = jarWithDuplicateRootMetadata(metadata, "{invalid-json")
        val original = Files.readAllBytes(zip)
        assertFa013("fabric.mod.json", zip)
        assertTrue(original.contentEquals(Files.readAllBytes(zip)),
            "archive verification must not rewrite duplicate ZIP content")
    }

    @Test
    fun `incorrect jars field type fails at jars location`() {
        for (jars in listOf("true", "{}", "\"META-INF/jars/lib.jar\"")) {
            assertFa013("fabric.mod.json.jars", jarOf("fabric.mod.json" to metadataWithJars(jars)))
        }
    }

    @Test
    fun `nested jars entries require objects with string file paths`() {
        for (item in listOf("null", "17", "\"lib.jar\"", "{}", "{\"file\":null}", "{\"file\":25}")) {
            assertFa013("fabric.mod.json.jars[0].file", jarOf("fabric.mod.json" to metadataWithJars("[$item]")))
        }
    }

    @Test
    fun `nested jar references reject noncanonical and traversing paths`() {
        val malformed = listOf(
            "../lib.jar", "META-INF/../lib.jar", "./lib.jar", "/absolute.jar",
            "C:/lib.jar", "META-INF//lib.jar", "META-INF\\lib.jar", "",
        )
        for (name in malformed) {
            val escapedName = name.replace("\\", "\\\\")
            val zip = jarOf("fabric.mod.json" to metadataWithJars("""[{"file":"$escapedName"}]"""))
            val error = YamlFixtures.generatorError("FA013") {
                VerifyFabricAmperJar.verifyJar(zip)
            }
            assertEquals("fabric.mod.json.jars[0].file", error.diagnostic.location, name)
        }
    }

    @Test
    fun `same nested jar referenced twice fails even if archive has one copy`() {
        val zip = jarOfBytes(
            "fabric.mod.json" to metadataWithJars("""[{"file":"META-INF/jars/lib.jar"},{"file":"META-INF/jars/lib.jar"}]""")
                .toByteArray(StandardCharsets.UTF_8),
            "META-INF/jars/lib.jar" to nestedJarBytes(),
        )
        val error = YamlFixtures.generatorError("FA013") { VerifyFabricAmperJar.verifyJar(zip) }
        assertEquals("fabric.mod.json.jars[1].file", error.diagnostic.location)
        assertTrue(error.message!!.contains("fabric.mod.json.jars[1].file"))
    }
}
