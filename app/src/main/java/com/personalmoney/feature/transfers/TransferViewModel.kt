package com.personalmoney.feature.transfers

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.R
import com.personalmoney.core.calc.Calculator
import com.personalmoney.core.calc.ExpressionInput
import com.personalmoney.core.currency.FxSnapshot
import com.personalmoney.core.data.repository.AccountRepository
import com.personalmoney.core.data.repository.ExchangeRateRepository
import com.personalmoney.core.data.repository.TransactionRepository
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.ui.components.NumpadKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.UUID
import javax.inject.Inject

/** Which transfer amount field the numpad currently edits. */
enum class TransferAmountField { FROM, TO, FEE }

data class TransferUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val fromAccount: AccountEntity? = null,
    val toAccount: AccountEntity? = null,
    val amountFrom: String = "",
    val amountTo: String = "",
    /** True once the user has typed the destination amount themselves, so auto-conversion stops overwriting it. */
    val amountToEdited: Boolean = false,
    /** Human-readable FX rate hint, e.g. "1 USD ≈ 0.92 EUR"; blank when same-currency or no rate available. */
    val rate: String = "",
    val feeFrom: String = "0",
    val note: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
) {
    val sameCurrency: Boolean
        get() = fromAccount?.currencyCode == toAccount?.currencyCode

    val canSave: Boolean
        get() = fromAccount != null && toAccount != null && fromAccount.id != toAccount.id &&
                amountFrom.isNotBlank() && !saving
}

@HiltViewModel
class TransferViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    accountRepository: AccountRepository,
    exchangeRateRepository: ExchangeRateRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val internalState = MutableStateFlow(TransferUiState())
    val uiState: StateFlow<TransferUiState> = internalState.asStateFlow()

    @Volatile
    private var fxSnapshot: FxSnapshot = FxSnapshot.EMPTY

    init {
        // Materialise the account list and default the from/to selection into the single source of
        // truth so the imperative auto-conversion can read the resolved accounts.
        viewModelScope.launch {
            accountRepository.observeActive().collect { accounts ->
                internalState.update { s ->
                    val from = s.fromAccount ?: accounts.firstOrNull()
                    val to = s.toAccount ?: accounts.firstOrNull { it.id != from?.id }
                    s.copy(accounts = accounts, fromAccount = from, toAccount = to)
                }
                recomputeDestination()
            }
        }
        viewModelScope.launch {
            exchangeRateRepository.observeAll().collect { rates ->
                fxSnapshot = FxSnapshot.from(rates)
                recomputeDestination()
            }
        }
    }

    fun setFromAccount(account: AccountEntity) {
        internalState.update { it.copy(fromAccount = account, amountToEdited = false) }
        recomputeDestination()
    }

    fun setToAccount(account: AccountEntity) {
        internalState.update { it.copy(toAccount = account, amountToEdited = false) }
        recomputeDestination()
    }

    fun setAmountFrom(value: String) {
        internalState.update { it.copy(amountFrom = value) }
        recomputeDestination()
    }

    fun setAmountTo(value: String) = internalState.update { it.copy(amountTo = value, amountToEdited = true) }
    fun setFee(value: String) = internalState.update { it.copy(feeFrom = value) }
    fun setNote(value: String) = internalState.update { it.copy(note = value) }
    fun clearError() = internalState.update { it.copy(error = null) }

    /** Routes a numpad key into the currently-focused transfer amount field. */
    fun onKey(field: TransferAmountField, key: NumpadKey) {
        if (key == NumpadKey.DONE) {
            save()
            return
        }
        internalState.update { s ->
            when (field) {
                TransferAmountField.FROM -> s.copy(amountFrom = applyKey(s.amountFrom, key, isFee = false))
                TransferAmountField.FEE -> s.copy(feeFrom = applyKey(s.feeFrom, key, isFee = true))
                TransferAmountField.TO -> {
                    // The first edit to an auto-converted destination starts fresh and switches to manual mode.
                    val current = if (s.amountToEdited) s.amountTo else ""
                    s.copy(amountTo = applyKey(current, key, isFee = false), amountToEdited = true)
                }
            }
        }
        if (field == TransferAmountField.FROM) recomputeDestination()
    }

    private fun applyKey(current: String, key: NumpadKey, isFee: Boolean): String = when (key) {
        NumpadKey.BACKSPACE -> current.dropLast(1)
        else -> {
            // Fee starts at "0"; treat a lone "0" as empty so the first keypress replaces it.
            val base = if (isFee && current == "0") "" else current
            ExpressionInput.append(base, key)
        }
    }

    /**
     * Keeps the destination amount in sync for a cross-currency transfer by converting the source
     * amount at the stored FX rate. Skipped once the user has typed their own destination amount.
     */
    private fun recomputeDestination() {
        internalState.update { s ->
            val from = s.fromAccount
            val to = s.toAccount
            when {
                from == null || to == null -> s
                from.currencyCode == to.currencyCode -> s.copy(rate = "")
                s.amountToEdited -> s
                else -> s.copy(
                    amountTo = convertAmount(s.amountFrom, from.currencyCode, to.currencyCode),
                    rate = formatRate(from.currencyCode, to.currencyCode)
                )
            }
        }
    }

    /**
     * Converts a source expression into a plain destination amount string. Falls back to a 1:1
     * value when no rate is available so the destination is never left blank (the user can correct it).
     */
    private fun convertAmount(expr: String, from: String, to: String): String {
        if (expr.isBlank()) return ""
        val fromMajor = Calculator.evaluate(expr, currencyDecimals(from)) ?: return ""
        val rate = fxSnapshot.rate(from, to)
        val toMajor = if (rate != null) fromMajor.multiply(BigDecimal.valueOf(rate)) else fromMajor
        return toMajor.setScale(currencyDecimals(to), RoundingMode.HALF_UP).toPlainString()
    }

    private fun formatRate(from: String, to: String): String {
        val rate = fxSnapshot.rate(from, to) ?: return ""
        val shown = BigDecimal.valueOf(rate).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return "1 $from ≈ $shown $to"
    }

    fun save() {
        val s = uiState.value
        if (!s.canSave) return
        val from = s.fromAccount ?: return
        val to = s.toAccount ?: return
        val fromDecimals = currencyDecimals(from.currencyCode)
        val toDecimals = currencyDecimals(to.currencyCode)

        val amountFromBd = Calculator.evaluate(s.amountFrom, fromDecimals) ?: run {
            internalState.update { it.copy(error = appContext.getString(R.string.transfer_invalid_source)) }; return
        }
        val amountToBd = if (s.sameCurrency) {
            amountFromBd
        } else if (s.amountTo.isBlank()) {
            // No destination entered (e.g. no FX rate to auto-fill) — convert the source, or 1:1 as last resort.
            val rate = fxSnapshot.rate(from.currencyCode, to.currencyCode)
            if (rate != null) amountFromBd.multiply(BigDecimal.valueOf(rate)) else amountFromBd
        } else {
            Calculator.evaluate(s.amountTo, toDecimals) ?: run {
                internalState.update { it.copy(error = appContext.getString(R.string.transfer_invalid_destination)) }; return
            }
        }
        val feeBd = if (s.feeFrom.isBlank()) BigDecimal.ZERO
        else Calculator.evaluate(s.feeFrom, fromDecimals) ?: BigDecimal.ZERO

        val amountFromMinor = amountFromBd.movePointRight(fromDecimals).setScale(0, RoundingMode.HALF_UP).toLong()
        val amountToMinor = amountToBd.movePointRight(toDecimals).setScale(0, RoundingMode.HALF_UP).toLong()
        val feeMinor = feeBd.movePointRight(fromDecimals).setScale(0, RoundingMode.HALF_UP).toLong()

        val now = Clock.System.now()
        val groupId = UUID.randomUUID().toString()
        val totalFromMinor = amountFromMinor + feeMinor

        val debit = TransactionEntity(
            type = TransactionType.TRANSFER,
            accountId = from.id,
            categoryId = null,
            amountMinor = -totalFromMinor,
            currencyCode = from.currencyCode,
            accountAmountMinor = -totalFromMinor,
            fxRateToAccount = 1.0,
            occurredAt = now,
            createdAt = now,
            note = s.note.takeIf { it.isNotBlank() },
            transferGroupId = groupId,
            transferCounterpartAccountId = to.id,
            transferFeeMinor = if (feeMinor > 0) feeMinor else null
        )
        val credit = TransactionEntity(
            type = TransactionType.TRANSFER,
            accountId = to.id,
            categoryId = null,
            amountMinor = amountToMinor,
            currencyCode = to.currencyCode,
            accountAmountMinor = amountToMinor,
            fxRateToAccount = 1.0,
            occurredAt = now,
            createdAt = now,
            note = s.note.takeIf { it.isNotBlank() },
            transferGroupId = groupId,
            transferCounterpartAccountId = from.id
        )

        internalState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { transactionRepository.insertTransferPair(debit, credit) }
                .onSuccess { internalState.update { it.copy(saving = false, saved = true) } }
                .onFailure { e -> internalState.update { it.copy(saving = false, error = e.message) } }
        }
    }

    private fun currencyDecimals(code: String): Int =
        runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2)
}
