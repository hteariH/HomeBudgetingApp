package com.homebudget.app.service

import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * The confirmation and reset flows.
 *
 * Delivery itself is out of scope - [MailService] is replaced - but the token handed to
 * it is captured, which is exactly what a real recipient would click. Everything that
 * makes these links safe is asserted: single use, purpose separation, no disclosure of
 * which addresses exist, and a reset that genuinely ends existing sessions.
 */
@SpringBootTest(classes = [com.homebudget.app.HomeBudgetingApplication::class, AuthFlowServiceTest.Mail::class])
@ActiveProfiles("test")
class AuthFlowServiceTest {

    @Autowired private lateinit var service: AuthFlowService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var refreshTokenService: RefreshTokenService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var mailer: RecordingMailService

    /**
     * Captures the token instead of sending it. A recorded implementation rather than a
     * mock: Mockito cannot instrument classes on this JDK, and the captured value is
     * literally what a recipient would click.
     */
    class RecordingMailService : MailService {
        val verifications = mutableMapOf<String, String>()
        val resets = mutableMapOf<String, String>()
        override fun sendVerification(to: String, token: String) { verifications[to] = token }
        override fun sendPasswordReset(to: String, token: String) { resets[to] = token }
    }

    @TestConfiguration
    class Mail {
        @Bean @Primary fun recordingMailService() = RecordingMailService()
    }

    private fun newUser(): User = userRepository.save(
        User(
            email = "flow-${UUID.randomUUID()}@example.com",
            passwordHash = passwordEncoder.encode("originalpassword"),
            firstName = "Flow",
            lastName = "Test",
            role = Role.USER
        )
    )

    /** The token as it reaches the user: whatever was handed to the mailer. */
    private fun verificationTokenFor(user: User): String {
        service.sendVerification(user)
        return mailer.verifications[user.email] ?: error("no verification mail for ${'$'}{user.email}")
    }

    private fun resetTokenFor(user: User): String {
        service.requestPasswordReset(user.email)
        return mailer.resets[user.email] ?: error("no reset mail for ${'$'}{user.email}")
    }

    @Test
    fun `a confirmation link works once and marks the address verified`() {
        val user = newUser()
        assertFalse(user.emailVerified)

        val raw = verificationTokenFor(user)
        assertTrue(service.verifyEmail(raw).emailVerified)

        // A link left sitting in an inbox must stop being a key.
        assertThrows<IllegalArgumentException> { service.verifyEmail(raw) }
    }

    @Test
    fun `issuing a new link retires the previous one`() {
        val user = newUser()
        val first = verificationTokenFor(user)
        val second = verificationTokenFor(user)
        assertNotEquals(first, second)

        assertThrows<IllegalArgumentException> { service.verifyEmail(first) }
        assertTrue(service.verifyEmail(second).emailVerified)
    }

    @Test
    fun `requesting a reset for an unknown address is silent rather than an error`() {
        // Anything else turns the form into a test for which addresses are registered.
        service.requestPasswordReset("nobody-${UUID.randomUUID()}@example.com")
    }

    @Test
    fun `a reset changes the password, confirms the address and ends every session`() {
        val user = newUser()
        val refresh = refreshTokenService.issue(user)

        val raw = resetTokenFor(user)
        val updated = service.resetPassword(raw, "brand-new-password")

        assertTrue(passwordEncoder.matches("brand-new-password", updated.passwordHash))
        assertTrue(updated.emailVerified, "using the link proves the address")

        // The point of a reset is that access was lost; a surviving session defeats it.
        assertThrows<IllegalArgumentException> { refreshTokenService.rotate(refresh) }
    }

    @Test
    fun `a reset link cannot be replayed`() {
        val user = newUser()
        val raw = resetTokenFor(user)
        service.resetPassword(raw, "brand-new-password")
        assertThrows<IllegalArgumentException> { service.resetPassword(raw, "another-password-x") }
    }

    @Test
    fun `a confirmation link is not accepted as a reset link`() {
        val user = newUser()
        val raw = verificationTokenFor(user)
        assertThrows<IllegalArgumentException> { service.resetPassword(raw, "brand-new-password") }
    }

    @Test
    fun `a short password is refused`() {
        val user = newUser()
        val raw = resetTokenFor(user)
        assertThrows<IllegalArgumentException> { service.resetPassword(raw, "short") }
    }

    @Test
    fun `an unknown token is refused`() {
        assertThrows<IllegalArgumentException> { service.verifyEmail("not-a-real-token") }
        assertThrows<IllegalArgumentException> { service.resetPassword("not-a-real-token", "brand-new-password") }
    }
}
