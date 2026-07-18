package com.duitrip.app.data.model

import com.google.firebase.Timestamp

/**
 * users/{uid}. Field names match the existing Firestore documents exactly so the
 * native app reads data written by the old backend without migration.
 */
data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoURL: String? = null,
    val homeCurrency: String = "",
    val createdAt: Timestamp? = null,
)
