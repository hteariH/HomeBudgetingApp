package com.homebudget.app.bank

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * A QR code as a grid of bits, for a client to draw.
 *
 * Not an image. The clients here are Compose, which can fill a rectangle far more easily
 * than it can decode a PNG that arrived over JSON, and a bitmap would have had to guess a
 * size, a colour and a theme that only the screen knows. Rows of '1' and '0' leave all
 * three where they belong and cost about a kilobyte.
 *
 * The quiet zone is left out for the same reason: it is white space, and the screen is
 * what has the padding.
 */
object QrCode {

    fun rows(text: String): List<String> {
        val matrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            // Zero means "no scaling": one bit per module, which is all the client needs.
            0,
            0,
            mapOf(
                EncodeHintType.MARGIN to 0,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                // A phone camera pointed at a screen has an easy time of it; M keeps the
                // grid small enough to stay crisp in a dialog.
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
            )
        )
        return (0 until matrix.height).map { y ->
            buildString(matrix.width) {
                for (x in 0 until matrix.width) append(if (matrix.get(x, y)) '1' else '0')
            }
        }
    }
}
