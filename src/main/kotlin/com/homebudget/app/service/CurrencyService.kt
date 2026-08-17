package com.homebudget.app.service

import com.homebudget.app.domain.Currency
import com.homebudget.app.dto.CreateCurrencyRequest
import com.homebudget.app.dto.UpdateCurrencyRequest
import com.homebudget.app.repository.CurrencyRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CurrencyService(private val repo: CurrencyRepository) {

    fun findAll(): List<Currency> = repo.findAll().sortedWith(compareBy({ it.sortOrder }, { it.code }))

    fun findByCode(code: String): Currency = repo.findByCode(code.uppercase())
        ?: throw NoSuchElementException("Currency not found: $code")

    fun findById(id: Long): Currency = repo.findByIdOrNull(id)
        ?: throw NoSuchElementException("Currency not found: $id")

    fun create(req: CreateCurrencyRequest): Currency {
        if (repo.existsByCode(req.code.uppercase())) {
            throw IllegalArgumentException("Currency already exists: ${req.code}")
        }
        return repo.save(
            Currency(
                code = req.code.uppercase(),
                name = req.name,
                symbol = req.symbol,
                decimals = req.decimals,
                exchangeRateToBase = req.exchangeRateToBase,
                isBase = req.isBase
            )
        )
    }

    fun update(id: Long, req: UpdateCurrencyRequest): Currency {
        val currency = findById(id)
        // Changing `decimals` reinterprets every stored minor-unit amount in this
        // currency, so it is only safe while nothing references the currency yet.
        val updated = currency.copy(
            name = req.name ?: currency.name,
            symbol = req.symbol ?: currency.symbol,
            decimals = req.decimals ?: currency.decimals,
            exchangeRateToBase = req.exchangeRateToBase ?: currency.exchangeRateToBase
        )
        return repo.save(updated)
    }

    fun delete(id: Long) {
        val currency = findById(id)
        repo.delete(currency)
    }
}
