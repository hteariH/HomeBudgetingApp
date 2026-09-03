package com.personalmoney.core.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * How a screen's own bar is painted, given what is above it.
 *
 * On a device that bar *is* the chrome, and Material's filled surface is exactly right. In
 * a browser there is already a header across the top, and a second filled band under it
 * read as a stray strip of app rather than as the page's heading - so there the bar keeps
 * its title and its back arrow but loses its background, and what is left is a heading
 * sitting on the page, which is what the React client always had.
 *
 * A function rather than a theme override because `surface` is not only the bar: the
 * overview and the analytics cards ask for the same slot by name, and flattening it for
 * the subtree would have taken them with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun pageBarColors(): TopAppBarColors =
    if (webChrome) TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    else TopAppBarDefaults.topAppBarColors()
