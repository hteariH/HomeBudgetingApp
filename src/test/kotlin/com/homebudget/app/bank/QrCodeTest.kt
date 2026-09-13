package com.homebudget.app.bank

import com.google.zxing.BinaryBitmap
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * That the grid the client draws is the URL, read the way a phone will read it.
 *
 * The failure this guards against is silent: rows and columns transposed, or read back to
 * front, still produces a plausible-looking square of black and white. Nobody reviewing the
 * screenshot would see it, and the first report would be "the QR does nothing".
 */
class QrCodeTest {

    private val url = "https://mbnk.app/auth/uLkwh3NzFAfEkj7urj5C7AU_"

    @Test
    fun `the rows decode back to the URL that was encoded`() {
        val rows = QrCode.rows(url)

        assertEquals(url, decode(rows))
    }

    @Test
    fun `is square, and carries the finder pattern a scanner looks for`() {
        val rows = QrCode.rows(url)

        assertTrue(rows.all { it.length == rows.size }, "a QR is square")
        // The top-left finder: seven dark modules across the first row of the pattern.
        assertTrue(rows[0].startsWith("1111111"))
        // No quiet zone in the payload - the screen adds it as padding, and a border of
        // white rows here would be wasted bytes on every poll.
        assertTrue(rows[0].contains('1'))
    }

    /**
     * Reads the grid back the way a camera would - drawn as pixels, with the padding the
     * screen puts around it, and nothing else told to the reader about where it is.
     */
    private fun decode(rows: List<String>): String {
        // Scaled and padded to something camera-shaped: a reader given one pixel per module
        // and no margin fails on its own terms rather than on the code's.
        val scale = 4
        val quiet = 4 * scale
        val side = rows.size * scale + quiet * 2
        val bitmap = BitMatrix(side, side)
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, cell ->
                if (cell == '1') {
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            bitmap.set(quiet + x * scale + dx, quiet + y * scale + dy)
                        }
                    }
                }
            }
        }
        return QRCodeReader()
            .decode(BinaryBitmap(HybridBinarizer(BitMatrixSource(bitmap))))
            .text
    }
}

/** The smallest thing zxing will read a [BitMatrix] through. */
private class BitMatrixSource(private val matrix: BitMatrix) :
    com.google.zxing.LuminanceSource(matrix.width, matrix.height) {

    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        val out = if (row != null && row.size >= width) row else ByteArray(width)
        for (x in 0 until width) out[x] = if (matrix.get(x, y)) 0 else 255.toByte()
        return out
    }

    override fun getMatrix(): ByteArray {
        val out = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) out[y * width + x] = if (matrix.get(x, y)) 0 else 255.toByte()
        }
        return out
    }
}
