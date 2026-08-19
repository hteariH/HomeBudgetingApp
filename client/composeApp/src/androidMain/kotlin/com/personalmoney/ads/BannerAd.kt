package com.personalmoney.ads

import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.personalmoney.BuildConfig

@Composable
actual fun BannerAd(modifier: Modifier, enabled: Boolean) {
    if (!enabled) return
    val adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
    val context = LocalContext.current
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            this.adUnitId = adUnitId
        }
    }
    LaunchedEffect(Unit) {
        adView.loadAd(AdRequest.Builder().build())
    }
    DisposableEffect(Unit) {
        onDispose { adView.destroy() }
    }
    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                if (adView.parent != null) {
                    (adView.parent as? FrameLayout)?.removeView(adView)
                }
                addView(adView)
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}
