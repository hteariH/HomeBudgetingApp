package com.personalmoney

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.personalmoney.core.auth.TokenStore
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.di.webModule
import com.personalmoney.core.ui.theme.PersonalMoneyTheme
import com.personalmoney.navigation.PersonalMoneyNavHost
import kotlinx.browser.document
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/**
 * The web entry point: the same navigation host and the same screens the Android app runs.
 *
 * The bootstrap screen this replaces had served its purpose - it proved the shared stack
 * ran in a browser against the real server. What renders now is the app.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin { modules(webModule) }
    val koin = GlobalContext.get()
    ComposeViewport(document.body!!) {
        PersonalMoneyTheme {
            PersonalMoneyNavHost(
                userPreferences = koin.get<UserPreferences>(),
                tokenStore = koin.get<TokenStore>(),
            )
        }
    }
}
