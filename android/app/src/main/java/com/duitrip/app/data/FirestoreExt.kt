package com.duitrip.app.data

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/** Client-side id generation, matching the old backend's `uuid4().hex[:12]` prefixes. */
object IdGen {
    private fun hex(n: Int): String = UUID.randomUUID().toString().replace("-", "").take(n)
    fun trip(): String = "trip_${hex(12)}"
    fun expense(): String = "exp_${hex(12)}"
    fun ghost(): String = "ghost_${hex(8)}"
    fun settlement(): String = "stl_${hex(12)}"
    fun category(): String = "custom_${hex(8)}"
}

/**
 * Emits the current list every time the query result changes. On a Firestore error
 * (e.g. permission-denied) it logs and emits an empty list instead of throwing, so a
 * rules/permission problem shows an empty screen rather than crashing the app.
 */
inline fun <reified T : Any> Query.snapshotsFlow(): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            Log.e("Firestore", "query failed: ${error.message}", error)
            trySend(emptyList())
            return@addSnapshotListener
        }
        if (snapshot != null) {
            trySend(snapshot.documents.mapNotNull { it.toObject(T::class.java) })
        }
    }
    awaitClose { registration.remove() }
}

/** Emits the document (or null) whenever it changes; logs + emits null on error. */
inline fun <reified T : Any> DocumentReference.snapshotFlow(): Flow<T?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            Log.e("Firestore", "doc listen failed: ${error.message}", error)
            trySend(null)
            return@addSnapshotListener
        }
        trySend(snapshot?.toObject(T::class.java))
    }
    awaitClose { registration.remove() }
}
