package com.personalmoney.core.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerAddressTest {

    @Test
    fun `a bare host becomes an https base url`() {
        assertEquals("https://money.example.com/", ServerAddress.normalize(" money.example.com "))
    }

    @Test
    fun `an explicit scheme, port and path are kept, with one trailing slash`() {
        assertEquals("http://192.168.1.10:8080/", ServerAddress.normalize("http://192.168.1.10:8080"))
        assertEquals("https://nas.local/money/", ServerAddress.normalize("https://nas.local/money//"))
    }

    @Test
    fun `nonsense is refused`() {
        assertNull(ServerAddress.normalize(""))
        assertNull(ServerAddress.normalize("not a url"))
        assertNull(ServerAddress.normalize("https://"))
    }

    @Test
    fun `plain http is flagged`() {
        assertTrue(ServerAddress.isCleartext("http://192.168.1.10:8080/"))
        assertFalse(ServerAddress.isCleartext("https://money.example.com/"))
    }
}
