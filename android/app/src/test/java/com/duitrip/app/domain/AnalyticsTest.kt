package com.duitrip.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsTest {

    private val members = listOf(
        MemberRef("alice", "Alice", "SGD", isGhost = false),
        MemberRef("bob", "Bob", "SGD", isGhost = false),
    )
    private val expenses = listOf(
        AnalyticsExpense("e1", "Dinner", "Food & Drink", 100.0, "alice", "2026-06-10",
            listOf(ShareAmount("alice", 50.0), ShareAmount("bob", 50.0))),
        AnalyticsExpense("e2", "Taxi", "Transport", 50.0, "bob", "2026-06-11",
            listOf(ShareAmount("alice", 25.0), ShareAmount("bob", 25.0))),
    )

    private fun compute() = Analytics.compute(
        expenses = expenses,
        members = members,
        realMemberCount = 2,
        destCurrency = "SGD",
        currentUid = "alice",
        startDate = "2026-06-10",
        endDate = "2026-06-15",
    )

    @Test
    fun group_totalsAreCorrect() {
        val a = compute()
        assertEquals(150.0, a.group.totalSpend, 0.0)
        assertEquals(75.0, a.group.totalSpendPerMember, 0.0)
    }

    @Test
    fun group_byCategorySortedWithPercentages() {
        val a = compute()
        assertEquals("Food & Drink", a.group.byCategory.first().category) // largest first
        assertEquals(100.0, a.group.byCategory.first().amount, 0.0)
        assertEquals(66.67, a.group.byCategory.first().percentage, 0.0)
        assertEquals(33.33, a.group.byCategory.last().percentage, 0.0)
    }

    @Test
    fun individual_shareAndVsGroupAverage() {
        val a = compute()
        assertEquals(75.0, a.individual.totalShare, 0.0)        // 50 + 25
        assertEquals(75.0, a.individual.vsGroupAverage.groupAverage, 0.0)
        assertEquals(0.0, a.individual.vsGroupAverage.difference, 0.0)
        assertEquals(2, a.individual.timeline.size)
    }
}
