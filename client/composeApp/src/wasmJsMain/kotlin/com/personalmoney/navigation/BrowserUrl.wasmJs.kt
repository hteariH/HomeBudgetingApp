package com.personalmoney.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.NavController
import androidx.navigation.bindToBrowserNavigation

/**
 * The navigation library's own two-way binding: routes are written to the URL fragment
 * (`/app/#accounts`), and `popstate` - the Back button - is fed back into the controller.
 *
 * The fragment rather than the path is the library's choice, and it happens to be the one
 * that needs nothing of the server: every URL is still `/app/`, so no rewrite rule has to
 * learn the app's route table for a shared link to resolve.
 *
 * The effect never returns - it collects `popstate` for as long as the app is on screen -
 * so it belongs in a `LaunchedEffect` keyed on the controller, not in a one-shot callback.
 */
@OptIn(ExperimentalBrowserHistoryApi::class)
@Composable
internal actual fun BindBrowserUrl(navController: NavController) {
    LaunchedEffect(navController) {
        dropUnknownFragment()
        navController.bindToBrowserNavigation()
    }
}

/** The route the address bar is asking for, without the `#`. Empty at the app's root. */
@JsFun("() => window.location.hash.slice(1)")
private external fun currentFragment(): String

/** Drops the fragment without adding a history entry - nobody navigated anywhere. */
@JsFun("() => window.history.replaceState(null, '', window.location.pathname + window.location.search)")
private external fun dropFragment()

/**
 * Clears a fragment no screen answers to, before the binding is asked to honour it.
 *
 * An unregistered route is not an error the library reports: `navigate()` throws, the
 * binding swallows it, and what the visitor gets is the screen that happened to be there
 * under an address bar naming a different one. A stale bookmark is the way this actually
 * happens - `#more` is a phone-only screen the browser build no longer registers, and
 * links from before a rename are the same shape of problem.
 *
 * Dropping the fragment means the app starts where a bare `/app/` starts, which is the
 * overview, and the address bar says so. Not a 404 page: there is no page here to serve
 * one from, and the honest reading of "this address names nothing" for a single-page app
 * is the app's front door.
 *
 * Cold start only. A fragment typed into the address bar of a running app is left to the
 * binding, which ignores it and repairs the URL on the next real navigation - fixing that
 * too would mean a second route matcher living in JavaScript, which is a worse trade than
 * the stale address bar it would buy.
 */
private fun dropUnknownFragment() {
    val fragment = currentFragment()
    if (fragment.isNotBlank() && !Routes.isKnown(fragment, registeredRoutes)) dropFragment()
}
