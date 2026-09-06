package com.personalmoney.feature.homemoney

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.CategoryRepository
import com.personalmoney.core.data.CurrencyRepository
import com.personalmoney.core.data.HomeMoneyImportRepository
import com.personalmoney.core.data.TransactionRepository
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.domain.model.Currency
import com.personalmoney.core.domain.model.HomeMoneyConnection
import com.personalmoney.core.domain.model.HomeMoneyImportPlan
import com.personalmoney.core.domain.model.HomeMoneyImportProgress
import com.personalmoney.core.network.serverMessage
import com.personalmoney.resources.Res
import com.personalmoney.resources.common_request_failed
import com.personalmoney.resources.homemoney_denied
import com.personalmoney.resources.homemoney_link_expired
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

data class HomeMoneyImportUiState(
    val loading: Boolean = true,
    val connection: HomeMoneyConnection = HomeMoneyConnection(connected = false),
    val plan: HomeMoneyImportPlan? = null,
    /** The user's own accounts, for pointing a HomeMoney one at an existing account. */
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val progress: HomeMoneyImportProgress = HomeMoneyImportProgress(),
    val busy: Boolean = false,
    val error: String? = null,
    /**
     * Set when the user asked to connect; the screen navigates to it and clears it.
     *
     * Held in state rather than returned from a call because leaving the page is the
     * screen's job, not the ViewModel's - it is the only side that can reach a browser.
     */
    val authorizationUrl: String? = null,
    /** Categories are collapsed by default: there are routinely dozens of them. */
    val categoriesExpanded: Boolean = false,
)

/**
 * Bringing a ledger over from homemoney.ua.
 *
 * Three steps, and the state above is really three states in one: connect, agree the
 * mapping, watch it run. They are one ViewModel rather than three because the middle step
 * is the only one with anything to decide and it needs both ends - the connection to fetch
 * a plan, and the plan to start the import.
 *
 * Bound only in the web graph; see [HomeMoneyImportRepository].
 */
class HomeMoneyImportViewModel(
    private val repository: HomeMoneyImportRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val currencyRepository: CurrencyRepository,
    private val preferences: UserPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeMoneyImportUiState())
    val state: StateFlow<HomeMoneyImportUiState> = _state.asStateFlow()

    init {
        observeLocalData()
        val redirect = PendingHomeMoneyRedirect.take()
        if (redirect == null) refresh() else finishAuthorization(redirect)
    }

    /**
     * The user's own accounts and categories, which is what a mapping maps *onto*.
     *
     * Collected rather than fetched once: the import creates accounts as it runs, and the
     * pickers should offer them without the user reloading the page.
     */
    private fun observeLocalData() {
        viewModelScope.launch {
            accountRepository.observeAll().collect { accounts ->
                _state.update { it.copy(accounts = accounts.filter { account -> !account.archived }) }
            }
        }
        viewModelScope.launch {
            categoryRepository.observeAll().collect { categories ->
                _state.update { it.copy(categories = categories) }
            }
        }
        viewModelScope.launch {
            currencyRepository.observeAll().collect { currencies ->
                _state.update { it.copy(currencies = currencies) }
            }
        }
    }

    /**
     * Re-reads the connection.
     *
     * The ordinary way in. A screen opened by HomeMoney's redirect goes through
     * [finishAuthorization] instead, which has a code to spend first.
     */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            reload(message = null)
        }
    }

    /**
     * Reads the connection and any running import back, keeping [message] if there is one.
     *
     * The message outlives the reload on purpose: after a refused or expired consent the
     * server has nothing to report - there simply is no connection - and the only account
     * of what went wrong is the one carried in from the redirect.
     */
    private suspend fun reload(message: String?) {
        try {
            val connection = repository.connection()
            val progress = repository.progress()
            _state.update {
                it.copy(loading = false, connection = connection, progress = progress, error = message)
            }
            // An import left running by an earlier visit is still running on the
            // server; pick the watch back up rather than showing a stale figure.
            if (progress.running) {
                pollProgress()
            } else if (progress.state == HomeMoneyImportProgress.State.DONE) {
                accountRepository.refresh()
                categoryRepository.refresh()
                transactionRepository.refresh()
            }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = message ?: messageFor(e)) }
        }
    }

    /**
     * Picks the consent round trip up where the page reload left it.
     *
     * This is the half that cannot happen on the server: homemoney.ua's redirect carries no
     * state, so the request that lands there cannot tell whose consent it is. This one can
     * - it is signed in - and it pairs the returning code with the state stored before the
     * browser left.
     */
    private fun finishAuthorization(redirect: HomeMoneyRedirect) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val message = when (redirect) {
                HomeMoneyRedirect.Denied -> getString(Res.string.homemoney_denied)
                is HomeMoneyRedirect.Pending -> exchange(redirect.code)
            }
            // Spent or refused, the stored state has done its job and a stale one would
            // only be offered to a code it never belonged to.
            preferences.setHomeMoneyAuthState(null)
            reload(message)
        }
    }

    /** Null once the token is stored; a sentence for the user when it is not. */
    private suspend fun exchange(code: String): String? {
        val state = preferences.homeMoneyAuthState.first()
        if (state.isNullOrBlank()) return getString(Res.string.homemoney_link_expired)
        return try {
            repository.completeAuthorization(code, state)
            null
        } catch (e: Exception) {
            messageFor(e)
        }
    }

    fun connect() = withBusy {
        val authorization = repository.authorizationUrl()
        // Written before the page leaves for their consent screen, because that is the
        // last moment this browser is ours: it comes back reloaded from scratch, and
        // homemoney.ua returns no state of its own to pair the code with.
        preferences.setHomeMoneyAuthState(authorization.state)
        _state.update { it.copy(authorizationUrl = authorization.url) }
    }

    /** Called once the screen has navigated, so a recomposition does not send it twice. */
    fun onAuthorizationOpened() = _state.update { it.copy(authorizationUrl = null) }

    fun disconnect() = withBusy {
        repository.disconnect()
        // The plan belonged to the connection that just went; keeping it on screen would
        // offer to import from a token that no longer exists.
        _state.update {
            it.copy(connection = HomeMoneyConnection(connected = false), plan = null)
        }
    }

    fun loadPlan() = withBusy {
        val plan = repository.plan()
        _state.update { it.copy(plan = plan) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun toggleCategories() = _state.update { it.copy(categoriesExpanded = !it.categoriesExpanded) }

    // ── Editing the plan ────────────────────────────────────────────────────

    fun setAccountTarget(externalId: String, accountId: Long?) = updateAccount(externalId) {
        // Pointing at an existing account settles the currency too - it is that account's
        // - so a currency the user had picked stops mattering rather than staying to
        // contradict it.
        it.copy(targetAccountId = accountId)
    }

    fun setAccountIncluded(externalId: String, included: Boolean) = updateAccount(externalId) {
        it.copy(included = included)
    }

    fun setAccountCurrency(externalId: String, code: String?) = updateAccount(externalId) {
        it.copy(currencyCode = code)
    }

    fun setCategoryTarget(externalId: String, categoryId: Long?) = _state.update { state ->
        val plan = state.plan ?: return@update state
        state.copy(
            plan = plan.copy(
                categories = plan.categories.map {
                    if (it.externalId == externalId) it.copy(targetCategoryId = categoryId) else it
                }
            )
        )
    }

    private fun updateAccount(
        externalId: String,
        block: (com.personalmoney.core.domain.model.HomeMoneyAccountMapping) ->
        com.personalmoney.core.domain.model.HomeMoneyAccountMapping,
    ) = _state.update { state ->
        val plan = state.plan ?: return@update state
        state.copy(
            plan = plan.copy(
                accounts = plan.accounts.map { if (it.externalId == externalId) block(it) else it }
            )
        )
    }

    // ── Running it ──────────────────────────────────────────────────────────

    fun start() {
        val plan = _state.value.plan ?: return
        if (!plan.canStart) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val progress = repository.start(plan.accounts, plan.categories)
                _state.update { it.copy(busy = false, progress = progress) }
                pollProgress()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = messageFor(e)) }
            }
        }
    }

    /**
     * Watches the background import until it stops.
     *
     * Polling rather than a stream: the work runs on the server across several minutes and
     * a browser tab has nothing pushing to it. Every second and a half is often enough to
     * feel live and rare enough that a long import is not a few thousand requests.
     */
    private fun pollProgress() {
        viewModelScope.launch {
            while (_state.value.progress.running) {
                delay(POLL_INTERVAL_MS)
                val progress = try {
                    repository.progress()
                } catch (e: Exception) {
                    // A dropped poll is not a failed import. Stop watching and say so;
                    // the ledger is unaffected either way.
                    _state.update { it.copy(error = messageFor(e)) }
                    return@launch
                }
                _state.update { it.copy(progress = progress) }
            }
            // The import created accounts and categories directly on the server, so the
            // local caches are a step behind until they are told.
            accountRepository.refresh()
            categoryRepository.refresh()
            transactionRepository.refresh()
        }
    }

    private fun withBusy(block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = messageFor(e)) }
            }
        }
    }

    private suspend fun messageFor(e: Exception): String =
        e.serverMessage() ?: getString(Res.string.common_request_failed)

    private companion object {
        const val POLL_INTERVAL_MS = 1_500L
    }
}
