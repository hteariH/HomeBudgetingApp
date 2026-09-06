package com.personalmoney.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** The site's sign-in page. A route of the React app rather than of this one. */
private const val SIGN_IN_PATH = "/login"

/**
 * `replace`, not `href`: the page being left is the one that sent us here, and leaving it
 * on the stack makes Back a loop rather than a way out. See the expect declaration.
 */
@JsFun("(u) => { window.location.replace(u); }")
private external fun replacePage(url: String)

/** The route the address bar is asking for, without the `#`. Empty at the app's root. */
@JsFun("() => window.location.hash.slice(1)")
private external fun currentFragment(): String

@JsFun("(s) => encodeURIComponent(s)")
private external fun encodeUriComponent(s: String): String

@Composable
internal actual fun LeaveForSignIn() {
    LaunchedEffect(Unit) {
        val next = currentFragment()
        replacePage(
            if (next.isBlank()) SIGN_IN_PATH
            else "$SIGN_IN_PATH?next=${encodeUriComponent(next)}"
        )
    }
}
