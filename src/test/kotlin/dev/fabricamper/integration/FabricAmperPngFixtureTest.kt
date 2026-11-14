package dev.fabricamper.integration

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import javax.imageio.ImageIO

/** Fast, offline regression for the PNG bytes used by G24. No GradleRunner or Minecraft. */
class FabricAmperPngFixtureTest {
    @Test
    fun `G24 sample PNG decodes and every chunk CRC matches`() {
        val png = TestKitFixture.onePixelPng
        val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        assertTrue(png.size > signature.size)
        assertArrayEquals(signature, png.copyOfRange(0, 8), "Invalid PNG signature")

        val image = ImageIO.read(ByteArrayInputStream(png))
        assertNotNull(image, "G24 icon must be readable by ImageIO")
        assertEquals(1, image!!.width)
        assertEquals(1, image.height)
        assertEquals(0xFF336699.toInt(), image.getRGB(0, 0))

        val chunks = ByteBuffer.wrap(png).order(ByteOrder.BIG_ENDIAN)
        chunks.position(8)
        val seen = mutableListOf<String>()
        while (chunks.remaining() >= 12) {
            val length = chunks.int
            assertTrue(length >= 0 && length <= chunks.remaining() - 8, "Invalid PNG chunk length")
            val crcStart = chunks.position()
            val typeBytes = ByteArray(4)
            chunks.get(typeBytes)
            val type = String(typeBytes, StandardCharsets.US_ASCII)
            chunks.position(chunks.position() + length)
            val storedCrc = chunks.int.toLong() and 0xFFFF_FFFFL
            val computedCrc = CRC32().apply { update(png, crcStart, 4 + length) }.value
            assertEquals(computedCrc, storedCrc, "Invalid PNG CRC for $type")
            seen += type
        }
        assertEquals(0, chunks.remaining(), "Trailing bytes after PNG chunks")
        assertEquals("IHDR", seen.firstOrNull())
        assertTrue("IDAT" in seen, "Missing compressed image data")
        assertEquals("IEND", seen.lastOrNull())
    }
}
