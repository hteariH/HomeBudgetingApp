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
import com.personalmoney.feature.homemoney.HomeMoneyRedirect
import com.personalmoney.feature.homemoney.PendingHomeMoneyRedirect
import com.personalmoney.navigation.PersonalMoneyNavHost
import com.personalmoney.navigation.Routes
import com.personalmoney.navigation.dropUnknownFragment
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

@JsFun("(s) => decodeURIComponent(s)")
private external fun decodeUriComponent(s: String): String

/**
 * Drops the query string, keeping the path and the fragment the router works in.
 *
 * Done before the code is spent rather than after: it is single-use, and leaving it in the
 * address bar means a reload - or a restored tab - tries it again and shows a failure for
 * an import that actually worked.
 */
@JsFun("() => window.history.replaceState(null, '', window.location.pathname + window.location.hash)")
private external fun dropQueryString()

private fun queryParams(search: String): Map<String, String> =
    search.removePrefix("?")
        .split("&")
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null
            else decodeUriComponent(part.take(separator)) to decodeUriComponent(part.substring(separator + 1))
        }
        .toMap()

/**
 * What the server's redirect is telling us, if this load is one.
 *
 * `pending` carries a code that still has to be exchanged; anything else the server can
 * say means no token was obtained, and the screen should say so rather than look untouched.
 */
private fun redirectFrom(search: String): HomeMoneyRedirect? {
    val params = queryParams(search)
    val outcome = params["homemoney"] ?: return null
    val code = params["code"]
    return if (outcome == "pending" && !code.isNullOrBlank()) {
        HomeMoneyRedirect.Pending(code)
    } else {
        HomeMoneyRedirect.Denied
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin { modules(webModule) }
    val koin = GlobalContext.get()
    val preferences = koin.get<UserPreferences>()
    // Read and cleared before the first frame: the ViewModel picks it up from here, and
    // the address bar stops holding a code that must not be spent twice.
    val redirect = redirectFrom(locationSearch())
    PendingHomeMoneyRedirect.set(redirect)
    if (redirect != null) dropQueryString()
    // A stale link naming a screen that no longer exists here - before the router is built
    // and reads it, which is the only moment early enough to matter.
    dropUnknownFragment()
    ComposeViewport(document.body!!) {
        // The stored choice, not just the system's. Until the header had a theme switch
        // nothing in the browser could set this, so the preference existed and was ignored
        // - the web always followed `prefers-color-scheme`.
        val themeMode by preferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
        PersonalMoneyTheme(darkTheme = themeMode.isDark()) {
            PersonalMoneyNavHost(
                // Whatever the outcome, the import screen is where it belongs - it is the
                // screen the user left from, and the one that finishes the exchange.
                deepLink = Routes.IMPORT_HOMEMONEY.takeIf { redirect != null },
                userPreferences = preferences,
                tokenStore = koin.get<TokenStore>(),
            )
        }
    }
}
