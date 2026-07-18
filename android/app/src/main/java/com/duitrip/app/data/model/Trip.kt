package com.duitrip.app.data.model

import com.google.firebase.Timestamp

data class TripMember(
    val userId: String? = null,
    val email: String? = null,
    val displayName: String = "",
    val photoURL: String? = null,
    val homeCurrency: String = "USD",
    val role: String = "member", // "owner" | "member" | "ghost"
    val joinedAt: Timestamp? = null,
    val ghostId: String? = null,
) {
    /** userId for real members, ghostId for ghosts — the id used across splits. */
    val memberId: String? get() = userId ?: ghostId
    val isGhost: Boolean get() = role == "ghost"
}

data class TripInvite(
    val email: String = "",
    val invitedBy: String = "",
    val status: String = "pending", // "pending" | "accepted" | "declined"
    val ghostId: String? = null,
)

data class CustomCategory(
    val id: String = "",
    val name: String = "",
    val emoji: String = "",
    val createdBy: String = "",
    val createdAt: Timestamp? = null,
)

/**
 * trips/{tripId}. Adds [memberUids] — a top-level array of real member uids — so the
 * app can query `whereArrayContains("memberUids", uid)` instead of the old
 * fetch-all-and-filter approach. Kept in sync with [members] on every mutation.
 */
data class Trip(
    val tripId: String = "",
    val name: String = "",
    val destination: String = "",
    val destinationCurrency: String = "USD",
    val startDate: String = "",
    val endDate: String = "",
    val createdBy: String = "",
    val members: List<TripMember> = emptyList(),
    val memberUids: List<String> = emptyList(),
    val invites: List<TripInvite> = emptyList(),
    // Emails with a pending invite — mirrors memberUids so Security Rules can verify
    // that a joining user was actually invited (request.auth.token.email in inviteEmails).
    val inviteEmails: List<String> = emptyList(),
    val customCategories: List<CustomCategory> = emptyList(),
    val status: String = "active", // "active" | "settled" | "archived"
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
) {
    val realMembers: List<TripMember> get() = members.filter { it.role != "ghost" }
    val isOwner: (String) -> Boolean get() = { uid -> createdBy == uid }
}
