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
import androidx.biometric.BiometricPrompt
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.personalmoney.ads.AdsConsentManager
import com.personalmoney.ads.AdsInitializer
import com.personalmoney.ads.InterstitialAdManager
import com.personalmoney.core.data.ThemeMode
import com.personalmoney.core.auth.TokenStore
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.security.BiometricGate
import com.personalmoney.core.security.LockScreen
import com.personalmoney.core.ui.theme.PersonalMoneyTheme
import com.personalmoney.navigation.PersonalMoneyNavHost
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {

    private val consentManager: AdsConsentManager by inject()
    private val adsInitializer: AdsInitializer by inject()
    private val interstitialManager: InterstitialAdManager by inject()
    private val preferences: UserPreferences by inject()
    private val tokenStore: TokenStore by inject()

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
            var lockError by remember { mutableStateOf<String?>(null) }
            val locked = biometricLockEnabled && !unlocked

            LaunchedEffect(biometricLockEnabled) {
                if (biometricLockEnabled && !unlocked) {
                    promptUnlock(onSuccess = { unlocked = true }, onError = { lockError = it })
                } else if (!biometricLockEnabled) {
                    unlocked = true
                }
            }

            PersonalMoneyTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                if (locked) {
                    LockScreen(
                        error = lockError,
                        onUnlock = {
                            lockError = null
                            promptUnlock(onSuccess = { unlocked = true }, onError = { lockError = it })
                        }
                    )
                } else {
                    PersonalMoneyNavHost(
                        deepLinkIntent = intent,
                        userPreferences = preferences,
                        tokenStore = tokenStore
                    )
                }
            }
        }
    }

    private fun promptUnlock(onSuccess: () -> Unit, onError: (String?) -> Unit) {
        BiometricGate.authenticate(
            activity = this,
            title = getString(R.string.lock_prompt_title),
            subtitle = getString(R.string.lock_prompt_subtitle),
            cancelLabel = getString(R.string.action_cancel),
            onSuccess = onSuccess,
            onFailure = { code, message ->
                // User dismissals are not errors; surface real failures so the lock screen
                // can explain why and offer a retry instead of looking unresponsive.
                val userDismissed = code == BiometricPrompt.ERROR_USER_CANCELED ||
                    code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    code == BiometricPrompt.ERROR_CANCELED
                onError(if (userDismissed) null else message.toString())
            }
        )
    }
}
