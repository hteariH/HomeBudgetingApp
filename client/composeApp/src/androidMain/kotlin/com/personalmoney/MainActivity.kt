package com.personalmoney

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.personalmoney.core.data.ThemeMode
import com.personalmoney.core.auth.TokenStore
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.security.BiometricGate
import com.personalmoney.core.security.LockScreen
import com.personalmoney.core.ui.theme.PersonalMoneyTheme
import com.personalmoney.core.ui.theme.isDark
import com.personalmoney.navigation.PersonalMoneyNavHost
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {

    private val preferences: UserPreferences by inject()
    private val tokenStore: TokenStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by preferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val dynamicColor by preferences.dynamicColor.collectAsState(initial = false)
            val biometricLockEnabled by preferences.biometricLockEnabled.collectAsState(initial = false)
            val darkTheme = themeMode.isDark()
            var unlocked by remember { mutableStateOf(false) }
            var lockError by remember { mutableStateOf<String?>(null) }
            val locked = biometricLockEnabled && !unlocked

            // The biometric prompt is platform UI and wants plain strings, but the shared
            // resource reader is suspend - so the labels are resolved here, in composition,
            // and handed down rather than looked up at the call site.
            val lockTitle = stringResource(Res.string.lock_prompt_title)
            val lockSubtitle = stringResource(Res.string.lock_prompt_subtitle)
            val lockCancel = stringResource(Res.string.action_cancel)

            LaunchedEffect(biometricLockEnabled) {
                if (biometricLockEnabled && !unlocked) {
                    promptUnlock(lockTitle, lockSubtitle, lockCancel,
                        onSuccess = { unlocked = true }, onError = { lockError = it })
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
                            promptUnlock(lockTitle, lockSubtitle, lockCancel,
                                onSuccess = { unlocked = true }, onError = { lockError = it })
                        }
                    )
                } else {
                    PersonalMoneyNavHost(
                        // The nav host takes a URI, not an Intent: only Android
                        // wraps a deep link in one.
                        deepLink = intent?.data?.toString(),
                        userPreferences = preferences,
                        tokenStore = tokenStore
                    )
                }
            }
        }
    }

    private fun promptUnlock(
        title: String,
        subtitle: String,
        cancelLabel: String,
        onSuccess: () -> Unit,
        onError: (String?) -> Unit,
    ) {
        BiometricGate.authenticate(
            activity = this,
            title = title,
            subtitle = subtitle,
            cancelLabel = cancelLabel,
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
