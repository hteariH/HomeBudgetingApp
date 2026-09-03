package com.personalmoney.core.ui

import androidx.compose.runtime.Composable

@JsFun("(u) => { window.location.href = u; }")
private external fun navigateTo(url: String)

/** Leaves the page. The app comes back when the server redirects to /app. */
@Composable
actual fun rememberPageNavigator(): (String) -> Unit = ::navigateTo
