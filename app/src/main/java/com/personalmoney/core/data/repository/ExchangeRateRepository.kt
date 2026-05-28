package com.personalmoney.core.data.repository

import com.personalmoney.core.database.dao.ExchangeRateDao
import com.personalmoney.core.database.entity.ExchangeRateEntity
import com.personalmoney.core.domain.ExchangeRateSource
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExchangeRateRepository @Inject constructor(
    private val dao: ExchangeRateDao
) {
    fun observeAll(): Flow<List<ExchangeRateEntity>> = dao.observeAll()

    suspend fun find(base: String, quote: String): ExchangeRateEntity? = dao.find(base, quote)

    suspend fun saveManual(base: String, quote: String, rate: Double, date: LocalDate) {
        dao.upsert(
            ExchangeRateEntity(
                baseCode = base,
                quoteCode = quote,
                rate = rate,
                date = date,
                source = ExchangeRateSource.MANUAL,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun upsertAll(rates: List<ExchangeRateEntity>) = dao.upsertAll(rates)
}
