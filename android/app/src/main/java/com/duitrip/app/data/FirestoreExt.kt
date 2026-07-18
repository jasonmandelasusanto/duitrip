package com.duitrip.app.data

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

/** Emits the current list every time the query result changes. */
inline fun <reified T : Any> Query.snapshotsFlow(): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        if (snapshot != null) {
            trySend(snapshot.documents.mapNotNull { it.toObject(T::class.java) })
        }
    }
    awaitClose { registration.remove() }
}

/** Emits the document (or null if it doesn't exist) whenever it changes. */
inline fun <reified T : Any> DocumentReference.snapshotFlow(): Flow<T?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        trySend(snapshot?.toObject(T::class.java))
    }
    awaitClose { registration.remove() }
}
