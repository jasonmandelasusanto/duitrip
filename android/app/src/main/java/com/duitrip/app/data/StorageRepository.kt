package com.duitrip.app.data

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/** Firebase Storage — receipt image upload (replaces frontend/src/utils/imageUpload.ts). */
class StorageRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) {
    /** Uploads a receipt image and returns its download URL. */
    suspend fun uploadReceipt(tripId: String, expenseId: String, localUri: Uri): String {
        val ref = storage.reference.child("receipts/$tripId/$expenseId.jpg")
        ref.putFile(localUri).await()
        return ref.downloadUrl.await().toString()
    }
}
