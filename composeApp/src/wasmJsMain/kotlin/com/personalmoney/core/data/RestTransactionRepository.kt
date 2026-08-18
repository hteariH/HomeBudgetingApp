package com.personalmoney.core.data

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.domain.model.Transaction
import com.personalmoney.core.domain.toDecimalString
import com.personalmoney.core.domain.toMinorUnits
import com.personalmoney.core.network.CreateTransactionRequest
import com.personalmoney.core.network.RestApi
import com.personalmoney.core.network.RestTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The shared ledger contract, backed by REST. See [RestAccountRepository].
 *
 * Filters go to the server as query parameters rather than being applied to a cached list:
 * the ledger is the one table that will not fit in a tab's memory, and the server already
 * pages it.
 */
class RestTransactionRepository(private val api: RestApi) : TransactionRepository {

    private val recent = MutableStateFlow<List<Transaction>>(emptyList())

    override fun observeRecent(limit: Int): Flow<List<Transaction>> =
        recent.map { it.take(limit) }

    override fun observeFiltered(
        accountId: Long?,
        categoryId: Long?,
        type: TransactionType?,
        from: Instant?,
        to: Instant?,
    ): Flow<List<Transaction>> = flow {
        // One emission per collection. There is nothing to observe over REST, so the query
        // runs when a collector arrives and callers re-collect for a newer answer - rather
        // than this owning a coroutine scope it has no business creating.
        emit(
            api.transactions(
                accountId = accountId,
                categoryId = categoryId,
                type = type,
                dateFrom = from?.toIsoDate(),
                dateTo = to?.toIsoDate(),
                size = PAGE_SIZE,
            ).content.map { it.toTransaction() }
        )
    }

    override suspend fun findById(id: Long): Transaction? =
        recent.value.firstOrNull { it.id == id }

    override suspend fun insert(transaction: Transaction): Long {
        val created = api.createTransaction(transaction.toRequest())
        refresh()
        return created.id
    }

    override suspend fun update(transaction: Transaction) {
        api.updateTransaction(transaction.id, transaction.toRequest())
        refresh()
    }

    override suspend fun delete(id: Long) {
        api.deleteTransaction(id)
        refresh()
    }

    override suspend fun refresh() {
        recent.value = api.transactions(size = PAGE_SIZE).content.map { it.toTransaction() }
    }

    private companion object { const val PAGE_SIZE = 100 }
}

private fun Instant.toIsoDate(): String =
    toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

private fun RestTransaction.toTransaction(): Transaction {
    val decimals = CurrencyRegistry.decimalsOrFallback(currencyCode)
    val accountDecimals = account.currency.decimals
    return Transaction(
        id = id,
        uuid = uuid,
        type = type,
        accountId = account.id,
        categoryId = category?.id,
        amountMinor = BigDecimal.parseString(amount).toMinorUnits(decimals),
        currencyCode = currencyCode,
        accountAmountMinor = BigDecimal.parseString(accountAmount).toMinorUnits(accountDecimals),
        occurredAt = Instant.parse(occurredAt),
        note = description,
        subscriptionId = subscriptionId,
        transferGroupId = transferGroupId,
    )
}

private fun Transaction.toRequest() = CreateTransactionRequest(
    accountId = accountId,
    categoryId = categoryId,
    amount = amountMinor.toDecimalString(CurrencyRegistry.decimalsOrFallback(currencyCode)),
    type = type,
    date = occurredAt.toIsoDate(),
    description = note,
)
