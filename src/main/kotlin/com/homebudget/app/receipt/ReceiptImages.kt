package com.homebudget.app.receipt

import com.twelvemonkeys.imageio.metadata.jpeg.JPEG
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegmentUtil
import com.twelvemonkeys.imageio.metadata.tiff.TIFF
import com.twelvemonkeys.imageio.metadata.tiff.TIFFReader
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.roundToInt

/** The upload is not a photo this server can read (HEIC, GIF, PDF, a broken file, a pixel bomb...). */
class UnsupportedImageException(message: String = WRONG_FORMAT) : RuntimeException(message) {
    companion object {
        const val WRONG_FORMAT = "Send the photo as JPEG or PNG"
        const val TOO_LARGE = "The photo is too large to read"
    }
}

/**
 * Turns an uploaded photo into the JPEG that is kept and sent to the model, and keeps
 * it on disk under `<app.data-dir>/receipts/<household uuid>/<receipt uuid>.jpg`.
 *
 * The photo is decoded and drawn into a fresh image, upright, so nothing from the original
 * file - EXIF location, camera serial, embedded thumbnails - survives into what is stored.
 */
@Component
class ReceiptImages(@Value("\${app.data-dir:/data}") dataDir: String) {

    companion object {
        const val MAX_SIDE = 1600
        const val QUALITY = 0.85f

        /**
         * What a photo may declare before anything is decoded. A few hundred bytes of PNG can
         * claim 100000 x 100000 pixels; no phone camera comes near these.
         */
        const val MAX_DECLARED_SIDE = 20_000
        const val MAX_DECLARED_PIXELS = 50_000_000L
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
        // Written beside its final place and moved in, so no reader ever sees half a file.
        val partial = Files.createTempFile(file.parent, "$receiptUuid-", ".part")
        try {
            Files.write(partial, jpeg)
            Files.move(partial, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(partial)
        }
        return relative
    }

    /** Deletes [relative] once the current transaction commits (at once outside one). */
    fun deleteAfterCommit(relative: String) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delete(relative)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = delete(relative)
        })
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
        val (source, format) = decode(upload)
        // Phones store the sensor's pixels and say in EXIF which way is up; the metadata is
        // dropped below, so the turn is applied here or the photo ends up sideways.
        val orientation = if (format == "jpeg" || format == "jpg") exifOrientation(upload) else 1
        val sideways = orientation in 5..8
        val uprightWidth = if (sideways) source.height else source.width
        val uprightHeight = if (sideways) source.width else source.height
        val scale = minOf(1.0, MAX_SIDE.toDouble() / max(uprightWidth, uprightHeight))
        val width = max(1, (uprightWidth * scale).roundToInt())
        val height = max(1, (uprightHeight * scale).roundToInt())

        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = target.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            // Transparent PNG areas become white paper rather than JPEG black.
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
            g.scale(width.toDouble() / uprightWidth, height.toDouble() / uprightHeight)
            g.drawImage(source, uprightTransform(orientation, source.width.toDouble(), source.height.toDouble()), null)
        } finally {
            g.dispose()
        }
        return encode(target)
    }

    /** The EXIF Orientation (1-8) of a JPEG; 1 when there is none or it cannot be read. */
    private fun exifOrientation(upload: ByteArray): Int {
        try {
            ImageIO.createImageInputStream(ByteArrayInputStream(upload)).use { stream ->
                val exif = JPEGSegmentUtil.readSegments(stream, JPEG.APP1, "Exif").firstOrNull() ?: return 1
                val data = exif.data()
                data.read() // the pad byte after the "Exif" identifier
                val directory = ImageIO.createImageInputStream(data).use { TIFFReader().read(it) }
                val value = directory.getEntryById(TIFF.TAG_ORIENTATION)?.value as? Number
                return value?.toInt()?.takeIf { it in 1..8 } ?: 1
            }
        } catch (e: Exception) {
            // Broken EXIF is common; the photo itself decoded fine, so keep it as it is.
            return 1
        }
    }

    /** Maps the stored pixels ([w] x [h]) onto the upright picture for EXIF [orientation]. */
    private fun uprightTransform(orientation: Int, w: Double, h: Double): AffineTransform = when (orientation) {
        2 -> AffineTransform(-1.0, 0.0, 0.0, 1.0, w, 0.0)   // mirrored
        3 -> AffineTransform(-1.0, 0.0, 0.0, -1.0, w, h)    // upside down
        4 -> AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, h)   // mirrored upside down
        5 -> AffineTransform(0.0, 1.0, 1.0, 0.0, 0.0, 0.0)  // transposed
        6 -> AffineTransform(0.0, 1.0, -1.0, 0.0, h, 0.0)   // turn 90 clockwise
        7 -> AffineTransform(0.0, -1.0, -1.0, 0.0, h, w)    // transversed
        8 -> AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, w)   // turn 90 anticlockwise
        else -> AffineTransform()
    }

    /** The decoded picture and its format name (lower case). */
    private fun decode(upload: ByteArray): Pair<BufferedImage, String> {
        val input = ImageIO.createImageInputStream(ByteArrayInputStream(upload)) ?: throw UnsupportedImageException()
        input.use {
            val reader = ImageIO.getImageReaders(input).asSequence()
                .firstOrNull { it.formatName.lowercase() in ACCEPTED_FORMATS }
                ?: throw UnsupportedImageException()
            try {
                reader.setInput(input, true, true)
                val declaredWidth = reader.getWidth(0)
                val declaredHeight = reader.getHeight(0)
                if (declaredWidth > MAX_DECLARED_SIDE || declaredHeight > MAX_DECLARED_SIDE ||
                    declaredWidth.toLong() * declaredHeight > MAX_DECLARED_PIXELS
                ) {
                    throw UnsupportedImageException(UnsupportedImageException.TOO_LARGE)
                }
                val longest = max(declaredWidth, declaredHeight)
                val param = reader.defaultReadParam
                // Decoded at no more than twice the size it is kept at, so the raster stays small
                // whatever the camera's resolution, yet never below MAX_SIDE (a ceil here would
                // turn a 4000 px photo into 1334 px).
                val step = max(1, longest / MAX_SIDE)
                if (step > 1) param.setSourceSubsampling(step, step, 0, 0)
                val image = try {
                    reader.read(0, param)
                } catch (e: OutOfMemoryError) {
                    throw UnsupportedImageException(UnsupportedImageException.TOO_LARGE)
                } ?: throw UnsupportedImageException()
                return image to reader.formatName.lowercase()
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
