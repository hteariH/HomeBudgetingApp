package com.personalmoney.core.data

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.domain.model.Transaction
import com.personalmoney.core.domain.toDecimalString
import com.personalmoney.core.domain.toMinorUnits
import com.personalmoney.core.network.CreateTransactionRequest
import com.personalmoney.core.network.CreateTransferRequest
import com.personalmoney.core.network.RestApi
import com.personalmoney.core.network.RestTransaction
import com.personalmoney.core.util.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
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

    private val revision = MutableStateFlow(0)
    private val recent = MutableStateFlow<List<Transaction>>(emptyList())

    /**
     * See [LazyRefresh]. The screens are written against Room's guarantee - collect and
     * rows arrive - and a REST cache is empty until something asks the server.
     */
    private val lazyLoad = LazyRefresh { refresh() }

    /** The cache, but collecting it fetches first if nothing has yet. */
    private val loaded get() = recent.loadingOnce(lazyLoad)

    override fun observeRecent(limit: Int): Flow<List<Transaction>> =
        loaded.map { it.take(limit) }

    override fun observeFiltered(
        accountId: Long?,
        categoryId: Long?,
        type: TransactionType?,
        from: Instant?,
        to: Instant?,
    ): Flow<List<Transaction>> = revision.map {
        try {
            // [to] is an exclusive upper bound. The server interprets dateTo as an inclusive
            // day (until = dateTo + 1 day at midnight), so pass the day before [to] when at a day boundary.
            val dateTo = to?.let { (it - 1.milliseconds).toIsoDate() }
            val all = mutableListOf<Transaction>()
            var page = 0
            do {
                val res = api.transactions(
                    accountId = accountId,
                    categoryId = categoryId,
                    type = type,
                    dateFrom = from?.toIsoDate(),
                    dateTo = dateTo,
                    page = page,
                    size = PAGE_SIZE,
                )
                all.addAll(res.content.map { it.toTransaction() })
                page++
            } while (page < res.totalPages && all.size < MAX_FETCH_LIMIT)

            all.filter { tx ->
                (from == null || tx.occurredAt >= from) &&
                (to == null || tx.occurredAt < to)
            }
        } catch (e: Exception) {
            AppLog.w("RestTransactionRepository", "Failed to fetch filtered transactions: ${e.message}")
            emptyList()
        }
    }

    override fun observeById(id: Long): Flow<Transaction?> =
        loaded.map { rows ->
            rows.firstOrNull { it.id == id } ?: try {
                api.transaction(id).toTransaction()
            } catch (_: Exception) {
                null
            }
        }

    override suspend fun findTransferPair(groupId: String): List<Transaction> =
        recent.value.filter { it.transferGroupId == groupId }

    /**
     * The transfer endpoint takes the group's own id, and removes both legs server-side -
     * so unlike the ledger delete, this is one call rather than two.
     */
    override suspend fun deleteTransfer(groupId: String) {
        val leg = recent.value.firstOrNull { it.transferGroupId == groupId } ?: return
        api.deleteTransfer(leg.id)
        refresh()
    }

    override suspend fun findById(id: Long): Transaction? =
        recent.value.firstOrNull { it.id == id } ?: try {
            api.transaction(id).toTransaction()
        } catch (_: Exception) {
            null
        }

    override suspend fun insert(transaction: Transaction): Long {
        val created = api.createTransaction(transaction.toRequest())
        refresh()
        return created.id
    }

    override suspend fun update(transaction: Transaction) {
        api.updateTransaction(transaction.id, transaction.toRequest())
        refresh()
    }

    /**
     * One `/api/transfers` call rather than two inserts: the server writes both legs in a
     * single transaction, which is the guarantee the contract promises. The debit carries
     * the fee and the negative amount, so the request is reconstructed from it.
     */
    override suspend fun insertTransferPair(debit: Transaction, credit: Transaction) {
        val fromDecimals = CurrencyRegistry.decimalsOrFallback(debit.currencyCode)
        val toDecimals = CurrencyRegistry.decimalsOrFallback(credit.currencyCode)
        val fee = debit.transferFeeMinor ?: 0L
        api.createTransfer(
            CreateTransferRequest(
                fromAccountId = debit.accountId,
                toAccountId = credit.accountId,
                // The debit is stored signed and fee-inclusive; the endpoint wants the
                // amount that actually moves, positive and without the fee.
                fromAmount = (-debit.amountMinor - fee).toDecimalString(fromDecimals),
                toAmount = credit.amountMinor.toDecimalString(toDecimals),
                fee = fee.takeIf { it > 0 }?.toDecimalString(fromDecimals),
                date = debit.occurredAt.toString(),
                description = debit.note,
            )
        )
        refresh()
    }

    override suspend fun delete(id: Long) {
        api.deleteTransaction(id)
        refresh()
    }

    override suspend fun refresh() {
        recent.value = api.transactions(size = PAGE_SIZE).content.map { it.toTransaction() }
        revision.update { it + 1 }
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_FETCH_LIMIT = 5000
    }
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
