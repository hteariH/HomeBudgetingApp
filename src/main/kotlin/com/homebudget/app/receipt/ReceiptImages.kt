package com.homebudget.app.receipt

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.roundToInt

/** The upload is not a photo this server can read (HEIC, GIF, PDF, a broken file...). */
class UnsupportedImageException : RuntimeException("Send the photo as JPEG or PNG")

/**
 * Turns an uploaded photo into the JPEG that is kept and sent to the model, and keeps
 * it on disk under `<app.data-dir>/receipts/<household uuid>/<receipt uuid>.jpg`.
 *
 * The photo is decoded and drawn into a fresh image, so nothing from the original file -
 * EXIF location, camera serial, embedded thumbnails - survives into what is stored.
 */
@Component
class ReceiptImages(@Value("\${app.data-dir:/data}") dataDir: String) {

    companion object {
        const val MAX_SIDE = 1600
        const val QUALITY = 0.85f
        private val ACCEPTED_FORMATS = setOf("jpeg", "jpg", "png", "webp")
    }

    private val log = LoggerFactory.getLogger(javaClass)
    private val root: Path = Path.of(dataDir).toAbsolutePath().normalize()

    init {
        // Inside a Spring Boot jar the WebP reader sits in a nested jar that ImageIO's
        // first scan (with the system class loader) does not see.
        ImageIO.scanForPlugins()
    }

    /** Compresses [upload] and writes it; returns the path to store on the receipt. */
    fun store(upload: ByteArray, householdUuid: String, receiptUuid: String): String {
        val jpeg = compress(upload)
        val relative = "receipts/$householdUuid/$receiptUuid.jpg"
        val file = resolve(relative)
        Files.createDirectories(file.parent)
        Files.write(file, jpeg)
        return relative
    }

    fun read(relative: String): ByteArray? {
        val file = resolve(relative)
        return if (Files.isRegularFile(file)) Files.readAllBytes(file) else null
    }

    fun delete(relative: String) {
        try {
            Files.deleteIfExists(resolve(relative))
        } catch (e: IOException) {
            // A file left behind is wasted disk, not lost data; the row is gone either way.
            log.warn("Could not delete a receipt photo: {}", e.javaClass.simpleName)
        }
    }

    fun resolve(relative: String): Path {
        val file = root.resolve(relative).normalize()
        check(file.startsWith(root)) { "Receipt photo path outside the data directory" }
        return file
    }

    /** JPEG, PNG or WebP in; a JPEG at most [MAX_SIDE] px on its longest side out. */
    fun compress(upload: ByteArray): ByteArray {
        val source = decode(upload)
        val scale = minOf(1.0, MAX_SIDE.toDouble() / max(source.width, source.height))
        val width = max(1, (source.width * scale).roundToInt())
        val height = max(1, (source.height * scale).roundToInt())

        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = target.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            // Transparent PNG areas become white paper rather than JPEG black.
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
            g.drawImage(source, 0, 0, width, height, null)
        } finally {
            g.dispose()
        }
        return encode(target)
    }

    private fun decode(upload: ByteArray): BufferedImage {
        val input = ImageIO.createImageInputStream(ByteArrayInputStream(upload)) ?: throw UnsupportedImageException()
        input.use {
            val reader = ImageIO.getImageReaders(input).asSequence()
                .firstOrNull { it.formatName.lowercase() in ACCEPTED_FORMATS }
                ?: throw UnsupportedImageException()
            try {
                reader.setInput(input, true, true)
                val longest = max(reader.getWidth(0), reader.getHeight(0))
                val param = reader.defaultReadParam
                // A huge photo is decoded at a fraction of its size so memory stays bounded;
                // it is scaled down to MAX_SIDE afterwards anyway.
                val step = max(1, longest / (MAX_SIDE * 2))
                if (step > 1) param.setSourceSubsampling(step, step, 0, 0)
                return reader.read(0, param) ?: throw UnsupportedImageException()
            } catch (e: IOException) {
                throw UnsupportedImageException()
            } catch (e: RuntimeException) {
                if (e is UnsupportedImageException) throw e
                // Decoders throw all sorts on a truncated or hostile file.
                throw UnsupportedImageException()
            } finally {
                reader.dispose()
            }
        }
    }

    private fun encode(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = QUALITY
                }
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }
}
