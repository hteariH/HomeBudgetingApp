package com.personalmoney.feature.settings.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.personalmoney.core.domain.toMajorUnits

class BackupManager constructor(
    private val context: Context,
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

    /** Export all transactions as a spreadsheet-friendly CSV to [uri]. Returns the row count. */
    suspend fun exportCsvTo(uri: Uri): Result<Int> = runCatching {
        val txs = db.transactionDao().observeRecent(Int.MAX_VALUE).first()
        val accounts = db.accountDao().observeAll().first().associateBy { it.id }
        val categories = db.categoryDao().observeAll().first().associateBy { it.id }
        val zone = TimeZone.currentSystemDefault()

        val sb = StringBuilder()
        sb.append("Date,Type,Account,Category,Amount,Currency,Note\n")
        for (t in txs) {
            val date = t.occurredAt.toLocalDateTime(zone).date.toString()
            val account = accounts[t.accountId]?.name.orEmpty()
            val category = t.categoryId?.let { categories[it]?.name }.orEmpty()
            val decimals = CurrencyRegistry.decimalsOrFallback(t.currencyCode)
            val amount = t.accountAmountMinor.toMajorUnits(decimals).toStringExpanded()
            sb.append(
                listOf(date, t.type.name, account, category, amount, t.currencyCode, t.note.orEmpty())
                    .joinToString(",") { csvEscape(it) }
            )
            sb.append("\n")
        }
        context.contentResolver.openOutputStream(uri, "w")?.use { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        } ?: error("Cannot open output")
        txs.size
    }

    private fun csvEscape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
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
