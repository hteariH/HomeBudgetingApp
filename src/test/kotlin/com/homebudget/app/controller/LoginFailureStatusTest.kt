package com.homebudget.app.controller

import com.homebudget.app.domain.User
import com.homebudget.app.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.util.UUID

/**
 * What the server says when a sign-in fails.
 *
 * `AuthenticationManager.authenticate` throws `BadCredentialsException`, and for a long
 * time nothing handled it: the commonest error in the application answered 500 and logged
 * a stack trace on every typo, while the client - which maps 500 to "the server is having
 * trouble" - told the user to try again later instead of checking their password.
 *
 * The second assertion is the security one. A 401 that differs between "no such account"
 * and "wrong password" is an account-enumeration oracle, so the two must be
 * indistinguishable from outside.
 */
@SpringBootTest(classes = [com.homebudget.app.HomeBudgetingApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginFailureStatusTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var knownEmail: String

    @BeforeEach
    fun createAnAccount() {
        knownEmail = "known-${UUID.randomUUID()}@example.com"
        userRepository.save(
            User(
                email = knownEmail,
                passwordHash = passwordEncoder.encode("the-real-password"),
                firstName = "Known",
                lastName = "User",
            )
        )
    }

    private fun login(email: String, password: String) = mvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","password":"$password"}""")
    ).andReturn().response

    @Test
    fun `a wrong password is unauthorised, not a server error`() {
        val response = login(knownEmail, "not-the-real-password")

        assertEquals(401, response.status)
    }

    @Test
    fun `an unknown address is unauthorised too`() {
        val response = login("nobody-${UUID.randomUUID()}@example.com", "not-the-real-password")

        assertEquals(401, response.status)
    }

    @Test
    fun `the two are indistinguishable from outside`() {
        val wrongPassword = login(knownEmail, "not-the-real-password")
        val unknownAddress = login("nobody-${UUID.randomUUID()}@example.com", "not-the-real-password")

        assertEquals(wrongPassword.status, unknownAddress.status)
        assertEquals(
            wrongPassword.contentAsString.substringAfter("\"error\""),
            unknownAddress.contentAsString.substringAfter("\"error\""),
            "the bodies must not say which address is registered",
        )
    }
}
