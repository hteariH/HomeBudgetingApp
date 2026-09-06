package com.homebudget.app.service

import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.dto.CreateAccountRequest
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * A dollar stablecoin has to convert, and must never be asked about.
 *
 * Both halves of the same bug: Frankfurter answers `invalid currency: USDT` with a 422
 * that kills the whole request, so an imported crypto account first froze every rate the
 * install had, and then sat outside every total without a word - 3 415 USDT, a third of
 * somebody's net worth, quietly counted as nothing.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FxPegTest {

    @Autowired private lateinit var fxService: FxService
    @Autowired private lateinit var accountService: AccountService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var currencyRepository: CurrencyRepository

    private lateinit var user: User

    @BeforeEach
    fun signIn() {
        user = userRepository.save(
            User(
                email = "fx-peg-${System.nanoTime()}@example.com",
                passwordHash = "x",
                firstName = "Test",
                lastName = "User",
                role = Role.USER,
                baseCurrency = currencyRepository.findByCode("USD")
            )
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UserPrincipal(user), null, emptyList())
    }

    @AfterEach
    fun signOut() = SecurityContextHolder.clearContext()

    @Test
    fun `a stablecoin account is never quoted`() {
        accountService.create(
            CreateAccountRequest(
                name = "Crypto",
                initialBalance = BigDecimal.ZERO,
                currencyId = currencyRepository.findByCode("USDT")!!.id
            )
        )

        val quotes = fxService.quotedCodes()

        assertFalse(quotes.contains("USDT"), "USDT must stay out of the request: $quotes")
    }

    @Test
    fun `a stablecoin converts without any stored rate`() {
        val rate = fxService.rate(user, "USDT", "USD")

        assertNotNull(rate, "USDT should convert to USD by definition")
        assertEquals(0, BigDecimal.ONE.compareTo(rate!!.stripTrailingZeros()))
    }

    @Test
    fun `a rate set by hand beats the peg`() {
        fxService.setOverride(user, "USDT", BigDecimal("0.93"))

        val rate = fxService.rate(user, "USDT", "USD")

        assertNotNull(rate)
        assertEquals(0, BigDecimal("0.93").compareTo(rate!!.stripTrailingZeros()))
    }

    @Test
    fun `an override is not buried by a rate fetched later the same day`() {
        fxService.setOverride(user, "BTC", BigDecimal("95000"))

        val rate = fxService.rate(user, "BTC", "USD")

        assertNotNull(rate, "a hand-set rate is the only one BTC will ever have")
        assertEquals(0, BigDecimal("95000").compareTo(rate!!.stripTrailingZeros()))
    }
}
