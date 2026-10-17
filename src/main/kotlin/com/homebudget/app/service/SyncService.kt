package com.homebudget.app.service

import com.homebudget.app.domain.*
import com.homebudget.app.dto.*
import com.homebudget.app.household.HouseholdChangedException
import com.homebudget.app.receipt.ReceiptService
import com.homebudget.app.repository.*
import com.homebudget.app.security.SecurityUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Two-way sync between the server and an offline-capable client.
 *
 * Three rules hold the whole thing together:
 *
 * 1. **Identity is the uuid, not the id.** A phone creates rows while offline and
 *    references them before the server has ever seen them, so a push carries a whole
 *    object graph keyed by uuids the client minted itself.
 *
 * 2. **Ordering is the server's clock.** `updatedAt` is stamped here on every accepted
 *    write and doubles as the pull cursor. Client clocks never enter into it.
 *
 * 3. **Conflicts are decided by version, not by time.** A client sends the
 *    `baseUpdatedAt` it last saw; if the server's copy has moved on since, the write is
 *    rejected as STALE and the client takes the server's version on its next pull. This
 *    is optimistic concurrency, and unlike last-write-wins-by-timestamp it cannot be
 *    defeated by a device with a wrong clock.
 *
 * Every reference is re-checked against the caller's household. A uuid in a push body
 * is a pointer, and an unchecked pointer is how one household writes into another's data.
 */
@Service
class SyncService(
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val subscriptionRepo: SubscriptionRepository,
    private val transactionRepo: TransactionRepository,
    private val settingRepo: UserCurrencySettingRepository,
    private val currencyRepo: CurrencyRepository,
    private val householdRepo: HouseholdRepository,
    private val memberRepo: HouseholdMemberRepository,
    private val securityUtils: SecurityUtils,
    private val syncClock: SyncClock,
    private val receiptService: ReceiptService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Cap on rows per entity per pull, and on a whole push. */
        const val MAX_PAGE = 500
        const val MAX_PUSH = 2000
    }

    // ── Pull ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun pull(since: Instant, limit: Int): SyncPullResponse {
        val household = securityUtils.getCurrentHousehold()
        val capped = limit.coerceIn(1, MAX_PAGE)
        val baseDecimals = household.baseCurrency?.decimals ?: DEFAULT_DECIMALS

        val settings = settingRepo.findChangedSince(household.id, since, capped)
        val accounts = accountRepo.findChangedSince(household.id, since, capped)
        val categories = categoryRepo.findChangedSince(household.id, since, capped)
        val subscriptions = subscriptionRepo.findChangedSince(household.id, since, capped)
        val transactions = transactionRepo.findChangedSince(household.id, since, capped)

        val payload = SyncPayload(
            currencySettings = settings.map { it.toSync() },
            accounts = accounts.map { it.toSync() },
            categories = categories.map { it.toSync(baseDecimals) },
            subscriptions = subscriptions.map { it.toSync() },
            // Transfer legs point at their counterpart by id in the database; the wire
            // format speaks uuids, so the whole account list is indexed once here
            // rather than looked up per row.
            transactions = transactions.map { it.toSync(accountUuidsById(household)) }
        )

        val hasMore = listOf(settings, accounts, categories, subscriptions, transactions)
            .any { it.size >= capped }

        // The cursor is a timestamp that actually appears in the data, never `now`.
        // Pushes stamp strictly-increasing times (see [SyncClock]), so any later write
        // is guaranteed to sort after this cursor; handing back `now` instead would
        // skip a write that happened to land in the same millisecond as the pull.
        //
        // When the page was capped, back off to the *earliest* of the per-entity page
        // ends so nothing between the lists is stepped over.
        val returned = listOf(settings, accounts, categories, subscriptions, transactions)
        val cursor = if (hasMore) {
            returned.filter { it.size >= capped }
                .mapNotNull { list -> list.lastOrNull()?.updatedAt }
                .minOrNull() ?: since
        } else {
            returned.flatten().maxOfOrNull { it.updatedAt } ?: since
        }

        return SyncPullResponse(serverTime = cursor, hasMore = hasMore, changes = payload)
    }

    // ── Push ────────────────────────────────────────────────────────────────

    @Transactional
    fun push(request: SyncPushRequest): SyncPushResponse {
        val household = securityUtils.getCurrentHousehold()
        val actor = securityUtils.getCurrentUser()
        val changes = request.changes
        require(changes.size() <= MAX_PUSH) {
            "Push of ${changes.size()} records exceeds the limit of $MAX_PUSH; split it"
        }

        // One push per household at a time: two members pushing together would otherwise
        // read the same latest stamp and write the same instant, and a pull whose cursor
        // fell between their commits would skip one of them for good.
        householdRepo.lockById(household.id)
        // The request was authenticated against the household the caller belonged to
        // then; a join, leave or removal may have moved them since. Writing on would put
        // their rows into a household they are no longer in.
        if (memberRepo.currentHouseholdIdOf(actor.id) != listOf(household.id)) {
            throw HouseholdChangedException()
        }

        val now = syncClock.next(household.id)
        val rejected = mutableListOf<SyncRejection>()
        var applied = 0

        // Parents before children: a transaction cannot resolve an account uuid that
        // has not been written yet in this same request.
        applied += applyCurrencySettings(household, changes.currencySettings, now, rejected)
        applied += applyAccounts(household, changes.accounts, now, rejected)
        applied += applyCategories(household, changes.categories, now, rejected)
        applied += applySubscriptions(household, changes.subscriptions, now, rejected)
        applied += applyTransactions(household, actor, changes.transactions, now, rejected)

        if (rejected.isNotEmpty()) {
            log.info("Sync push for household {}: {} applied, {} rejected", household.id, applied, rejected.size)
        }
        return SyncPushResponse(serverTime = now, applied = applied, rejected = rejected)
    }

    /**
     * True when the client's write is based on a version the server has since replaced.
     * A record the server has never seen (`existing == null`) is always accepted.
     */
    private fun isStale(existing: Syncable?, record: SyncRecord): Boolean {
        if (existing == null) return false
        val base = record.baseUpdatedAt ?: return true // client thinks it is new, server disagrees
        return existing.updatedAt.isAfter(base)
    }

    private fun reject(
        rejected: MutableList<SyncRejection>,
        record: SyncRecord,
        entity: String,
        reason: SyncRejectionReason,
        message: String
    ) {
        rejected += SyncRejection(record.uuid, entity, reason, message)
    }

    private fun applyCurrencySettings(
        household: Household,
        records: List<SyncCurrencySetting>,
        now: Instant,
        rejected: MutableList<SyncRejection>
    ): Int {
        var applied = 0
        for (record in records) {
            val existing = settingRepo.findByUuidIncludingDeleted(record.uuid, household.id)
            if (isStale(existing, record)) {
                reject(rejected, record, "currencySetting", SyncRejectionReason.STALE, "server copy is newer")
                continue
            }
            val currency = currencyRepo.findByCode(record.currencyCode)
            if (currency == null) {
                reject(rejected, record, "currencySetting", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown currency ${record.currencyCode}")
                continue
            }
            val entity = existing ?: UserCurrencySetting(uuid = record.uuid, household = household, currency = currency)
            entity.currency = currency
            entity.active = record.active
            entity.sortOrder = record.sortOrder
            entity.deleted = record.deleted
            entity.updatedAt = now
            settingRepo.save(entity)
            applied++
        }
        return applied
    }

    private fun applyAccounts(
        household: Household,
        records: List<SyncAccount>,
        now: Instant,
        rejected: MutableList<SyncRejection>
    ): Int {
        var applied = 0
        for (record in records) {
            val existing = accountRepo.findByUuidIncludingDeleted(record.uuid, household.id)
            if (isStale(existing, record)) {
                reject(rejected, record, "account", SyncRejectionReason.STALE, "server copy is newer")
                continue
            }
            val currency = currencyRepo.findByCode(record.currencyCode)
            if (currency == null) {
                reject(rejected, record, "account", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown currency ${record.currencyCode}")
                continue
            }
            val entity = existing ?: Account(
                uuid = record.uuid, name = record.name, household = household, currency = currency
            )
            entity.name = record.name
            entity.type = record.type
            entity.currency = currency
            entity.initialBalanceMinor = MinorUnits.toMinor(record.initialBalance, currency.decimals)
            entity.description = record.description
            entity.icon = record.icon
            entity.color = record.color
            entity.archived = record.archived
            entity.sortOrder = record.sortOrder
            entity.includeInTotal = record.includeInTotal
            entity.deleted = record.deleted
            entity.updatedAt = now
            accountRepo.save(entity)
            applied++
        }
        return applied
    }

    private fun applyCategories(
        household: Household,
        records: List<SyncCategory>,
        now: Instant,
        rejected: MutableList<SyncRejection>
    ): Int {
        val baseDecimals = household.baseCurrency?.decimals ?: DEFAULT_DECIMALS
        var applied = 0
        // Parents first, so a child in the same batch resolves. Categories are one
        // level deep in practice, so a single ordering pass is enough.
        val ordered = records.sortedBy { it.parentUuid != null }
        for (record in ordered) {
            val existing = categoryRepo.findByUuidIncludingDeleted(record.uuid, household.id)
            if (isStale(existing, record)) {
                reject(rejected, record, "category", SyncRejectionReason.STALE, "server copy is newer")
                continue
            }
            val parent = record.parentUuid?.let { categoryRepo.findByUuidIncludingDeleted(it, household.id) }
            if (record.parentUuid != null && parent == null) {
                reject(rejected, record, "category", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown parent ${record.parentUuid}")
                continue
            }
            val entity = existing ?: Category(
                uuid = record.uuid, name = record.name, household = household, type = record.type
            )
            entity.name = record.name
            entity.type = record.type
            entity.icon = record.icon
            entity.color = record.color
            entity.description = record.description
            entity.parent = parent
            entity.archived = record.archived
            entity.sortOrder = record.sortOrder
            entity.isDefault = record.isDefault
            entity.budgetLimitMinor = record.budgetLimit?.let { MinorUnits.toMinor(it, baseDecimals) }
            entity.deleted = record.deleted
            entity.updatedAt = now
            categoryRepo.save(entity)
            applied++
        }
        return applied
    }

    private fun applySubscriptions(
        household: Household,
        records: List<SyncSubscription>,
        now: Instant,
        rejected: MutableList<SyncRejection>
    ): Int {
        var applied = 0
        for (record in records) {
            val existing = subscriptionRepo.findByUuidIncludingDeleted(record.uuid, household.id)
            if (isStale(existing, record)) {
                reject(rejected, record, "subscription", SyncRejectionReason.STALE, "server copy is newer")
                continue
            }
            val account = accountRepo.findByUuidIncludingDeleted(record.accountUuid, household.id)
            if (account == null) {
                reject(rejected, record, "subscription", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown account ${record.accountUuid}")
                continue
            }
            val category = record.categoryUuid?.let { categoryRepo.findByUuidIncludingDeleted(it, household.id) }
            if (record.categoryUuid != null && category == null) {
                reject(rejected, record, "subscription", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown category ${record.categoryUuid}")
                continue
            }
            val entity = existing ?: Subscription(
                uuid = record.uuid,
                household = household,
                name = record.name,
                account = account,
                amountMinor = 0,
                currencyCode = record.currencyCode,
                frequency = record.frequency,
                nextChargeDate = record.nextChargeDate,
                startDate = record.startDate
            )
            entity.name = record.name
            entity.account = account
            entity.category = category
            entity.amountMinor = MinorUnits.toMinor(record.amount, account.currency.decimals)
            entity.currencyCode = record.currencyCode
            entity.frequency = record.frequency
            entity.intervalCount = record.intervalCount
            entity.nextChargeDate = record.nextChargeDate
            entity.startDate = record.startDate
            entity.endDate = record.endDate
            entity.active = record.active
            entity.reminderMinutesBefore = record.reminderMinutesBefore
            entity.icon = record.icon
            entity.color = record.color
            entity.note = record.note
            entity.deleted = record.deleted
            entity.updatedAt = now
            subscriptionRepo.save(entity)
            applied++
        }
        return applied
    }

    /**
     * Writes pushed transactions. A null `receiptUuid` keeps whatever receipt the server
     * has linked - deliberately, so a client that predates receipts cannot unlink one.
     */
    private fun applyTransactions(
        household: Household,
        actor: User,
        records: List<SyncTransaction>,
        now: Instant,
        rejected: MutableList<SyncRejection>
    ): Int {
        var applied = 0
        for (record in records) {
            val existing = transactionRepo.findByUuidIncludingDeleted(record.uuid, household.id)
            if (isStale(existing, record)) {
                reject(rejected, record, "transaction", SyncRejectionReason.STALE, "server copy is newer")
                continue
            }
            val account = accountRepo.findByUuidIncludingDeleted(record.accountUuid, household.id)
            if (account == null) {
                reject(rejected, record, "transaction", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown account ${record.accountUuid}")
                continue
            }
            val category = record.categoryUuid?.let { categoryRepo.findByUuidIncludingDeleted(it, household.id) }
            if (record.categoryUuid != null && category == null) {
                reject(rejected, record, "transaction", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown category ${record.categoryUuid}")
                continue
            }
            val subscription = record.subscriptionUuid?.let {
                subscriptionRepo.findByUuidIncludingDeleted(it, household.id)
            }
            val counterpart = record.transferCounterpartAccountUuid?.let {
                accountRepo.findByUuidIncludingDeleted(it, household.id)
            }
            if (record.transferCounterpartAccountUuid != null && counterpart == null) {
                reject(rejected, record, "transaction", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown counterpart account ${record.transferCounterpartAccountUuid}")
                continue
            }
            // A receipt to attach must be the household's and not another entry's. One that is
            // not is dropped rather than the entry: the entry is the record, the photo a bonus.
            // A null uuid leaves the link alone; a tombstone takes its receipt with it below.
            val receiptUuid = record.receiptUuid.takeUnless { record.deleted }
            val receipt = receiptUuid?.let { receiptService.lookUp(it, household.id) }?.takeUnless {
                receiptService.isLinkedElsewhere(it, record.uuid)
            }
            if (receiptUuid != null && receipt == null) {
                log.info("Sync push for household {}: a transaction names a receipt it cannot have; saving it without", household.id)
            }

            val decimals = account.currency.decimals
            // The author is whoever first pushed the row; later edits do not rewrite it. Nor is
            // the source a client's to set: a new row is the app's, an existing one keeps its own.
            val entity = existing ?: Transaction(
                uuid = record.uuid,
                household = household,
                createdBy = actor,
                account = account,
                amountMinor = 0,
                currencyCode = record.currencyCode,
                accountAmountMinor = 0,
                type = record.type,
                occurredAt = record.occurredAt
            )
            entity.account = account
            entity.category = category
            entity.amountMinor = MinorUnits.toMinor(record.amount, decimals)
            entity.currencyCode = record.currencyCode
            entity.accountAmountMinor = MinorUnits.toMinor(record.accountAmount, decimals)
            entity.fxRateToAccount = record.fxRateToAccount
            entity.type = record.type
            entity.occurredAt = record.occurredAt
            entity.description = record.description
            entity.subscription = subscription
            entity.transferGroupId = record.transferGroupId
            entity.transferCounterpartAccountId = counterpart?.id
            entity.transferFeeMinor = record.transferFee?.let { MinorUnits.toMinor(it, decimals) }
            entity.deleted = record.deleted
            entity.updatedAt = now
            val discardedReceipt = if (record.deleted) entity.receiptId else null
            if (record.deleted) entity.linkReceipt(null) else if (receipt != null) entity.linkReceipt(receipt)
            transactionRepo.save(entity)
            discardedReceipt?.let(receiptService::deleteWithPhoto)
            applied++
        }
        return applied
    }

    // ── Entity -> wire ──────────────────────────────────────────────────────

    private fun UserCurrencySetting.toSync() = SyncCurrencySetting(
        uuid = uuid,
        updatedAt = updatedAt,
        deleted = deleted,
        currencyCode = currency.code,
        active = active,
        sortOrder = sortOrder
    )

    private fun Account.toSync() = SyncAccount(
        uuid = uuid,
        updatedAt = updatedAt,
        deleted = deleted,
        name = name,
        type = type,
        currencyCode = currency.code,
        initialBalance = MinorUnits.toDecimal(initialBalanceMinor, currency.decimals),
        description = description,
        icon = icon,
        color = color,
        archived = archived,
        sortOrder = sortOrder,
        includeInTotal = includeInTotal
    )

    private fun accountUuidsById(household: Household): Map<Long, String> =
        accountRepo.findAllByHousehold(household).associate { it.id to it.uuid }

    private fun Category.toSync(baseDecimals: Int) = SyncCategory(
        uuid = uuid,
        updatedAt = updatedAt,
        deleted = deleted,
        name = name,
        type = type,
        icon = icon,
        color = color,
        description = description,
        parentUuid = parent?.uuid,
        archived = archived,
        sortOrder = sortOrder,
        isDefault = isDefault,
        budgetLimit = budgetLimitMinor?.let { MinorUnits.toDecimal(it, baseDecimals) }
    )

    private fun Subscription.toSync() = SyncSubscription(
        uuid = uuid,
        updatedAt = updatedAt,
        deleted = deleted,
        name = name,
        accountUuid = account.uuid,
        categoryUuid = category?.uuid,
        amount = MinorUnits.toDecimal(amountMinor, account.currency.decimals),
        currencyCode = currencyCode,
        frequency = frequency,
        intervalCount = intervalCount,
        nextChargeDate = nextChargeDate,
        startDate = startDate,
        endDate = endDate,
        active = active,
        reminderMinutesBefore = reminderMinutesBefore,
        icon = icon,
        color = color,
        note = note
    )

    private fun Transaction.toSync(accountUuids: Map<Long, String>) = SyncTransaction(
        uuid = uuid,
        updatedAt = updatedAt,
        deleted = deleted,
        accountUuid = account.uuid,
        categoryUuid = category?.uuid,
        amount = MinorUnits.toDecimal(amountMinor, account.currency.decimals),
        currencyCode = currencyCode,
        accountAmount = MinorUnits.toDecimal(accountAmountMinor, account.currency.decimals),
        fxRateToAccount = fxRateToAccount,
        type = type,
        occurredAt = occurredAt,
        description = description,
        subscriptionUuid = subscription?.uuid,
        transferGroupId = transferGroupId,
        transferCounterpartAccountUuid = transferCounterpartAccountId?.let { accountUuids[it] },
        transferFee = transferFeeMinor?.let { MinorUnits.toDecimal(it, account.currency.decimals) },
        createdByUserId = createdBy?.id,
        receiptUuid = receiptUuid,
        source = source
    )
}
