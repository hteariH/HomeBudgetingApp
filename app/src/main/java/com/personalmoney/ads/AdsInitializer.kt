package com.personalmoney.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdsInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val consent: AdsConsentManager
) {
    private val initialized = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initIfAllowed() {
        if (!consent.canRequestAds) return
        if (initialized.compareAndSet(false, true)) {
            scope.launch {
                MobileAds.initialize(context) { /* no-op */ }
            }
        }
    }
}
