package com.personalmoney.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.googlecode.tesseract.android.TessBaseAPI
import com.personalmoney.core.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "ReceiptScanner"

/** Where the capture lands. Cleared as soon as it has been read. */
private const val CAPTURE_DIR = "receipts"

/**
 * Ukrainian and Russian for the receipt, English for the merchant names that are printed in
 * Latin. Tesseract loads every language named here on each init, so the list is a direct
 * trade against speed - it is not free to add one "just in case".
 */
private const val LANGUAGES = "ukr+rus+eng"

/**
 * Long side of the image handed to Tesseract.
 *
 * A phone photo is far larger than the engine needs and decoding one at full size is how an
 * OCR feature turns into an OutOfMemoryError on a cheap device. Going much below this starts
 * losing the small print - which on a receipt is the part with the prices.
 */
private const val MAX_IMAGE_SIDE = 2000

/** Bump when the bundled traineddata changes, so an installed app re-extracts it. */
private const val TESSDATA_VERSION = 1

actual val receiptScanAvailable: Boolean = true

/**
 * The system camera, then Tesseract on-device.
 *
 * **Why Tesseract and not ML Kit.** Google ships no Cyrillic model for ML Kit at all, so on a
 * Ukrainian receipt it read `СУМА` as `CYMA` and the merchant name came back as noise. That is
 * the wrong engine for this app's market. Tesseract reads Cyrillic, at the cost of ~11 MB of
 * language data in the APK and a slower recognition pass.
 *
 * Three decisions worth keeping:
 *
 * **`TakePicture`, not `TakePicturePreview`.** The preview contract hands back the thumbnail
 * the camera app puts in the result intent - a couple of hundred pixels wide, which no OCR
 * engine can read a price off. `TakePicture` writes the full-resolution frame to a uri we
 * provide, which is why there is a FileProvider in the manifest.
 *
 * **No `CAMERA` permission.** Shooting through `ACTION_IMAGE_CAPTURE` needs none - the photo
 * is taken by the camera app, not by this process. Declaring the permission anyway would
 * oblige us to request it at runtime and would put a camera entry in the Play listing, in
 * exchange for nothing.
 *
 * **The photo is deleted once read.** It goes to the cache directory and is removed as soon
 * as the recogniser is done, so a scan leaves the ledger row and no image. Recognition is
 * entirely on-device; nothing about the receipt reaches the server.
 */
@Composable
actual fun rememberReceiptScanner(
    onScanned: (ScannedReceipt) -> Unit,
    onFailed: (ScanFailure) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The camera outlives recomposition, so the callbacks are read when it returns rather
    // than captured when the launcher was built.
    val scanned by rememberUpdatedState(onScanned)
    val failed by rememberUpdatedState(onFailed)

    var pendingCapture by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { taken ->
        val uri = pendingCapture
        pendingCapture = null
        // `taken` is false when the user backed out of the camera, which is not a failure
        // and should say nothing.
        if (!taken || uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val lines = runCatching { recognize(context, uri) }
                .onFailure { AppLog.w(TAG, "Could not read the photo: ${it.message}") }
                .getOrNull()
            deleteCapture(context, uri)

            when {
                lines == null -> failed(ScanFailure.ENGINE_ERROR)
                else -> {
                    val receipt = ReceiptParser.parse(lines)
                    if (receipt.isEmpty) failed(ScanFailure.NOTHING_FOUND) else scanned(receipt)
                }
            }
        }
    }

    return {
        val uri = runCatching { newCaptureUri(context) }
            .onFailure { AppLog.w(TAG, "Could not create a capture file: ${it.message}") }
            .getOrNull()
        if (uri == null) {
            failed(ScanFailure.ENGINE_ERROR)
        } else {
            pendingCapture = uri
            launcher.launch(uri)
        }
    }
}

private fun newCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }
    val file = File.createTempFile("receipt_", ".jpg", dir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun deleteCapture(context: Context, uri: Uri) {
    runCatching { context.contentResolver.delete(uri, null, null) }
        .onFailure { AppLog.w(TAG, "Could not delete the capture: ${it.message}") }
}

private suspend fun recognize(context: Context, uri: Uri): List<String> {
    val bitmap = withContext(Dispatchers.IO) { decodeCapture(context, uri) }
        ?: error("Could not decode the capture")
    return try {
        recognizeLines(context, bitmap)
    } finally {
        bitmap.recycle()
    }
}

/**
 * Decodes the capture down to something Tesseract can work on.
 *
 * Two passes: read the bounds only, then decode with the power-of-two sample size that gets
 * the long side under [MAX_IMAGE_SIDE]. Decoding a 12-megapixel photo at full size would be
 * ~48 MB of ARGB, which is a crash on a low-end phone rather than a slow scan.
 */
private fun decodeCapture(context: Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longSide = maxOf(bounds.outWidth, bounds.outHeight)
    if (longSide <= 0) return null

    var sample = 1
    while (longSide / sample > MAX_IMAGE_SIDE) sample *= 2

    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        // Tesseract wants pixels, not transparency, and this halves the memory.
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}

/**
 * Lines in top-to-bottom order.
 *
 * Tesseract already returns text in reading order, one line per newline, so unlike ML Kit -
 * which reported blocks in its own order and had to be re-sorted by bounding box - there is
 * nothing to reassemble here.
 *
 * Internal rather than private so an instrumented test can put a rendered receipt through the
 * real engine. The parser's own tests cover the text; this is the only way to check that the
 * engine hands over the text those tests assume.
 */
internal suspend fun recognizeLines(context: Context, image: Bitmap): List<String> {
    val dataPath = withContext(Dispatchers.IO) { ensureTessData(context) }
    return withContext(Dispatchers.Default) {
        val tess = TessBaseAPI()
        try {
            // A fresh instance per scan. TessBaseAPI is not thread-safe and holds the loaded
            // models, so keeping one around would mean owning its lifetime for the sake of a
            // few hundred milliseconds on an action the user takes by hand.
            check(tess.init(dataPath.absolutePath, LANGUAGES)) { "Tesseract init failed" }
            // SINGLE_BLOCK, emphatically not AUTO. Automatic segmentation reads the wide
            // gap between an item name and its price as a column boundary and splits the
            // receipt down the middle - which cut "91.90" into a "91" on one side and a
            // ".90" on the other, leaving no money-shaped number on the page at all. A
            // receipt is one block of text whose rows happen to be widely spaced.
            tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
            tess.setImage(image)
            tess.getUTF8Text()
                .orEmpty()
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } finally {
            tess.recycle()
        }
    }
}

/**
 * Unpacks the bundled language data to a real directory, because Tesseract reads it by path
 * and cannot see inside the APK.
 *
 * Returns the directory to pass to `init` - the one *containing* `tessdata`, not `tessdata`
 * itself. Costs about 11 MB and one copy on first use; afterwards the marker file makes it a
 * single existence check.
 */
private fun ensureTessData(context: Context): File {
    val root = File(context.filesDir, "tesseract")
    val tessdata = File(root, "tessdata")
    val marker = File(root, "version-$TESSDATA_VERSION")
    if (marker.exists()) return root

    tessdata.mkdirs()
    // Stale files from an older version, if the constant was bumped.
    tessdata.listFiles()?.forEach { it.delete() }
    root.listFiles()?.filter { it.isFile && it.name.startsWith("version-") }?.forEach { it.delete() }

    context.assets.list("tessdata").orEmpty().forEach { name ->
        context.assets.open("tessdata/$name").use { source ->
            File(tessdata, name).outputStream().use { source.copyTo(it) }
        }
    }
    marker.createNewFile()
    return root
}
