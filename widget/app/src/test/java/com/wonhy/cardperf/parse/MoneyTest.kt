package com.wonhy.cardperf.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parsesManWonUnits() {
        assertEquals(300_000L, Money.parse("30만원"))
        assertEquals(1_000_000L, Money.parse("100만원"))
        assertEquals(15_000L, Money.parse("1.5만원"))
    }

    @Test fun parsesPlainWon() {
        assertEquals(1_000_000L, Money.parse("1,000,000원"))
        assertEquals(0L, Money.parse("0원"))
    }

    @Test fun rejectsUnknown() {
        assertNull(Money.parse("??원"))
        assertNull(Money.parse("목표미정원"))
    }
}
