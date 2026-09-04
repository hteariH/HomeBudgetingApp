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
    val canSave: Boolean get() = isAmountValid && selectedAccount != null && selectedCategory != null && !saving
}

private fun currentDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class QuickAddViewModel constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val userPreferences: UserPreferences
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
    }

    /** Saves from the button, or from the keyboard's own Done action. */
    fun submit() = save()

    fun selectAccount(account: Account) {
        selectedAccountIdFlow.value = account.id
    }

    fun selectAccountById(id: Long) {
        selectedAccountIdFlow.value = id
    }

    fun selectCategory(category: Category) {
        selectedCategoryIdFlow.value = category.id
    }

    fun setNote(value: String) {
        noteFlow.value = value
    }

    fun setDate(date: LocalDate) {
        dateFlow.value = date
    }

    fun setType(type: TransactionType) {
        typeFlow.value = type
        selectedCategoryIdFlow.value = null
    }

    fun clearError() { errorFlow.value = null }

    private fun save() {
        if (savingFlow.value) return
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
            note = state.note.takeIf { it.isNotBlank() }
        )

        viewModelScope.launch {
            savingFlow.value = true
            errorFlow.value = null
            runCatching {
                transactionRepository.insert(tx)
                userPreferences.setDefaultAccount(account.id)
                userPreferences.incrementTransactionSaveCount()
            }.onSuccess {
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
