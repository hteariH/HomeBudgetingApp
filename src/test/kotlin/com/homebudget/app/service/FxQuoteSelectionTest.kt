package com.homebudget.app.service

import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.dto.CreateAccountRequest
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
 * Which currencies the daily FX job asks for.
 *
 * The list used to come from `user_currency_settings` alone, and that table is written by
 * the phone's currency screen and by nothing else - so a browser-only user held two
 * currencies, was quoted neither, and had every cross-currency transfer converted
 * one-to-one without a word. An account is the reason a rate is needed, so an account is
 * what has to put its currency on the list.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FxQuoteSelectionTest {

    @Autowired private lateinit var fxService: FxService
    @Autowired private lateinit var accountService: AccountService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var currencyRepository: CurrencyRepository

    @BeforeEach
    fun signIn() {
        val user = userRepository.save(
            User(
                email = "fx-quotes-${System.nanoTime()}@example.com",
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
    fun `a currency is quoted because an account is held in it`() {
        accountService.create(
            CreateAccountRequest(
                name = "Yen",
                initialBalance = BigDecimal.ZERO,
                currencyId = currencyRepository.findByCode("JPY")!!.id
            )
        )

        val quotes = fxService.quotedCodes()

        assertTrue(quotes.contains("JPY"), "expected JPY among $quotes")
        // The base is what everything is quoted against, so asking for it would be asking
        // for 1.0 - and Frankfurter rejects a quote equal to the base outright.
        assertFalse(quotes.contains("USD"), "base currency should not be quoted: $quotes")
    }
}
