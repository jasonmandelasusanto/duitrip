package com.duitrip.app.data

import com.duitrip.app.data.model.User
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/** users/{uid} — profile + onboarding (home currency). */
class UserRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private fun doc(uid: String) = db.collection("users").document(uid)

    fun userFlow(uid: String): Flow<User?> = doc(uid).snapshotFlow()

    suspend fun getUser(uid: String): User? = doc(uid).get().await().toObject(User::class.java)

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
