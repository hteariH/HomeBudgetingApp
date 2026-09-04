package com.personalmoney.feature.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.calc.Calculator
import com.personalmoney.core.calc.ExpressionInput
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.currency.FxSnapshot
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.ExchangeRateRepository
import com.personalmoney.core.data.TransactionRepository
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.Transaction
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.personalmoney.core.domain.toMinorUnits
import com.personalmoney.core.domain.roundTo
import com.personalmoney.core.domain.toTrimmedString
import com.personalmoney.core.domain.toFixedString

data class TransferUiState(
    val accounts: List<Account> = emptyList(),
    val fromAccount: Account? = null,
    val toAccount: Account? = null,
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

class TransferViewModel constructor(
    accountRepository: AccountRepository,
    exchangeRateRepository: ExchangeRateRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val internalState = MutableStateFlow(TransferUiState())
    val uiState: StateFlow<TransferUiState> = internalState.asStateFlow()

    @kotlin.concurrent.Volatile
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

    fun setFromAccount(account: Account) {
        internalState.update { it.copy(fromAccount = account, amountToEdited = false) }
        recomputeDestination()
    }

    fun setToAccount(account: Account) {
        internalState.update { it.copy(toAccount = account, amountToEdited = false) }
        recomputeDestination()
    }

    fun setAmountFrom(value: String) {
        internalState.update { it.copy(amountFrom = ExpressionInput.sanitize(value)) }
        recomputeDestination()
    }

    /** Typing here is what makes the destination manual, so the FX rate stops overwriting it. */
    fun setAmountTo(value: String) = internalState.update {
        it.copy(amountTo = ExpressionInput.sanitize(value), amountToEdited = true)
    }

    fun setFee(value: String) = internalState.update {
        it.copy(feeFrom = ExpressionInput.sanitize(value))
    }
    fun setNote(value: String) = internalState.update { it.copy(note = value) }
    fun clearError() = internalState.update { it.copy(error = null) }

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
        val toMajor = if (rate != null) fromMajor.times(BigDecimal.fromDouble(rate)) else fromMajor
        return toMajor.toFixedString(currencyDecimals(to))
    }

    private fun formatRate(from: String, to: String): String {
        val rate = fxSnapshot.rate(from, to) ?: return ""
        val shown = BigDecimal.fromDouble(rate).roundTo(4).toTrimmedString()
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
            failWith(Res.string.transfer_invalid_source); return
        }
        val amountToBd = if (s.sameCurrency) {
            amountFromBd
        } else if (s.amountTo.isBlank()) {
            // No destination entered (e.g. no FX rate to auto-fill) — convert the source, or 1:1 as last resort.
            val rate = fxSnapshot.rate(from.currencyCode, to.currencyCode)
            if (rate != null) amountFromBd.times(BigDecimal.fromDouble(rate)) else amountFromBd
        } else {
            Calculator.evaluate(s.amountTo, toDecimals) ?: run {
                failWith(Res.string.transfer_invalid_destination); return
            }
        }
        val feeBd = if (s.feeFrom.isBlank()) BigDecimal.ZERO
        else Calculator.evaluate(s.feeFrom, fromDecimals) ?: BigDecimal.ZERO

        val amountFromMinor = amountFromBd.toMinorUnits(fromDecimals)
        val amountToMinor = amountToBd.toMinorUnits(toDecimals)
        val feeMinor = feeBd.toMinorUnits(fromDecimals)

        val now = Clock.System.now()
        @OptIn(ExperimentalUuidApi::class)
        val groupId = Uuid.random().toString()
        val totalFromMinor = amountFromMinor + feeMinor

        val debit = Transaction(
            type = TransactionType.TRANSFER,
            accountId = from.id,
            categoryId = null,
            amountMinor = -totalFromMinor,
            currencyCode = from.currencyCode,
            accountAmountMinor = -totalFromMinor,
            fxRateToAccount = 1.0,
            occurredAt = now,
            note = s.note.takeIf { it.isNotBlank() },
            transferGroupId = groupId,
            transferCounterpartAccountId = to.id,
            transferFeeMinor = if (feeMinor > 0) feeMinor else null
        )
        val credit = Transaction(
            type = TransactionType.TRANSFER,
            accountId = to.id,
            categoryId = null,
            amountMinor = amountToMinor,
            currencyCode = to.currencyCode,
            accountAmountMinor = amountToMinor,
            fxRateToAccount = 1.0,
            occurredAt = now,
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

    /**
     * Validation messages are resource-backed, and the multiplatform resource reader is
     * suspend - so the string is resolved in [viewModelScope] and lands in state a
     * dispatch later, rather than being read straight off an Android Context.
     */
    private fun failWith(res: StringResource) {
        viewModelScope.launch { internalState.update { it.copy(error = getString(res)) } }
    }

    private fun currencyDecimals(code: String): Int =
        CurrencyRegistry.decimalsOrFallback(code)
}
