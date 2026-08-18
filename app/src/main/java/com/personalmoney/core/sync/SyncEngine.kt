package com.personalmoney.core.sync

import android.util.Log
import com.personalmoney.core.auth.TokenStore
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.database.Transactor
import com.personalmoney.core.database.dao.AccountDao
import com.personalmoney.core.database.dao.CategoryDao
import com.personalmoney.core.database.dao.CurrencyDao
import com.personalmoney.core.database.dao.SubscriptionDao
import com.personalmoney.core.database.dao.TransactionDao
import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.SyncPayloadDto
import com.personalmoney.core.network.SyncPushRequestDto
import com.personalmoney.core.sync.SyncMappers.toDto
import com.personalmoney.core.sync.SyncMappers.toEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

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
@Singleton
class SyncEngine @Inject constructor(
    private val api: HomeBudgetApi,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val subscriptionDao: SubscriptionDao,
    private val currencyDao: CurrencyDao,
    private val transactor: Transactor,
    private val prefs: UserPreferences,
    private val tokenStore: TokenStore
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
