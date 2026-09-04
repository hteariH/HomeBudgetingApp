package com.personalmoney.core.ocr

import androidx.compose.runtime.Composable

/**
 * No scanning in the browser.
 *
 * Not a stub waiting to be filled in: the whole point of the feature is pointing a phone at
 * a paper receipt, and a desktop page has nothing to point. `getUserMedia` plus a WebAssembly
 * OCR model would be a different feature with a different payoff, so the honest thing is for
 * the web build to say it cannot and for the button never to appear.
 */
actual val receiptScanAvailable: Boolean = false

@Composable
actual fun rememberReceiptScanner(
    onScanned: (ScannedReceipt) -> Unit,
    onFailed: (ScanFailure) -> Unit,
): () -> Unit = {
    // Unreachable while [receiptScanAvailable] gates the button, and a no-op rather than a
    // throw so that a future caller which forgets the check misbehaves quietly.
}
