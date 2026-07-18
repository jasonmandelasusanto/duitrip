package com.duitrip.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SplitCalculatorTest {

    @Test
    fun equalSplit_dividesEvenly() {
        val splits = SplitCalculator.equal(90.0, listOf("a", "b", "c"), payerId = "a")
        assertEquals(3, splits.size)
        splits.forEach { assertEquals(30.0, it.amountInDestinationCurrency, 0.0001) }
    }

    @Test
    fun equalSplit_remainderGoesToPayer() {
        val splits = SplitCalculator.equal(100.0, listOf("a", "b", "c"), payerId = "a")
        val byUser = splits.associate { it.userId to it.amountInDestinationCurrency }
        assertEquals(33.34, byUser["a"]!!, 0.0001) // payer absorbs the 0.01 remainder
        assertEquals(33.33, byUser["b"]!!, 0.0001)
        assertEquals(33.33, byUser["c"]!!, 0.0001)
        assertEquals(100.0, splits.sumOf { it.amountInDestinationCurrency }, 0.0001)
    }

    @Test
    fun percentageSplit_reconcilesToTotalOnPayer() {
        val splits = SplitCalculator.percentage(
            total = 100.0,
            inputs = listOf(
                PercentageInput("a", 33.0),
                PercentageInput("b", 33.0),
                PercentageInput("c", 34.0),
            ),
            payerId = "a",
        )
        assertEquals(100.0, splits.sumOf { it.amountInDestinationCurrency }, 0.0001)
    }

    @Test
    fun percentageSplit_rejectsNon100() {
        assertThrows(IllegalArgumentException::class.java) {
            SplitCalculator.percentage(
                100.0,
                listOf(PercentageInput("a", 50.0), PercentageInput("b", 40.0)),
                "a",
            )
        }
    }

    @Test
    fun exactSplit_sameCurrency_balances() {
        val splits = SplitCalculator.exact(
            totalDest = 100.0,
            inputs = listOf(
                ExactInput("a", 60.0, "USD"),
                ExactInput("b", 40.0, "USD"),
            ),
            rates = mapOf("USD" to 1.0),
            destCurrency = "USD",
            payerId = "a",
        )
        assertEquals(100.0, splits.sumOf { it.amountInDestinationCurrency }, 0.0001)
    }

    @Test
    fun exactSplit_rejectsMismatchBeyondTolerance() {
        assertThrows(IllegalArgumentException::class.java) {
            SplitCalculator.exact(
                totalDest = 100.0,
                inputs = listOf(ExactInput("a", 60.0, "USD"), ExactInput("b", 30.0, "USD")),
                rates = mapOf("USD" to 1.0),
                destCurrency = "USD",
                payerId = "a",
            )
        }
    }
}
