package com.duitrip.app.data.model

import com.google.firebase.Timestamp

/** trips/{tripId}/settlements/{settlementId} — a manually recorded payback. */
data class Settlement(
    val settlementId: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val amountInDestinationCurrency: Double = 0.0,
    val destinationCurrency: String = "",
    val note: String? = null,
    val settledAt: Timestamp? = null,
    val createdBy: String = "",
) {
    // Display names are resolved from the trip's members at read time (not stored).
    @get:com.google.firebase.firestore.Exclude
    var fromDisplayName: String = ""

    @get:com.google.firebase.firestore.Exclude
    var toDisplayName: String = ""
}
