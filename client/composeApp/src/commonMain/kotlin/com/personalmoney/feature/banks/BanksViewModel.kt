package com.personalmoney.feature.banks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.BankRepository
import com.personalmoney.core.data.CategoryRepository
import com.personalmoney.core.data.TransactionRepository
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.BankAuthMode
import com.personalmoney.core.domain.model.BankConnection
import com.personalmoney.core.domain.model.BankProvider
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.domain.model.ConsentChallenge
import com.personalmoney.core.domain.model.ImportResult
import com.personalmoney.core.domain.model.PendingImport
import com.personalmoney.core.domain.model.RemoteAccount
import com.personalmoney.core.network.serverMessage
import com.personalmoney.resources.Res
import com.personalmoney.resources.common_request_failed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString

/** What the connect dialog is showing, or null when it is closed. */
data class ConnectDraft(
    val provider: BankProvider,
    /** Keyed by field name, so a provider wanting two inputs needs no new state here. */
    val values: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
    val error: String? = null,
) {
    /** Every required field filled. The bank judges the rest; this only stops empty posts. */
    val canSubmit: Boolean
        get() = !busy && provider.credentialFields
            .filter { it.required }
            .all { values[it.name]?.isNotBlank() == true }
}

/**
 * A connection waiting on the user to approve it in the bank's app.
 *
 * Separate from [ConnectDraft] because nothing about it is a form: there is nothing to
 * type, nothing to validate, and no submit. What the user does happens on another device
 * entirely, and all this holds is what to show them while it does.
 */
data class ConsentDraft(
    val provider: BankProvider,
    /** Null until the bank has issued one - the first moment there is a QR to show. */
    val challenge: ConsentChallenge? = null,
    val error: String? = null,
)

/** The statement range, and what a preview of it turned up. */
data class ImportDraft(
    val accountId: Long? = null,
    val from: LocalDate,
    val to: LocalDate,
    /** Null until a preview has run; empty means the range held nothing. */
    val rows: List<PendingImport>? = null,
    /** External id to category. This is the selection the confirm sends, not just a display. */
    val categories: Map<String, Long?> = emptyMap(),
    /**
     * External id to the other account, for lines the user marked as a transfer instead of
     * giving them a category. Separate from [categories] because the two have different
     * consequences: one is spending, the other only moved money the user already had.
     */
    val transfers: Map<String, Long?> = emptyMap(),
    val busy: Boolean = false,
    /** Held rather than formatted, so the screen renders it in the user's language. */
    val result: ImportResult? = null,
    val error: String? = null,
) {
    /** Rows that would actually land. The rest are already in the ledger. */
    val freshCount: Int get() = rows?.count { !it.alreadyImported } ?: 0
}

data class BanksUiState(
    val import: ImportDraft,
    val loading: Boolean = true,
    val providers: List<BankProvider> = emptyList(),
    val connections: List<BankConnection> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val error: String? = null,
    val connect: ConnectDraft? = null,
    /** The consent dialog, while one is open. Never open at the same time as [connect]. */
    val consent: ConsentDraft? = null,
    /** The connection whose bank-side accounts are expanded, if any. */
    val openConnectionId: Long? = null,
    val remoteAccounts: List<RemoteAccount> = emptyList(),
    val remoteBusy: Boolean = false,
) {
    /**
     * Which local accounts a bank feeds.
     *
     * Read off the connections rather than off the account, because linkage is a fact about
     * the bank side. The shared [Account] has no bank field and giving it one would put a
     * column on the phone that Room can never fill.
     */
    val linkedAccountIds: Set<Long>
        get() = connections.flatMapTo(mutableSetOf()) { it.linkedAccountIds }

    /** The only accounts a statement can be imported into. */
    val linkedAccounts: List<Account> get() = accounts.filter { it.id in linkedAccountIds }

    val importAccount: Account? get() = accounts.firstOrNull { it.id == import.accountId }

    /** Local accounts free to be linked, in the currency the bank holds the remote one in. */
    fun linkCandidates(currencyCode: String): List<Account> =
        accounts.filter { it.id !in linkedAccountIds && it.currencyCode == currencyCode }

    fun categoriesFor(type: TransactionType): List<Category> {
        val wanted = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        return categories.filter { it.type == wanted && !it.archived }
    }
}

/**
 * Bank connections and statement import.
 *
 * Nothing here knows which banks exist. The provider list and each provider's connect form
 * come from the server, so adding a bank is a server change alone - the same property the
 * React page was built around, and the reason the form is rendered from metadata.
 *
 * Bound only in the web graph; see [BankRepository] for why the phone has no bank screen.
 */
class BanksViewModel(
    private val banks: BankRepository,
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
) : ViewModel() {

    private val providersFlow = MutableStateFlow<List<BankProvider>>(emptyList())
    private val loadingFlow = MutableStateFlow(true)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val connectFlow = MutableStateFlow<ConnectDraft?>(null)
    private val consentFlow = MutableStateFlow<ConsentDraft?>(null)
    private val openConnectionFlow = MutableStateFlow<Long?>(null)
    private val remoteFlow = MutableStateFlow<List<RemoteAccount>>(emptyList())
    private val remoteBusyFlow = MutableStateFlow(false)
    private val importFlow = MutableStateFlow(defaultImportDraft())

    /** The four lists the screen draws from, whatever the user is currently doing to them. */
    private data class Lists(
        val providers: List<BankProvider>,
        val connections: List<BankConnection>,
        val accounts: List<Account>,
        val categories: List<Category>,
    )

    /** Everything that is about the screen rather than about the data. */
    private data class Screen(
        val loading: Boolean,
        val error: String?,
        val connect: ConnectDraft?,
        val openConnectionId: Long?,
        val remoteAccounts: List<RemoteAccount>,
    )

    private val lists = combine(
        providersFlow,
        banks.observeConnections(),
        accounts.observeActive(),
        categories.observeAll(),
        ::Lists,
    )

    private val screen = combine(
        loadingFlow,
        errorFlow,
        connectFlow,
        openConnectionFlow,
        remoteFlow,
        ::Screen,
    )

    val uiState: StateFlow<BanksUiState> =
        combine(lists, screen, remoteBusyFlow, importFlow, consentFlow) {
                data, ui, remoteBusy, import, consent ->
            BanksUiState(
                import = import,
                loading = ui.loading,
                providers = data.providers,
                connections = data.connections,
                accounts = data.accounts,
                categories = data.categories,
                error = ui.error,
                connect = ui.connect,
                consent = consent,
                openConnectionId = ui.openConnectionId,
                remoteAccounts = ui.remoteAccounts,
                remoteBusy = remoteBusy,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BanksUiState(import = defaultImportDraft()),
        )

    init {
        viewModelScope.launch {
            runReporting { providersFlow.value = banks.providers() }
            loadingFlow.value = false
        }
    }

    fun clearError() { errorFlow.value = null }

    // ── connecting ───────────────────────────────────────────────────────────

    /**
     * Both ways of connecting start here, because the screen has one button per bank and
     * should not know which kind it is pressing.
     */
    fun openConnect(provider: BankProvider) = when (provider.authMode) {
        BankAuthMode.CONSENT -> startConsent(provider)
        BankAuthMode.CREDENTIALS -> connectFlow.value = ConnectDraft(provider)
    }

    fun closeConnect() { connectFlow.value = null }

    fun setCredential(name: String, value: String) = connectFlow.update { draft ->
        draft?.copy(values = draft.values + (name to value), error = null)
    }

    fun submitConnect() {
        val draft = connectFlow.value ?: return
        if (!draft.canSubmit) return
        connectFlow.update { it?.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                banks.connect(draft.provider.id, draft.values)
                connectFlow.value = null
            } catch (e: Exception) {
                // Left open on failure. A rejected token is a typo often enough that
                // throwing the form away would be the wrong response to the commonest case.
                val message = e.serverMessage() ?: getString(Res.string.common_request_failed)
                connectFlow.update { it?.copy(busy = false, error = message) }
            }
        }
    }

    // ── consent ────────────────────────────────────────────────────────────

    /**
     * The poll, and the thing that stops it.
     *
     * Held rather than fired and forgotten because it outlives nothing else on this screen:
     * closing the dialog has to end it, and a second attempt must not leave the first one
     * running and able to connect an account the user walked away from.
     */
    private var consentJob: Job? = null

    /**
     * Asks the bank for a request the user can approve, then waits for them to.
     *
     * The wait is a poll because there is nothing to subscribe to: the approval happens on
     * the user's phone, in the bank's app, and reaches this app only when it next asks.
     * Every tick is a real request to the bank via the server, which is why it is seconds
     * apart rather than continuous.
     */
    private fun startConsent(provider: BankProvider) {
        consentJob?.cancel()
        consentFlow.value = ConsentDraft(provider)
        consentJob = viewModelScope.launch {
            val challenge = try {
                banks.beginConsent(provider.id)
            } catch (e: CancellationException) {
                // Not a failure, and by now the draft this was writing into may be a *newer*
                // attempt's - retrying cancels the old job and replaces the draft in the
                // same breath. Reporting here would put "cancelled" on the live dialog.
                throw e
            } catch (e: Exception) {
                consentFlow.update { it?.copy(error = messageFor(e)) }
                return@launch
            }
            consentFlow.update { it?.copy(challenge = challenge) }

            while (isActive) {
                delay(CONSENT_POLL_MILLIS)
                val connected = try {
                    banks.pollConsent(provider.id, challenge.reference)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Terminal: the request expired, or the bank refused it. The dialog
                    // stays open holding the reason, because closing it would leave the
                    // user with a QR that stopped working and no idea why.
                    consentFlow.update { it?.copy(error = messageFor(e)) }
                    return@launch
                }
                if (connected) {
                    consentFlow.value = null
                    return@launch
                }
            }
        }
    }

    /** Retries from the top: the reference is what expired, so a fresh one is the fix. */
    fun retryConsent() {
        consentFlow.value?.provider?.let { startConsent(it) }
    }

    fun closeConsent() {
        consentJob?.cancel()
        consentJob = null
        consentFlow.value = null
    }

    // ── connections ──────────────────────────────────────────────────────────

    fun verify(connectionId: Long) = launchReporting { banks.verify(connectionId) }

    fun disconnect(connectionId: Long) = launchReporting {
        banks.disconnect(connectionId)
        if (openConnectionFlow.value == connectionId) collapseAccounts()
    }

    /** Expands a connection's bank-side accounts, or collapses it if it is already open. */
    fun toggleAccounts(connectionId: Long) {
        if (openConnectionFlow.value == connectionId) {
            collapseAccounts()
            return
        }
        openConnectionFlow.value = connectionId
        loadRemoteAccounts(connectionId)
    }

    fun link(connectionId: Long, accountId: Long, externalAccountId: String) = launchReporting {
        banks.link(connectionId, accountId, externalAccountId)
        loadRemoteAccounts(connectionId)
    }

    fun unlink(connectionId: Long, accountId: Long) = launchReporting {
        banks.unlink(accountId)
        // An unlinked account cannot be imported into, so a range aimed at one has to go
        // too rather than sit there waiting to fail.
        if (importFlow.value.accountId == accountId) importFlow.value = defaultImportDraft()
        loadRemoteAccounts(connectionId)
    }

    private fun collapseAccounts() {
        openConnectionFlow.value = null
        remoteFlow.value = emptyList()
    }

    /**
     * Asks the bank what these credentials can see. Every call is a live request - there is
     * nothing cached to fall back on, which is why a failure closes the section rather than
     * leaving an empty table that looks like "this bank has no accounts".
     */
    private fun loadRemoteAccounts(connectionId: Long) {
        remoteBusyFlow.value = true
        viewModelScope.launch {
            try {
                remoteFlow.value = banks.remoteAccounts(connectionId)
            } catch (e: Exception) {
                errorFlow.value = e.serverMessage() ?: getString(Res.string.common_request_failed)
                collapseAccounts()
            } finally {
                remoteBusyFlow.value = false
            }
        }
    }

    // ── import ───────────────────────────────────────────────────────────────

    /** Picking another account drops the preview: those rows belonged to the old one. */
    fun setImportAccount(accountId: Long?) = importFlow.update {
        it.copy(
            accountId = accountId,
            rows = null,
            categories = emptyMap(),
            transfers = emptyMap(),
            result = null,
            error = null,
        )
    }

    fun setImportFrom(date: LocalDate) = importFlow.update {
        it.copy(from = date, rows = null, result = null)
    }

    fun setImportTo(date: LocalDate) = importFlow.update {
        it.copy(to = date, rows = null, result = null)
    }

    /**
     * Records one line's choice from the shared dropdown - see [ImportChoice].
     *
     * Exclusive by construction: a line is either spending in a category or a move between
     * the user's own accounts, and a stale category left behind would come back the moment
     * they changed their mind.
     */
    fun setRowChoice(externalId: String, optionId: Long?) = importFlow.update {
        it.copy(
            categories = it.categories + (externalId to ImportChoice.categoryIdOf(optionId)),
            transfers = it.transfers + (externalId to ImportChoice.accountIdOf(optionId)),
        )
    }

    fun preview() {
        val draft = importFlow.value
        val accountId = draft.accountId ?: return
        importFlow.update { it.copy(busy = true, error = null, result = null) }
        viewModelScope.launch {
            try {
                val rows = banks.previewImport(accountId, draft.from, draft.to)
                importFlow.update {
                    it.copy(
                        busy = false,
                        rows = rows,
                        // Pre-filled from the server's MCC guess, and still only a
                        // suggestion: nothing is written until the user presses import.
                        categories = rows.filter { row -> !row.alreadyImported }
                            .associate { row -> row.externalId to row.suggestedCategoryId },
                        transfers = emptyMap(),
                    )
                }
            } catch (e: Exception) {
                importFlow.update { it.copy(busy = false, error = messageFor(e)) }
            }
        }
    }

    fun confirmImport() {
        val draft = importFlow.value
        val accountId = draft.accountId ?: return
        val rows = draft.rows ?: return
        if (draft.freshCount == 0) return
        importFlow.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                // Fresh rows only. The map is the selection as well as the categorisation,
                // so leaving the duplicates out of it is what keeps them out of the ledger.
                val fresh = rows.filter { !it.alreadyImported }
                val selection = fresh.associate { it.externalId to draft.categories[it.externalId] }
                // Only the lines actually marked as transfers: a null entry here means "no
                // counterpart", which is what every other line is.
                val transfers = fresh.mapNotNull { row ->
                    draft.transfers[row.externalId]?.let { row.externalId to it }
                }.toMap()
                val result =
                    banks.confirmImport(accountId, draft.from, draft.to, selection, transfers)
                importFlow.update {
                    it.copy(
                        busy = false,
                        rows = null,
                        categories = emptyMap(),
                        transfers = emptyMap(),
                        result = result,
                    )
                }
                onLedgerChanged()
            } catch (e: Exception) {
                importFlow.update { it.copy(busy = false, error = messageFor(e)) }
            }
        }
    }

    /**
     * An import wrote transactions, so every cache that derives from the ledger is now
     * wrong - balances above all. Room would have pushed this out on its own; a REST cache
     * has to be told, and the import is the only place that knows it happened.
     */
    fun onLedgerChanged() {
        viewModelScope.launch {
            runReporting {
                accounts.refresh()
                transactions.refresh()
            }
        }
    }

    // ── plumbing ─────────────────────────────────────────────────────────────

    private fun launchReporting(block: suspend () -> Unit) {
        viewModelScope.launch { runReporting(block) }
    }

    /**
     * Reports into the page-level banner. The connect dialog and the import panel keep
     * their own error instead, so a failure is shown beside the thing that failed.
     */
    private suspend fun runReporting(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            errorFlow.value = messageFor(e)
        }
    }

    /** The bank's own explanation when there is one - see `serverMessage`. */
    private suspend fun messageFor(e: Exception): String =
        e.serverMessage() ?: getString(Res.string.common_request_failed)

    private companion object {
        /**
         * Every tick is a real request to the bank. Slow enough not to hammer it, fast
         * enough that the dialog closes while the user is still holding their phone.
         */
        const val CONSENT_POLL_MILLIS = 2_500L
    }
}

/** A month back to today: the range a statement is most often wanted for. */
private fun defaultImportDraft(): ImportDraft {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return ImportDraft(from = today.minus(DatePeriod(months = 1)), to = today)
}
