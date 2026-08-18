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
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.CategoryRepository
import com.personalmoney.core.data.TransactionRepository
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.domain.model.Transaction
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.toDecimalString
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.util.AppLog
import com.personalmoney.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
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
    val accountsRepo = remember { koin.get<AccountRepository>() }
    val categoriesRepo = remember { koin.get<CategoryRepository>() }
    val transactionsRepo = remember { koin.get<TransactionRepository>() }
    val tokens = remember { koin.get<TokenStore>() }

    val signedIn by tokens.signedIn.collectAsState()

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium)
                if (signedIn) {
                    AccountsPane(accountsRepo, auth)
                    TransactionsPane(transactionsRepo, categoriesRepo)
                } else {
                    SignInPane(auth)
                }
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
        OutlinedTextField(email, { email = it }, label = { Text(stringResource(Res.string.auth_email)) })
        OutlinedTextField(
            password, { password = it }, label = { Text(stringResource(Res.string.auth_password)) },
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
        ) {
            Text(stringResource(if (busy) Res.string.auth_signing_in else Res.string.auth_sign_in))
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun AccountsPane(repository: AccountRepository, auth: AuthRepository) {
    var accounts by remember { mutableStateOf<List<Account>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { repository.refresh() }
            .onFailure {
                AppLog.w("WebApp", "Loading accounts failed: ${it.message}")
                error = it.message ?: getString(Res.string.common_load_failed)
            }
    }
    // Collected rather than assigned once: the repository republishes after every write.
    LaunchedEffect(repository) {
        repository.observeActive().collect { accounts = it }
    }

    Row(
        Modifier.fillMaxWidth().widthIn(max = 640.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.accounts_title), style = MaterialTheme.typography.titleLarge)
        Button(onClick = { scope.launch { auth.logout() } }) {
            Text(stringResource(Res.string.header_sign_out))
        }
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
                        // Minor units all the way; formatted only at the edge.
                        val decimals = CurrencyRegistry.decimalsOrFallback(account.currencyCode)
                        Text("${account.balanceMinor.toDecimalString(decimals)} ${account.currencyCode}")
                    }
                }
            }
        }
    }
}

/**
 * Recent ledger rows, resolving each one's category name.
 *
 * Exists to exercise the transaction and category repositories the same way the accounts
 * pane exercises its own - the shared contract is only proven once something reads it.
 */
@Composable
private fun TransactionsPane(
    transactions: TransactionRepository,
    categories: CategoryRepository,
) {
    var rows by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var categoriesById by remember { mutableStateOf<Map<Long, Category>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            categories.refresh()
            transactions.refresh()
        }.onFailure {
            AppLog.w("WebApp", "Loading the ledger failed: ${it.message}")
            error = it.message ?: getString(Res.string.common_load_failed)
        }
    }
    LaunchedEffect(transactions) { transactions.observeRecent(10).collect { rows = it } }
    LaunchedEffect(categories) {
        categories.observeAll().collect { all -> categoriesById = all.associateBy { it.id } }
    }

    Text(
        stringResource(Res.string.dashboard_recent),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(top = 8.dp),
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    LazyColumn(
        Modifier.fillMaxWidth().widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows) { tx ->
            val decimals = CurrencyRegistry.decimalsOrFallback(tx.currencyCode)
            val signed = if (tx.type == TransactionType.EXPENSE) -tx.amountMinor else tx.amountMinor
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(tx.note ?: categoriesById[tx.categoryId]?.name
                        ?: stringResource(Res.string.common_none))
                    Text(
                        "${signed.toDecimalString(decimals)} ${tx.currencyCode}",
                        color = if (signed < 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
