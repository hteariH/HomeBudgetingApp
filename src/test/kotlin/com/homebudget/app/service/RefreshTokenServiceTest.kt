package com.homebudget.app.service

import com.homebudget.app.TestIdentity
import com.homebudget.app.repository.RefreshTokenRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.security.MessageDigest

@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
class RefreshTokenServiceTest {

    @Autowired private lateinit var service: RefreshTokenService
    @Autowired private lateinit var repo: RefreshTokenRepository
    @Autowired private lateinit var identity: TestIdentity

    private fun newUser() = identity.newMember("refresh").user

    private fun sha256(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test
    fun `the raw token is never stored`() {
        // A leaked table should be a list of sessions, not a set of working credentials.
        val user = newUser()
        val token = service.issue(user)
        assertNull(repo.findByTokenHash(token), "raw token found in the table")
        assertEquals(user.id, repo.findByTokenHash(sha256(token))?.user?.id)
    }

    @Test
    fun `rotating returns a different token and retires the old one`() {
        val user = newUser()
        val first = service.issue(user)

        val (rotatedUser, second) = service.rotate(first)

        assertEquals(user.id, rotatedUser.id)
        assertNotEquals(first, second)
        // Replaying the old token must fail: rotation is what limits the damage of a
        // stolen refresh token to the window before the real client next refreshes.
        assertThrows<IllegalArgumentException> { service.rotate(first) }
    }

    @Test
    fun `a revoked token cannot be redeemed`() {
        val user = newUser()
        val token = service.issue(user)
        service.revokeAll(user)
        assertThrows<IllegalArgumentException> { service.rotate(token) }
    }

    @Test
    fun `an unknown token is rejected`() {
        assertThrows<IllegalArgumentException> { service.rotate("not-a-real-token") }
    }
}
