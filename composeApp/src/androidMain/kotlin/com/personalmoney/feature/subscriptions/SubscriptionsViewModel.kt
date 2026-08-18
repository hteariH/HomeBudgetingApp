package com.personalmoney.feature.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.currency.FxSnapshot
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.repository.ExchangeRateRepository
import com.personalmoney.core.data.repository.SubscriptionRepository
import com.personalmoney.core.database.entity.SubscriptionEntity
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money
import com.personalmoney.core.domain.SubscriptionFrequency
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.personalmoney.core.currency.toFxRates
import com.personalmoney.core.domain.toMinorUnits
import com.personalmoney.core.domain.toMajorUnits
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode

data class SubscriptionsUiState(
    val subscriptions: List<SubscriptionEntity> = emptyList(),
    val baseCurrency: String = "USD",
    val perMonthMinor: Long = 0L,
    val perYearMinor: Long = 0L
) {
    val perMonth: Money get() = Money(perMonthMinor, CurrencyCode(baseCurrency))
    val perYear: Money get() = Money(perYearMinor, CurrencyCode(baseCurrency))
}

class SubscriptionsViewModel constructor(
    private val repository: SubscriptionRepository,
    userPreferences: UserPreferences,
    exchangeRateRepository: ExchangeRateRepository
) : ViewModel() {

    val uiState: StateFlow<SubscriptionsUiState> = combine(
        repository.observeAll(),
        userPreferences.baseCurrency,
        exchangeRateRepository.observeAll().map { FxSnapshot.from(it.toFxRates()) }
    ) { subs, base, fx ->
        var perMonth = BigDecimal.ZERO
        for (s in subs.filter { it.active }) {
            val decimals = runCatching { java.util.Currency.getInstance(s.currencyCode).defaultFractionDigits }.getOrDefault(2)
            val major = s.amountMinor.toMajorUnits(decimals)
            val monthlyFactor = when (s.frequency) {
                // Average days/weeks per month over a 365.25-day year (30.4375 and 4.348214).
                SubscriptionFrequency.DAILY -> BigDecimal.parseString("30.4375").divide(BigDecimal.fromInt(s.interval.coerceAtLeast(1)), DecimalMode(30, RoundingMode.ROUND_HALF_AWAY_FROM_ZERO, 6))
                SubscriptionFrequency.WEEKLY -> BigDecimal.parseString("4.348214").divide(BigDecimal.fromInt(s.interval.coerceAtLeast(1)), DecimalMode(30, RoundingMode.ROUND_HALF_AWAY_FROM_ZERO, 6))
                SubscriptionFrequency.MONTHLY -> BigDecimal.ONE.divide(BigDecimal.fromInt(s.interval.coerceAtLeast(1)), DecimalMode(30, RoundingMode.ROUND_HALF_AWAY_FROM_ZERO, 6))
                SubscriptionFrequency.YEARLY -> BigDecimal.ONE.divide(BigDecimal.fromInt(12).multiply(BigDecimal.fromInt(s.interval.coerceAtLeast(1))), DecimalMode(30, RoundingMode.ROUND_HALF_AWAY_FROM_ZERO, 6))
            }
            val monthMajor = major.times(monthlyFactor)
            val rate = fx.rate(s.currencyCode, base) ?: continue
            perMonth += monthMajor * BigDecimal.fromDouble(rate)
        }
        val baseDecimals = runCatching { java.util.Currency.getInstance(base).defaultFractionDigits }.getOrDefault(2)
        val perMonthMinor = perMonth.toMinorUnits(baseDecimals)
        val perYearMinor = perMonth.times(BigDecimal.fromInt(12)).toMinorUnits(baseDecimals)
        SubscriptionsUiState(
            subscriptions = subs,
            baseCurrency = base,
            perMonthMinor = perMonthMinor,
            perYearMinor = perYearMinor
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionsUiState())

    fun setActive(id: Long, active: Boolean) {
        viewModelScope.launch { repository.setActive(id, active) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
