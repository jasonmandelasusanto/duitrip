package com.duitrip.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun floor2_truncatesToTwoDecimals() {
        assertEquals(3.33, Money.floor2(10.0 / 3.0), 0.0)
        assertEquals(3.99, Money.floor2(3.999), 0.0)
        assertEquals(5.0, Money.floor2(5.0), 0.0)
    }

    @Test
    fun round2_roundsHalfToEven() {
        assertEquals(1.23, Money.round2(1.234), 0.0)
        assertEquals(1.24, Money.round2(1.236), 0.0)
        // Banker's rounding: 2.345 -> 2.34 (4 is even), 2.355 -> 2.36 (round to even)
        assertEquals(2.34, Money.round2(2.345), 0.0)
        assertEquals(2.36, Money.round2(2.355), 0.0)
    }

    @Test
    fun roundN_roundsToScale() {
        assertEquals(1.2346, Money.roundN(1.23456, 4), 0.0)
        assertEquals(33.3333, Money.roundN(100.0 / 3.0, 4), 0.0)
    }
}
