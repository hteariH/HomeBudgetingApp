package com.personalmoney.feature.quickadd

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.calc.Calculator
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.repository.AccountRepository
import com.personalmoney.core.data.repository.CategoryRepository
import com.personalmoney.core.data.repository.TransactionRepository
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.database.entity.CategoryEntity
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.ui.components.NumpadKey
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal
import javax.inject.Inject

data class QuickAddUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val expression: String = "",
    val previewResult: String? = null,
    val isAmountValid: Boolean = false,
    val accounts: List<AccountEntity> = emptyList(),
    val selectedAccount: AccountEntity? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val recentCategoryIds: List<Long> = emptyList(),
    val selectedCategory: CategoryEntity? = null,
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

@HiltViewModel
class QuickAddViewModel @Inject constructor(
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
            runCatching { java.util.Currency.getInstance(acc.currencyCode).defaultFractionDigits }.getOrDefault(2)
        } ?: 2
        val evaluated = Calculator.evaluate(expr, decimals)
        val showPreview = Calculator.hasOperator(expr) && evaluated != null

        QuickAddUiState(
            type = type,
            expression = expr,
            previewResult = if (showPreview) evaluated!!.toPlainString() else null,
            isAmountValid = evaluated != null && evaluated.compareTo(BigDecimal.ZERO) > 0,
            accounts = accounts,
            selectedAccount = accounts.firstOrNull { it.id == accId } ?: accounts.firstOrNull(),
            categories = categories,
            recentCategoryIds = recentRows.map { it.categoryId },
            selectedCategory = categories.firstOrNull { it.id == catId },
            note = note,
            date = date,
            saving = saving,
            saved = saved,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickAddUiState(type = initialType))

    val numpadPhoneLayout: StateFlow<Boolean> = userPreferences.numpadPhoneLayout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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

    fun onKey(key: NumpadKey) {
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

            // Sanitize: don't allow leading operator (except minus), no double operators, no double dot in current number
            val sanitized = when {
                symbol.isEmpty() -> current
                symbol == "." -> {
                    val lastNumberPart = current.takeLastWhile { it.isDigit() || it == '.' }
                    if (lastNumberPart.contains('.')) current
                    else if (current.isEmpty() || current.last() in "+−×÷") current + "0."
                    else current + "."
                }
                symbol in setOf("+", "−", "×", "÷") -> {
                    if (current.isEmpty()) {
                        if (symbol == "−") symbol else current
                    } else if (current.last() in "+−×÷") {
                        current.dropLast(1) + symbol
                    } else if (current.last() == '.') {
                        current
                    } else {
                        current + symbol
                    }
                }
                else -> current + symbol
            }
            sanitized
        }
    }

    fun selectAccount(account: AccountEntity) {
        selectedAccountIdFlow.value = account.id
    }

    fun selectAccountById(id: Long) {
        selectedAccountIdFlow.value = id
    }

    fun selectCategory(category: CategoryEntity) {
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
        val decimals = runCatching {
            java.util.Currency.getInstance(account.currencyCode).defaultFractionDigits
        }.getOrDefault(2)
        val amount = Calculator.evaluate(state.expression, decimals) ?: return
        val amountMinor = amount.movePointRight(decimals).toLong()
        val now = Clock.System.now()
        val occurredAt = run {
            val tz = TimeZone.currentSystemDefault()
            val nowLdt = now.toLocalDateTime(tz)
            kotlinx.datetime.LocalDateTime(
                date = state.date,
                time = nowLdt.time
            ).toInstant(tz)
        }
        val tx = TransactionEntity(
            type = state.type,
            accountId = account.id,
            categoryId = category.id,
            amountMinor = amountMinor,
            currencyCode = account.currencyCode,
            accountAmountMinor = amountMinor,
            fxRateToAccount = 1.0,
            occurredAt = occurredAt,
            createdAt = now,
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
