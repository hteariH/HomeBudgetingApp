package com.personalmoney.feature.settings.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.personalmoney.core.database.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportTo(uri: Uri): Result<Int> = runCatching {
        val envelope = BackupEnvelope(
            exportedAt = System.currentTimeMillis(),
            accounts = db.accountDao().observeAll().first().map(AccountDto.Companion::from),
            categories = db.categoryDao().observeAll().first().map(CategoryDto.Companion::from),
            transactions = db.transactionDao().observeRecent(Int.MAX_VALUE).first().map(TransactionDto.Companion::from),
            subscriptions = db.subscriptionDao().observeAll().first().map(SubscriptionDto.Companion::from),
            currencies = db.currencyDao().observeAll().first().map(CurrencyDto.Companion::from),
            exchangeRates = db.exchangeRateDao().observeAll().first().map(ExchangeRateDto.Companion::from)
        )
        val text = json.encodeToString(BackupEnvelope.serializer(), envelope)
        context.contentResolver.openOutputStream(uri, "w")?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("Cannot open output")
        envelope.transactions.size
    }

    /** Parse a backup file and report its contents without modifying the database. */
    suspend fun peek(uri: Uri): Result<BackupPreview> = runCatching {
        readEnvelope(uri).toPreview()
    }

    /**
     * Restore from a backup. This REPLACES all current data: the database is wiped and
     * rebuilt from the file inside a single transaction, so a re-import is idempotent
     * (no duplicates) and a failure rolls back rather than leaving a half-imported state.
     */
    suspend fun importFrom(uri: Uri): Result<BackupPreview> = runCatching {
        restore(readEnvelope(uri))
    }

    /**
     * Wipe and rebuild the database from [envelope] in a single transaction. Extracted from
     * [importFrom] so it can be tested without a content Uri. Idempotent: running it twice with
     * the same data leaves identical contents (no duplicate-key failure).
     */
    suspend fun restore(envelope: BackupEnvelope): BackupPreview {
        db.withTransaction {
            // Clear existing data — children before parents (FK-safe).
            db.transactionDao().deleteAll()
            db.subscriptionDao().deleteAll()
            db.categoryDao().deleteAll()
            db.accountDao().deleteAll()
            db.exchangeRateDao().deleteAll()
            db.currencyDao().deleteAll()
            // Restore — parents before children.
            db.currencyDao().insertAll(envelope.currencies.map { it.toEntity() })
            db.exchangeRateDao().upsertAll(envelope.exchangeRates.map { it.toEntity() })
            envelope.accounts.forEach { db.accountDao().insert(it.toEntity()) }
            // Top-level categories first so child parent_id references resolve.
            envelope.categories
                .sortedBy { it.parentId != null }
                .forEach { db.categoryDao().insert(it.toEntity()) }
            envelope.transactions.forEach { db.transactionDao().insert(it.toEntity()) }
            envelope.subscriptions.forEach { db.subscriptionDao().insert(it.toEntity()) }
        }
        return envelope.toPreview()
    }

    private fun readEnvelope(uri: Uri): BackupEnvelope {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: error("Cannot open input")
        return json.decodeFromString(BackupEnvelope.serializer(), text)
    }

    private fun BackupEnvelope.toPreview() = BackupPreview(
        accounts = accounts.size,
        categories = categories.size,
        transactions = transactions.size,
        subscriptions = subscriptions.size,
        currencies = currencies.size,
        exchangeRates = exchangeRates.size
    )
}

data class BackupPreview(
    val accounts: Int,
    val categories: Int,
    val transactions: Int,
    val subscriptions: Int,
    val currencies: Int,
    val exchangeRates: Int
)
