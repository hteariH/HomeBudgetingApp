package com.personalmoney.feature.homemoney

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.CategoryRepository
import com.personalmoney.core.data.CurrencyRepository
import com.personalmoney.core.data.HomeMoneyImportRepository
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.domain.model.Currency
import com.personalmoney.core.domain.model.HomeMoneyConnection
import com.personalmoney.core.domain.model.HomeMoneyImportPlan
import com.personalmoney.core.domain.model.HomeMoneyImportProgress
import com.personalmoney.core.network.serverMessage
import com.personalmoney.resources.Res
import com.personalmoney.resources.common_request_failed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val currencyRepository: CurrencyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeMoneyImportUiState())
    val state: StateFlow<HomeMoneyImportUiState> = _state.asStateFlow()

    init {
        observeLocalData()
        refresh()
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
     * Also what runs when the screen opens after HomeMoney has redirected back, which is
     * why the outcome is read from the server rather than from the redirect's own query
     * parameter: the server is the side that knows whether a token was actually stored.
     */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val connection = repository.connection()
                val progress = repository.progress()
                _state.update { it.copy(loading = false, connection = connection, progress = progress) }
                // An import left running by an earlier visit is still running on the
                // server; pick the watch back up rather than showing a stale figure.
                if (progress.running) pollProgress()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = messageFor(e)) }
            }
        }
    }

    fun connect() = withBusy {
        val url = repository.authorizationUrl()
        _state.update { it.copy(authorizationUrl = url) }
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
