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
 * Clears a fragment no screen answers to.
 *
 * An unregistered route is not an error the library reports: `navigate()` throws, the
 * binding swallows it, and what the visitor gets is the screen that happened to be there
 * under an address bar naming a different one. A stale bookmark is how this actually
 * happens - `#more` is a phone-only screen the browser build no longer registers, and a
 * link from before a rename is the same shape of problem.
 *
 * Dropping the fragment means the app starts where a bare `/app/` starts, which is the
 * overview, and the address bar says so. Not a 404 page: there is no page here to serve one
 * from, and the honest reading of "this address names nothing" for a single-page app is the
 * app's front door.
 *
 * **Called from `main()`, before Compose starts.** It was a `LaunchedEffect` beside the
 * binding first, and there it did nothing at all: effects run after the first composition,
 * by which time the nav host is built and the binding has already read the address it was
 * given, so clearing it afterwards changed neither the screen nor the URL. Before the
 * viewport exists there is nothing to be too late for.
 *
 * Cold start only, which is the case that happens: a fragment typed into the address bar of
 * a running app is left to the binding, which ignores it and repairs the URL on the next
 * real navigation. Catching that one too would mean a second route matcher living in
 * JavaScript, which is a worse trade than the stale address bar it would buy.
 */
internal fun dropUnknownFragment() {
    val fragment = currentFragment()
    if (fragment.isNotBlank() && !Routes.isKnown(fragment, registeredRoutes)) dropFragment()
}
