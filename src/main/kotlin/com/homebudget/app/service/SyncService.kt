package com.homebudget.app.service

import com.homebudget.app.domain.*
import com.homebudget.app.dto.*
import com.homebudget.app.repository.*
import com.homebudget.app.security.SecurityUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
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
 * Every reference is re-checked against the calling user. A uuid in a push body is a
 * pointer, and an unchecked pointer is how one account writes into another's data.
 */
@Service
class SyncService(
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val subscriptionRepo: SubscriptionRepository,
    private val transactionRepo: TransactionRepository,
    private val settingRepo: UserCurrencySettingRepository,
    private val currencyRepo: CurrencyRepository,
    private val cursorRepo: SyncCursorRepository,
    private val splitRepo: TransactionSplitRepository,
    private val goalRepo: GoalRepository,
    private val tagRepo: TagRepository,
    private val transactionTagRepo: TransactionTagRepository,
    private val securityUtils: SecurityUtils,
    private val clock: Clock
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
        val user = securityUtils.getCurrentUser()
        val capped = limit.coerceIn(1, MAX_PAGE)
        val baseDecimals = user.baseCurrency?.decimals ?: DEFAULT_DECIMALS

        val settings = settingRepo.findChangedSince(user.id, since, capped)
        val accounts = accountRepo.findChangedSince(user.id, since, capped)
        val categories = categoryRepo.findChangedSince(user.id, since, capped)
        val subscriptions = subscriptionRepo.findChangedSince(user.id, since, capped)
        val transactions = transactionRepo.findChangedSince(user.id, since, capped)
        val splits = splitRepo.findChangedSince(user.id, since, capped)
        val goals = goalRepo.findChangedSince(user.id, since, capped)
        val tags = tagRepo.findChangedSince(user.id, since, capped)
        val transactionTags = transactionTagRepo.findChangedSince(user.id, since, capped)

        val payload = SyncPayload(
            currencySettings = settings.map { it.toSync() },
            accounts = accounts.map { it.toSync() },
            categories = categories.map { it.toSync(baseDecimals) },
            subscriptions = subscriptions.map { it.toSync() },
            // Transfer legs point at their counterpart by id in the database; the wire
            // format speaks uuids, so the whole account list is indexed once here
            // rather than looked up per row.
            transactions = transactions.map { it.toSync(accountUuidsById(user)) },
            transactionSplits = splits.map { it.toSync() },
            goals = goals.map { it.toSync() },
            tags = tags.map { it.toSync() },
            transactionTags = transactionTags.map { it.toSync() }
        )

        val returned = listOf(settings, accounts, categories, subscriptions, transactions, splits, goals, tags, transactionTags)
        val hasMore = returned.any { it.size >= capped }

        // The cursor is a timestamp that actually appears in the data, never `now`.
        // Pushes stamp strictly-increasing times (see [nextStamp]), so any later write
        // is guaranteed to sort after this cursor; handing back `now` instead would
        // skip a write that happened to land in the same millisecond as the pull.
        //
        // When the page was capped, back off to the *earliest* of the per-entity page
        // ends so nothing between the lists is stepped over.
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
        val user = securityUtils.getCurrentUser()
        val changes = request.changes
        require(changes.size() <= MAX_PUSH) {
            "Push of ${changes.size()} records exceeds the limit of $MAX_PUSH; split it"
        }

        val now = nextStamp(user)
        val rejected = mutableListOf<SyncRejection>()
        var applied = 0

        // Parents before children: a transaction cannot resolve an account uuid that
        // has not been written yet in this same request.
        applied += applyCurrencySettings(user, changes.currencySettings, now, rejected)
        applied += applyAccounts(user, changes.accounts, now, rejected)
        applied += applyCategories(user, changes.categories, now, rejected)
        applied += applySubscriptions(user, changes.subscriptions, now, rejected)
        applied += applyTransactions(user, changes.transactions, now, rejected)
        applied += applyTransactionSplits(user, changes.transactionSplits, now, rejected)
        applied += applyGoals(user, changes.goals, now, rejected)
        applied += applyTags(user, changes.tags, now, rejected)
        applied += applyTransactionTags(user, changes.transactionTags, now, rejected)

        if (rejected.isNotEmpty()) {
            log.info("Sync push for user {}: {} applied, {} rejected", user.id, applied, rejected.size)
        }
        return SyncPushResponse(serverTime = now, applied = applied, rejected = rejected)
    }

    /**
     * A stamp strictly later than anything this user already has.
     *
     * Millisecond collisions are not hypothetical - creating and deleting a row in
     * quick succession lands both in the same millisecond, and `updated_at > cursor`
     * then loses the second one permanently.
     */
    private fun nextStamp(user: User): Instant {
        val now = Instant.now(clock)
        val latest = cursorRepo.latestUpdatedAt(user.id) ?: return now
        return if (now.isAfter(latest)) now else latest.plusMillis(1)
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
        user: User,
        records: List<SyncCurrencySetting>,
        now: Instant,
        rejected: MutableList<SyncRejection>
    ): Int {
        var applied = 0
        for (record in records) {
            val existing = settingRepo.findByUuidIncludingDeleted(record.uuid, user.id)
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
            val entity = existing ?: UserCurrencySetting(uuid = record.uuid, user = user, currency = currency)
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
        user: User,
        records: List<SyncAccount>,
        now: Instant,
        rejected: MutableList<SyncRejection>
    ): Int {
        var applied = 0
        for (record in records) {
            val existing = accountRepo.findByUuidIncludingDeleted(record.uuid, user.id)
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
            // The same cap the REST path applies, on the path a phone actually uses.
            // Enforced here too because a limit only one door honours is not a limit -
            // and only for a record the server has never seen, so an edit to an account
            // that already exists always goes through.
            if (existing == null && !user.isPremium() &&
                accountRepo.countByUserAndArchivedFalse(user) >= PlanLimits.FREE_ACCOUNTS
            ) {
                reject(rejected, record, "account", SyncRejectionReason.INVALID,
                    "the free plan covers ${PlanLimits.FREE_ACCOUNTS} accounts")
                continue
            }
            val entity = existing ?: Account(
                uuid = record.uuid, name = record.name, user = user, currency = currency
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
        user: User,
        records: List<SyncCategory>,
        now: Instant,
        rejected: MutableList<SyncRejection>
    ): Int {
        val baseDecimals = user.baseCurrency?.decimals ?: DEFAULT_DECIMALS
        var applied = 0
        // Parents first, so a child in the same batch resolves. Categories are one
        // level deep in practice, so a single ordering pass is enough.
        val ordered = records.sortedBy { it.parentUuid != null }
        for (record in ordered) {
            val existing = categoryRepo.findByUuidIncludingDeleted(record.uuid, user.id)
            if (isStale(existing, record)) {
                reject(rejected, record, "category", SyncRejectionReason.STALE, "server copy is newer")
                continue
            }
            val parent = record.parentUuid?.let { categoryRepo.findByUuidIncludingDeleted(it, user.id) }
            if (record.parentUuid != null && parent == null) {
                reject(rejected, record, "category", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown parent ${record.parentUuid}")
                continue
            }
            val entity = existing ?: Category(
                uuid = record.uuid, name = record.name, user = user, type = record.type
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
            entity.rolloverEnabled = record.rolloverEnabled
            entity.rolloverNegative = record.rolloverNegative
            entity.deleted = record.deleted
            entity.updatedAt = now
            categoryRepo.save(entity)
            applied++
        }
        return applied
    }

    private fun applySubscriptions(
        user: User,
        records: List<SyncSubscription>,
        now: Instant,
        rejected: MutableList<SyncRejection>
    ): Int {
        var applied = 0
        for (record in records) {
            val existing = subscriptionRepo.findByUuidIncludingDeleted(record.uuid, user.id)
            if (isStale(existing, record)) {
                reject(rejected, record, "subscription", SyncRejectionReason.STALE, "server copy is newer")
                continue
            }
            val account = accountRepo.findByUuidIncludingDeleted(record.accountUuid, user.id)
            if (account == null) {
                reject(rejected, record, "subscription", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown account ${record.accountUuid}")
                continue
            }
            val category = record.categoryUuid?.let { categoryRepo.findByUuidIncludingDeleted(it, user.id) }
            if (record.categoryUuid != null && category == null) {
                reject(rejected, record, "subscription", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown category ${record.categoryUuid}")
                continue
            }
            val entity = existing ?: Subscription(
                uuid = record.uuid,
                user = user,
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

    private fun applyTransactions(
        user: User,
        records: List<SyncTransaction>,
        now: Instant,
        rejected: MutableList<SyncRejection>
    ): Int {
        var applied = 0
        for (record in records) {
            val existing = transactionRepo.findByUuidIncludingDeleted(record.uuid, user.id)
            if (isStale(existing, record)) {
                reject(rejected, record, "transaction", SyncRejectionReason.STALE, "server copy is newer")
                continue
            }
            val account = accountRepo.findByUuidIncludingDeleted(record.accountUuid, user.id)
            if (account == null) {
                reject(rejected, record, "transaction", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown account ${record.accountUuid}")
                continue
            }
            val category = record.categoryUuid?.let { categoryRepo.findByUuidIncludingDeleted(it, user.id) }
            if (record.categoryUuid != null && category == null) {
                reject(rejected, record, "transaction", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown category ${record.categoryUuid}")
                continue
            }
            val subscription = record.subscriptionUuid?.let {
                subscriptionRepo.findByUuidIncludingDeleted(it, user.id)
            }
            val counterpart = record.transferCounterpartAccountUuid?.let {
                accountRepo.findByUuidIncludingDeleted(it, user.id)
            }
            if (record.transferCounterpartAccountUuid != null && counterpart == null) {
                reject(rejected, record, "transaction", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown counterpart account ${record.transferCounterpartAccountUuid}")
                continue
            }

            val decimals = account.currency.decimals
            val entity = existing ?: Transaction(
                uuid = record.uuid,
                user = user,
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
            transactionRepo.save(entity)
            applied++
        }
        return applied
    }

    private fun applyTransactionSplits(
        user: User,
        records: List<SyncTransactionSplit>,
        now: Instant,
        rejected: MutableList<SyncRejection>
    ): Int {
        var applied = 0
        val byTx = records.groupBy { it.transactionUuid }
        for ((txUuid, txRecords) in byTx) {
            val transaction = transactionRepo.findByUuidIncludingDeleted(txUuid, user.id)
            if (transaction == null) {
                for (record in txRecords) {
                    reject(rejected, record, "transactionSplit", SyncRejectionReason.UNKNOWN_REFERENCE,
                        "unknown transaction $txUuid")
                }
                continue
            }

            val decimals = transaction.account.currency.decimals

            // Existing splits for this transaction
            val existingSplits = splitRepo.findAllByTransactionIdIncludingDeleted(transaction.id)
                .associateBy { it.uuid }
                .toMutableMap()

            // Simulate the resulting state for non-deleted splits
            val simulatedSplits = existingSplits.toMutableMap()
            for (record in txRecords) {
                if (record.deleted) {
                    simulatedSplits.remove(record.uuid)
                } else {
                    val amountMinor = MinorUnits.toMinor(record.amount, decimals)
                    simulatedSplits[record.uuid] = simulatedSplits[record.uuid]?.copy(amountMinor = amountMinor, deleted = false)
                        ?: TransactionSplit(
                            uuid = record.uuid,
                            user = user,
                            transaction = transaction,
                            amountMinor = amountMinor
                        )
                }
            }

            val activeSplits = simulatedSplits.values.filter { !it.deleted }
            if (activeSplits.isNotEmpty()) {
                val sumSplits = activeSplits.sumOf { it.amountMinor }
                if (sumSplits != transaction.amountMinor) {
                    for (record in txRecords) {
                        reject(rejected, record, "transactionSplit", SyncRejectionReason.INVALID,
                            "Sum of split amounts ($sumSplits) does not equal transaction amount (${transaction.amountMinor})")
                    }
                    continue
                }
            }

            for (record in txRecords) {
                val existing = splitRepo.findByUuidIncludingDeleted(record.uuid, user.id)
                if (isStale(existing, record)) {
                    reject(rejected, record, "transactionSplit", SyncRejectionReason.STALE, "server copy is newer")
                    continue
                }
                val category = record.categoryUuid?.let { categoryRepo.findByUuidIncludingDeleted(it, user.id) }
                if (record.categoryUuid != null && category == null) {
                    reject(rejected, record, "transactionSplit", SyncRejectionReason.UNKNOWN_REFERENCE,
                        "unknown category ${record.categoryUuid}")
                    continue
                }

                val amountMinor = MinorUnits.toMinor(record.amount, decimals)
                val entity = existing ?: TransactionSplit(
                    uuid = record.uuid,
                    user = user,
                    transaction = transaction,
                    amountMinor = amountMinor
                )
                entity.transaction = transaction
                entity.category = category
                entity.amountMinor = amountMinor
                entity.note = record.note
                entity.sortOrder = record.sortOrder
                entity.deleted = record.deleted
                entity.updatedAt = now
                splitRepo.save(entity)
                applied++
            }
        }
        return applied
    }

    private fun applyGoals(
        user: User,
        records: List<SyncGoal>,
        now: Instant,
        rejected: MutableList<SyncRejection>
    ): Int {
        var applied = 0
        for (record in records) {
            val existing = goalRepo.findByUuidIncludingDeleted(record.uuid, user.id)
            if (isStale(existing, record)) {
                reject(rejected, record, "goal", SyncRejectionReason.STALE, "server copy is newer")
                continue
            }
            val linkedAccount = record.linkedAccountUuid?.let {
                accountRepo.findByUuidIncludingDeleted(it, user.id)
            }
            if (record.linkedAccountUuid != null && linkedAccount == null) {
                reject(rejected, record, "goal", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown account ${record.linkedAccountUuid}")
                continue
            }
            val decimals = currencyRepo.findByCode(record.currencyCode)?.decimals ?: 2
            val targetMinor = MinorUnits.toMinor(record.targetAmount, decimals)
            val currentMinor = MinorUnits.toMinor(record.currentAmount, decimals)

            val entity = existing ?: SavingsGoal(
                uuid = record.uuid,
                user = user,
                name = record.name,
                targetAmountMinor = targetMinor,
                currencyCode = record.currencyCode
            )
            entity.name = record.name
            entity.targetAmountMinor = targetMinor
            entity.currencyCode = record.currencyCode
            entity.currentAmountMinor = currentMinor
            entity.targetDate = record.targetDate
            entity.linkedAccount = linkedAccount
            entity.icon = record.icon
            entity.color = record.color
            entity.archived = record.archived
            entity.deleted = record.deleted
            entity.updatedAt = now
            goalRepo.save(entity)
            applied++
        }
        return applied
    }

    private fun applyTags(
        user: User,
        records: List<SyncTag>,
        now: Instant,
        rejected: MutableList<SyncRejection>
    ): Int {
        var applied = 0
        for (record in records) {
            val existing = tagRepo.findByUuidIncludingDeleted(record.uuid, user.id)
            if (isStale(existing, record)) {
                reject(rejected, record, "tag", SyncRejectionReason.STALE, "server copy is newer")
                continue
            }
            val entity = existing ?: Tag(
                uuid = record.uuid,
                user = user,
                name = record.name
            )
            entity.name = record.name
            entity.color = record.color
            entity.deleted = record.deleted
            entity.updatedAt = now
            tagRepo.save(entity)
            applied++
        }
        return applied
    }

    private fun applyTransactionTags(
        user: User,
        records: List<SyncTransactionTag>,
        now: Instant,
        rejected: MutableList<SyncRejection>
    ): Int {
        var applied = 0
        for (record in records) {
            val existing = transactionTagRepo.findByUuidIncludingDeleted(record.uuid, user.id)
            if (isStale(existing, record)) {
                reject(rejected, record, "transactionTag", SyncRejectionReason.STALE, "server copy is newer")
                continue
            }
            val transaction = transactionRepo.findByUuidIncludingDeleted(record.transactionUuid, user.id)
            if (transaction == null) {
                reject(rejected, record, "transactionTag", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown transaction ${record.transactionUuid}")
                continue
            }
            val tag = tagRepo.findByUuidIncludingDeleted(record.tagUuid, user.id)
            if (tag == null) {
                reject(rejected, record, "transactionTag", SyncRejectionReason.UNKNOWN_REFERENCE,
                    "unknown tag ${record.tagUuid}")
                continue
            }
            val entity = existing ?: TransactionTag(
                uuid = record.uuid,
                user = user,
                transaction = transaction,
                tag = tag
            )
            entity.transaction = transaction
            entity.tag = tag
            entity.deleted = record.deleted
            entity.updatedAt = now
            transactionTagRepo.save(entity)
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

    private fun accountUuidsById(user: User): Map<Long, String> =
        accountRepo.findAllByUser(user).associate { it.id to it.uuid }

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
        budgetLimit = budgetLimitMinor?.let { MinorUnits.toDecimal(it, baseDecimals) },
        rolloverEnabled = rolloverEnabled,
        rolloverNegative = rolloverNegative
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
        transferFee = transferFeeMinor?.let { MinorUnits.toDecimal(it, account.currency.decimals) }
    )

    private fun TransactionSplit.toSync() = SyncTransactionSplit(
        uuid = uuid,
        updatedAt = updatedAt,
        deleted = deleted,
        transactionUuid = transaction.uuid,
        categoryUuid = category?.uuid,
        amount = MinorUnits.toDecimal(amountMinor, transaction.account.currency.decimals),
        note = note,
        sortOrder = sortOrder
    )

    private fun SavingsGoal.toSync() = SyncGoal(
        uuid = uuid,
        updatedAt = updatedAt,
        deleted = deleted,
        name = name,
        targetAmount = MinorUnits.toDecimal(targetAmountMinor, currencyRepo.findByCode(currencyCode)?.decimals ?: 2),
        currencyCode = currencyCode,
        currentAmount = MinorUnits.toDecimal(currentAmountMinor, currencyRepo.findByCode(currencyCode)?.decimals ?: 2),
        targetDate = targetDate,
        linkedAccountUuid = linkedAccount?.uuid,
        icon = icon,
        color = color,
        archived = archived
    )

    private fun Tag.toSync() = SyncTag(
        uuid = uuid,
        updatedAt = updatedAt,
        deleted = deleted,
        name = name,
        color = color
    )

    private fun TransactionTag.toSync() = SyncTransactionTag(
        uuid = uuid,
        updatedAt = updatedAt,
        deleted = deleted,
        transactionUuid = transaction.uuid,
        tagUuid = tag.uuid
    )
}
