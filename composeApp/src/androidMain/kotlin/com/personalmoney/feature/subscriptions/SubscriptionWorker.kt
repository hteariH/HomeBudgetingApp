package com.personalmoney.feature.subscriptions

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personalmoney.core.data.repository.SubscriptionRepository
import com.personalmoney.core.data.repository.TransactionRepository
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.TransactionType
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

class SubscriptionWorker constructor(
    context: Context,
    params: WorkerParameters,
    private val subscriptionRepository: SubscriptionRepository,
    private val transactionRepository: TransactionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val due = subscriptionRepository.findDueAsOf(today)
        val now = Clock.System.now()
        for (sub in due) {
            // Catch up: keep generating transactions until next_charge_date > today
            var charge = sub.nextChargeDate
            while (charge <= today && (sub.endDate == null || charge <= sub.endDate)) {
                val occurredAt = charge.atStartOfDayIn(TimeZone.currentSystemDefault())
                transactionRepository.insert(
                    TransactionEntity(
                        type = TransactionType.EXPENSE,
                        accountId = sub.accountId,
                        categoryId = sub.categoryId,
                        amountMinor = sub.amountMinor,
                        currencyCode = sub.currencyCode,
                        accountAmountMinor = sub.amountMinor,
                        fxRateToAccount = 1.0,
                        occurredAt = occurredAt,
                        createdAt = now,
                        note = sub.name,
                        subscriptionId = sub.id
                    )
                )
                charge = SubscriptionScheduler.computeNextChargeDate(charge, sub.frequency, sub.interval)
            }
            subscriptionRepository.setNextChargeDate(sub.id, charge)
        }
        Result.success()
    }.getOrElse {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "subscription_charge"
    }
}
