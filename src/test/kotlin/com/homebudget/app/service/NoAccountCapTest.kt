package com.homebudget.app.service

import com.homebudget.app.domain.AccountType
import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.dto.CreateAccountRequest
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.util.UUID

/** A self-hosted instance sells nothing, so nothing caps how many accounts a person keeps. */
@SpringBootTest
@ActiveProfiles("test")
class NoAccountCapTest {

    @Autowired private lateinit var accountService: AccountService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var currencyRepository: CurrencyRepository

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    @Test
    fun `a twelfth account is created like the first`() {
        val usd = currencyRepository.findByCode("USD")!!
        val user = userRepository.save(
            User(
                email = "cap-${UUID.randomUUID()}@example.com",
                passwordHash = "x",
                firstName = "Cap",
                lastName = "Test",
                role = Role.USER,
                baseCurrency = usd
            )
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UserPrincipal(user), null, emptyList())

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
