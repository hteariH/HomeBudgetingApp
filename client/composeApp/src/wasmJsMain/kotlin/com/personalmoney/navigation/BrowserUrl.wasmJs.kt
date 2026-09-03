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
