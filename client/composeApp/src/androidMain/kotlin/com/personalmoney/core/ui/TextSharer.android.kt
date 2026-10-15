package com.personalmoney.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberTextSharer(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
            val chooser = Intent.createChooser(send, null)
            // Started from an application context, an activity needs its own task.
            if (!context.isActivity()) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // runCatching: a device with nothing to share to should not crash the app.
            runCatching { context.startActivity(chooser) }
            Unit
        }
    }
}

private fun Context.isActivity(): Boolean {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return true
        c = c.baseContext
    }
    return false
}
