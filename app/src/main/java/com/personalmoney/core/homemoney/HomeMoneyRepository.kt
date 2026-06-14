package com.personalmoney.core.homemoney

import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.currency.FxScheduler
import com.personalmoney.core.data.repository.AccountRepository
import com.personalmoney.core.data.repository.CurrencyRepository
import com.personalmoney.core.database.Transactor
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.database.entity.CurrencyEntity
import kotlinx.coroutines.flow.first
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the HomeMoney import: authenticate → fetch the balance tree → create local accounts.
 * Network shapes live in [HomeMoneyApi]/DTOs; the response→candidate mapping is in [HomeMoneyMapper].
 */
@Singleton
class HomeMoneyRepository @Inject constructor(
    private val api: HomeMoneyApi,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val transactor: Transactor,
    private val fxScheduler: FxScheduler,
) {

    /** Exchanges credentials for an access token, or throws [HomeMoneyException] on failure. */
    suspend fun login(username: String, password: String): String {
        val resp = api.tokenPassword(
            clientId = HomeMoneyConfig.CLIENT_ID,
            clientSecret = HomeMoneyConfig.CLIENT_SECRET,
            username = username.trim(),
            password = password,
        )
        val token = resp.accessToken
        if (token.isNullOrBlank()) {
            throw HomeMoneyException(resp.error?.message, resp.error?.code)
        }
        return token
    }

    /** Fetches the account tree and flattens it to import candidates, flagging existing accounts. */
    suspend fun fetchCandidates(token: String): FetchResult {
        val resp = api.balanceList(token)
        resp.error?.let { if (it.code != 0) throw HomeMoneyException(it.message, it.code) }
        // Key must match the mapper's (lower-case name, UPPER-case code) contract on both halves.
        val existing = accountRepository.observeAll().first()
            .mapTo(mutableSetOf()) { it.name.lowercase() to it.currencyCode.uppercase() }
        // HomeMoney sends a symbol/abbreviation as the currency shortname; resolve it to an ISO code
        // against our currency table so accounts reuse existing currencies instead of duplicating them.
        val resolver = HomeMoneyCurrencyResolver(currencyRepository.observeAll().first())
        return FetchResult(
            accountHolder = resp.name?.trim()?.takeIf { it.isNotEmpty() },
            candidates = HomeMoneyMapper.toCandidates(resp, existing, resolver::resolve),
        )
    }

    /**
     * Creates a local account for each candidate (current balance → initial balance), activating any
     * currency that isn't active yet. All inserts run in one transaction so a mid-batch failure rolls
     * back cleanly (no half-imported state, no duplicates on retry). Returns the number created.
     */
    suspend fun import(candidates: List<ImportCandidate>): Int {
        if (candidates.isEmpty()) return 0
        val existingAccounts = accountRepository.observeAll().first()
        var nextOrder = (existingAccounts.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val created = transactor.transaction {
            var count = 0
            for (candidate in candidates) {
                ensureCurrencyActive(candidate.currencyCode)
                accountRepository.create(
                    AccountEntity(
                        name = candidate.displayName,
                        currencyCode = candidate.currencyCode,
                        initialBalanceMinor = candidate.balanceMinor,
                        includeInTotal = candidate.includeInTotal,
                        sortOrder = nextOrder++,
                    )
                )
                count++
            }
            count
        }
        // Newly-activated currencies have no FX rate yet, so the dashboard can't convert their
        // balances into the base currency (net worth would silently omit them). Kick a refresh so
        // rates arrive and the total fills in reactively.
        if (created > 0) fxScheduler.refreshNow()
        return created
    }

    /** Ensures a [CurrencyEntity] exists and is active so imported balances format/aggregate correctly. */
    private suspend fun ensureCurrencyActive(code: String) {
        val existing = currencyRepository.findByCode(code)
        when {
            existing == null -> currencyRepository.create(
                CurrencyEntity(
                    code = code,
                    name = isoOrNull(code)?.displayName ?: code,
                    symbol = isoOrNull(code)?.symbol ?: code,
                    decimals = CurrencyRegistry.decimalsOrFallback(code),
                    active = true,
                )
            )
            !existing.active -> currencyRepository.setActive(code, true)
        }
    }

    private fun isoOrNull(code: String): Currency? =
        runCatching { Currency.getInstance(code) }.getOrNull()
}
