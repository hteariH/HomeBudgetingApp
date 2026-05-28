package com.personalmoney.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.personalmoney.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterstitialAdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val consent: AdsConsentManager
) {
    private var ad: InterstitialAd? = null
    private var loading = false
    private var lastShownAtMs: Long = 0

    private val cooldownMs = 5 * 60 * 1000L
    private val unitId = BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID

    fun preload() {
        if (!consent.canRequestAds) return
        if (ad != null || loading) return
        loading = true
        InterstitialAd.load(
            context, unitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    ad = loaded
                    loading = false
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    loading = false
                }
            }
        )
    }

    fun showIfReady(activity: Activity, onDismiss: () -> Unit = {}) {
        val now = System.currentTimeMillis()
        if (now - lastShownAtMs < cooldownMs) {
            onDismiss(); return
        }
        val current = ad
        if (current == null) {
            preload()
            onDismiss()
            return
        }
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                lastShownAtMs = System.currentTimeMillis()
                onDismiss()
                preload()
            }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                ad = null
                onDismiss()
            }
        }
        current.show(activity)
    }
}
