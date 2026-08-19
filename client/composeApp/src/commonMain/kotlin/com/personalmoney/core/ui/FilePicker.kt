package com.personalmoney.core.ui

import androidx.compose.runtime.Composable

/**
 * A file the user chose, already read.
 *
 * Not a data class on purpose: [bytes] would give it an `equals` that compares array
 * identity, which is the kind of thing that works in every test and fails once in
 * production.
 */
class PickedFile(val name: String, val bytes: ByteArray)

/**
 * Lets the user choose a statement file, and hands back its bytes.
 *
 * The only genuinely platform-specific part of the CSV import: everything else - the
 * mapping editor, the preview, the confirm - is ordinary Compose over ordinary values.
 * So the seam is exactly this and nothing more.
 *
 * Returns the function that opens the picker. Nothing happens until it is called, and
 * [onPicked] does not fire if the user cancels.
 */
@Composable
expect fun rememberCsvFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit
