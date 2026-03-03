package com.homebudget.app.service

import com.homebudget.app.domain.Category
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.Currency
import com.homebudget.app.repository.CategoryRepository
import com.homebudget.app.repository.CurrencyRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class DataInitializer(
    private val currencyRepo: CurrencyRepository,
    private val categoryRepo: CategoryRepository
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        seedCurrencies()
        seedCategories()
    }

    private fun seedCurrencies() {
        if (currencyRepo.count() > 0) return
        val currencies = listOf(
            Currency(code = "USD", name = "US Dollar", symbol = "$", exchangeRateToBase = BigDecimal("1.000000"), isBase = true),
            Currency(code = "EUR", name = "Euro", symbol = "€", exchangeRateToBase = BigDecimal("0.920000")),
            Currency(code = "GBP", name = "British Pound", symbol = "£", exchangeRateToBase = BigDecimal("0.790000")),
            Currency(code = "JPY", name = "Japanese Yen", symbol = "¥", exchangeRateToBase = BigDecimal("149.500000")),
            Currency(code = "CAD", name = "Canadian Dollar", symbol = "CA$", exchangeRateToBase = BigDecimal("1.360000")),
            Currency(code = "AUD", name = "Australian Dollar", symbol = "A$", exchangeRateToBase = BigDecimal("1.530000")),
            Currency(code = "CHF", name = "Swiss Franc", symbol = "CHF", exchangeRateToBase = BigDecimal("0.890000")),
            Currency(code = "PLN", name = "Polish Złoty", symbol = "zł", exchangeRateToBase = BigDecimal("3.970000")),
            Currency(code = "CZK", name = "Czech Koruna", symbol = "Kč", exchangeRateToBase = BigDecimal("22.800000")),
            Currency(code = "HUF", name = "Hungarian Forint", symbol = "Ft", exchangeRateToBase = BigDecimal("355.000000")),
            Currency(code = "UAH", name = "Ukrainian Hryvnia", symbol = "₴", exchangeRateToBase = BigDecimal("38.500000")),
            Currency(code = "ILS", name = "Israeli New Shekel", symbol = "₪", exchangeRateToBase = BigDecimal("3.650000"))
        )
        currencyRepo.saveAll(currencies)
    }

    private fun seedCategories() {
        // No longer seeding global categories as they need a user_id
        return
    }
}
