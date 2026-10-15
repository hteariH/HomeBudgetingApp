package com.homebudget.app.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegistrationPolicyTest {

    @Test
    fun `unset means open until somebody exists`() {
        assertTrue(RegistrationPolicy.isOpen(environment = "", stored = null, userCount = 0))
        assertFalse(RegistrationPolicy.isOpen(environment = "", stored = null, userCount = 1))
    }

    @Test
    fun `an administrator's choice beats the default`() {
        assertTrue(RegistrationPolicy.isOpen(environment = "", stored = "open", userCount = 5))
        assertFalse(RegistrationPolicy.isOpen(environment = "", stored = "closed", userCount = 0))
    }

    @Test
    fun `the environment beats everything and locks the toggle`() {
        assertTrue(RegistrationPolicy.isOpen(environment = "open", stored = "closed", userCount = 5))
        assertFalse(RegistrationPolicy.isOpen(environment = " CLOSED ", stored = "open", userCount = 0))
        assertTrue(RegistrationPolicy.lockedByEnvironment("open"))
        assertFalse(RegistrationPolicy.lockedByEnvironment(""))
        assertFalse(RegistrationPolicy.lockedByEnvironment("sometimes"))
    }
}
