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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class SubscriptionsUiState(
    val subscriptions: List<SubscriptionEntity> = emptyList(),
    val baseCurrency: String = "USD",
    val perMonthMinor: Long = 0L,
    val perYearMinor: Long = 0L
) {
    val perMonth: Money get() = Money(perMonthMinor, CurrencyCode(baseCurrency))
    val perYear: Money get() = Money(perYearMinor, CurrencyCode(baseCurrency))
}

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    userPreferences: UserPreferences,
    exchangeRateRepository: ExchangeRateRepository
) : ViewModel() {

    val uiState: StateFlow<SubscriptionsUiState> = combine(
        repository.observeAll(),
        userPreferences.baseCurrency,
        exchangeRateRepository.observeAll().map { FxSnapshot.from(it) }
    ) { subs, base, fx ->
        var perMonth = BigDecimal.ZERO
        for (s in subs.filter { it.active }) {
            val decimals = runCatching { java.util.Currency.getInstance(s.currencyCode).defaultFractionDigits }.getOrDefault(2)
            val major = BigDecimal(s.amountMinor).movePointLeft(decimals)
            val monthlyFactor = when (s.frequency) {
                // Average days/weeks per month over a 365.25-day year (30.4375 and 4.348214).
                SubscriptionFrequency.DAILY -> BigDecimal("30.4375").divide(BigDecimal(s.interval.coerceAtLeast(1)), 6, RoundingMode.HALF_UP)
                SubscriptionFrequency.WEEKLY -> BigDecimal("4.348214").divide(BigDecimal(s.interval.coerceAtLeast(1)), 6, RoundingMode.HALF_UP)
                SubscriptionFrequency.MONTHLY -> BigDecimal.ONE.divide(BigDecimal(s.interval.coerceAtLeast(1)), 6, RoundingMode.HALF_UP)
                SubscriptionFrequency.YEARLY -> BigDecimal.ONE.divide(BigDecimal(12).multiply(BigDecimal(s.interval.coerceAtLeast(1))), 6, RoundingMode.HALF_UP)
            }
            val monthMajor = major.multiply(monthlyFactor)
            val rate = fx.rate(s.currencyCode, base) ?: continue
            perMonth = perMonth.add(monthMajor.multiply(BigDecimal(rate)))
        }
        val baseDecimals = runCatching { java.util.Currency.getInstance(base).defaultFractionDigits }.getOrDefault(2)
        val perMonthMinor = perMonth.movePointRight(baseDecimals).setScale(0, RoundingMode.HALF_UP).toLong()
        val perYearMinor = perMonth.multiply(BigDecimal(12)).movePointRight(baseDecimals).setScale(0, RoundingMode.HALF_UP).toLong()
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
