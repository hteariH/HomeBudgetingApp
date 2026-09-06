package com.homebudget.app.importer.homemoney

import com.homebudget.app.domain.CategoryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * The translation from HomeMoney's model to ours, which is where every decision that can
 * silently corrupt a ledger lives: what a multi-currency account becomes, which operations
 * are not ledger entries at all, and how a transfer turns into a pair of rows.
 */
class HomeMoneyMapperTest {

    private fun account(id: String, name: String, vararg currencies: HomeMoneyAccountCurrency) =
        HomeMoneyAccount(id = id, name = name, currencies = currencies.toList())

    private fun currency(id: String, short: String, balance: String) =
        HomeMoneyAccountCurrency(id = id, shortName = short, balance = BigDecimal(balance))

    // ── Accounts ────────────────────────────────────────────────────────────

    @Test
    fun `an account holding two currencies becomes two accounts`() {
        val balances = HomeMoneyBalanceList(
            groups = listOf(
                HomeMoneyGroup(
                    id = "g1",
                    accounts = listOf(
                        account("1", "Wallet", currency("980", "UAH", "100"), currency("840", "USD", "20"))
                    )
                )
            )
        )

        val planned = HomeMoneyMapper.plannedAccounts(balances) { it }

        assertEquals(2, planned.size)
        assertEquals(listOf("homemoney:1:980", "homemoney:1:840"), planned.map { it.externalId })
        // The suffix earns its place here: two accounts called "Wallet" would be
        // indistinguishable in every picker in the app.
        assertEquals(listOf("Wallet (UAH)", "Wallet (USD)"), planned.map { it.name })
        assertEquals(BigDecimal("100"), planned[0].balance)
    }

    @Test
    fun `a single-currency account keeps its own name`() {
        val balances = HomeMoneyBalanceList(
            groups = listOf(HomeMoneyGroup(accounts = listOf(account("7", "Cash", currency("980", "UAH", "0")))))
        )

        val planned = HomeMoneyMapper.plannedAccounts(balances) { it }

        assertEquals(1, planned.size)
        assertEquals("Cash", planned[0].name)
    }

    @Test
    fun `an unresolved currency is reported rather than guessed`() {
        val balances = HomeMoneyBalanceList(
            groups = listOf(HomeMoneyGroup(accounts = listOf(account("9", "Odd", currency("1", "???", "5")))))
        )

        // Importing into the wrong currency misstates a balance and nothing says so, which
        // is exactly why the resolver is allowed to answer "I do not know".
        val planned = HomeMoneyMapper.plannedAccounts(balances) { null }

        assertNull(planned[0].currencyCode)
        assertEquals("???", planned[0].currencyLabel)
    }

    // ── Categories ──────────────────────────────────────────────────────────

    @Test
    fun `transfer categories are dropped and the other two directions are kept`() {
        val list = HomeMoneyCategoryList(
            categories = listOf(
                HomeMoneyCategory(id = "1", type = HomeMoneyKind.EXPENSE, name = "Groceries"),
                HomeMoneyCategory(id = "2", type = HomeMoneyKind.INCOME, name = "Salary"),
                HomeMoneyCategory(id = "3", type = HomeMoneyKind.TRANSFER, name = "Between accounts")
            )
        )

        val planned = HomeMoneyMapper.plannedCategories(list)

        // Our transfers carry no category at all - the two accounts are the whole story -
        // so a transfer category would be a row nothing could ever be filed under.
        assertEquals(2, planned.size)
        assertEquals(CategoryType.EXPENSE, planned[0].type)
        assertEquals(CategoryType.INCOME, planned[1].type)
    }

    @Test
    fun `a parent id of zero means the category is a root`() {
        val list = HomeMoneyCategoryList(
            categories = listOf(
                HomeMoneyCategory(id = "1", type = HomeMoneyKind.EXPENSE, name = "Food", parentId = "0"),
                HomeMoneyCategory(id = "2", type = HomeMoneyKind.EXPENSE, name = "Fuel", parentId = "1")
            )
        )

        val planned = HomeMoneyMapper.plannedCategories(list)

        assertNull(planned[0].parentExternalId)
        assertEquals("homemoney:1", planned[1].parentExternalId)
    }

    @Test
    fun `parents come before their children`() {
        val categories = listOf(
            PlannedCategory("homemoney:3", "Diesel", "Car / Fuel / Diesel", CategoryType.EXPENSE, "homemoney:2", false),
            PlannedCategory("homemoney:2", "Fuel", "Car / Fuel", CategoryType.EXPENSE, "homemoney:1", false),
            PlannedCategory("homemoney:1", "Car", "Car", CategoryType.EXPENSE, null, false)
        )

        val ordered = HomeMoneyMapper.parentsFirst(categories).map { it.externalId }

        assertEquals(listOf("homemoney:1", "homemoney:2", "homemoney:3"), ordered)
    }

    @Test
    fun `a category whose parent is missing is kept as a root`() {
        val categories = listOf(
            PlannedCategory("homemoney:5", "Orphan", "Orphan", CategoryType.EXPENSE, "homemoney:404", false)
        )

        // Losing the parent must not lose the children with it: an uncategorised-by-accident
        // tree is recoverable, a missing category takes its history's meaning with it.
        val ordered = HomeMoneyMapper.parentsFirst(categories)

        assertEquals(1, ordered.size)
        assertEquals("homemoney:5", ordered[0].externalId)
    }

    // ── Operations ──────────────────────────────────────────────────────────

    private fun operation(
        guid: String = "g1",
        type: Int = HomeMoneyKind.EXPENSE,
        total: String = "10.00",
        plan: Boolean = false,
        deleted: Boolean = false,
        transAccountId: String? = null,
        transCurrencyId: String? = null,
        transTotal: String = "0"
    ) = HomeMoneyTransaction(
        guid = guid,
        type = type,
        total = BigDecimal(total),
        accountId = "1",
        currencyId = "980",
        categoryId = "7",
        dateUnix = 1_700_000_000,
        plan = plan,
        deleted = deleted,
        transAccountId = transAccountId,
        transCurrencyId = transCurrencyId,
        transTotal = BigDecimal(transTotal)
    )

    @Test
    fun `a scheduled operation is not a ledger entry`() {
        // It has not happened. Importing it would overstate every balance it touches.
        assertNull(HomeMoneyMapper.toRow(operation(plan = true)))
    }

    @Test
    fun `a deleted operation is not imported`() {
        // HomeMoney tombstones rather than deletes, so the API keeps returning these
        // forever; importing one resurrects something the user threw away.
        assertNull(HomeMoneyMapper.toRow(operation(deleted = true)))
    }

    @Test
    fun `an expense keeps a positive magnitude and takes its direction from the type`() {
        val row = HomeMoneyMapper.toRow(operation(type = HomeMoneyKind.EXPENSE, total = "-24.50"))

        val single = assertIsSingle(row)
        assertEquals(BigDecimal("24.50"), single.amount)
        assertEquals(false, single.income)
        assertEquals("homemoney:1:980", single.accountExternalId)
        assertEquals("homemoney:7", single.categoryExternalId)
        assertEquals("homemoney:g1", single.externalId)
    }

    @Test
    fun `an income is the same row the other way round`() {
        val single = assertIsSingle(HomeMoneyMapper.toRow(operation(type = HomeMoneyKind.INCOME, total = "5000")))
        assertEquals(true, single.income)
        assertEquals(BigDecimal("5000"), single.amount)
    }

    @Test
    fun `a transfer names both ends`() {
        val row = HomeMoneyMapper.toRow(
            operation(
                type = HomeMoneyKind.TRANSFER,
                total = "1000",
                transAccountId = "2",
                transCurrencyId = "840",
                transTotal = "24.50"
            )
        )

        val transfer = row as HomeMoneyMapper.PlannedRow.Transfer
        assertEquals("homemoney:1:980", transfer.fromAccountExternalId)
        assertEquals("homemoney:2:840", transfer.toAccountExternalId)
        assertEquals(BigDecimal("1000"), transfer.fromAmount)
        assertEquals(BigDecimal("24.50"), transfer.toAmount)
    }

    @Test
    fun `a transfer missing its far side is skipped rather than half written`() {
        // One leg without its counterpart is not a partial import, it is a wrong balance
        // on the account that did get written.
        assertNull(HomeMoneyMapper.toRow(operation(type = HomeMoneyKind.TRANSFER, transAccountId = null)))
        assertNull(HomeMoneyMapper.toRow(operation(type = HomeMoneyKind.TRANSFER, transAccountId = "0")))
        assertNull(
            HomeMoneyMapper.toRow(
                operation(type = HomeMoneyKind.TRANSFER, transAccountId = "2", transCurrencyId = null)
            )
        )
        assertNull(
            HomeMoneyMapper.toRow(
                operation(type = HomeMoneyKind.TRANSFER, transAccountId = "2", transCurrencyId = "0")
            )
        )
    }

    @Test
    fun `description has HTML entities unescaped`() {
        val row = HomeMoneyMapper.toRow(
            operation().copy(description = "BAG&amp;GAB SHOP &#171;McDonald&#39;s&#187;")
        )
        val single = assertIsSingle(row)
        assertEquals("BAG&GAB SHOP «McDonald's»", single.description)
    }

    @Test
    fun `a category id of zero means uncategorised`() {
        val single = assertIsSingle(HomeMoneyMapper.toRow(operation().copy(categoryId = "0")))
        assertNull(single.categoryExternalId)
    }

    // ── Dates ───────────────────────────────────────────────────────────────

    @Test
    fun `seconds and milliseconds are both read as the same instant`() {
        // The unit is undocumented, and being out by a factor of a thousand puts an
        // operation in the year 55000 rather than failing where anyone would notice.
        assertEquals(
            Instant.ofEpochSecond(1_700_000_000),
            operation().copy(dateUnix = 1_700_000_000).occurredAt()
        )
        assertEquals(
            Instant.ofEpochMilli(1_700_000_000_000),
            operation().copy(dateUnix = 1_700_000_000_000).occurredAt()
        )
    }

    @Test
    fun `the dot-net date format is understood`() {
        val at = operation().copy(dateUnix = 0, date = "/Date(1700000000000)/").occurredAt()
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000), at)
    }

    @Test
    fun `the dot-separated date format from HomeMoney is understood`() {
        val at = operation().copy(dateUnix = 0, date = "31.12.2024 0:00:00").occurredAt()
        assertEquals(Instant.parse("2024-12-31T00:00:00Z"), at)
    }

    @Test
    fun `an ISO date with and without a time both work`() {
        assertEquals(
            Instant.parse("2026-03-04T10:15:00Z"),
            operation().copy(dateUnix = 0, date = "2026-03-04T10:15:00").occurredAt()
        )
        assertEquals(
            Instant.parse("2026-03-04T00:00:00Z"),
            operation().copy(dateUnix = 0, date = "2026-03-04").occurredAt()
        )
    }

    // ── Currency labels ─────────────────────────────────────────────────────

    @Test
    fun `currency labels resolve from codes and symbols, and give up otherwise`() {
        assertEquals("UAH", HomeMoneyCurrencyLabels.toIsoCode("UAH"))
        assertEquals("UAH", HomeMoneyCurrencyLabels.toIsoCode("₴"))
        assertEquals("UAH", HomeMoneyCurrencyLabels.toIsoCode("грн"))
        assertEquals("USD", HomeMoneyCurrencyLabels.toIsoCode("$"))
        assertEquals("USDT", HomeMoneyCurrencyLabels.toIsoCode("usdt"))
        // Anything else is handed back to the user to choose. A guess here is a silently
        // wrong balance.
        assertNull(HomeMoneyCurrencyLabels.toIsoCode("Ukrainian hryvnia"))
        assertNull(HomeMoneyCurrencyLabels.toIsoCode(" "))
    }

    // ── Unused currency slots ───────────────────────────────────────────────

    private fun planned(externalId: String, balance: String) = PlannedAccount(
        externalId = externalId,
        homeMoneyAccountId = externalId.substringAfter("homemoney:").substringBefore(":"),
        homeMoneyCurrencyId = externalId.substringAfterLast(":"),
        name = externalId,
        currencyLabel = "UAH",
        currencyCode = "UAH",
        balance = BigDecimal(balance)
    )

    private fun single(accountExternalId: String) = HomeMoneyMapper.PlannedRow.Single(
        externalId = "homemoney:op",
        occurredAt = Instant.EPOCH,
        description = null,
        accountExternalId = accountExternalId,
        categoryExternalId = null,
        income = false,
        amount = BigDecimal("10")
    )

    private fun transfer(from: String, to: String) = HomeMoneyMapper.PlannedRow.Transfer(
        externalId = "homemoney:tx",
        occurredAt = Instant.EPOCH,
        description = null,
        fromAccountExternalId = from,
        toAccountExternalId = to,
        fromAmount = BigDecimal("10"),
        toAmount = BigDecimal("10")
    )

    @Test
    fun `a currency slot that is empty and was never used comes unticked`() {
        // What HomeMoney reports for one deposit: the currency it is actually in, and
        // four slots somebody added to it years ago and never put anything in.
        val accounts = listOf(
            planned("homemoney:7:980", "1500.00"),
            planned("homemoney:7:840", "0"),
            planned("homemoney:7:978", "0.0000"),
            planned("homemoney:7:946", "0")
        )

        val result = HomeMoneyMapper.untickUnusedCurrencySlots(accounts, listOf(single("homemoney:7:980")))

        assertEquals(listOf(true, false, false, false), result.map { it.included })
    }

    @Test
    fun `an empty account with history stays ticked, and so does the far end of a transfer`() {
        val accounts = listOf(
            // Spent down to nothing, but a decade of operations behind it: unticking this
            // would drop that history, and with it the transfers it took part in.
            planned("homemoney:1:980", "0"),
            planned("homemoney:2:840", "0.00"),
            planned("homemoney:3:978", "0")
        )

        val result = HomeMoneyMapper.untickUnusedCurrencySlots(
            accounts,
            listOf(single("homemoney:1:980"), transfer("homemoney:2:840", "homemoney:3:978"))
        )

        assertTrue(result.all { it.included })
    }

    @Test
    fun `a slot with a balance is kept even when nothing ever moved through it`() {
        val accounts = listOf(planned("homemoney:9:840", "20.00"))

        val result = HomeMoneyMapper.untickUnusedCurrencySlots(accounts, emptyList())

        // Money with no history is still money: the opening balance is the whole account.
        assertTrue(result.single().included)
    }

    private fun assertIsSingle(row: HomeMoneyMapper.PlannedRow?): HomeMoneyMapper.PlannedRow.Single {
        assertTrue(row is HomeMoneyMapper.PlannedRow.Single, "expected a single-account row, got $row")
        return row as HomeMoneyMapper.PlannedRow.Single
    }
}
