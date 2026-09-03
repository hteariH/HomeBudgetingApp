package com.personalmoney.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

/** No address bar to keep in step - see the expect declaration. */
@Composable
internal actual fun BindBrowserUrl(navController: NavController) = Unit
