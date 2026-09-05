package com.homebudget.app.importer.homemoney

import com.homebudget.app.domain.CategoryType
import java.math.BigDecimal
import java.time.Instant

/**
 * HomeMoney's model translated into ours, with no database and no HTTP in sight.
 *
 * Everything that is genuinely a decision - how a multi-currency account splits, which
 * operations are not ledger entries, how a transfer becomes two rows - lives here so it
 * can be tested against captured payloads rather than only against a live account.
 */
object HomeMoneyMapper {

    const val PREFIX = "homemoney"

    fun accountExternalId(accountId: String, currencyId: String) = "$PREFIX:$accountId:$currencyId"

    fun categoryExternalId(categoryId: String) = "$PREFIX:$categoryId"

    fun transactionExternalId(guid: String) = "$PREFIX:$guid"

    /**
     * Every account-and-currency pair HomeMoney reports, flattened.
     *
     * The currency suffix is added only when an account actually holds more than one:
     * "Cash (USD)" is helpful next to "Cash (UAH)", and noise on its own.
     *
     * @param resolveCode turns HomeMoney's currency label into a code our catalog knows,
     *   or returns null so the user is asked instead of the wrong currency being guessed.
     */
    fun plannedAccounts(
        balances: HomeMoneyBalanceList,
        resolveCode: (String) -> String?
    ): List<PlannedAccount> = balances.groups.flatMap { group ->
        group.accounts.flatMap { account ->
            val multiCurrency = account.currencies.size > 1
            account.currencies.map { currency ->
                val code = resolveCode(currency.shortName)
                PlannedAccount(
                    externalId = accountExternalId(account.id, currency.id),
                    homeMoneyAccountId = account.id,
                    homeMoneyCurrencyId = currency.id,
                    name = if (multiCurrency) {
                        "${account.name} (${code ?: currency.shortName})"
                    } else {
                        account.name
                    },
                    currencyLabel = currency.shortName,
                    currencyCode = code,
                    balance = currency.balance
                )
            }
        }
    }

    /**
     * Unticks the currency slots that were added to an account and never used.
     *
     * HomeMoney lets an account carry several currencies, and adding one costs nothing -
     * so a single "Deposit" routinely reports slots in USD, EUR, HUF and RON that hold
     * nothing and never held anything. They are not accounts anybody has: importing them
     * turns one real account into five empty ones, and on the free plan they count five
     * against the cap.
     *
     * Both conditions, not just the balance. An account can sit at zero today and still
     * have a decade behind it, and dropping that would take its history with it - and,
     * since a transfer needs both ends, a slice of the history of whatever it traded with
     * as well. Untouched *and* empty is the slot that never existed.
     *
     * Unticked rather than removed: it stays on the confirmation screen, so anyone who
     * does want an empty account can say so.
     */
    fun untickUnusedCurrencySlots(
        accounts: List<PlannedAccount>,
        rows: List<PlannedRow>
    ): List<PlannedAccount> {
        val everUsed = rows.flatMapTo(mutableSetOf()) { row ->
            when (row) {
                is PlannedRow.Single -> listOf(row.accountExternalId)
                is PlannedRow.Transfer -> listOf(row.fromAccountExternalId, row.toAccountExternalId)
            }
        }
        return accounts.map {
            if (it.balance.signum() == 0 && it.externalId !in everUsed) it.copy(included = false) else it
        }
    }

    /**
     * The categories worth carrying over.
     *
     * HomeMoney's type 2 is a transfer category, and our transfers have no category at
     * all - the two accounts are the whole story. Importing them would leave a column of
     * categories nothing can ever be filed under.
     */
    fun plannedCategories(list: HomeMoneyCategoryList): List<PlannedCategory> = list.categories
        .filter { it.type == HomeMoneyKind.EXPENSE || it.type == HomeMoneyKind.INCOME }
        .map { category ->
            PlannedCategory(
                externalId = categoryExternalId(category.id),
                name = category.name.ifBlank { category.fullName },
                fullName = category.fullName.ifBlank { category.name },
                type = if (category.type == HomeMoneyKind.INCOME) CategoryType.INCOME else CategoryType.EXPENSE,
                parentExternalId = category.parentId
                    ?.takeIf { it.isNotBlank() && it != "0" }
                    ?.let { categoryExternalId(it) },
                archived = category.archived
            )
        }

    /**
     * Parents before children, so a category can always be created with its parent
     * already in hand. Anything whose parent is not in the list is treated as a root
     * rather than dropped - a missing parent must not take its children with it.
     */
    fun parentsFirst(categories: List<PlannedCategory>): List<PlannedCategory> {
        val present = categories.mapTo(mutableSetOf()) { it.externalId }
        val (roots, children) = categories.partition {
            it.parentExternalId == null || it.parentExternalId !in present
        }
        val ordered = roots.toMutableList()
        val remaining = children.toMutableList()
        // One level deep in practice, but the loop does not assume that.
        while (remaining.isNotEmpty()) {
            val placed = ordered.mapTo(mutableSetOf()) { it.externalId }
            val next = remaining.filter { it.parentExternalId in placed }
            if (next.isEmpty()) {
                // A cycle, which HomeMoney should not produce. Appending beats looping.
                ordered.addAll(remaining)
                break
            }
            ordered.addAll(next)
            remaining.removeAll(next.toSet())
        }
        return ordered
    }

    /** One HomeMoney operation, in the shape the ledger writer needs. */
    sealed interface PlannedRow {
        val externalId: String
        val occurredAt: Instant
        val description: String?

        data class Single(
            override val externalId: String,
            override val occurredAt: Instant,
            override val description: String?,
            val accountExternalId: String,
            val categoryExternalId: String?,
            val income: Boolean,
            /** Positive magnitude; the direction is in [income], as everywhere else. */
            val amount: BigDecimal
        ) : PlannedRow

        data class Transfer(
            override val externalId: String,
            override val occurredAt: Instant,
            override val description: String?,
            val fromAccountExternalId: String,
            val toAccountExternalId: String,
            /** Positive magnitudes; the signs are applied when the two legs are written. */
            val fromAmount: BigDecimal,
            val toAmount: BigDecimal
        ) : PlannedRow
    }

    /**
     * One operation, or null when it does not belong in a ledger.
     *
     * Three things are dropped, each for a different reason:
     * - `isPlan` is a scheduled operation that has not happened. It is a forecast, and
     *   importing it would overstate every balance it touches.
     * - `isDeleted` is a tombstone. HomeMoney keeps deleted operations so other devices
     *   learn about the deletion, so the API keeps returning them; importing one would
     *   resurrect it.
     * - A transfer missing either side is unwritable: one leg without its counterpart is
     *   a corrupted balance, which is worse than a missing operation.
     */
    fun toRow(tx: HomeMoneyTransaction): PlannedRow? {
        if (tx.plan || tx.deleted) return null
        if (tx.guid.isBlank()) return null

        val occurredAt = tx.occurredAt()
        val description = tx.description?.trim()?.takeIf { it.isNotEmpty() }
        val externalId = transactionExternalId(tx.guid)

        return when (tx.type) {
            HomeMoneyKind.EXPENSE, HomeMoneyKind.INCOME -> PlannedRow.Single(
                externalId = externalId,
                occurredAt = occurredAt,
                description = description,
                accountExternalId = accountExternalId(tx.accountId, tx.currencyId),
                categoryExternalId = tx.categoryId
                    ?.takeIf { it.isNotBlank() && it != "0" }
                    ?.let { categoryExternalId(it) },
                income = tx.type == HomeMoneyKind.INCOME,
                amount = tx.total.abs()
            )

            HomeMoneyKind.TRANSFER -> {
                val toAccount = tx.transAccountId?.takeIf { it.isNotBlank() } ?: return null
                val toCurrency = tx.transCurrencyId?.takeIf { it.isNotBlank() } ?: return null
                PlannedRow.Transfer(
                    externalId = externalId,
                    occurredAt = occurredAt,
                    description = description,
                    fromAccountExternalId = accountExternalId(tx.accountId, tx.currencyId),
                    toAccountExternalId = accountExternalId(toAccount, toCurrency),
                    fromAmount = tx.total.abs(),
                    toAmount = tx.transTotal.abs()
                )
            }

            else -> null
        }
    }
}
