package com.duitrip.app.data

import com.duitrip.app.data.model.User
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** users/{uid} — profile + onboarding (home currency). */
class UserRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private fun doc(uid: String) = db.collection("users").document(uid)

    /**
     * A profile lives at users/{uid}; that path (and Firebase Auth), rather than the
     * duplicated `uid` field inside the document, is the source of truth. Older PWA
     * profiles may not contain that field, which otherwise makes trip queries use an
     * empty or stale uid after a successful sign-in.
     */
    fun userFlow(uid: String): Flow<User?> =
        doc(uid).snapshotFlow<User>().map { profile -> profile?.copy(uid = uid) }

    suspend fun getUser(uid: String): User? =
        doc(uid).get().await().toObject(User::class.java)?.copy(uid = uid)

    /**
     * Create the users/{uid} document on first sign-in if it doesn't exist yet.
     * homeCurrency stays empty until onboarding, which the router uses to redirect.
     */
    suspend fun ensureUserDoc(firebaseUser: FirebaseUser) {
        val ref = doc(firebaseUser.uid)
        if (ref.get().await().exists()) return
        ref.set(
            mapOf(
                "uid" to firebaseUser.uid,
                "email" to (firebaseUser.email ?: ""),
                "displayName" to (firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@") ?: ""),
                "photoURL" to firebaseUser.photoUrl?.toString(),
                "homeCurrency" to "",
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    /** Onboarding — set the home currency. */
    suspend fun setHomeCurrency(uid: String, homeCurrency: String) {
        doc(uid).update("homeCurrency", homeCurrency).await()
    }

    suspend fun updateProfile(uid: String, displayName: String?, homeCurrency: String?) {
        val updates = buildMap {
            displayName?.let { put("displayName", it) }
            homeCurrency?.let { put("homeCurrency", it) }
        }
        if (updates.isNotEmpty()) doc(uid).update(updates).await()
    }
}
