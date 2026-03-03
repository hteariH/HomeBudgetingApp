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

    fun findAll(): List<Currency> = repo.findAll()

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
                exchangeRateToBase = req.exchangeRateToBase,
                isBase = req.isBase
            )
        )
    }

    fun update(id: Long, req: UpdateCurrencyRequest): Currency {
        val currency = findById(id)
        val updated = currency.copy(
            name = req.name ?: currency.name,
            symbol = req.symbol ?: currency.symbol,
            exchangeRateToBase = req.exchangeRateToBase ?: currency.exchangeRateToBase
        )
        return repo.save(updated)
    }

    fun delete(id: Long) {
        val currency = findById(id)
        repo.delete(currency)
    }
}
