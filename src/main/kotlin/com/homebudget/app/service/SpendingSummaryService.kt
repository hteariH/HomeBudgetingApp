package com.homebudget.app.service

import com.homebudget.app.domain.MinorUnits
import com.homebudget.app.domain.Transaction
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.receipt.Receipt
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.security.SecurityUtils
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64

/**
 * Reads behind the MCP `search_transactions` and `summarize_spending` tools. Everything is
 * scoped to the signed-in household, and tombstones never show (`@SQLRestriction`).
 */
@Service
@Transactional(readOnly = true)
class SpendingSummaryService(
    private val em: EntityManager,
    private val securityUtils: SecurityUtils,
    private val currencies: CurrencyRepository
) {
    enum class GroupBy { CATEGORY, ACCOUNT, MONTH }

    data class SearchCriteria(
        val from: LocalDate? = null,
        val to: LocalDate? = null,
        val accountId: Long? = null,
        val categoryId: Long? = null,
        val type: TransactionType? = null,
        val text: String? = null,
        val minAmount: BigDecimal? = null,
        val maxAmount: BigDecimal? = null
    )

    /** A ledger entry as the assistant sees it: a transfer is one entry, [credit] being its other leg. */
    data class Entry(
        val transaction: Transaction,
        val credit: Transaction?,
        val source: String,
        val receipt: Receipt?
    )

    data class SearchPage(val entries: List<Entry>, val nextCursor: String?)

    data class Group(val uuid: String?, val name: String, val monthKey: String?, val totals: Map<String, BigDecimal>)

    data class Summary(val groups: List<Group>, val totals: Map<String, BigDecimal>)

    /** Digits after the point of each currency, by code. */
    fun decimals(): Map<String, Int> = currencies.findAll().associate { it.code to it.decimals }

    /**
     * Newest first by (occurredAt, id) - the order REST uses. A transfer is listed once, as
     * its debit leg, whichever of its two accounts the filter names.
     * @throws IllegalArgumentException for a [cursor] that is not a previous `nextCursor`.
     */
    fun search(criteria: SearchCriteria, limit: Int, cursor: String?): SearchPage {
        val household = securityUtils.getCurrentHousehold()
        val decimals = decimals()
        val filtersAmount = criteria.minAmount != null || criteria.maxAmount != null

        val hql = StringBuilder(
            "SELECT t FROM Transaction t WHERE t.household = :household " +
                "AND (t.type <> com.homebudget.app.domain.TransactionType.TRANSFER OR t.accountAmountMinor < 0)"
        )
        val params = mutableMapOf<String, Any>("household" to household)
        criteria.from?.let { hql.append(" AND t.occurredAt >= :from"); params["from"] = startOf(it) }
        criteria.to?.let { hql.append(" AND t.occurredAt < :until"); params["until"] = startOf(it.plusDays(1)) }
        criteria.accountId?.let {
            hql.append(" AND (t.account.id = :accountId OR t.transferCounterpartAccountId = :accountId)")
            params["accountId"] = it
        }
        criteria.categoryId?.let { hql.append(" AND t.category.id = :categoryId"); params["categoryId"] = it }
        criteria.type?.let { hql.append(" AND t.type = :type"); params["type"] = it }
        criteria.text?.let {
            hql.append(
                " AND (lower(t.description) LIKE :text ESCAPE '\\' OR EXISTS (SELECT 1 FROM Receipt r " +
                    "WHERE r.id = t.receiptId AND r.household = :household AND lower(r.merchant) LIKE :text ESCAPE '\\'))"
            )
            params["text"] = "%" + it.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        }

        val rows = ArrayList<Transaction>()
        var last: Pair<Instant, Long>? = cursor?.let(::decodeCursor)
        // Amounts compare in each row's own currency, so they are filtered here, in batches.
        val batch = if (filtersAmount) 200 else limit + 1
        var exhausted = false
        while (rows.size <= limit && !exhausted) {
            val q = StringBuilder(hql)
            last?.let { q.append(" AND (t.occurredAt < :cAt OR (t.occurredAt = :cAt AND t.id < :cId))") }
            q.append(" ORDER BY t.occurredAt DESC, t.id DESC")
            val query = em.createQuery(q.toString(), Transaction::class.java).setMaxResults(batch)
            params.forEach { (k, v) -> query.setParameter(k, v) }
            last?.let { query.setParameter("cAt", it.first); query.setParameter("cId", it.second) }
            val fetched = query.resultList
            exhausted = fetched.size < batch
            for (t in fetched) {
                if (!filtersAmount || withinAmounts(t, criteria, decimals)) rows.add(t) else em.detach(t)
                if (rows.size > limit) break
            }
            fetched.lastOrNull()?.let { last = it.occurredAt to it.id }
        }
        val more = rows.size > limit
        val page = rows.take(limit)
        return SearchPage(
            entries = entries(page),
            nextCursor = if (more) page.last().let { encodeCursor(it.occurredAt, it.id) } else null
        )
    }

    /** Income or expense totals per currency, grouped; transfers are not spending and never count. */
    fun summarize(from: LocalDate, to: LocalDate, groupBy: GroupBy, type: TransactionType): Summary {
        require(type != TransactionType.TRANSFER) { "type must be EXPENSE or INCOME" }
        val household = securityUtils.getCurrentHousehold()
        val decimals = decimals()
        class Acc(val uuid: String?, val name: String, val monthKey: String?) {
            val minor = linkedMapOf<String, Long>()
        }
        val groups = linkedMapOf<String, Acc>()
        val grand = linkedMapOf<String, Long>()
        fun add(key: String, uuid: String?, name: String, month: String?, currency: String, sum: Long) {
            groups.getOrPut(key) { Acc(uuid, name, month) }.minor.merge(currency, sum, Long::plus)
            grand.merge(currency, sum, Long::plus)
        }

        // The database sums; the JVM only sees one row per (group, currency).
        fun grouped(select: String, groupBy: String, start: LocalDate, end: LocalDate): List<Array<Any?>> =
            em.createQuery(
                "SELECT $select, t.currencyCode, SUM(t.amountMinor) " +
                    "FROM Transaction t JOIN t.account a LEFT JOIN t.category c " +
                    "WHERE t.household = :household AND t.type = :type " +
                    "AND t.occurredAt >= :from AND t.occurredAt < :until " +
                    "GROUP BY $groupBy, t.currencyCode",
                Array<Any?>::class.java
            )
                .setParameter("household", household)
                .setParameter("type", type)
                .setParameter("from", startOf(start))
                .setParameter("until", startOf(end.plusDays(1)))
                .resultList

        when (groupBy) {
            GroupBy.CATEGORY -> grouped("c.uuid, c.name", "c.uuid, c.name", from, to).forEach {
                add((it[0] as String?).orEmpty(), it[0] as String?, (it[1] as String?) ?: "Uncategorised", null,
                    it[2] as String, it[3] as Long)
            }
            GroupBy.ACCOUNT -> grouped("a.uuid, a.name", "a.uuid, a.name", from, to).forEach {
                add(it[0] as String, it[0] as String, it[1] as String, null, it[2] as String, it[3] as Long)
            }
            // One grouped query per calendar month (UTC), so no database time-zone function is involved.
            GroupBy.MONTH -> {
                var month = java.time.YearMonth.from(from)
                while (!month.isAfter(java.time.YearMonth.from(to))) {
                    val start = maxOf(from, month.atDay(1))
                    val end = minOf(to, month.atEndOfMonth())
                    grouped("1", "1", start, end).forEach {
                        add(month.toString(), null, month.toString(), month.toString(), it[1] as String, it[2] as Long)
                    }
                    month = month.plusMonths(1)
                }
            }
        }
        fun money(m: Map<String, Long>) = m.entries
            .sortedBy { it.key }
            .associate { (c, v) -> c to MinorUnits.toDecimal(v, decimals[c] ?: 2) }
        val result = groups.values.map { Group(it.uuid, it.name, it.monthKey, money(it.minor)) }
        val sorted = if (groupBy == GroupBy.MONTH) {
            result.sortedBy { it.monthKey }
        } else {
            result.sortedWith(
                compareByDescending<Group> { it.totals.values.maxOrNull() ?: BigDecimal.ZERO }.thenBy { it.name }
            )
        }
        return Summary(sorted, money(grand))
    }

    private fun withinAmounts(t: Transaction, c: SearchCriteria, decimals: Map<String, Int>): Boolean {
        val value = MinorUnits.toDecimal(Math.abs(t.amountMinor), decimals[t.currencyCode] ?: 2)
        return (c.minAmount == null || value >= c.minAmount) && (c.maxAmount == null || value <= c.maxAmount)
    }

    private fun entries(page: List<Transaction>): List<Entry> {
        if (page.isEmpty()) return emptyList()
        val household = securityUtils.getCurrentHousehold()
        val groupIds = page.mapNotNull { if (it.type == TransactionType.TRANSFER) it.transferGroupId else null }
        val credits = if (groupIds.isEmpty()) emptyMap() else em.createQuery(
            "SELECT t FROM Transaction t WHERE t.household = :household AND t.transferGroupId IN :groups " +
                "AND t.accountAmountMinor > 0",
            Transaction::class.java
        ).setParameter("household", household).setParameter("groups", groupIds).resultList
            .associateBy { it.transferGroupId }
        val receiptIds = page.mapNotNull { it.receiptId }
        val receipts = if (receiptIds.isEmpty()) emptyMap() else em.createQuery(
            "SELECT DISTINCT r FROM Receipt r LEFT JOIN FETCH r.items WHERE r.id IN :ids AND r.household = :household",
            Receipt::class.java
        ).setParameter("ids", receiptIds).setParameter("household", household).resultList.associateBy { it.id }
        // TEMPORARY until Task 3 adds `source` to the Transaction entity: then read the field and drop this query.
        val sources = em.createNativeQuery("SELECT id, source FROM transactions WHERE id IN (:ids)")
            .setParameter("ids", page.map { it.id }).resultList
            .associate { row ->
                val r = row as Array<*>
                (r[0] as Number).toLong() to r[1] as String
            }
        return page.map {
            Entry(it, it.transferGroupId?.let(credits::get), sources[it.id] ?: "APP", it.receiptId?.let(receipts::get))
        }
    }

    private fun startOf(day: LocalDate): Instant = day.atStartOfDay(ZoneOffset.UTC).toInstant()

    private fun encodeCursor(at: Instant, id: Long): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString("$at|$id".toByteArray())

    private fun decodeCursor(cursor: String): Pair<Instant, Long> = try {
        val (at, id) = String(Base64.getUrlDecoder().decode(cursor)).split("|")
        Instant.parse(at) to id.toLong()
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid cursor — pass the nextCursor of the previous page unchanged")
    }
}
