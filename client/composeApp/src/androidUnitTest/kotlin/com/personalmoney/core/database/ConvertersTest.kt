package com.personalmoney.core.database

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test fun `valid date round trips`() {
        val date = LocalDate(2026, 6, 11)
        assertEquals(date, converters.stringToLocalDate(converters.localDateToString(date)))
    }

    @Test fun `malformed date returns null instead of throwing`() {
        assertNull(converters.stringToLocalDate("not-a-date"))
        assertNull(converters.stringToLocalDate("2026-13-99"))
        assertNull(converters.stringToLocalDate(""))
    }

    @Test fun `null date returns null`() {
        assertNull(converters.stringToLocalDate(null))
    }
}
