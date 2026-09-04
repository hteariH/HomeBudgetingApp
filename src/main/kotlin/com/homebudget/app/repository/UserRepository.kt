package com.homebudget.app.repository

import com.homebudget.app.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, Long> {

    /** The currencies totals are reported in, which is the other half of what FX needs. */
    @Query("SELECT DISTINCT u.baseCurrency.code FROM User u WHERE u.baseCurrency IS NOT NULL")
    fun baseCurrencyCodes(): List<String>
    fun findByEmail(email: String): Optional<User>
    fun existsByEmail(email: String): Boolean
}
