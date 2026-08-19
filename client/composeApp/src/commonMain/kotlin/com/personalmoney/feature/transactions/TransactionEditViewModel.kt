package com.personalmoney.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.calc.Calculator
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.CategoryRepository
import com.personalmoney.core.data.TransactionRepository
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.domain.model.Transaction
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.personalmoney.core.calc.NumpadKey
import com.personalmoney.core.domain.toMinorUnits
import com.personalmoney.core.domain.toMajorUnits
import com.personalmoney.core.domain.toTrimmedString
import com.personalmoney.core.domain.toFixedString

data class TransactionEditUiState(
    val loading: Boolean = true,
    val original: Transaction? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val expression: String = "",
    val previewResult: String? = null,
    val isAmountValid: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val note: String = "",
    val date: LocalDate = LocalDate(2000, 1, 1),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null
) {
    val currencyCode: String? get() = selectedAccount?.currencyCode
    val isTransfer: Boolean get() = original?.transferGroupId != null
    val canSave: Boolean
        get() = !loading && !saving && (
            isTransfer || (isAmountValid && selectedAccount != null && selectedCategory != null)
        )
}

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionEditViewModel constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    // A malformed/missing arg must not crash the screen (e.g. a bad deep link).
    private val transactionId: Long =
        savedStateHandle.get<String>("transactionId")?.toLongOrNull() ?: -1L

    private val txFlow = transactionRepository.observeById(transactionId).filterNotNull()

    private val typeFlow = MutableStateFlow(TransactionType.EXPENSE)
    private val expressionFlow = MutableStateFlow("")
    private val noteFlow = MutableStateFlow("")
    private val dateFlow = MutableStateFlow(
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    )
    private val selectedAccountIdFlow = MutableStateFlow<Long?>(null)
    private val selectedCategoryIdFlow = MutableStateFlow<Long?>(null)
    private val savingFlow = MutableStateFlow(false)
    private val savedFlow = MutableStateFlow(false)
    private val deletedFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val loadingFlow = MutableStateFlow(true)
    private val originalFlow = MutableStateFlow<Transaction?>(null)

    private val categoriesFlow = typeFlow.flatMapLatest { type ->
        categoryRepository.observeByType(
            if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        )
    }

    val uiState: StateFlow<TransactionEditUiState> = combine(
        combine(loadingFlow, originalFlow, typeFlow, expressionFlow, noteFlow) { l, orig, t, e, n ->
            FiveVal(l, orig, t, e, n)
        },
        combine(dateFlow, savingFlow, savedFlow, deletedFlow, errorFlow) { d, sav, saved, del, err ->
            FiveVal(d, sav, saved, del, err)
        },
        combine(selectedAccountIdFlow, selectedCategoryIdFlow) { accId, catId -> accId to catId },
        accountRepository.observeActive(),
        categoriesFlow
    ) { first, second, accCat, accounts, categories ->
        val loading = first.a
        val original = first.b
        val type = first.c
        val expr = first.d
        val note = first.e
        val date = second.a
        val saving = second.b
        val saved = second.c
        val deleted = second.d
        val error = second.e
        val (accId, catId) = accCat

        val selectedAccount = accounts.firstOrNull { it.id == accId } ?: accounts.firstOrNull()
        val decimals = selectedAccount?.let {
            CurrencyRegistry.decimalsOrFallback(it.currencyCode)
        } ?: 2
        val evaluated = Calculator.evaluate(expr, decimals)
        val showPreview = Calculator.hasOperator(expr) && evaluated != null

        TransactionEditUiState(
            loading = loading,
            original = original,
            type = type,
            expression = expr,
            previewResult = if (showPreview) evaluated!!.toFixedString(decimals) else null,
            isAmountValid = evaluated != null && evaluated.compareTo(BigDecimal.ZERO) > 0,
            accounts = accounts,
            selectedAccount = selectedAccount,
            categories = categories,
            selectedCategory = categories.firstOrNull { it.id == catId },
            note = note,
            date = date,
            saving = saving,
            saved = saved,
            deleted = deleted,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionEditUiState())

    val numpadPhoneLayout: StateFlow<Boolean> = userPreferences.numpadPhoneLayout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        if (transactionId <= 0L) {
            // Nothing to load — surface an empty, non-crashing state.
            loadingFlow.value = false
        } else viewModelScope.launch {
            val tx = txFlow.first()
            originalFlow.value = tx
            typeFlow.value = tx.type
            noteFlow.value = tx.note ?: ""
            dateFlow.value = tx.occurredAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
            selectedAccountIdFlow.value = tx.accountId
            selectedCategoryIdFlow.value = tx.categoryId

            // Set expression from amount
            val account = accountRepository.findById(tx.accountId)
            val decimals = account?.let {
                CurrencyRegistry.decimalsOrFallback(it.currencyCode)
            } ?: 2
            val amount = tx.accountAmountMinor.toMajorUnits(decimals)
            expressionFlow.value = amount.toTrimmedString()

            loadingFlow.value = false
        }
    }

    fun onKey(key: NumpadKey) {
        if (uiState.value.isTransfer) return
        when (key) {
            NumpadKey.BACKSPACE -> expressionFlow.update { it.dropLast(1) }
            NumpadKey.DONE -> save()
            else -> appendKey(key)
        }
    }

    private fun appendKey(key: NumpadKey) {
        expressionFlow.update { current ->
            val symbol = when (key) {
                NumpadKey.PLUS -> "+"
                NumpadKey.MINUS -> "−"
                NumpadKey.MULTIPLY -> "×"
                NumpadKey.DIVIDE -> "÷"
                NumpadKey.DOT -> "."
                NumpadKey.DIGIT_0 -> "0"
                NumpadKey.DIGIT_1 -> "1"
                NumpadKey.DIGIT_2 -> "2"
                NumpadKey.DIGIT_3 -> "3"
                NumpadKey.DIGIT_4 -> "4"
                NumpadKey.DIGIT_5 -> "5"
                NumpadKey.DIGIT_6 -> "6"
                NumpadKey.DIGIT_7 -> "7"
                NumpadKey.DIGIT_8 -> "8"
                NumpadKey.DIGIT_9 -> "9"
                else -> ""
            }
            if (symbol.isEmpty()) return@update current

            when {
                symbol == "." -> {
                    val lastNum = current.takeLastWhile { it.isDigit() || it == '.' }
                    if (lastNum.contains('.')) current
                    else if (current.isEmpty() || current.last() in "+−×÷") current + "0."
                    else current + "."
                }
                symbol in setOf("+", "−", "×", "÷") -> {
                    when {
                        current.isEmpty() -> if (symbol == "−") symbol else current
                        current.last() in "+−×÷" -> current.dropLast(1) + symbol
                        current.last() == '.' -> current
                        else -> current + symbol
                    }
                }
                else -> current + symbol
            }
        }
    }

    fun selectAccount(account: Account) { selectedAccountIdFlow.value = account.id }
    fun selectCategory(category: Category) { selectedCategoryIdFlow.value = category.id }
    fun setNote(value: String) { noteFlow.value = value }
    fun setDate(date: LocalDate) { dateFlow.value = date }
    fun setType(type: TransactionType) {
        typeFlow.value = type
        selectedCategoryIdFlow.value = null
    }
    fun clearError() { errorFlow.value = null }

    fun save() {
        if (savingFlow.value) return
        val state = uiState.value
        if (!state.canSave) return
        val original = state.original ?: return

        viewModelScope.launch {
            savingFlow.value = true
            errorFlow.value = null
            runCatching {
                val tz = TimeZone.currentSystemDefault()
                val now = Clock.System.now()
                val nowLdt = now.toLocalDateTime(tz)
                val occurredAt = kotlinx.datetime.LocalDateTime(
                    date = state.date,
                    time = nowLdt.time
                ).toInstant(tz)

                if (state.isTransfer) {
                    // Transfer: only note and date
                    val counterpart = transactionRepository.findTransferPair(original.transferGroupId!!)
                    counterpart.forEach { tx ->
                        transactionRepository.update(
                            tx.copy(
                                note = state.note.takeIf { it.isNotBlank() },
                                occurredAt = occurredAt
                            )
                        )
                    }
                } else {
                    val account = state.selectedAccount ?: return@runCatching
                    val category = state.selectedCategory ?: return@runCatching
                    val decimals = CurrencyRegistry.decimalsOrFallback(account.currencyCode)
                    val amount = Calculator.evaluate(state.expression, decimals) ?: return@runCatching
                    val amountMinor = amount.toMinorUnits(decimals)

                    transactionRepository.update(
                        original.copy(
                            type = state.type,
                            accountId = account.id,
                            categoryId = category.id,
                            amountMinor = amountMinor,
                            currencyCode = account.currencyCode,
                            accountAmountMinor = amountMinor,
                            occurredAt = occurredAt,
                            note = state.note.takeIf { it.isNotBlank() }
                        )
                    )
                }
            }.onSuccess {
                savedFlow.value = true
            }.onFailure { e ->
                errorFlow.value = e.message ?: "Save failed"
            }
            savingFlow.value = false
        }
    }

    fun delete() {
        val original = uiState.value.original ?: return
        viewModelScope.launch {
            runCatching {
                if (original.transferGroupId != null) {
                    transactionRepository.deleteTransfer(original.transferGroupId)
                } else {
                    transactionRepository.delete(original.id)
                }
            }.onSuccess {
                deletedFlow.value = true
            }.onFailure { e ->
                errorFlow.value = e.message ?: "Delete failed"
            }
        }
    }
}

private data class FiveVal<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
