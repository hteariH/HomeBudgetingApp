package com.personalmoney.core.data

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.toDecimalString
import com.personalmoney.core.domain.toMinorUnits
import com.personalmoney.core.network.CreateAccountRequest
import com.personalmoney.core.network.RestAccount
import com.personalmoney.core.network.RestApi
import com.personalmoney.core.network.UpdateAccountRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The shared account contract, backed by REST.
 *
 * A browser tab has no database, so there is nothing to observe: the flow is a cache that
 * [refresh] republishes, and every write refreshes before returning so callers see their
 * own change. That is a weaker guarantee than the Room implementation gives, which is why
 * the interface documents the weaker one.
 */
class RestAccountRepository(private val api: RestApi) : AccountRepository {

    private val cache = MutableStateFlow<List<Account>>(emptyList())

    // The server keys accounts to a currency row rather than a code, so creating one needs
    // an id. Learned from whatever the server has already sent.
    private val currencyIds = mutableMapOf<String, Long>()

    override fun observeActive(): Flow<List<Account>> =
        cache.map { accounts -> accounts.filter { !it.archived }.sortedBy { it.sortOrder } }

    override fun observeAll(): Flow<List<Account>> = cache

    override suspend fun findById(id: Long): Account? =
        cache.value.firstOrNull { it.id == id } ?: api.account(id).toAccount()

    override suspend fun create(account: Account): Long {
        val created = api.createAccount(
            CreateAccountRequest(
                name = account.name,
                initialBalance = account.initialBalanceMinor.toDecimalString(
                    CurrencyRegistry.decimalsOrFallback(account.currencyCode)
                ),
                currencyId = currencyIds[account.currencyCode]
                    ?: error("Unknown currency ${account.currencyCode}; refresh() must run first"),
                icon = account.icon,
                sortOrder = account.sortOrder,
                includeInTotal = account.includeInTotal,
            )
        )
        refresh()
        return created.id
    }

    override suspend fun update(account: Account) {
        api.updateAccount(
            account.id,
            UpdateAccountRequest(
                name = account.name,
                initialBalance = account.initialBalanceMinor.toDecimalString(
                    CurrencyRegistry.decimalsOrFallback(account.currencyCode)
                ),
                icon = account.icon,
                archived = account.archived,
                sortOrder = account.sortOrder,
                includeInTotal = account.includeInTotal,
            ),
        )
        refresh()
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        api.updateAccount(id, UpdateAccountRequest(archived = archived))
        refresh()
    }

    override suspend fun delete(id: Long) {
        api.deleteAccount(id)
        refresh()
    }

    override suspend fun refresh() {
        val remote = api.accounts()
        remote.forEach { currencyIds[it.currency.code] = it.currency.id }
        // Keep the registry fed so money formatting uses the currencies the user actually
        // has, exactly as the Android app does on startup.
        CurrencyRegistry.update(
            remote.map { CurrencyRegistry.Info(it.currency.code, it.currency.symbol, it.currency.decimals) }
                .distinctBy { it.code }
        )
        cache.value = remote.map { it.toAccount() }
    }
}

private fun RestAccount.toAccount(): Account {
    val decimals = currency.decimals
    return Account(
        id = id,
        uuid = uuid,
        name = name,
        currencyCode = currency.code,
        initialBalanceMinor = BigDecimal.parseString(initialBalance).toMinorUnits(decimals),
        // Already summed from the ledger by the server; never stored on either side.
        balanceMinor = BigDecimal.parseString(balance).toMinorUnits(decimals),
        icon = icon,
        archived = archived,
        sortOrder = sortOrder,
        includeInTotal = includeInTotal,
        note = description,
    )
}
