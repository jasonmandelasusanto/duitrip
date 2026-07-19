package com.duitrip.app.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase Authentication — email/password + Google sign-in (via Credential Manager).
 * Replaces the old client `services/auth.ts`; token verification is no longer needed
 * because the app talks to Firestore directly with the signed-in user.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Emits the signed-in user (or null) and updates on every auth state change. */
    fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithEmail(email: String, password: String): FirebaseUser =
        auth.signInWithEmailAndPassword(email.trim(), password).await().user!!

    suspend fun registerWithEmail(email: String, password: String): FirebaseUser =
        auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!

    /**
     * Google sign-in. Requires the Web client id in strings.xml (default_web_client_id).
     * Uses Credential Manager to obtain a Google ID token, then exchanges it with Firebase.
     */
    suspend fun signInWithGoogle(context: Context, webClientId: String): FirebaseUser {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = CredentialManager.create(context).getCredential(context, request)
        val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        return auth.signInWithCredential(firebaseCredential).await().user!!
    }

    fun signOut() = auth.signOut()

    /** True if the signed-in user authenticates with email/password (vs. Google). */
    val currentUserIsPasswordAccount: Boolean
        get() = auth.currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

    /** Re-proves identity with a password before a sensitive op like account deletion. */
    suspend fun reauthenticateWithPassword(password: String) {
        val user = auth.currentUser ?: throw IllegalStateException("Not signed in")
        val email = user.email ?: throw IllegalStateException("No email on this account")
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
    }

    /** Re-proves identity via Google before a sensitive op like account deletion. */
    suspend fun reauthenticateWithGoogle(context: Context, webClientId: String) {
        val user = auth.currentUser ?: throw IllegalStateException("Not signed in")
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
        val result = CredentialManager.create(context).getCredential(context, request)
        val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        user.reauthenticate(firebaseCredential).await()
    }

    /** Permanently deletes the signed-in Firebase Auth account. May require a recent sign-in. */
    suspend fun deleteCurrentUser() {
        auth.currentUser?.delete()?.await()
    }
}
