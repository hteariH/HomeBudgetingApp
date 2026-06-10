package com.personalmoney

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.personalmoney.ads.AdsConsentManager
import com.personalmoney.ads.AdsInitializer
import com.personalmoney.ads.InterstitialAdManager
import com.personalmoney.core.data.ThemeMode
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.security.BiometricGate
import com.personalmoney.core.security.LockScreen
import com.personalmoney.core.ui.theme.PersonalMoneyTheme
import com.personalmoney.navigation.PersonalMoneyNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var consentManager: AdsConsentManager
    @Inject lateinit var adsInitializer: AdsInitializer
    @Inject lateinit var interstitialManager: InterstitialAdManager
    @Inject lateinit var preferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        consentManager.requestConsentIfNeeded(this) {
            adsInitializer.initIfAllowed()
            interstitialManager.preload()
        }

        setContent {
            val themeMode by preferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val dynamicColor by preferences.dynamicColor.collectAsState(initial = true)
            val biometricLockEnabled by preferences.biometricLockEnabled.collectAsState(initial = false)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            var unlocked by remember { mutableStateOf(false) }
            val locked = biometricLockEnabled && !unlocked

            LaunchedEffect(biometricLockEnabled) {
                if (biometricLockEnabled && !unlocked) {
                    promptUnlock { unlocked = true }
                } else if (!biometricLockEnabled) {
                    unlocked = true
                }
            }

            PersonalMoneyTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                if (locked) {
                    LockScreen(onUnlock = { promptUnlock { unlocked = true } })
                } else {
                    PersonalMoneyNavHost(
                        deepLinkIntent = intent,
                        userPreferences = preferences
                    )
                }
            }
        }
    }

    private fun promptUnlock(onSuccess: () -> Unit) {
        BiometricGate.authenticate(
            activity = this,
            title = getString(R.string.lock_prompt_title),
            subtitle = getString(R.string.lock_prompt_subtitle),
            cancelLabel = getString(R.string.action_cancel),
            onSuccess = onSuccess,
            onFailure = { _, _ -> /* stay on lock screen */ }
        )
    }
}
