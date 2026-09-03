package com.personalmoney.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

/**
 * Keeps the browser's address bar in step with the back stack, where there is one.
 *
 * On the web an app whose URL never changes is a broken app: nothing can be linked to, the
 * browser's Back button leaves the app rather than going back a screen, and a reload always
 * lands on the dashboard. This binds the two directions - a `navigate()` writes the route
 * into the URL, and a URL typed, shared or reached with Back navigates the controller.
 *
 * On Android the address bar does not exist and the system's own back stack is already the
 * one being drawn, so the actual is a no-op. This is a composable rather than a plain
 * function because the binding lives as long as the screen does: it is an effect that runs
 * for the life of the composition and is torn down with it.
 */
@Composable
internal expect fun BindBrowserUrl(navController: NavController)
