package com.personalmoney.core.data.repository

import com.personalmoney.core.data.TransactionRepository as SharedTransactionRepository
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

/** The shared ledger contract, backed by Room. */
class RoomTransactionRepository(
    private val delegate: TransactionRepository,
) : SharedTransactionRepository {

    override fun observeRecent(limit: Int): Flow<List<Transaction>> =
        delegate.observeRecent(limit).map { rows -> rows.map { it.toTransaction() } }

    override fun observeFiltered(
        accountId: Long?,
        categoryId: Long?,
        type: TransactionType?,
        from: Instant?,
        to: Instant?,
    ): Flow<List<Transaction>> =
        delegate.observeFiltered(from, to, accountId, categoryId, type)
            .map { rows -> rows.map { it.toTransaction() } }

    override fun observeById(id: Long): Flow<Transaction?> =
        delegate.observeById(id).map { it?.toTransaction() }

    override suspend fun findTransferPair(groupId: String): List<Transaction> =
        delegate.findTransferPair(groupId).map { it.toTransaction() }

    override suspend fun deleteTransfer(groupId: String) = delegate.deleteTransfer(groupId)

    override suspend fun findById(id: Long): Transaction? =
        delegate.findById(id)?.toTransaction()

    override suspend fun insert(transaction: Transaction): Long =
        delegate.insert(transaction.toEntity(createdAt = Clock.System.now()))

    override suspend fun update(transaction: Transaction) {
        // Room writes the whole row, so createdAt has to be carried over from the stored
        // one. Rebuilding it from the model would stamp every edit as a new entry.
        val existingCreatedAt = delegate.findById(transaction.id)?.createdAt
            ?: Clock.System.now()
        delegate.update(transaction.toEntity(createdAt = existingCreatedAt))
    }

    override suspend fun insertTransferPair(debit: Transaction, credit: Transaction) {
        val now = Clock.System.now()
        delegate.insertTransferPair(debit.toEntity(now), credit.toEntity(now))
    }

    override suspend fun delete(id: Long) = delegate.delete(id)
}

private fun TransactionEntity.toTransaction() = Transaction(
    id = id,
    uuid = uuid,
    type = type,
    accountId = accountId,
    categoryId = categoryId,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    accountAmountMinor = accountAmountMinor,
    fxRateToAccount = fxRateToAccount,
    occurredAt = occurredAt,
    note = note,
    subscriptionId = subscriptionId,
    transferGroupId = transferGroupId,
    transferCounterpartAccountId = transferCounterpartAccountId,
    transferFeeMinor = transferFeeMinor,
)

private fun Transaction.toEntity(createdAt: Instant) = TransactionEntity(
    id = id,
    type = type,
    accountId = accountId,
    categoryId = categoryId,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    accountAmountMinor = accountAmountMinor,
    fxRateToAccount = fxRateToAccount,
    occurredAt = occurredAt,
    // Not on the model: nothing above the repository reads it, and it must survive edits.
    createdAt = createdAt,
    note = note,
    subscriptionId = subscriptionId,
    transferGroupId = transferGroupId,
    transferCounterpartAccountId = transferCounterpartAccountId,
    transferFeeMinor = transferFeeMinor,
    uuid = uuid,
)
