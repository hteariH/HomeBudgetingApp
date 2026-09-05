package com.homebudget.app.importer.homemoney

import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional

/**
 * Linking a HomeMoney account, and who a returning authorisation is allowed to land on.
 *
 * The claim worth a test is the one homemoney.ua forced. Their consent redirect comes back
 * with a `code` and **no `state`**, so the request that receives it cannot say whose
 * consent it is; the exchange therefore happens on a signed-in call instead, and what stops
 * that call from being a way to push a connection onto somebody else is the state check.
 *
 * `base-url` points at a closed port on purpose. Nothing here should reach homemoney.ua -
 * the refusal is meant to happen before the code is ever spent - so a regression that let
 * the exchange through fails here as a connection error rather than quietly calling out
 * from a test run.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = ["app.homemoney.base-url=http://127.0.0.1:1"])
@Transactional
class HomeMoneyConnectionServiceTest {

    @Autowired private lateinit var connections: HomeMoneyConnectionService
    @Autowired private lateinit var userRepo: UserRepository
    @Autowired private lateinit var currencyRepo: CurrencyRepository

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    private fun newUser(): User = userRepo.save(
        User(
            email = "homemoney-conn-${System.nanoTime()}@example.com",
            passwordHash = "x",
            firstName = "Test",
            lastName = "User",
            role = Role.USER,
            baseCurrency = currencyRepo.findByCode("UAH")
        )
    )

    private fun signIn(user: User) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UserPrincipal(user), null, emptyList())
    }

    @Test
    fun `the authorisation hands the state back, because homemoney does not return it`() {
        signIn(newUser())

        val authorization = connections.authorizationUrl()

        // It travels to them as well - that is what the parameter is for - but the copy the
        // flow depends on is this one, kept by the client across the redirect.
        assertTrue(authorization.state.isNotBlank())
        assertTrue(authorization.url.contains("state=${authorization.state}"))
    }

    @Test
    fun `a code is refused when the state was issued to somebody else`() {
        signIn(newUser())
        val issuedToSomebodyElse = connections.authorizationUrl().state

        // A second account holding a state it was never given: the shape of a consent
        // screen completed elsewhere and handed to whoever is signed in here.
        val victim = newUser()
        signIn(victim)

        assertThrows<HomeMoneyAuthException> {
            connections.completeAuthorization("any-code", issuedToSomebodyElse)
        }
        assertNull(connections.findFor(victim))
    }

    @Test
    fun `a state that is not a state at all is refused`() {
        val user = newUser()
        signIn(user)

        assertThrows<HomeMoneyAuthException> { connections.completeAuthorization("any-code", "not-a-jwt") }
        assertNull(connections.findFor(user))
    }
}
