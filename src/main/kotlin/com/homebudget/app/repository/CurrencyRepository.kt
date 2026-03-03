package com.homebudget.app.repository

import com.homebudget.app.domain.Currency
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CurrencyRepository : JpaRepository<Currency, Long> {
    fun findByCode(code: String): Currency?
    fun existsByCode(code: String): Boolean
    fun findByIsBaseTrue(): Currency?
}
