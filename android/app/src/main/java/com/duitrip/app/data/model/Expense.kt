package com.duitrip.app.data.model

import com.google.firebase.Timestamp

data class SplitEntry(
    val userId: String = "",
    val percentage: Double = 0.0,
    val amountInDestinationCurrency: Double = 0.0,
    val amountInHomeCurrency: Double = 0.0,
    val homeCurrency: String = "",
)

/**
 * trips/{tripId}/expenses/{expenseId}. `exchangeRates` is the frankfurter snapshot
 * (base = destinationCurrency) locked at record time; `exchangeRateTimestamp` is an
 * ISO-8601 string (matches the old backend); createdAt/updatedAt are Timestamps.
 */
data class Expense(
    val expenseId: String = "",
    val description: String = "",
    val category: String = "Other",
    val originalAmount: Double = 0.0,
    val originalCurrency: String = "",
    val destinationCurrency: String = "",
    val amountInDestinationCurrency: Double = 0.0,
    val exchangeRateUsed: Double = 1.0,
    val exchangeRateTimestamp: String = "",
    val exchangeRates: Map<String, Double> = emptyMap(),
    val splitMode: String = "equal", // "equal" | "percentage" | "exact"
    val paidBy: String = "",
    val splits: List<SplitEntry> = emptyList(),
    val receiptUrl: String? = null,
    val createdBy: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
)

/** Derived per-member status for the expense list (not stored in Firestore). */
data class MemberStatus(
    val userId: String,
    val displayName: String,
    val isGhost: Boolean,
    val isPayer: Boolean,
    val amountInDestinationCurrency: Double,
    val amountInHomeCurrency: Double,
    val homeCurrency: String,
    val status: String, // "paid" | "settled" | "outstanding"
)
