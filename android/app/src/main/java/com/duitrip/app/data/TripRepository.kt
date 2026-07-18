package com.duitrip.app.data

import com.duitrip.app.data.model.CustomCategory
import com.duitrip.app.data.model.Trip
import com.duitrip.app.data.model.TripInvite
import com.duitrip.app.data.model.TripMember
import com.duitrip.app.data.model.User
import com.duitrip.app.domain.Categories
import com.duitrip.app.domain.CountryCurrency
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * trips/{tripId} — CRUD plus embedded members/invites/custom-categories, ported from
 * the old backend trips/members/categories routers. Membership-changing writes run in
 * a transaction and always recompute [Trip.memberUids] from [Trip.members] so the
 * array stays consistent for querying.
 */
class TripRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val trips = db.collection("trips")
    private fun tripDoc(id: String) = trips.document(id)

    // ── Reads ──────────────────────────────────────────────────────────────────
    /** All non-archived trips the user belongs to (status filtered client-side). */
    fun observeUserTrips(uid: String): Flow<List<Trip>> =
        trips.whereArrayContains("memberUids", uid)
            .snapshotsFlow<Trip>()
            .map { list -> list.filter { it.status != "archived" }.sortedByDescending { it.createdAt } }

    fun observeTrip(tripId: String): Flow<Trip?> = tripDoc(tripId).snapshotFlow()

    suspend fun getTrip(tripId: String): Trip? =
        tripDoc(tripId).get().await().toObject(Trip::class.java)

    // ── Trip CRUD ────────────────────────────────────────────────────────────────
    suspend fun createTrip(
        owner: User,
        name: String,
        destination: String,
        destinationCurrency: String?,
        startDate: String,
        endDate: String,
    ): String {
        val tripId = IdGen.trip()
        val now = Timestamp.now()
        val destCurrency = destinationCurrency?.takeIf { it.isNotBlank() }
            ?: CountryCurrency.resolveCurrency(destination)

        val ownerMember = TripMember(
            userId = owner.uid,
            email = owner.email,
            displayName = owner.displayName,
            photoURL = owner.photoURL,
            homeCurrency = owner.homeCurrency.ifBlank { "USD" },
            role = "owner",
            joinedAt = now,
            ghostId = null,
        )
        val trip = Trip(
            tripId = tripId,
            name = name,
            destination = destination,
            destinationCurrency = destCurrency,
            startDate = startDate,
            endDate = endDate,
            createdBy = owner.uid,
            members = listOf(ownerMember),
            memberUids = listOf(owner.uid),
            invites = emptyList(),
            customCategories = emptyList(),
            status = "active",
            createdAt = now,
            updatedAt = now,
        )
        tripDoc(tripId).set(trip).await()
        return tripId
    }

    suspend fun updateTrip(
        tripId: String,
        name: String? = null,
        destination: String? = null,
        startDate: String? = null,
        endDate: String? = null,
    ) {
        val updates = buildMap<String, Any> {
            name?.let { put("name", it) }
            destination?.let {
                put("destination", it)
                put("destinationCurrency", CountryCurrency.resolveCurrency(it))
            }
            startDate?.let { put("startDate", it) }
            endDate?.let { put("endDate", it) }
            put("updatedAt", Timestamp.now())
        }
        tripDoc(tripId).update(updates).await()
    }

    /** Soft-delete (owner-only, enforced by rules). */
    suspend fun archiveTrip(tripId: String) {
        tripDoc(tripId).update(mapOf("status" to "archived", "updatedAt" to Timestamp.now())).await()
    }

    // ── Members / invites ────────────────────────────────────────────────────────
    private fun realUids(members: List<TripMember>): List<String> =
        members.filter { it.role != "ghost" && it.userId != null }.map { it.userId!! }

    private fun pendingEmails(invites: List<TripInvite>): List<String> =
        invites.filter { it.status == "pending" }.map { it.email }.distinct()

    private suspend fun mutateMembers(
        tripId: String,
        block: (Trip) -> List<TripMember>,
    ) {
        db.runTransaction { txn ->
            val ref = tripDoc(tripId)
            val trip = txn.get(ref).toObject(Trip::class.java)
                ?: throw IllegalStateException("Trip not found")
            val newMembers = block(trip)
            txn.update(
                ref,
                mapOf(
                    "members" to newMembers,
                    "memberUids" to realUids(newMembers),
                    "updatedAt" to Timestamp.now(),
                ),
            )
        }.await()
    }

    suspend fun addGhost(tripId: String, displayName: String, homeCurrency: String): String {
        val ghostId = IdGen.ghost()
        mutateMembers(tripId) { trip ->
            trip.members + TripMember(
                userId = null,
                email = null,
                displayName = displayName,
                photoURL = null,
                homeCurrency = homeCurrency,
                role = "ghost",
                joinedAt = null,
                ghostId = ghostId,
            )
        }
        return ghostId
    }

    suspend fun updateGhost(tripId: String, ghostId: String, displayName: String?, homeCurrency: String?) {
        mutateMembers(tripId) { trip ->
            var found = false
            val updated = trip.members.map { m ->
                if (m.ghostId == ghostId) {
                    found = true
                    m.copy(
                        displayName = displayName?.takeIf { it.isNotBlank() } ?: m.displayName,
                        homeCurrency = homeCurrency?.takeIf { it.isNotBlank() } ?: m.homeCurrency,
                    )
                } else m
            }
            if (!found) throw IllegalStateException("Ghost member not found")
            updated
        }
    }

    /** Owner-only. Records an invite tied to the ghost; the ghost becomes real on accept. */
    suspend fun promoteGhost(tripId: String, ghostId: String, email: String, invitedBy: String) {
        db.runTransaction { txn ->
            val ref = tripDoc(tripId)
            val trip = txn.get(ref).toObject(Trip::class.java) ?: throw IllegalStateException("Trip not found")
            val newInvites = trip.invites + TripInvite(
                email = email,
                invitedBy = invitedBy,
                status = "pending",
                ghostId = ghostId,
            )
            txn.update(
                ref,
                mapOf(
                    "invites" to newInvites,
                    "inviteEmails" to pendingEmails(newInvites),
                    "updatedAt" to Timestamp.now(),
                ),
            )
        }.await()
    }

    suspend fun removeMember(tripId: String, memberId: String) {
        mutateMembers(tripId) { trip ->
            if (memberId == trip.createdBy) throw IllegalStateException("Cannot remove the trip owner")
            val remaining = trip.members.filter { it.userId != memberId && it.ghostId != memberId }
            if (remaining.size == trip.members.size) throw IllegalStateException("Member not found")
            remaining
        }
    }

    suspend fun inviteMember(tripId: String, email: String, invitedBy: String) {
        db.runTransaction { txn ->
            val ref = tripDoc(tripId)
            val trip = txn.get(ref).toObject(Trip::class.java) ?: throw IllegalStateException("Trip not found")
            if (trip.members.any { it.email == email }) throw IllegalStateException("Already a member")
            if (trip.invites.any { it.email == email && it.status == "pending" }) {
                throw IllegalStateException("Already invited")
            }
            val newInvites = trip.invites + TripInvite(email = email, invitedBy = invitedBy, status = "pending")
            txn.update(
                ref,
                mapOf(
                    "invites" to newInvites,
                    "inviteEmails" to pendingEmails(newInvites),
                    "updatedAt" to Timestamp.now(),
                ),
            )
        }.await()
    }

    /** Accept a pending invite for [user]'s email; replaces the ghost if it was a promotion. */
    suspend fun acceptInvite(tripId: String, user: User) {
        db.runTransaction { txn ->
            val ref = tripDoc(tripId)
            val trip = txn.get(ref).toObject(Trip::class.java) ?: throw IllegalStateException("Trip not found")
            val invite = trip.invites.firstOrNull { it.email == user.email && it.status == "pending" }
                ?: throw IllegalStateException("No pending invite found for your email")

            val newInvites = trip.invites.map {
                if (it.email == user.email && it.status == "pending") it.copy(status = "accepted") else it
            }
            val newMember = TripMember(
                userId = user.uid,
                email = user.email,
                displayName = user.displayName,
                photoURL = user.photoURL,
                homeCurrency = user.homeCurrency.ifBlank { "USD" },
                role = "member",
                joinedAt = Timestamp.now(),
                ghostId = null,
            )
            val ghostId = invite.ghostId
            val newMembers = if (ghostId != null) {
                trip.members.map { if (it.ghostId == ghostId) newMember else it }
            } else {
                trip.members + newMember
            }
            txn.update(
                ref,
                mapOf(
                    "members" to newMembers,
                    "memberUids" to realUids(newMembers),
                    "invites" to newInvites,
                    "inviteEmails" to pendingEmails(newInvites),
                    "updatedAt" to Timestamp.now(),
                ),
            )
        }.await()
    }

    // ── Custom categories ────────────────────────────────────────────────────────
    suspend fun addCategory(tripId: String, name: String, emoji: String, createdBy: String): CustomCategory {
        val newCat = CustomCategory(
            id = IdGen.category(),
            name = name,
            emoji = emoji,
            createdBy = createdBy,
            createdAt = Timestamp.now(),
        )
        db.runTransaction { txn ->
            val ref = tripDoc(tripId)
            val trip = txn.get(ref).toObject(Trip::class.java) ?: throw IllegalStateException("Trip not found")
            if (trip.customCategories.size >= Categories.MAX_CUSTOM_CATEGORIES) {
                throw IllegalStateException("Max ${Categories.MAX_CUSTOM_CATEGORIES} custom categories reached")
            }
            if (trip.customCategories.any { it.name.equals(name, ignoreCase = true) }) {
                throw IllegalStateException("Category name already exists")
            }
            txn.update(
                ref,
                mapOf(
                    "customCategories" to trip.customCategories + newCat,
                    "updatedAt" to Timestamp.now(),
                ),
            )
        }.await()
        return newCat
    }

    /** Deletes a custom category after verifying no expense still uses it. */
    suspend fun deleteCategory(tripId: String, categoryId: String) {
        val trip = getTrip(tripId) ?: throw IllegalStateException("Trip not found")
        val cat = trip.customCategories.firstOrNull { it.id == categoryId }
            ?: throw IllegalStateException("Category not found")

        val inUse = tripDoc(tripId).collection("expenses")
            .whereEqualTo("category", cat.name).limit(1).get().await()
        if (!inUse.isEmpty) throw IllegalStateException("Category is in use — reassign expenses first")

        tripDoc(tripId).update(
            mapOf(
                "customCategories" to trip.customCategories.filter { it.id != categoryId },
                "updatedAt" to Timestamp.now(),
            ),
        ).await()
    }
}
