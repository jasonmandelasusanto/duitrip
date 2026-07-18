package com.duitrip.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementCalculatorTest {

    @Test
    fun balances_payerCreditedParticipantsDebited() {
        // a paid 90, split equally 3 ways (30 each).
        val expenses = listOf(
            ExpenseShares(
                paidBy = "a",
                amountInDestinationCurrency = 90.0,
                splits = listOf(ShareAmount("a", 30.0), ShareAmount("b", 30.0), ShareAmount("c", 30.0)),
            ),
        )
        val balances = SettlementCalculator.calculateBalances(expenses, emptyList())
        assertEquals(60.0, balances["a"]!!, 0.0001)   // paid 90, owes 30
        assertEquals(-30.0, balances["b"]!!, 0.0001)
        assertEquals(-30.0, balances["c"]!!, 0.0001)
    }

    @Test
    fun settlement_reducesOutstandingBalance() {
        val expenses = listOf(
            ExpenseShares("a", 90.0, listOf(ShareAmount("a", 30.0), ShareAmount("b", 30.0), ShareAmount("c", 30.0))),
        )
        val settlements = listOf(SettlementAmount(fromUserId = "b", toUserId = "a", amountInDestinationCurrency = 30.0))
        val balances = SettlementCalculator.calculateBalances(expenses, settlements)
        assertEquals(30.0, balances["a"]!!, 0.0001) // b already paid back 30
        assertEquals(0.0, balances["b"]!!, 0.0001)
        assertEquals(-30.0, balances["c"]!!, 0.0001)
    }

    @Test
    fun simplifyDebts_producesMinimalTransfers() {
        val balances = mapOf("a" to 60.0, "b" to -30.0, "c" to -30.0)
        val txs = SettlementCalculator.simplifyDebts(balances)
        assertEquals(2, txs.size)
        assertTrue(txs.all { it.to == "a" })
        assertEquals(60.0, txs.sumOf { it.amount }, 0.0001)
    }

    @Test
    fun simplifyDebts_ignoresSettledBalances() {
        val balances = mapOf("a" to 0.0, "b" to 0.0)
        assertTrue(SettlementCalculator.simplifyDebts(balances).isEmpty())
    }
}
