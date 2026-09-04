package com.personalmoney.core.ocr

import androidx.compose.runtime.Composable

/** Why a scan came back with nothing to put in the form. Cancelling is not a failure. */
enum class ScanFailure {
    /** The photo was read, but held no amount, date or shop name worth offering. */
    NOTHING_FOUND,

    /** The camera, the file, or the recogniser itself failed. */
    ENGINE_ERROR,
}

/**
 * False where there is no camera to point at a receipt - the web build.
 *
 * The same shape as `navigation.homeMoneyImportAvailable`: the screen asks before it draws
 * the button, rather than drawing a button whose platform actual quietly does nothing.
 */
expect val receiptScanAvailable: Boolean

/**
 * Photographs a receipt, reads it, and hands back what [ReceiptParser] made of it.
 *
 * Returns the function that starts the flow - nothing happens until it is called, and
 * neither callback fires if the user backs out of the camera. The seam is exactly this:
 * everything downstream (the parsing, the form) is ordinary code over ordinary values, so
 * swapping the recognition engine is a change to one actual.
 */
@Composable
expect fun rememberReceiptScanner(
    onScanned: (ScannedReceipt) -> Unit,
    onFailed: (ScanFailure) -> Unit,
): () -> Unit
