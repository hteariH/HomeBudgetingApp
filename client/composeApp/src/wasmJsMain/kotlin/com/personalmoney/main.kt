package com.personalmoney

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeViewport
import com.personalmoney.core.auth.TokenStore
import com.personalmoney.core.data.ThemeMode
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.di.webModule
import com.personalmoney.core.ui.theme.PersonalMoneyTheme
import com.personalmoney.core.ui.theme.isDark
import com.personalmoney.navigation.PersonalMoneyNavHost
import com.personalmoney.navigation.Routes
import kotlinx.browser.document
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/**
 * The web entry point: the same navigation host and the same screens the Android app runs.
 *
 * The bootstrap screen this replaces had served its purpose - it proved the shared stack
 * ran in a browser against the real server. What renders now is the app.
 */
/**
 * The query string HomeMoney's redirect comes back with.
 *
 * Read here rather than inside a screen because by the time anything renders, the app has
 * been reloaded from scratch: the consent round trip leaves the page entirely, so the only
 * trace of where the user was is what the server put in the URL.
 */
@JsFun("() => window.location.search")
private external fun locationSearch(): String

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin { modules(webModule) }
    val koin = GlobalContext.get()
    val preferences = koin.get<UserPreferences>()
    ComposeViewport(document.body!!) {
        // The stored choice, not just the system's. Until the header had a theme switch
        // nothing in the browser could set this, so the preference existed and was ignored
        // - the web always followed `prefers-color-scheme`.
        val themeMode by preferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
        PersonalMoneyTheme(darkTheme = themeMode.isDark()) {
            PersonalMoneyNavHost(
                // Whatever the outcome, the import screen is where it makes sense: it
                // reads the connection back from the server, which is the only side that
                // knows whether a token was actually stored.
                deepLink = Routes.IMPORT_HOMEMONEY
                    .takeIf { "homemoney=" in locationSearch() },
                userPreferences = preferences,
                tokenStore = koin.get<TokenStore>(),
            )
        }
    }
}
