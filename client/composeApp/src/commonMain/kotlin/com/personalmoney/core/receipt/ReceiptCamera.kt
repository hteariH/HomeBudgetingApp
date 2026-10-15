package com.personalmoney.core.receipt

import androidx.compose.runtime.Composable

/**
 * False where there is no camera to point at a receipt.
 *
 * The same shape as `navigation.homeMoneyImportAvailable`: the screen asks before it draws
 * the button, rather than drawing a button whose platform actual quietly does nothing.
 */
expect val receiptScanAvailable: Boolean

/**
 * Photographs a receipt and hands back the photo as JPEG bytes, for [ReceiptScanner] to send
 * to the server.
 *
 * Returns the function that starts the camera - nothing happens until it is called, and
 * neither callback fires if the user backs out of it. [onFailed] is for the camera or the
 * file failing, not for the user changing their mind.
 */
@Composable
expect fun rememberReceiptCamera(
    onCaptured: (ByteArray) -> Unit,
    onFailed: () -> Unit,
): () -> Unit
