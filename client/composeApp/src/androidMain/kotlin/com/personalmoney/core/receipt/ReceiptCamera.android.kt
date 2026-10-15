package com.personalmoney.core.receipt

import android.content.Context
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
import com.personalmoney.core.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "ReceiptCamera"

/** Where the capture lands. Cleared as soon as it has been read. */
private const val CAPTURE_DIR = "receipts"

actual val receiptScanAvailable: Boolean = true

/**
 * The system camera, writing the full-resolution photo to the cache.
 *
 * **`TakePicture`, not `TakePicturePreview`.** The preview contract hands back the thumbnail
 * the camera app puts in the result intent - a couple of hundred pixels wide, which no model
 * can read a price off. `TakePicture` writes the full frame to a uri we provide, which is why
 * there is a FileProvider in the manifest.
 *
 * **No `CAMERA` permission.** Shooting through `ACTION_IMAGE_CAPTURE` needs none - the photo
 * is taken by the camera app, not by this process.
 *
 * **The capture is deleted once read.** The server keeps the copy that matters (downscaled,
 * without metadata, with the transaction it is saved with); nothing of it stays on the phone.
 * Orientation is the server's to fix: it reads the EXIF tag before stripping it.
 */
@Composable
actual fun rememberReceiptCamera(
    onCaptured: (ByteArray) -> Unit,
    onFailed: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The camera outlives recomposition, so the callbacks are read when it returns rather
    // than captured when the launcher was built.
    val captured by rememberUpdatedState(onCaptured)
    val failed by rememberUpdatedState(onFailed)

    var pendingCapture by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { taken ->
        val uri = pendingCapture
        pendingCapture = null
        // `taken` is false when the user backed out of the camera, which is not a failure
        // and should say nothing. The empty file it leaves is still removed.
        if (uri == null) return@rememberLauncherForActivityResult
        if (!taken) {
            scope.launch { withContext(Dispatchers.IO) { deleteCapture(context, uri) } }
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } catch (e: Exception) {
                    AppLog.w(TAG, "Could not read the photo: ${e.message}")
                    null
                } finally {
                    deleteCapture(context, uri)
                }
            }
            if (bytes == null || bytes.isEmpty()) failed() else captured(bytes)
        }
    }

    return {
        val uri = runCatching { newCaptureUri(context) }
            .onFailure { AppLog.w(TAG, "Could not create a capture file: ${it.message}") }
            .getOrNull()
        if (uri == null) {
            failed()
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
