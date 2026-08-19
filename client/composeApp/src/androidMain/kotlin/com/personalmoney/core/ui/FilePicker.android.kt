package com.personalmoney.core.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.personalmoney.core.util.AppLog

/**
 * The Storage Access Framework picker.
 *
 * No bank screen reaches this today - the CSV import writes to the server's ledger, and
 * the phone reads its own out of Room - so this is currently unused. It is written for
 * real anyway rather than stubbed out: a picker that silently does nothing is a trap for
 * whoever wires the screen up on Android, and this is short enough that being honest
 * costs nothing.
 *
 * `OpenDocument` rather than `GetContent`: it gives a stable document uri, and the file
 * comes from wherever the user keeps it rather than only from apps willing to share.
 */
@Composable
actual fun rememberCsvFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onPicked)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        // Null when the user backs out of the picker.
        val chosen = uri ?: return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(chosen)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null) {
            // A uri that will not open is a revoked permission or a deleted file, not a
            // parse problem, so there is nothing useful to hand the import screen.
            AppLog.w("FilePicker", "Could not read the chosen file")
            return@rememberLauncherForActivityResult
        }
        callback(PickedFile(chosen.lastPathSegment ?: "statement.csv", bytes))
    }

    // Some file managers label CSV as text/comma-separated-values, and a few as
    // application/octet-stream; text/* keeps those visible rather than greyed out.
    return { launcher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) }
}
