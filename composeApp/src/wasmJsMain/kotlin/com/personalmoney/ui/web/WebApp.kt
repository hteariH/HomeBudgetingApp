package com.personalmoney.ui.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.personalmoney.core.auth.AuthRepository
import com.personalmoney.core.auth.AuthResult
import com.personalmoney.core.auth.TokenStore
import com.personalmoney.core.network.RestAccount
import com.personalmoney.core.network.RestApi
import com.personalmoney.core.util.AppLog
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * The web client's first real screen.
 *
 * Everything it does goes through shared code - [AuthRepository], [TokenStore] and
 * [RestApi] are the same classes the Android app uses. That is the point of it: it proves
 * the shared stack actually runs in a browser against the real server, rather than only
 * compiling to WebAssembly.
 *
 * This is a bootstrap screen, not the finished web UI. The real screens arrive when the
 * Android ones move into commonMain.
 */
@Composable
fun WebApp() {
    val koin = remember { GlobalContext.get() }
    val auth = remember { koin.get<AuthRepository>() }
    val api = remember { koin.get<RestApi>() }
    val tokens = remember { koin.get<TokenStore>() }

    val signedIn by tokens.signedIn.collectAsState()

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("PersonalMoney", style = MaterialTheme.typography.headlineMedium)
                if (signedIn) AccountsPane(api, auth) else SignInPane(auth)
            }
        }
    }
}

@Composable
private fun SignInPane(auth: AuthRepository) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.widthIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(email, { email = it }, label = { Text("Email") })
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(
            enabled = !busy,
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    when (val result = auth.login(email.trim(), password)) {
                        is AuthResult.Success -> Unit // signedIn flips and the pane swaps
                        is AuthResult.Failure -> error = result.message
                    }
                    busy = false
                }
            },
        ) { Text(if (busy) "Signing in…" else "Sign in") }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun AccountsPane(api: RestApi, auth: AuthRepository) {
    var accounts by remember { mutableStateOf<List<RestAccount>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { api.accounts() }
            .onSuccess { accounts = it }
            .onFailure {
                AppLog.w("WebApp", "Loading accounts failed: ${it.message}")
                error = it.message ?: "Could not load accounts"
            }
    }

    Row(
        Modifier.fillMaxWidth().widthIn(max = 640.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Accounts", style = MaterialTheme.typography.titleLarge)
        Button(onClick = { scope.launch { auth.logout() } }) { Text("Sign out") }
    }

    when {
        error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
        accounts == null -> CircularProgressIndicator()
        else -> LazyColumn(
            Modifier.fillMaxWidth().widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(accounts!!) { account ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(account.name)
                        // Balance stays a string end to end; it is never a Double.
                        Text("${account.balance} ${account.currency.code}")
                    }
                }
            }
        }
    }
}
