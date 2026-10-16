package com.personalmoney.feature.quickadd

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.calc.Calculator
import com.personalmoney.core.calc.ExpressionInput
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.CategoryRepository
import com.personalmoney.core.data.TransactionRepository
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.domain.model.Transaction
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.receipt.ReceiptDraft
import com.personalmoney.core.receipt.ReceiptScanner
import com.personalmoney.core.receipt.ScanOutcome
import com.personalmoney.core.receipt.receiptScanAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.personalmoney.core.domain.toMinorUnits

data class QuickAddUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val expression: String = "",
    val previewResult: String? = null,
    val isAmountValid: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    val categories: List<Category> = emptyList(),
    val recentCategoryIds: List<Long> = emptyList(),
    val selectedCategory: Category? = null,
    val note: String = "",
    val date: LocalDate = currentDate(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
) {
    val currencyCode: String? get() = selectedAccount?.currencyCode
    val canSave: Boolean
        get() = isAmountValid && selectedAccount != null && selectedCategory != null && !saving && !saved
}

/** A receipt in [receipt] currency, read into a form on an account in [account] currency. */
data class CurrencyDiffers(val receipt: String, val account: String)

/** Something a scan has to tell the user, once. */
sealed interface ScanNotice {
    /**
     * The form was filled. [withMerchant]: the note was too, so the screen shows it.
     * [currencyDiffers]: the amount was left alone, because the receipt is in another
     * currency. [totalMismatch]: the items do not add up to the total.
     */
    data class Filled(
        val withMerchant: Boolean,
        val currencyDiffers: CurrencyDiffers? = null,
        val totalMismatch: Boolean = false,
    ) : ScanNotice

    /** [reason] is the server's own sentence, or null for "it did not work". */
    data class Failed(val reason: String?) : ScanNotice

    data object Offline : ScanNotice
}

/**
 * Receipt scanning, kept out of [QuickAddUiState] because none of it feeds the form's own
 * derivation - and that state is already rebuilt from a dozen flows.
 */
data class ReceiptScanState(
    /** The household has a receipt reader. False until the server says so. */
    val canScan: Boolean = false,
    /** A photo is with the server, being read. */
    val reading: Boolean = false,
    val notice: ScanNotice? = null,
)

private fun currentDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class QuickAddViewModel constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val userPreferences: UserPreferences,
    private val receiptScanner: ReceiptScanner,
) : ViewModel() {

    private val initialType: TransactionType = runCatching {
        TransactionType.valueOf(savedStateHandle.get<String>("type") ?: "EXPENSE")
    }.getOrDefault(TransactionType.EXPENSE)

    private val initialCategoryId: Long? =
        savedStateHandle.get<String>("categoryId")?.toLongOrNull()

    private val typeFlow = MutableStateFlow(initialType)
    private val expressionFlow = MutableStateFlow("")
    private val noteFlow = MutableStateFlow("")
    private val dateFlow = MutableStateFlow(currentDate())
    private val selectedAccountIdFlow = MutableStateFlow<Long?>(null)
    private val selectedCategoryIdFlow = MutableStateFlow<Long?>(initialCategoryId)
    private val savingFlow = MutableStateFlow(false)
    private val savedFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    private val _scan = MutableStateFlow(ReceiptScanState())
    val scan: StateFlow<ReceiptScanState> = _scan.asStateFlow()

    /** The receipt the form was last filled from; saved with the entry. */
    private var receiptUuid: String? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val categoriesFlow = typeFlow.flatMapLatest { type ->
        categoryRepository.observeByType(
            if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val recentCategoryIdsFlow = typeFlow.flatMapLatest { type ->
        categoryRepository.observeMostUsed(
            if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE,
            limit = 8
        )
    }

    val uiState: StateFlow<QuickAddUiState> = combine(
        combine(typeFlow, expressionFlow, noteFlow, dateFlow, savedFlow) { t, e, n, d, sav -> Quint(t, e, n, d, sav) },
        combine(savingFlow, errorFlow, selectedAccountIdFlow, selectedCategoryIdFlow) { sav2, err, accId, catId -> Quad(sav2, err, accId, catId) },
        accountRepository.observeActive(),
        categoriesFlow,
        recentCategoryIdsFlow
    ) { primary, secondary, accounts, categories, recentRows ->
        val type = primary.a
        val expr = primary.b
        val note = primary.c
        val date = primary.d
        val saved = primary.e
        val saving = secondary.a
        val error = secondary.b
        val accId = secondary.c
        val catId = secondary.d

        val decimals = accounts.firstOrNull { it.id == accId }?.let { acc ->
            CurrencyRegistry.decimalsOrFallback(acc.currencyCode)
        } ?: 2
        val evaluated = Calculator.evaluate(expr, decimals)
        val showPreview = Calculator.hasOperator(expr) && evaluated != null

        QuickAddUiState(
            type = type,
            expression = expr,
            previewResult = if (showPreview) evaluated!!.toStringExpanded() else null,
            isAmountValid = evaluated != null && evaluated.compareTo(BigDecimal.ZERO) > 0,
            accounts = accounts,
            selectedAccount = accounts.firstOrNull { it.id == accId } ?: accounts.firstOrNull(),
            categories = categories,
            recentCategoryIds = recentRows.map { it.id },
            selectedCategory = categories.firstOrNull { it.id == catId },
            note = note,
            date = date,
            saving = saving,
            saved = saved,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickAddUiState(type = initialType))

    init {
        // Asked each time the screen opens: an owner can set a provider up on the web at
        // any moment. Anything short of a clear yes keeps the button hidden.
        if (receiptScanAvailable) viewModelScope.launch {
            val canScan = try {
                receiptScanner.canScan()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _scan.update { it.copy(canScan = canScan) }
        }
        // Resolve default account on first run.
        viewModelScope.launch {
            if (selectedAccountIdFlow.value == null) {
                val storedDefault = userPreferences.defaultAccountId.first()
                if (storedDefault != null) {
                    selectedAccountIdFlow.value = storedDefault
                } else {
                    accountRepository.observeActive().first().firstOrNull()?.let {
                        selectedAccountIdFlow.value = it.id
                    }
                }
            }
        }
    }

    /**
     * The one way an amount is entered, on every platform: what the keyboard typed, cleaned
     * up. The phone used to build this string a numpad key at a time; now the phone and the
     * browser take the same path, and there is one behaviour to reason about instead of two.
     */
    fun setAmountText(value: String) {
        expressionFlow.value = ExpressionInput.sanitize(value)
        formChanged()
    }

    /** Saves from the button, or from the keyboard's own Done action. */
    fun submit() = save()

    fun selectAccount(account: Account) {
        selectedAccountIdFlow.value = account.id
        formChanged()
    }

    fun selectAccountById(id: Long) {
        selectedAccountIdFlow.value = id
        formChanged()
    }

    fun selectCategory(category: Category) {
        selectedCategoryIdFlow.value = category.id
        formChanged()
    }

    fun setNote(value: String) {
        noteFlow.value = value
        formChanged()
    }

    fun setDate(date: LocalDate) {
        dateFlow.value = date
        formChanged()
    }

    fun setType(type: TransactionType) {
        typeFlow.value = type
        formChanged()
        selectedCategoryIdFlow.value = null
    }

    fun clearError() { errorFlow.value = null }

    /**
     * A saved form cannot be saved again until something in it changes. That is what stops
     * a second tap (or the keyboard's Done) while "Saved" is showing from writing the same
     * entry twice - and, with a receipt, a second row the server refuses because the receipt
     * is already taken, which would sit unsynced forever. The inline panel stays on screen
     * after a save, so the next entry typed into it clears this again.
     */
    private fun formChanged() {
        savedFlow.value = false
    }

    /** Sends the photo to the server to be read, and fills the form from what comes back. */
    fun scanReceipt(jpeg: ByteArray) {
        if (_scan.value.reading) return
        _scan.update { it.copy(reading = true, notice = null) }
        viewModelScope.launch {
            val notice = when (val outcome = receiptScanner.scan(jpeg)) {
                is ScanOutcome.Ready -> ScanNotice.Filled(
                    withMerchant = outcome.draft.merchant != null,
                    currencyDiffers = apply(outcome.draft),
                    totalMismatch = outcome.draft.totalMismatch,
                )
                is ScanOutcome.Failed -> ScanNotice.Failed(outcome.reason)
                ScanOutcome.Offline -> ScanNotice.Offline
            }
            _scan.update { it.copy(reading = false, notice = notice) }
        }
    }

    fun clearScanNotice() {
        _scan.update { it.copy(notice = null) }
    }

    /**
     * Fills what the receipt says, and only that: a field it did not read keeps whatever the
     * user had typed. Everything stays editable - the scan fills the form and stops.
     *
     * The total goes into the amount only when the receipt is in the selected account's
     * currency (or does not say); otherwise the amount is left alone and the currencies are
     * returned, for the screen to explain.
     */
    private suspend fun apply(draft: ReceiptDraft): CurrencyDiffers? {
        // The same account the form shows: the chosen one, else the first.
        val accounts = accountRepository.observeActive().first()
        val account = accounts.firstOrNull { it.id == selectedAccountIdFlow.value } ?: accounts.firstOrNull()
        val differs = draft.currency
            ?.takeIf { account != null && !it.equals(account.currencyCode, ignoreCase = true) }
            ?.let { CurrencyDiffers(receipt = it, account = account!!.currencyCode) }
        if (differs == null) draft.total?.let(::setAmountText)
        draft.date?.let(::setDate)
        draft.merchant?.let(::setNote)
        // Only a category this phone has: the server suggests from the household's list,
        // which a device that has not synced yet may not hold.
        draft.suggestedCategoryUuid?.let { uuid ->
            categoriesFlow.first().firstOrNull { it.uuid == uuid }?.let(::selectCategory)
        }
        receiptUuid = draft.receiptUuid
        return differs
    }

    private fun save() {
        // The flows, not uiState: that one is derived and can lag a tap behind.
        if (savingFlow.value || savedFlow.value) return
        val state = uiState.value
        if (!state.canSave) return
        val account = state.selectedAccount ?: return
        val category = state.selectedCategory ?: return
        val decimals = CurrencyRegistry.decimalsOrFallback(account.currencyCode)
        val amount = Calculator.evaluate(state.expression, decimals) ?: return
        val amountMinor = amount.toMinorUnits(decimals)
        val now = Clock.System.now()
        val occurredAt = run {
            val tz = TimeZone.currentSystemDefault()
            val nowLdt = now.toLocalDateTime(tz)
            kotlinx.datetime.LocalDateTime(
                date = state.date,
                time = nowLdt.time
            ).toInstant(tz)
        }
        val tx = Transaction(
            type = state.type,
            accountId = account.id,
            categoryId = category.id,
            amountMinor = amountMinor,
            currencyCode = account.currencyCode,
            accountAmountMinor = amountMinor,
            fxRateToAccount = 1.0,
            occurredAt = occurredAt,
            note = state.note.takeIf { it.isNotBlank() },
            receiptUuid = receiptUuid,
        )

        // Set before the launch, so a second tap that arrives before the coroutine starts
        // already sees it.
        savingFlow.value = true
        viewModelScope.launch {
            errorFlow.value = null
            runCatching {
                transactionRepository.insert(tx)
                userPreferences.setDefaultAccount(account.id)
            }.onSuccess {
                // A receipt belongs to one entry. The next one starts without it.
                if (receiptUuid == tx.receiptUuid) receiptUuid = null
                savedFlow.value = true
            }.onFailure { e ->
                errorFlow.value = e.message ?: "Save failed"
            }
            savingFlow.value = false
        }
    }
}

private data class Quint<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
