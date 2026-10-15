package com.homebudget.app.admin

import com.homebudget.app.TestIdentity
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
class ResetPasswordCommandTest {

    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var command: ResetPasswordCommand

    @Test
    fun `prints a reset link for a known address and nothing for an unknown one`() {
        val user = identity.newMember("locked-out").user
        val link = command.linkFor(user.email)
        assertTrue(link!!.contains("/reset-password?token="))
        assertNull(command.linkFor("nobody@example.com"))
    }
}
