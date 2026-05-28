package com.personalmoney.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdsConsentManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val consentInformation: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)

    val canRequestAds: Boolean get() = consentInformation.canRequestAds()

    fun requestConsentIfNeeded(activity: Activity, onComplete: () -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    onComplete()
                }
            },
            { _ -> onComplete() }
        )
    }

    fun reset() {
        consentInformation.reset()
    }
}
