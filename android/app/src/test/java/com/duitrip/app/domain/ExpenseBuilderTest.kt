package com.duitrip.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseBuilderTest {

    @Test
    fun equalSplit_sameCurrency() {
        val draft = ExpenseDraft(
            description = "Lunch", category = "Food & Drink",
            originalAmount = 90.0, originalCurrency = "USD",
            paidBy = "a", splitMode = "equal", splits = emptyList(),
        )
        val result = ExpenseBuilder.build(
            draft = draft,
            destCurrency = "USD",
            memberIds = listOf("a", "b", "c"),
            homeCurrencies = mapOf("a" to "USD", "b" to "USD", "c" to "USD"),
            rates = mapOf("USD" to 1.0),
        )
        assertEquals(90.0, result.amountInDestinationCurrency, 0.0)
        assertEquals(1.0, result.exchangeRateUsed, 0.0)
        assertEquals(3, result.splits.size)
        assertEquals(90.0, result.splits.sumOf { it.amountInDestinationCurrency }, 0.0001)
        result.splits.forEach { assertEquals(it.amountInDestinationCurrency, it.amountInHomeCurrency, 0.0) }
    }

    @Test
    fun convertsOriginalCurrencyToDestination() {
        // rates are dest-per-unit: 26.5 THB per 1 SGD, so 265 THB = 10 SGD.
        val draft = ExpenseDraft(
            description = "Flight", category = "Flight",
            originalAmount = 265.0, originalCurrency = "THB",
            paidBy = "a", splitMode = "equal", splits = emptyList(),
        )
        val result = ExpenseBuilder.build(
            draft = draft,
            destCurrency = "SGD",
            memberIds = listOf("a"),
            homeCurrencies = mapOf("a" to "SGD"),
            rates = mapOf("SGD" to 1.0, "THB" to 26.5),
        )
        assertEquals(10.0, result.amountInDestinationCurrency, 0.0001)
        assertEquals(1.0 / 26.5, result.exchangeRateUsed, 0.0001)
        assertEquals(10.0, result.splits.first().amountInDestinationCurrency, 0.0001)
        assertEquals("SGD", result.splits.first().homeCurrency)
    }

    @Test
    fun percentageSplit_preservesPercentages() {
        val draft = ExpenseDraft(
            description = "Hotel", category = "Accommodation",
            originalAmount = 100.0, originalCurrency = "USD",
            paidBy = "a", splitMode = "percentage",
            splits = listOf(
                SplitInput("a", percentage = 60.0),
                SplitInput("b", percentage = 40.0),
            ),
        )
        val result = ExpenseBuilder.build(
            draft = draft,
            destCurrency = "USD",
            memberIds = listOf("a", "b"),
            homeCurrencies = mapOf("a" to "USD", "b" to "USD"),
            rates = mapOf("USD" to 1.0),
        )
        val byUser = result.splits.associateBy { it.userId }
        assertEquals(60.0, byUser["a"]!!.amountInDestinationCurrency, 0.0001)
        assertEquals(40.0, byUser["b"]!!.amountInDestinationCurrency, 0.0001)
        assertEquals(60.0, byUser["a"]!!.percentage, 0.0001)
        assertEquals(100.0, result.splits.sumOf { it.amountInDestinationCurrency }, 0.0001)
    }
}
