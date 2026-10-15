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
        assertFalse(RegistrationPolicy.isOpen(environment = "", stored = "closed", userCount = 5))
    }

    @Test
    fun `the environment beats the administrator and locks the toggle`() {
        assertTrue(RegistrationPolicy.isOpen(environment = "open", stored = "closed", userCount = 5))
        assertFalse(RegistrationPolicy.isOpen(environment = " CLOSED ", stored = "open", userCount = 5))
        assertTrue(RegistrationPolicy.lockedByEnvironment("open"))
        assertFalse(RegistrationPolicy.lockedByEnvironment(""))
        assertFalse(RegistrationPolicy.lockedByEnvironment("sometimes"))
    }

    @Test
    fun `the first account can always be created, whatever closed says`() {
        // `closed` means nobody after the administrator; an instance nobody can set up
        // would be locked out for good.
        assertTrue(RegistrationPolicy.isOpen(environment = "closed", stored = null, userCount = 0))
        assertTrue(RegistrationPolicy.isOpen(environment = "", stored = "closed", userCount = 0))
        assertTrue(RegistrationPolicy.isOpen(environment = "closed", stored = "closed", userCount = 0))
    }

    @Test
    fun `an unrecognised value is reported and otherwise ignored`() {
        assertTrue(RegistrationPolicy.unrecognised("sometimes"))
        assertFalse(RegistrationPolicy.unrecognised(""))
        assertFalse(RegistrationPolicy.unrecognised(" Closed "))
        assertFalse(RegistrationPolicy.isOpen(environment = "sometimes", stored = null, userCount = 1))
    }
}
