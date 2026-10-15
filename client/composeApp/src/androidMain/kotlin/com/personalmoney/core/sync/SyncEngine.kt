package com.personalmoney.core.sync

import android.util.Log
import com.personalmoney.core.auth.TokenStore
import com.personalmoney.core.data.LocalDataWiper
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.database.Transactor
import com.personalmoney.core.database.dao.AccountDao
import com.personalmoney.core.database.dao.CategoryDao
import com.personalmoney.core.database.dao.CurrencyDao
import com.personalmoney.core.database.dao.SubscriptionDao
import com.personalmoney.core.database.dao.TransactionDao
import com.personalmoney.core.household.HouseholdMembers
import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.SyncPayloadDto
import com.personalmoney.core.network.SyncPushRequestDto
import com.personalmoney.core.sync.SyncMappers.toDto
import com.personalmoney.core.sync.SyncMappers.toEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * One pass of two-way sync: **pull, merge, push**.
 *
 * Pull first on purpose. Pushing first would send edits based on a version the server
 * has already replaced, and every one of them would come back rejected as stale; by
 * merging the server's state first, most conflicts simply stop existing.
 *
 * References travel as uuids, so a merge has to resolve them back to local row ids -
 * and parents have to be written before children or the resolution fails.
 */
class SyncEngine constructor(
    private val api: HomeBudgetApi,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val subscriptionDao: SubscriptionDao,
    private val currencyDao: CurrencyDao,
    private val transactor: Transactor,
    private val prefs: UserPreferences,
    private val tokenStore: TokenStore,
    private val wiper: LocalDataWiper,
    private val members: HouseholdMembers
) {

    data class Result(
        val pulled: Int,
        val pushed: Int,
        val rejected: Int
    )

    suspend fun syncNow(): Result {
        if (!tokenStore.signedIn.value) {
            Log.d(TAG, "Not signed in; nothing to sync")
            return Result(0, 0, 0)
        }
        // Before the backfill: a wipe blanks the cursor, and the backfill then has nothing
        // left to push and only records that it need not run.
        followHouseholdChange()
        backfillAuthorsOnce()
        val pulled = pull()
        // Signing in on a second device lands on an account that already has data.
        // Asking such a user to pick a base currency and create their first account
        // would be nonsense, so onboarding is considered done once anything arrived.
        if (pulled > 0) markOnboardedIfDataArrived()
        val (pushed, rejected) = push()
        // A rejection means the server holds a newer version, so take it immediately
        // rather than leaving the screen showing an edit that did not stick.
        if (rejected > 0) pull()
        return Result(pulled, pushed, rejected)
    }

    /**
     * Notices that the account now belongs to a different household than the one this
     * phone's data was synced for - it was removed from one, left, or accepted an invitation
     * into another - and starts over: the local ledger is cleared and the next pull reads
     * the new household's data from the start. There is no confirmation; the old rows are
     * not the user's to keep once they are no longer in that household.
     *
     * Edits not yet pushed belonged to the previous household and are deliberately dropped
     * with the rest, not pushed first: after a removal they would land in the household the
     * user is in now. (A merge also overwrites local rows unconditionally, so they would
     * not survive the full pull anyway.)
     *
     * The first sync after an upgrade has no household recorded; it is just stored. If the
     * server cannot be asked, or does not report a household, the check is skipped and the
     * sync carries on as before.
     */
    private suspend fun followHouseholdChange() {
        val me = try {
            api.me()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Could not check the household; skipping", e)
            return
        }
        val current = me.householdId ?: return
        val known = prefs.householdId.first()
        if (known != null && known != current) {
            wiper.wipe()
            prefs.setHouseholdNotice(me.householdName)
            members.refresh()
        }
        // After a wipe, which removed both keys along with the rest of the account's state.
        if (known != current) prefs.setHouseholdId(current)
    }

    /**
     * Rows synced before schema v5 have no author, and the incremental pull will never
     * send them again because the server has not changed them. So, once, the cursor is
     * dropped and the next pull reads everything from the start.
     *
     * Pending local changes are pushed first, and that is required rather than tidy:
     * [merge] replaces a local row with the server's version unconditionally and marks it
     * synced, it does not look at `pendingSync`. An incremental pull only reaches rows the
     * server changed, but a full one reaches all of them and would silently discard every
     * unsynced edit.
     *
     * A phone that has never synced has a blank cursor, so its first pull is already a
     * full one; the flag is just recorded so it is never asked for again.
     */
    private suspend fun backfillAuthorsOnce() {
        if (prefs.authorBackfillDone.first()) return
        if (prefs.syncCursor.first().isNotBlank()) {
            pushEverythingPending()
            prefs.setSyncCursor("")
        }
        prefs.setAuthorBackfillDone(true)
    }

    private suspend fun pushEverythingPending() {
        var guard = 0
        while (guard++ < MAX_PAGES) {
            val (pushed, rejected) = push()
            // A rejected row stays pending and would be sent again for ever; the full
            // pull that follows takes the server's version of it, as a rejection always does.
            if (pushed == 0 || rejected > 0) break
        }
    }

    private suspend fun markOnboardedIfDataArrived() {
        val hasAccounts = accountDao.observeActiveCount().first() > 0
        if (hasAccounts && !prefs.onboarded.first()) {
            prefs.setOnboarded(true)
            // The base currency lives on the server for a signed-in user.
            api.me().baseCurrency?.code?.let { prefs.setBaseCurrency(it) }
        }
    }

    // ── Pull ────────────────────────────────────────────────────────────────

    private suspend fun pull(): Int {
        var total = 0
        var guard = 0
        while (guard++ < MAX_PAGES) {
            val since = prefs.syncCursor.first()
            val response = api.pull(since = since.ifBlank { EPOCH }, limit = PAGE_SIZE)
            val changes = response.changes
            if (changes.size > 0) {
                merge(changes)
                // The server has its own default categories. Once they have arrived the
                // app's local starter set is redundant, and keeping both shows every
                // category twice.
                if (changes.categories.isNotEmpty()) {
                    val dropped = categoryDao.dropUnusedLocalDefaults()
                    if (dropped > 0) Log.d(TAG, "Dropped $dropped unused local default categories")
                }
                total += changes.size
            }
            prefs.setSyncCursor(response.serverTime)
            if (!response.hasMore) break
        }
        return total
    }

    private suspend fun merge(changes: SyncPayloadDto) {
        val baseDecimals = decimalsOf(prefs.baseCurrency.first())

        transactor.transaction {
            // Currency activation first: accounts reference a currency code.
            for (dto in changes.currencySettings) {
                val existing = currencyDao.findByCode(dto.currencyCode) ?: continue
                currencyDao.update(
                    existing.copy(
                        uuid = dto.uuid,
                        active = dto.active,
                        sortOrder = dto.sortOrder,
                        updatedAt = SyncMappers.isoToStamp(dto.updatedAt),
                        deleted = dto.deleted,
                        pendingSync = false
                    )
                )
            }

            for (dto in changes.accounts) {
                val existing = accountDao.findByUuid(dto.uuid)
                val decimals = decimalsOf(dto.currencyCode)
                val entity = dto.toEntity(existing?.id ?: 0L, decimals)
                if (existing == null) accountDao.insert(entity) else accountDao.update(entity)
            }

            // Parents before children so parentUuid resolves within the same batch.
            for (dto in changes.categories.sortedBy { it.parentUuid != null }) {
                val existing = categoryDao.findByUuid(dto.uuid)
                val parentId = dto.parentUuid?.let { categoryDao.findByUuid(it)?.id }
                val entity = dto.toEntity(existing?.id ?: 0L, parentId, baseDecimals)
                if (existing == null) categoryDao.insert(entity) else categoryDao.update(entity)
            }

            for (dto in changes.subscriptions) {
                val account = accountDao.findByUuid(dto.accountUuid) ?: continue
                val existing = subscriptionDao.findByUuid(dto.uuid)
                val categoryId = dto.categoryUuid?.let { categoryDao.findByUuid(it)?.id }
                val entity = dto.toEntity(
                    existingId = existing?.id ?: 0L,
                    accountId = account.id,
                    categoryId = categoryId,
                    decimals = decimalsOf(account.currencyCode)
                )
                if (existing == null) subscriptionDao.insert(entity) else subscriptionDao.update(entity)
            }

            for (dto in changes.transactions) {
                val account = accountDao.findByUuid(dto.accountUuid)
                if (account == null) {
                    // The account is on a later page; the row arrives again next round
                    // because the cursor only advances past what was applied.
                    Log.w(TAG, "Skipping transaction ${dto.uuid}: account not here yet")
                    continue
                }
                val existing = transactionDao.findByUuid(dto.uuid)
                val entity = dto.toEntity(
                    existingId = existing?.id ?: 0L,
                    accountId = account.id,
                    categoryId = dto.categoryUuid?.let { categoryDao.findByUuid(it)?.id },
                    subscriptionId = dto.subscriptionUuid?.let { subscriptionDao.findByUuid(it)?.id },
                    counterpartAccountId = dto.transferCounterpartAccountUuid
                        ?.let { accountDao.findByUuid(it)?.id },
                    decimals = decimalsOf(account.currencyCode)
                )
                if (existing == null) transactionDao.insert(entity) else transactionDao.update(entity)
            }
        }
    }

    // ── Push ────────────────────────────────────────────────────────────────

    private suspend fun push(): Pair<Int, Int> {
        val baseDecimals = decimalsOf(prefs.baseCurrency.first())

        val currencies = currencyDao.pendingForSync(PAGE_SIZE)
        val accounts = accountDao.pendingForSync(PAGE_SIZE)
        val categories = categoryDao.pendingForSync(PAGE_SIZE)
        val subscriptions = subscriptionDao.pendingForSync(PAGE_SIZE)
        val transactions = transactionDao.pendingForSync(PAGE_SIZE)

        val payload = SyncPayloadDto(
            currencySettings = currencies.map { it.toDto() },
            accounts = accounts.map { it.toDto(decimalsOf(it.currencyCode)) },
            categories = categories.map { category ->
                val parentUuid = category.parentId?.let { categoryDao.findById(it)?.uuid }
                category.toDto(parentUuid, baseDecimals)
            },
            subscriptions = subscriptions.mapNotNull { sub ->
                val account = accountDao.findById(sub.accountId) ?: return@mapNotNull null
                sub.toDto(
                    accountUuid = account.uuid,
                    categoryUuid = sub.categoryId?.let { categoryDao.findById(it)?.uuid },
                    decimals = decimalsOf(account.currencyCode)
                )
            },
            transactions = transactions.mapNotNull { tx ->
                // findById filters tombstones, so a deleted account's rows are looked up
                // by uuid-free path: fall back to the raw row.
                val account = accountDao.findById(tx.accountId) ?: return@mapNotNull null
                tx.toDto(
                    accountUuid = account.uuid,
                    categoryUuid = tx.categoryId?.let { categoryDao.findById(it)?.uuid },
                    subscriptionUuid = tx.subscriptionId?.let { subscriptionDao.findById(it)?.uuid },
                    counterpartUuid = tx.transferCounterpartAccountId?.let { accountDao.findById(it)?.uuid },
                    decimals = decimalsOf(account.currencyCode)
                )
            }
        )

        if (payload.size == 0) return 0 to 0

        val response = api.push(SyncPushRequestDto(payload))
        val rejectedUuids = response.rejected.map { it.uuid }.toSet()
        if (rejectedUuids.isNotEmpty()) {
            Log.w(TAG, "Server rejected ${rejectedUuids.size} records: ${response.rejected.take(5)}")
        }

        val stamp = SyncMappers.isoToStamp(response.serverTime)
        transactor.transaction {
            // Only what the server accepted is cleared. A rejected row stays pending so
            // the next pass retries it - after the follow-up pull has given it a fresh
            // base version to build on.
            currencies.filterNot { it.uuid in rejectedUuids }.forEach { currencyDao.markSynced(it.uuid, stamp) }
            accounts.filterNot { it.uuid in rejectedUuids }.forEach { accountDao.markSynced(it.uuid, stamp) }
            categories.filterNot { it.uuid in rejectedUuids }.forEach { categoryDao.markSynced(it.uuid, stamp) }
            subscriptions.filterNot { it.uuid in rejectedUuids }.forEach { subscriptionDao.markSynced(it.uuid, stamp) }
            transactions.filterNot { it.uuid in rejectedUuids }.forEach { transactionDao.markSynced(it.uuid, stamp) }

            // Tombstones the server has acknowledged have no further purpose.
            transactionDao.purgeSyncedTombstones()
            subscriptionDao.purgeSyncedTombstones()
            categoryDao.purgeSyncedTombstones()
            accountDao.purgeSyncedTombstones()
        }

        return (payload.size - rejectedUuids.size) to rejectedUuids.size
    }

    /** Falls back to 2 for an unknown code, which is the common case and never crashes. */
    private suspend fun decimalsOf(code: String): Int =
        currencyDao.findByCode(code)?.decimals ?: 2

    private companion object {
        const val TAG = "SyncEngine"
        const val PAGE_SIZE = 500
        const val MAX_PAGES = 50
        const val EPOCH = "1970-01-01T00:00:00Z"
    }
}
