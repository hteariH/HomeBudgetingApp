package com.personalmoney.core.data

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.domain.model.Account
import kotlin.time.Instant
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

    /**
     * Replays the ledger up to [asOf] on top of each account's opening balance.
     *
     * The server has no as-of endpoint and the plan says not to add one, so this is the
     * client-side reconstruction the dashboard's net-worth trend needs. It reads the same
     * page of recent transactions the dashboard already pulls, which means the number is
     * only right as far back as that page reaches - and the trend covers six months, so
     * that is the limit worth knowing about.
     */
    override suspend fun balancesAsOf(asOf: Instant): List<AccountBalance> {
        val accounts = api.accounts()
        val ledger = api.transactions(size = LEDGER_PAGE).content
        val movementByAccount = ledger
            .filter { Instant.parse(it.occurredAt) <= asOf }
            .groupBy { it.account.id }
            .mapValues { (_, rows) ->
                rows.sumOf { row ->
                    BigDecimal.parseString(row.accountAmount)
                        .toMinorUnits(row.account.currency.decimals)
                }
            }
        return accounts.map { account ->
            AccountBalance(
                accountId = account.id,
                currencyCode = account.currency.code,
                balanceMinor = BigDecimal.parseString(account.initialBalance)
                    .toMinorUnits(account.currency.decimals) + (movementByAccount[account.id] ?: 0L),
            )
        }
    }

    /** One row asked for, the total read off the page - the rows themselves are not wanted. */
    override suspend fun transactionCount(id: Long): Int =
        api.transactions(accountId = id, size = 1).totalElements.toInt()

    /**
     * One request per account. There is no bulk reorder endpoint, and inventing one would
     * be a server change for a list that is a handful of rows long.
     */
    override suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id ->
            api.updateAccount(id, UpdateAccountRequest(sortOrder = index))
        }
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

/** Matches what the dashboard pulls, so the two see the same slice of history. */
private const val LEDGER_PAGE = 100
