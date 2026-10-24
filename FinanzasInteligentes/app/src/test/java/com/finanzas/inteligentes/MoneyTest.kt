package com.finanzas.inteligentes

import com.finanzas.inteligentes.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parsesDotAndComma() {
        assertEquals(1234L, Money.parseToCents("12.34"))
        assertEquals(1250L, Money.parseToCents("12,5"))
        assertEquals(1000L, Money.parseToCents(" 10 "))
    }
    @Test fun noFloatingPointError() {
        assertEquals(1010L, Money.parseToCents("10.10"))
        assertEquals(30L, Money.parseToCents("0.10")!! + Money.parseToCents("0.20")!!)
    }
    @Test fun rejectsInvalid() {
        assertNull(Money.parseToCents(""))
        assertNull(Money.parseToCents("abc"))
        assertNull(Money.parseToCents("0"))
        assertNull(Money.parseToCents("-5"))
        assertNull(Money.parseToCents("1.234"))
        assertNull(Money.parseToCents("99999999999"))
    }
    @Test fun zeroAllowedWhenRequested() {
        assertEquals(0L, Money.parseToCents("0", allowZero = true))
    }
    @Test fun toPlain() {
        assertEquals("12.30", Money.toPlain(1230L))
    }
}
