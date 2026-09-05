package com.personalmoney.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.personalmoney.core.billing.EntitlementRepository
import org.koin.compose.koinInject

/**
 * A banner slot, where the platform has one and the account has not paid it away.
 *
 * Declared in shared code so the Dashboard and Transactions screens can keep the slot
 * where it has always been, rather than forking per platform over one row of layout.
 * AdMob is an Android SDK with no web equivalent, so the browser draws nothing.
 *
 * The subscription check lives here rather than at the three call sites, which is the
 * point of the wrapper: "premium removes the ads" is one rule, and a screen that forgot to
 * ask would quietly sell a subscriber to an advertiser.
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val entitlements = koinInject<EntitlementRepository>()
    // Null while the answer is still being read - see EntitlementRepository.premium. Drawn
    // as "no banner", because the wrong guess in the other direction is one a paying user
    // watches happen.
    val premium by entitlements.premium.collectAsState(initial = null)
    PlatformBannerAd(modifier = modifier, enabled = enabled && premium == false)
}

/**
 * The platform's own banner. Never called directly by a screen - [BannerAd] is the one
 * that knows whether there should be an ad at all.
 */
@Composable
internal expect fun PlatformBannerAd(modifier: Modifier = Modifier, enabled: Boolean = true)
