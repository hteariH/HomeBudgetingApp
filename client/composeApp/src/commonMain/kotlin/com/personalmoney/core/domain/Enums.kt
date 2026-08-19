package com.personalmoney.core.domain

enum class TransactionType {
    EXPENSE, INCOME, TRANSFER
}

enum class CategoryType {
    EXPENSE, INCOME
}

enum class SubscriptionFrequency {
    DAILY, WEEKLY, MONTHLY, YEARLY
}

enum class ExchangeRateSource {
    MANUAL, ECB, FRANKFURTER
}
