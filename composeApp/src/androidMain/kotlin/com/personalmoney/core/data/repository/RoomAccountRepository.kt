package com.personalmoney.core.data.repository

import com.personalmoney.core.data.AccountRepository as SharedAccountRepository
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The shared account contract, backed by Room.
 *
 * Balances are not a column - they are `initial_balance_minor` plus the ledger - so the
 * account rows and the balance rows are two queries combined here rather than one join in
 * the DAO. That keeps the derived-balance rule in one place and lets both flows stay live.
 *
 * Delegates to [AccountRepository], which still owns the entity-level API the Android
 * screens use today; those move onto this interface as they migrate to shared code.
 */
class RoomAccountRepository(
    private val delegate: AccountRepository,
) : SharedAccountRepository {

    override fun observeActive(): Flow<List<Account>> = withBalances(delegate.observeActive())

    override fun observeAll(): Flow<List<Account>> = withBalances(delegate.observeAll())

    override suspend fun findById(id: Long): Account? =
        delegate.findById(id)?.toAccount(balanceMinor = null)

    override suspend fun create(account: Account): Long = delegate.create(account.toEntity())

    override suspend fun update(account: Account) = delegate.update(account.toEntity())

    override suspend fun setArchived(id: Long, archived: Boolean) =
        delegate.setArchived(id, archived)

    override suspend fun delete(id: Long) = delegate.delete(id)

    private fun withBalances(source: Flow<List<AccountEntity>>): Flow<List<Account>> =
        combine(source, delegate.observeBalances()) { accounts, balances ->
            val byId = balances.associate { it.accountId to it.balanceMinor }
            accounts.map { it.toAccount(byId[it.id]) }
        }
}

private fun AccountEntity.toAccount(balanceMinor: Long?): Account = Account(
    id = id,
    uuid = uuid,
    name = name,
    currencyCode = currencyCode,
    initialBalanceMinor = initialBalanceMinor,
    // Falling back to the opening balance is right for an account with no ledger rows yet.
    balanceMinor = balanceMinor ?: initialBalanceMinor,
    icon = icon,
    color = color,
    archived = archived,
    sortOrder = sortOrder,
    includeInTotal = includeInTotal,
    note = note,
)

private fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    currencyCode = currencyCode,
    initialBalanceMinor = initialBalanceMinor,
    icon = icon,
    color = color,
    archived = archived,
    sortOrder = sortOrder,
    includeInTotal = includeInTotal,
    note = note,
    uuid = uuid,
)
