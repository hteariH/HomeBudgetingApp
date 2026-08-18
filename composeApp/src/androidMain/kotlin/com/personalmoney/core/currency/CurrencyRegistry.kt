package com.personalmoney.core.currency

import com.personalmoney.core.database.entity.CurrencyEntity
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide snapshot of currency metadata (symbol + decimals) for every currency the
 * user has in their database. Read synchronously by [com.personalmoney.core.domain.Money]
 * and [com.personalmoney.core.domain.MoneyFormatter] so non-ISO codes (BTC, ETH, user
 * vouchers) get the symbol and precision the user actually chose.
 *
 * Populated from `CurrencyDao.observeAll()` in [com.personalmoney.PersonalMoneyApplication]
 * and refreshed every time the user edits a currency.
 *
 * Falls back to `java.util.Currency` (ICU) when an unknown code is requested.
 */
object CurrencyRegistry {

    data class Info(val code: String, val symbol: String, val decimals: Int)

    private val ref = AtomicReference<Map<String, Info>>(emptyMap())

    fun update(currencies: List<CurrencyEntity>) {
        ref.set(
            currencies.associate { entity ->
                entity.code to Info(entity.code, entity.symbol, entity.decimals)
            }
        )
    }

    fun lookup(code: String): Info? = ref.get()[code]

    fun symbolFor(code: String): String? = ref.get()[code]?.symbol

    fun decimalsFor(code: String): Int? = ref.get()[code]?.decimals

    fun decimalsOrFallback(code: String): Int =
        decimalsFor(code) ?: runCatching {
            java.util.Currency.getInstance(code).defaultFractionDigits
        }.getOrDefault(2).coerceAtLeast(0)
}
