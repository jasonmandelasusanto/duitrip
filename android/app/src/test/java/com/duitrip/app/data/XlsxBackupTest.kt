package com.duitrip.app.data

import com.duitrip.app.data.model.Expense
import com.duitrip.app.data.model.SplitEntry
import com.duitrip.app.data.model.Trip
import com.duitrip.app.data.model.TripMember
import org.junit.Assert.assertEquals
import org.junit.Test

class XlsxBackupTest {
    @Test fun roundTrip_preservesTripAndExpenseData() {
        val trip = Trip(
            tripId = "trip_source",
            name = "Tokyo 2026",
            destination = "Tokyo",
            destinationCurrency = "JPY",
            createdBy = "u1",
            members = listOf(TripMember(userId = "u1", displayName = "Jason", homeCurrency = "IDR", role = "owner")),
            memberUids = listOf("u1"),
        )
        val expense = Expense(
            expenseId = "exp_source", description = "Very long ramen dinner", category = "Food",
            originalAmount = 2400.0, originalCurrency = "JPY", destinationCurrency = "JPY",
            amountInDestinationCurrency = 2400.0, paidBy = "u1", createdBy = "u1",
            splits = listOf(SplitEntry("u1", 100.0, 2400.0, 2400.0, "JPY")),
        )
        val restored = XlsxBackup.read(XlsxBackup.write(listOf(TripBackup(trip, listOf(expense), emptyList())))).single()
        assertEquals("Tokyo 2026", restored.trip.name)
        assertEquals("u1", restored.trip.memberUids.single())
        assertEquals("Very long ramen dinner", restored.expenses.single().description)
        assertEquals(2400.0, restored.expenses.single().amountInDestinationCurrency, 0.0)
    }
}
