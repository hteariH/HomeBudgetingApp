package com.personalmoney.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A banner slot, where the platform has one.
 *
 * Declared in shared code so the Dashboard and Transactions screens can keep the slot
 * where it has always been, rather than the screens forking per platform over one row of
 * layout. AdMob is an Android SDK with no web equivalent, so the browser draws nothing -
 * and monetisation on the web is RevenueCat's job in phase 11, not this one's.
 */
@Composable
expect fun BannerAd(modifier: Modifier = Modifier, enabled: Boolean = true)
