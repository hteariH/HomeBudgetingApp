package com.homebudget.app.service

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.AccountType
import com.homebudget.app.dto.CreateAccountRequest
import com.homebudget.app.repository.CurrencyRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal

/** A self-hosted instance sells nothing, so nothing caps how many accounts a person keeps. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
class NoAccountCapTest {

    @Autowired private lateinit var accountService: AccountService
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var currencyRepository: CurrencyRepository

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    @Test
    fun `a twelfth account is created like the first`() {
        val usd = currencyRepository.findByCode("USD")!!
        identity.signIn(identity.newMember("cap"))

        repeat(12) { i ->
            accountService.create(
                CreateAccountRequest(
                    name = "Account $i",
                    type = AccountType.CASH,
                    initialBalance = BigDecimal.ZERO,
                    currencyId = usd.id
                )
            )
        }

        assertEquals(12, accountService.findAll().size)
    }
}
