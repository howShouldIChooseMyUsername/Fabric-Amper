package dev.fabricamper.unit

import dev.fabricamper.config.StrictYamlReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class StrictYamlReaderUnitTest {
    @TempDir lateinit var project: Path

    private fun parse(input: String): Map<String, Any?> =
        StrictYamlReader.read(input, project.resolve("mod.yaml"))

    @Test
    fun `preserves quoted version and boolean versus integer scalar types`() {
        val yaml = parse(
            """
            schema: 1
            version: "26.3"
            enabled: true
            release: 25
            empty: null
            list: ["1.0", 2, false]
            """.trimIndent(),
        )
        assertEquals(1, yaml["schema"])
        assertEquals("26.3", yaml["version"])
        assertEquals(true, yaml["enabled"])
        assertEquals(25, yaml["release"])
        assertEquals(null, yaml["empty"])
        assertEquals(listOf("1.0", 2, false), yaml["list"])
    }

    @Test
    fun `rejects malformed UTF8 rather than replacing invalid bytes`() {
        YamlFixtures.configError("FA002") {
            StrictYamlReader.readBytes(
                byteArrayOf(0x61, 0x3a, 0x20, 0xc3.toByte(), 0x28),
                project.resolve("mod.yaml"),
            )
        }
    }

    @Test
    fun `rejects UTF8 BOM and files exceeding 1 MiB`() {
        YamlFixtures.configError("FA002") {
            StrictYamlReader.readBytes(
                byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) +
                    "schema: 1".toByteArray(),
                project.resolve("mod.yaml"),
            )
        }
        YamlFixtures.configError("FA002") {
            StrictYamlReader.readBytes(
                ByteArray(StrictYamlReader.MAX_FILE_BYTES + 1) { 'x'.code.toByte() },
                project.resolve("mod.yaml"),
            )
        }
    }

    @Test
    fun `rejects duplicate keys including nested duplicates`() {
        YamlFixtures.configError("FA002") { parse("schema: 1\nschema: 2\n") }
        YamlFixtures.configError("FA002") {
            parse("mod:\n  id: first\n  id: second\n")
        }
    }

    @Test
    fun `rejects aliases including scalar aliases and anchors without aliases`() {
        YamlFixtures.configError("FA002") {
            parse("key: &shared value\nother: *shared\n")
        }
        YamlFixtures.configError("FA002") {
            parse("key: &unused value\n")
        }
        YamlFixtures.configError("FA002") {
            parse("mapping: &shared\n  a: b\nother: *shared\n")
        }
    }

    @Test
    fun `rejects explicit scalar and collection tags`() {
        YamlFixtures.configError("FA002") { parse("key: !!str value\n") }
        YamlFixtures.configError("FA002") { parse("key: !custom tag\n") }
        YamlFixtures.configError("FA002") { parse("key: !!seq [1, 2]\n") }
    }

    @Test
    fun `rejects merge keys and multiple YAML documents`() {
        YamlFixtures.configError("FA002") {
            parse("defaults: {x: 1}\nmerged:\n  <<: {x: 1}\n")
        }
        YamlFixtures.configError("FA002") {
            parse("---\nschema: 1\n---\nschema: 2\n")
        }
        YamlFixtures.configError("FA002") { parse("---\n") }
    }

    @Test
    fun `rejects non mapping root and non string keys`() {
        YamlFixtures.configError("FA002") { parse("- one\n- two\n") }
        YamlFixtures.configError("FA002") { parse("1: number-key\n") }
    }

    @Test
    fun `rejects nested YAML beyond depth limit`() {
        val nested = buildString {
            repeat(40) { index -> append("  ".repeat(index)).append("k$index:\n") }
            append("  ".repeat(40)).append("value: yes\n")
        }
        YamlFixtures.configError("FA002") { parse(nested) }
    }

    @Test
    fun `ordinary strings containing ampersand exclamation or merge text are legal`() {
        val value = parse(
            "first: \"A & B ! ok\"\nsecond: \"literal << text\"\n",
        )
        assertEquals("A & B ! ok", value["first"])
        assertTrue((value["second"] as String).contains("<<"))
    }
}
