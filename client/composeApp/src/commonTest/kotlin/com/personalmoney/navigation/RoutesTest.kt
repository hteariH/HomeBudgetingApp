package com.personalmoney.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether an address names a screen - the question the browser raises and the nav
 * controller does not answer.
 *
 * `navigate()` with an unregistered route throws, the URL binding swallows that, and the
 * visitor is left looking at one screen while the address bar names another. So this
 * matcher decides what a link is allowed to open, and getting it wrong is not visible in
 * the app: too strict silently drops a good bookmark, too loose puts the lie back.
 */
class RoutesTest {

    private val registered = listOf(
        Routes.DASHBOARD,
        Routes.ACCOUNTS,
        Routes.ACCOUNT_EDIT,
        Routes.TRANSACTION_EDIT,
        Routes.QUICK_ADD,
        Routes.IMPORT_HOMEMONEY,
    )

    @Test
    fun `a plain section is known`() {
        assertTrue(Routes.isKnown("accounts", registered))
        assertTrue(Routes.isKnown("import/homemoney", registered))
    }

    @Test
    fun `arguments do not have to be spelled the way the pattern spells them`() {
        // The query carries optional, defaulted arguments, so the same destination is
        // reachable with them and without - both are the accounts editor.
        assertTrue(Routes.isKnown("account/edit", registered))
        assertTrue(Routes.isKnown("account/edit?accountId=33", registered))
        assertTrue(Routes.isKnown("quickadd?type=EXPENSE&categoryId=", registered))
    }

    @Test
    fun `a path argument matches whatever is in its place`() {
        assertTrue(Routes.isKnown("transaction/edit/34", registered))
        assertTrue(Routes.isKnown("transaction/edit/anything", registered))
    }

    @Test
    fun `an address no screen answers to is not known`() {
        assertFalse(Routes.isKnown("nosuchscreen", registered))
        // The right prefix and the wrong depth is the shape a renamed route leaves behind.
        assertFalse(Routes.isKnown("account", registered))
        assertFalse(Routes.isKnown("account/edit/33", registered))
        assertFalse(Routes.isKnown("transaction/edit", registered))
    }

    @Test
    fun `an empty address is not known, so the app opens where a bare address opens`() {
        assertFalse(Routes.isKnown("", registered))
        assertFalse(Routes.isKnown("/", registered))
    }

    @Test
    fun `a screen this platform does not register is not known`() {
        // What `#more` is in a browser: a real route of the Android graph, and nothing here.
        assertFalse(Routes.isKnown("more", registered))
    }
}
