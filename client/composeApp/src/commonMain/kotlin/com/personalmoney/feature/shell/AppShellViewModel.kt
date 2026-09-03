package com.personalmoney.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.auth.AuthRepository
import com.personalmoney.core.data.ThemeMode
import com.personalmoney.core.data.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the header needs, which is everything about the session and nothing about a screen.
 *
 * Separate from `SettingsViewModel` even though both touch the theme: that one is scoped to
 * a screen the user has navigated to, while this one lives for as long as the shell does,
 * and giving the header a screen's view model would tie the chrome's lifetime to a page
 * that can be popped.
 */
class AppShellViewModel(
    private val preferences: UserPreferences,
    private val auth: AuthRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    /** Whoever is signed in, for the account menu. Null before sign-in and after sign-out. */
    val email: String? get() = auth.email

    /**
     * Commits to a side rather than cycling through three modes.
     *
     * The header's switch is a one-tap affordance and has to be honest about what the next
     * tap does, which "system" cannot be - so it writes the explicit mode opposite to what
     * is on screen. The three-way choice still lives on the Settings screen, where there is
     * room to say what "system" means.
     */
    fun setDark(dark: Boolean) {
        viewModelScope.launch {
            preferences.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        }
    }

    /**
     * Ends the session. Clearing the tokens flips `signedIn`, and the nav host swaps itself
     * for the sign-in screen - so there is nothing to navigate here.
     *
     * Web-only today: on a device this would also have to drop the local Room copy, which
     * is why the Settings screen's sign-out is a separate, confirmed action.
     */
    fun signOut() {
        viewModelScope.launch { auth.logout() }
    }
}
