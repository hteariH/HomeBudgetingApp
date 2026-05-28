package com.personalmoney.feature.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.calc.Calculator
import com.personalmoney.core.data.repository.AccountRepository
import com.personalmoney.core.data.repository.TransactionRepository
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.UUID
import javax.inject.Inject

data class TransferUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val fromAccount: AccountEntity? = null,
    val toAccount: AccountEntity? = null,
    val amountFrom: String = "",
    val amountTo: String = "",
    val rate: String = "",
    val sameCurrency: Boolean = true,
    val feeFrom: String = "0",
    val note: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
) {
    val canSave: Boolean
        get() = fromAccount != null && toAccount != null && fromAccount.id != toAccount.id &&
                amountFrom.isNotBlank() && !saving &&
                (sameCurrency || amountTo.isNotBlank())
}

@HiltViewModel
class TransferViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val internalState = MutableStateFlow(TransferUiState())

    val uiState: StateFlow<TransferUiState> = combine(
        internalState,
        accountRepository.observeActive()
    ) { local, accounts ->
        val from = local.fromAccount ?: accounts.firstOrNull()
        val to = local.toAccount ?: accounts.firstOrNull { it.id != from?.id }
        local.copy(
            accounts = accounts,
            fromAccount = from,
            toAccount = to,
            sameCurrency = from?.currencyCode == to?.currencyCode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransferUiState())

    fun setFromAccount(account: AccountEntity) = internalState.update { it.copy(fromAccount = account) }
    fun setToAccount(account: AccountEntity) = internalState.update { it.copy(toAccount = account) }
    fun setAmountFrom(value: String) = internalState.update { it.copy(amountFrom = value) }
    fun setAmountTo(value: String) = internalState.update { it.copy(amountTo = value) }
    fun setRate(value: String) = internalState.update { it.copy(rate = value) }
    fun setFee(value: String) = internalState.update { it.copy(feeFrom = value) }
    fun setNote(value: String) = internalState.update { it.copy(note = value) }
    fun clearError() = internalState.update { it.copy(error = null) }

    fun save() {
        val s = uiState.value
        if (!s.canSave) return
        val from = s.fromAccount ?: return
        val to = s.toAccount ?: return
        val fromDecimals = currencyDecimals(from.currencyCode)
        val toDecimals = currencyDecimals(to.currencyCode)

        val amountFromBd = Calculator.evaluate(s.amountFrom, fromDecimals) ?: run {
            internalState.update { it.copy(error = "Invalid source amount") }; return
        }
        val amountToBd = if (s.sameCurrency) amountFromBd
        else Calculator.evaluate(s.amountTo, toDecimals) ?: run {
            internalState.update { it.copy(error = "Invalid destination amount") }; return
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
