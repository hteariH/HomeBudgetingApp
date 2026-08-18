package com.personalmoney

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.personalmoney.core.di.webModule
import com.personalmoney.ui.web.WebApp
import kotlinx.browser.document
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin { modules(webModule) }
    ComposeViewport(document.body!!) { WebApp() }
}
