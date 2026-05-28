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

    suspend fun importFrom(uri: Uri): Result<Int> = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: error("Cannot open input")
        val envelope = json.decodeFromString(BackupEnvelope.serializer(), text)
        db.withTransaction {
            db.exchangeRateDao().upsertAll(envelope.exchangeRates.map { it.toEntity() })
            db.currencyDao().insertAll(envelope.currencies.map { it.toEntity() })
            envelope.accounts.forEach { db.accountDao().insert(it.toEntity()) }
            db.categoryDao().insertAll(envelope.categories.map { it.toEntity() })
            envelope.transactions.forEach { db.transactionDao().insert(it.toEntity()) }
            envelope.subscriptions.forEach { db.subscriptionDao().insert(it.toEntity()) }
        }
        envelope.transactions.size
    }
}
