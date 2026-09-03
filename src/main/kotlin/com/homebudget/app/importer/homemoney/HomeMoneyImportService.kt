package com.homebudget.app.importer.homemoney

import com.homebudget.app.domain.Account
import com.homebudget.app.domain.AccountType
import com.homebudget.app.domain.Category
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.MinorUnits
import com.homebudget.app.domain.PlanLimits
import com.homebudget.app.domain.Transaction
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.domain.User
import com.homebudget.app.domain.isPremium
import com.homebudget.app.repository.AccountRepository
import com.homebudget.app.repository.CategoryRepository
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Moving a HomeMoney ledger into ours.
 *
 * Deliberately not a [com.homebudget.app.bank.BankProvider]. That interface is shaped
 * around "one account's statement for a date range", which is the right shape for a bank
 * and the wrong one here: this is a migration of somebody's whole book - accounts,
 * category tree, every operation and the balances they add up to - done once.
 *
 * Two steps, like every other import in the app. [plan] reads HomeMoney and proposes a
 * mapping without writing anything; the user corrects it; the runner applies it. The
 * difference is what is being decided in between: not "which category is this line", but
 * "is this HomeMoney account the same thing as that account I already have".
 *
 * Nothing here reads the security context. The work runs on a background thread where
 * there is none, so the user is always a parameter.
 */
@Service
class HomeMoneyImportService(
    private val api: HomeMoneyApi,
    private val connections: HomeMoneyConnectionService,
    private val userRepo: UserRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val transactionRepo: TransactionRepository,
    private val currencyRepo: CurrencyRepository,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ── Plan ────────────────────────────────────────────────────────────────

    /**
     * What an import would do, writing nothing.
     *
     * The history is fetched here to report a count and a date range, and fetched again
     * by the apply pass. That is a deliberate second read rather than a cache: HomeMoney
     * is the authority on what happened, the user may sit on the confirmation screen for
     * a while, and holding a decade of operations in memory between two requests is a
     * worse trade than a dozen extra HTTP calls on a one-time migration.
     */
    fun plan(userId: Long): HomeMoneyPlan {
        val user = requireUser(userId)
        val connection = connections.requireFor(user)

        val (accounts, categories, history) = connections.withToken(connection) { token ->
            Triple(
                api.balanceList(token),
                api.categoryList(token),
                fetchHistory(token)
            )
        }

        val plannedAccounts = HomeMoneyMapper.plannedAccounts(accounts) { label ->
            HomeMoneyCurrencyLabels.toIsoCode(label)?.takeIf { currencyRepo.findByCode(it) != null }
        }.map { it.copy(targetAccountId = matchAccount(user, it)?.id) }

        val plannedCategories = HomeMoneyMapper.plannedCategories(categories)
            .map { it.copy(targetCategoryId = matchCategory(user, it)?.id) }

        // The count and the range have to describe the same thing: what would actually
        // be imported. Taking the dates from the raw history instead stretched the range
        // over a scheduled operation in December and a deleted one in February - neither
        // of which the count included, and neither of which would ever land.
        val rows = history.mapNotNull { runCatching { HomeMoneyMapper.toRow(it) }.getOrNull() }
        val dates = rows.map { it.occurredAt.atZone(ZoneOffset.UTC).toLocalDate() }

        return HomeMoneyPlan(
            accounts = plannedAccounts,
            categories = plannedCategories,
            transactionCount = rows.size,
            firstDate = dates.minOrNull(),
            lastDate = dates.maxOrNull(),
            blockedReason = accountCapReason(user, plannedAccounts.count { it.targetAccountId == null })
        )
    }

    /**
     * The free plan's account cap, phrased for a person rather than thrown at them.
     *
     * Checked up front because the alternative is finding out halfway through: accounts
     * created, half the history in, and a 402 on account six.
     */
    fun accountCapReason(user: User, newAccounts: Int): String? {
        if (user.isPremium(Instant.now(clock))) return null
        val inPlay = accountRepo.countByUserAndArchivedFalse(user)
        if (inPlay + newAccounts <= PlanLimits.FREE_ACCOUNTS) return null
        return "The import would create $newAccounts accounts on top of the $inPlay you have, " +
            "and the free plan covers ${PlanLimits.FREE_ACCOUNTS}. Subscribe, or point some of " +
            "them at accounts you already have."
    }

    /** Same name and same currency. Anything looser would silently merge two real accounts. */
    private fun matchAccount(user: User, planned: PlannedAccount): Account? {
        val existing = accountRepo.findAllByUser(user)
        existing.firstOrNull { it.externalAccountId == planned.externalId }?.let { return it }
        val code = planned.currencyCode ?: return null
        return existing.firstOrNull {
            it.name.equals(planned.name, ignoreCase = true) && it.currency.code == code
        }
    }

    /** Same name and same direction; a "Salary" expense category is not the income one. */
    private fun matchCategory(user: User, planned: PlannedCategory): Category? {
        val existing = categoryRepo.findAllByUser(user)
        existing.firstOrNull { it.externalId == planned.externalId }?.let { return it }
        return existing.firstOrNull {
            it.type == planned.type && it.name.equals(planned.name, ignoreCase = true)
        }
    }

    // ── Apply ───────────────────────────────────────────────────────────────

    /** Local ids for every external id the ledger rows will refer to. */
    data class Targets(
        val accounts: Map<String, Long>,
        val categories: Map<String, Long>,
        val accountsCreated: Int,
        val categoriesCreated: Int,
        /** Accounts this import created, and the balance HomeMoney reports for each. */
        val openingTargets: Map<Long, BigDecimal>
    )

    /**
     * Creates whatever the plan says is missing and resolves every mapping to a local id.
     *
     * Its own transaction: the ledger is written year by year afterwards, and the
     * accounts have to exist and be committed before any of that starts.
     */
    @Transactional
    fun prepare(userId: Long, plan: HomeMoneyPlan): Targets {
        val user = requireUser(userId)
        val included = plan.accounts.filter { it.included }
        plan.blockedReason?.let { throw com.homebudget.app.service.PlanLimitException(it) }
        accountCapReason(user, included.count { it.targetAccountId == null })?.let {
            throw com.homebudget.app.service.PlanLimitException(it)
        }

        val accountIds = mutableMapOf<String, Long>()
        val openingTargets = mutableMapOf<Long, BigDecimal>()
        var accountsCreated = 0

        for (planned in included) {
            val existing = planned.targetAccountId?.let {
                accountRepo.findByIdAndUser(it, user)
                    ?: throw NoSuchElementException("Account not found: $it")
            }
            if (existing != null) {
                accountIds[planned.externalId] = existing.id
                // An account the user already had keeps its own opening balance. The
                // import adds history to it; rewriting the opening figure would silently
                // overwrite something they entered themselves.
                continue
            }
            val code = planned.currencyCode
                ?: throw IllegalArgumentException(
                    "Choose a currency for '${planned.name}' - HomeMoney calls it " +
                        "'${planned.currencyLabel}', which is not a code the catalog knows"
                )
            val currency = currencyRepo.findByCode(code.uppercase())
                ?: throw IllegalArgumentException("Currency not found: $code")
            val created = accountRepo.save(
                Account(
                    name = planned.name,
                    type = AccountType.CHECKING,
                    // Set once the ledger is in; see reconcileOpeningBalances.
                    initialBalanceMinor = 0,
                    user = user,
                    currency = currency,
                    externalAccountId = planned.externalId,
                    sortOrder = accountIds.size
                )
            )
            accountIds[planned.externalId] = created.id
            openingTargets[created.id] = planned.balance
            accountsCreated++
        }

        val categoryIds = mutableMapOf<String, Long>()
        var categoriesCreated = 0
        for (planned in HomeMoneyMapper.parentsFirst(plan.categories)) {
            val existing = planned.targetCategoryId?.let {
                categoryRepo.findByIdAndUser(it, user)
                    ?: throw NoSuchElementException("Category not found: $it")
            }
            if (existing != null) {
                categoryIds[planned.externalId] = existing.id
                continue
            }
            val parent = planned.parentExternalId
                ?.let { categoryIds[it] }
                ?.let { categoryRepo.findByIdAndUser(it, user) }
                // A parent of the wrong direction cannot hold this child; CategoryService
                // rejects that, and so does the schema's own rule.
                ?.takeIf { it.type == planned.type }
            val created = categoryRepo.save(
                Category(
                    name = planned.name,
                    user = user,
                    type = planned.type,
                    parent = parent,
                    archived = planned.archived,
                    externalId = planned.externalId,
                    sortOrder = categoryIds.size
                )
            )
            categoryIds[planned.externalId] = created.id
            categoriesCreated++
        }

        return Targets(accountIds, categoryIds, accountsCreated, categoriesCreated, openingTargets)
    }

    data class ChunkResult(val imported: Int, val skipped: Int)

    /**
     * Writes one slice of history.
     *
     * Its own transaction so a failure part-way through a decade leaves the years that
     * did land intact and re-runnable, rather than rolling back an hour of work. Dedup by
     * external id is what makes resuming safe.
     */
    @Transactional
    fun importChunk(userId: Long, targets: Targets, operations: List<HomeMoneyTransaction>): ChunkResult {
        val user = requireUser(userId)
        val accounts = targets.accounts.mapValues { (_, id) -> accountRepo.findByIdAndUser(id, user) }
        var imported = 0
        var skipped = 0

        for (operation in operations) {
            val row = try {
                HomeMoneyMapper.toRow(operation)
            } catch (e: HomeMoneyRequestException) {
                // One unreadable date must not abandon the other nine thousand rows.
                log.warn("Skipping HomeMoney operation {}: {}", operation.guid, e.message)
                skipped++
                continue
            }
            if (row == null) {
                skipped++
                continue
            }

            when (row) {
                is HomeMoneyMapper.PlannedRow.Single -> {
                    // An account the user excluded from the plan: its operations are
                    // not an error, they are simply not being imported.
                    val account = accounts[row.accountExternalId]
                    if (account == null) {
                        skipped++
                        continue
                    }
                    if (transactionRepo.existsByAccountAndExternalId(account, row.externalId)) {
                        skipped++
                        continue
                    }
                    val category = row.categoryExternalId
                        ?.let { targets.categories[it] }
                        ?.let { categoryRepo.findByIdAndUser(it, user) }
                        ?.takeIf { it.type == if (row.income) CategoryType.INCOME else CategoryType.EXPENSE }
                    val minor = MinorUnits.toMinor(row.amount, account.currency.decimals)
                    transactionRepo.save(
                        Transaction(
                            user = user,
                            account = account,
                            category = category,
                            amountMinor = minor,
                            currencyCode = account.currency.code,
                            accountAmountMinor = minor,
                            type = if (row.income) TransactionType.INCOME else TransactionType.EXPENSE,
                            occurredAt = row.occurredAt,
                            description = row.description,
                            externalId = row.externalId
                        )
                    )
                    imported++
                }

                is HomeMoneyMapper.PlannedRow.Transfer -> {
                    val from = accounts[row.fromAccountExternalId]
                    val to = accounts[row.toAccountExternalId]
                    // Both legs or neither. A transfer with one end missing is not a
                    // partial import, it is a wrong balance on the end that did land.
                    if (from == null || to == null || from.id == to.id) {
                        skipped++
                        continue
                    }
                    if (transactionRepo.existsByAccountAndExternalId(from, row.externalId)) {
                        skipped++
                        continue
                    }
                    val fromMinor = MinorUnits.toMinor(row.fromAmount, from.currency.decimals)
                    val toMinor = MinorUnits.toMinor(row.toAmount, to.currency.decimals)
                    val rate = if (row.fromAmount.signum() != 0) {
                        row.toAmount.divide(row.fromAmount, 10, RoundingMode.HALF_UP)
                    } else {
                        BigDecimal.ONE
                    }
                    val groupId = UUID.randomUUID().toString()
                    transactionRepo.saveAll(
                        listOf(
                            Transaction(
                                user = user,
                                account = from,
                                category = null,
                                // Signed: the balance query adds TRANSFER rows as-is.
                                amountMinor = -fromMinor,
                                currencyCode = from.currency.code,
                                accountAmountMinor = -fromMinor,
                                fxRateToAccount = BigDecimal.ONE,
                                type = TransactionType.TRANSFER,
                                occurredAt = row.occurredAt,
                                description = row.description,
                                transferGroupId = groupId,
                                transferCounterpartAccountId = to.id,
                                externalId = row.externalId
                            ),
                            Transaction(
                                user = user,
                                account = to,
                                category = null,
                                amountMinor = toMinor,
                                currencyCode = to.currency.code,
                                accountAmountMinor = toMinor,
                                fxRateToAccount = rate,
                                type = TransactionType.TRANSFER,
                                occurredAt = row.occurredAt,
                                description = row.description,
                                transferGroupId = groupId,
                                transferCounterpartAccountId = from.id,
                                externalId = row.externalId
                            )
                        )
                    )
                    imported++
                }
            }
        }
        return ChunkResult(imported, skipped)
    }

    /**
     * Makes the imported balances agree with HomeMoney's.
     *
     * This is the half of the request that is not history. Our balance is derived -
     * opening figure plus the signed ledger - so the opening figure is the only lever,
     * and it is set last, once the ledger is complete: whatever the imported operations
     * do not account for is by definition what the account started with.
     *
     * Only for accounts this import created. One the user mapped onto an account of their
     * own keeps its own opening balance.
     */
    @Transactional
    fun reconcileOpeningBalances(userId: Long, targets: Targets) {
        if (targets.openingTargets.isEmpty()) return
        val user = requireUser(userId)
        val balances = accountRepo.balancesOf(user).associate { it.accountId to it.balanceMinor }
        for ((accountId, target) in targets.openingTargets) {
            val account = accountRepo.findByIdAndUser(accountId, user) ?: continue
            val ledger = (balances[accountId] ?: 0L) - account.initialBalanceMinor
            account.initialBalanceMinor =
                MinorUnits.toMinor(target, account.currency.decimals) - ledger
            account.updatedAt = Instant.now(clock)
            accountRepo.save(account)
        }
    }

    // ── History ─────────────────────────────────────────────────────────────

    /**
     * Every operation HomeMoney will admit to, walked backwards a year at a time.
     *
     * There is no "give me everything" call and no way to ask how far back the history
     * goes, so the range is discovered: walk back from this year and stop once several
     * years in a row come back empty. A single quiet year is common - a dormant account,
     * a year abroad - so one empty year is not the end of the history.
     */
    fun fetchHistory(token: String): List<HomeMoneyTransaction> =
        historyYears(token).flatMap { it.second }

    /** The same walk, kept as chunks so a long import can report progress. */
    fun historyYears(token: String): List<Pair<Int, List<HomeMoneyTransaction>>> {
        val thisYear = LocalDate.now(clock).year
        val chunks = mutableListOf<Pair<Int, List<HomeMoneyTransaction>>>()
        var emptyRun = 0
        var year = thisYear
        while (year >= EARLIEST_YEAR && emptyRun < EMPTY_YEARS_BEFORE_STOP) {
            val operations = api.transactionsByPeriod(
                token,
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31)
            ).transactions
            if (operations.isEmpty()) emptyRun++ else emptyRun = 0
            if (operations.isNotEmpty()) chunks += year to operations
            year--
        }
        // Oldest first, so a resumed import fills history in the order a person reads it.
        return chunks.sortedBy { it.first }
    }

    /**
     * One year, fetched with a token that is refreshed if it has gone stale.
     *
     * The apply pass walks year by year through this rather than pulling the whole
     * history at once: it bounds memory to a single year, gives the progress bar
     * something real to report, and keeps a token refresh from re-running an hour of
     * work the way wrapping the entire import in one [HomeMoneyConnectionService.withToken]
     * would.
     */
    fun fetchYear(connection: com.homebudget.app.domain.BankConnection, year: Int): List<HomeMoneyTransaction> =
        connections.withToken(connection) { token ->
            api.transactionsByPeriod(token, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).transactions
        }

    /** Newest first, which is the order the range discovery in [historyYears] relies on. */
    fun yearsToScan(): IntProgression = LocalDate.now(clock).year downTo EARLIEST_YEAR

    fun emptyYearsBeforeStop(): Int = EMPTY_YEARS_BEFORE_STOP

    fun requireUser(userId: Long): User =
        userRepo.findById(userId).orElseThrow { NoSuchElementException("User not found: $userId") }

    private companion object {
        const val EARLIEST_YEAR = 1995

        /**
         * A single quiet year is common - a dormant account, a year spent abroad - so one
         * empty response is not the end of somebody's history.
         */
        const val EMPTY_YEARS_BEFORE_STOP = 3
    }
}
