package com.personalmoney.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Nothing to draw: AdMob has no web SDK. See the shared declaration. */
@Composable
actual fun BannerAd(modifier: Modifier, enabled: Boolean) = Unit
