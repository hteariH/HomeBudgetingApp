package com.personalmoney.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlin.js.Promise

/**
 * A hidden `<input type="file">`, clicked from code.
 *
 * There is no other way for a page to open a file dialog: the click has to come from a
 * real input element, and the browser only honours it inside a user gesture - which is
 * why this is triggered from a button's onClick and not from a side effect.
 *
 * The file is read as **text**, not as bytes. That looks like a shortcut and is not: the
 * server does `file.bytes.toString(Charsets.UTF_8)` on the other end, so it treats the
 * upload as UTF-8 text regardless. Reading it the same way here means the two agree, and
 * a file in some other encoding is mangled identically rather than differently. Fixing
 * that properly is the server's job - it is the side that would have to detect the
 * charset - and doing it here first would only hide the problem from the one place that
 * can solve it.
 *
 * Resolves to null when the user cancels, so nothing fires.
 */
@JsFun(
    """() => new Promise((resolve) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.csv,text/csv';
        input.onchange = () => {
            const file = input.files && input.files[0];
            if (!file) { resolve(null); return; }
            const reader = new FileReader();
            reader.onload = () => resolve({ name: file.name, text: reader.result });
            reader.onerror = () => resolve(null);
            reader.readAsText(file);
        };
        input.click();
    })"""
)
private external fun openCsvPicker(): Promise<JsAny?>

@JsFun("(picked) => picked.name")
private external fun pickedName(picked: JsAny): String

@JsFun("(picked) => picked.text")
private external fun pickedText(picked: JsAny): String

@Composable
actual fun rememberCsvFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    // The dialog outlives a recomposition, so the callback is read when it returns rather
    // than captured when the picker was built.
    val callback by rememberUpdatedState(onPicked)
    return {
        scope.launch {
            val picked = openCsvPicker().await() ?: return@launch
            callback(PickedFile(pickedName(picked), pickedText(picked).encodeToByteArray()))
        }
        Unit
    }
}
