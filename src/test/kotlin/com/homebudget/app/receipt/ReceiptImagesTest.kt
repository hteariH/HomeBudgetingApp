package com.homebudget.app.receipt

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO

/** Pictures in, the stored JPEG out. */
class ReceiptImagesTest {

    @TempDir lateinit var dataDir: Path

    private fun picture(width: Int, height: Int, format: String): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.color = Color.BLACK
        g.fillRect(width / 4, height / 4, width / 2, height / 2)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
    }

    /** A JPEG carrying an EXIF segment, the way a phone camera writes one. */
    private fun jpegWithExif(): ByteArray {
        val plain = picture(400, 300, "jpeg")
        val payload = "Exif\u0000\u0000MM\u0000*GPS-LOCATION-SECRET".toByteArray(Charsets.ISO_8859_1)
        val length = payload.size + 2
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        // SOI, then the APP1 segment, then the rest of the original file.
        return plain.copyOfRange(0, 2) + segment + plain.copyOfRange(2, plain.size)
    }

    /**
     * A 400x300 JPEG whose EXIF says "turn 90 degrees clockwise" (Orientation = 6), with a
     * black square in the stored top-left corner. The EXIF block is a minimal big-endian
     * TIFF directory written by hand, so the test does not lean on the code it checks.
     */
    private fun jpegTurnedClockwise(): ByteArray {
        val image = BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 400, 300)
        g.color = Color.BLACK
        g.fillRect(0, 0, 60, 60)
        g.dispose()
        val plain = ByteArrayOutputStream().also { ImageIO.write(image, "jpeg", it) }.toByteArray()
        val tiff = byteArrayOf(
            'M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8,  // header, first directory at 8
            0, 1,                                                      // one entry
            0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0,                  // Orientation, SHORT, 1, value 6
            0, 0, 0, 0                                                 // no next directory
        )
        val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
        val length = payload.size + 2
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        return plain.copyOfRange(0, 2) + segment + plain.copyOfRange(2, plain.size)
    }

    private fun dark(image: BufferedImage, x: Int, y: Int) = (image.getRGB(x, y) and 0xFF) < 64

    @Test
    fun `a phone photo is stored upright`() {
        val stored = ImageIO.read(ByteArrayInputStream(ReceiptImages(dataDir.toString()).compress(jpegTurnedClockwise())))
        assertEquals(300, stored.width, "width and height swap for a quarter turn")
        assertEquals(400, stored.height)
        // Turned clockwise, the stored top-left corner ends up top-right.
        assertTrue(dark(stored, 270, 30), "the marker is top-right")
        assertFalse(dark(stored, 30, 30), "and no longer top-left")
    }

    /** A real 1x1 PNG whose header claims [width] x [height]: a few bytes that would decode to gigabytes. */
    private fun pngDeclaring(width: Int, height: Int): ByteArray {
        val png = picture(1, 1, "png")
        val buffer = java.nio.ByteBuffer.wrap(png)
        // Signature (8), IHDR length (4), "IHDR" at 12, width at 16, height at 20, CRC at 29.
        buffer.putInt(16, width)
        buffer.putInt(20, height)
        val crc = java.util.zip.CRC32().apply { update(png, 12, 17) }
        buffer.putInt(29, crc.value.toInt())
        return png
    }

    @Test
    fun `a picture declaring huge dimensions is refused before it is decoded`() {
        val images = ReceiptImages(dataDir.toString())
        for ((w, h) in listOf(30_000 to 10, 10 to 20_001, 10_000 to 6_000)) {
            val e = assertThrows<UnsupportedImageException> { images.store(pngDeclaring(w, h), "h", "r") }
            assertEquals("The photo is too large to read", e.message, "${w}x$h")
        }
        assertFalse(Files.exists(dataDir.resolve("receipts")))
    }

    private fun ByteArray.contains(text: String) = String(this, Charsets.ISO_8859_1).contains(text)

    @Test
    fun `a large PNG is stored as a JPEG no longer than 1600 px`() {
        val images = ReceiptImages(dataDir.toString())
        val relative = images.store(picture(4000, 3000, "png"), "household-1", "receipt-1")

        assertEquals("receipts/household-1/receipt-1.jpg", relative)
        val file = dataDir.resolve("receipts").resolve("household-1").resolve("receipt-1.jpg")
        assertTrue(Files.isRegularFile(file))
        val stored = Files.readAllBytes(file)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte()), stored.copyOfRange(0, 2), "a JPEG")
        val decoded = ImageIO.read(ByteArrayInputStream(stored))
        assertEquals(1600, decoded.width)
        assertEquals(1200, decoded.height)
        assertArrayEquals(stored, images.read(relative))
        // Written through a temporary file and moved into place; nothing else is left.
        assertEquals(listOf("receipt-1.jpg"), Files.list(file.parent).use { s -> s.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun `a small photo keeps its size and loses its metadata`() {
        val images = ReceiptImages(dataDir.toString())
        val upload = jpegWithExif()
        assertTrue(upload.contains("GPS-LOCATION-SECRET"), "the fixture carries EXIF")

        val stored = images.compress(upload)
        val decoded = ImageIO.read(ByteArrayInputStream(stored))
        assertEquals(400, decoded.width)
        assertEquals(300, decoded.height)
        assertFalse(stored.contains("Exif"), "no EXIF segment")
        assertFalse(stored.contains("GPS-LOCATION-SECRET"))
    }

    @Test
    fun `a WebP photo is accepted`() {
        // The 1x1 lossless WebP browsers use to detect support.
        val webp = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==")
        val decoded = ImageIO.read(ByteArrayInputStream(ReceiptImages(dataDir.toString()).compress(webp)))
        assertEquals(1, decoded.width)
        assertEquals(1, decoded.height)
    }

    @Test
    fun `GIF, HEIC and junk are refused with the message the person sees`() {
        val images = ReceiptImages(dataDir.toString())
        val heic = byteArrayOf(0, 0, 0, 0x18) + "ftypheic".toByteArray() + ByteArray(64)
        for (upload in listOf(picture(10, 10, "gif"), heic, "not a picture".toByteArray(), ByteArray(0))) {
            val e = assertThrows<UnsupportedImageException> { images.store(upload, "h", "r") }
            assertEquals("Send the photo as JPEG or PNG", e.message)
        }
        assertFalse(Files.exists(dataDir.resolve("receipts")), "nothing written for a refused upload")
    }

    @Test
    fun `a photo is deleted and a missing one reads as null`() {
        val images = ReceiptImages(dataDir.toString())
        val relative = images.store(picture(20, 20, "png"), "h", "r")
        images.delete(relative)
        assertNull(images.read(relative))
        images.delete(relative) // twice is fine
    }

    @Test
    fun `half-written photos left by a crash are swept away at startup`() {
        val dir = Files.createDirectories(dataDir.resolve("receipts/household"))
        val photo = Files.write(dir.resolve("kept.jpg"), byteArrayOf(1))
        val partial = Files.write(dir.resolve("abc-123.part"), byteArrayOf(2))

        ReceiptImages(dataDir.toString())

        assertTrue(Files.exists(photo))
        assertFalse(Files.exists(partial))
    }

    @Test
    fun `a data directory without receipts starts fine`() {
        ReceiptImages(dataDir.resolve("missing").toString())
    }
}
