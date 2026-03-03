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
        )
        currencyRepo.saveAll(currencies)
    }

    private fun seedCategories() {
        if (categoryRepo.count() > 0) return
        val categories = listOf(
            // Income
            Category(name = "Salary", type = CategoryType.INCOME, icon = "briefcase", color = "#10B981"),
            Category(name = "Freelance", type = CategoryType.INCOME, icon = "laptop", color = "#06B6D4"),
            Category(name = "Investments", type = CategoryType.INCOME, icon = "trending-up", color = "#8B5CF6"),
            Category(name = "Rental Income", type = CategoryType.INCOME, icon = "home", color = "#F59E0B"),
            Category(name = "Other Income", type = CategoryType.INCOME, icon = "plus-circle", color = "#6366F1"),
            // Expenses
            Category(name = "Housing", type = CategoryType.EXPENSE, icon = "home", color = "#EF4444"),
            Category(name = "Food & Groceries", type = CategoryType.EXPENSE, icon = "shopping-cart", color = "#F97316"),
            Category(name = "Transportation", type = CategoryType.EXPENSE, icon = "car", color = "#EAB308"),
            Category(name = "Healthcare", type = CategoryType.EXPENSE, icon = "heart", color = "#EC4899"),
            Category(name = "Entertainment", type = CategoryType.EXPENSE, icon = "film", color = "#8B5CF6"),
            Category(name = "Shopping", type = CategoryType.EXPENSE, icon = "shopping-bag", color = "#06B6D4"),
            Category(name = "Utilities", type = CategoryType.EXPENSE, icon = "zap", color = "#10B981"),
            Category(name = "Education", type = CategoryType.EXPENSE, icon = "book", color = "#3B82F6"),
            Category(name = "Dining Out", type = CategoryType.EXPENSE, icon = "coffee", color = "#F59E0B"),
            Category(name = "Travel", type = CategoryType.EXPENSE, icon = "map", color = "#14B8A6"),
            Category(name = "Other Expense", type = CategoryType.EXPENSE, icon = "more-horizontal", color = "#6B7280"),
        )
        categoryRepo.saveAll(categories)
    }
}
