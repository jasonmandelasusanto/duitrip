package com.duitrip.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.duitrip.app.R
import com.duitrip.app.ui.LocalContainer
import com.duitrip.app.ui.LocalCurrentUser
import com.duitrip.app.ui.components.CurrencyField
import com.duitrip.app.ui.components.DField
import com.duitrip.app.ui.components.PrimaryButton
import com.duitrip.app.ui.components.ScreenScaffold
import com.duitrip.app.ui.theme.BgBase
import com.duitrip.app.ui.theme.Danger
import com.duitrip.app.ui.theme.Teal
import com.duitrip.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(onBack: () -> Unit, onSignOut: () -> Unit) {
    val container = LocalContainer.current
    val user = LocalCurrentUser.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var displayName by remember(user) { mutableStateOf(user?.displayName ?: "") }
    var currency by remember(user) { mutableStateOf(user?.homeCurrency ?: "") }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backingUp by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    // Firestore data (trips/user doc) is already gone by the time we might need this —
    // only the Auth account deletion remains, retried after the user re-proves identity.
    var needsPasswordReauth by remember { mutableStateOf(false) }
    var reauthPassword by remember { mutableStateOf("") }
    val webClientId = stringResource(R.string.default_web_client_id)

    suspend fun finishAuthDeletion() {
        try {
            container.authRepository.deleteCurrentUser()
            // AuthStateListener in RootViewModel picks up the sign-out and navigates away.
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            if (container.authRepository.currentUserIsPasswordAccount) {
                needsPasswordReauth = true
            } else {
                container.authRepository.reauthenticateWithGoogle(context, webClientId)
                container.authRepository.deleteCurrentUser()
            }
        }
    }

    fun deleteAccount() {
        val uid = user?.uid ?: return
        scope.launch {
            deleting = true
            deleteError = null
            try {
                container.tripRepository.deleteAccountData(uid)
                container.userRepository.deleteUserDoc(uid)
                finishAuthDeletion()
            } catch (e: Exception) {
                deleteError = e.localizedMessage ?: "Failed to delete account"
            } finally {
                deleting = false
            }
        }
    }

    fun retryAuthDeletionWithPassword() {
        scope.launch {
            deleting = true
            deleteError = null
            try {
                container.authRepository.reauthenticateWithPassword(reauthPassword)
                container.authRepository.deleteCurrentUser()
                needsPasswordReauth = false
            } catch (e: Exception) {
                deleteError = e.localizedMessage ?: "Re-authentication failed"
            } finally {
                deleting = false
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null && user != null) scope.launch {
            backingUp = true
            try {
                val bytes = container.backupRepository.export(user.uid)
                requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) }
                backupMessage = "Backup exported"
            } catch (e: Exception) { backupMessage = e.localizedMessage ?: "Export failed" } finally { backingUp = false }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && user != null) scope.launch {
            backingUp = true
            try {
                val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
                val count = container.backupRepository.restore(bytes, user)
                backupMessage = "$count trip${if (count == 1) "" else "s"} restored"
            } catch (e: Exception) { backupMessage = e.localizedMessage ?: "Import failed" } finally { backingUp = false }
        }
    }

    ScreenScaffold(title = "Profile", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            val photoUrl = FirebaseAuth.getInstance().currentUser?.photoUrl?.toString() ?: user?.photoURL
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (photoUrl != null) {
                    AsyncImage(model = photoUrl, contentDescription = "Google profile photo", modifier = Modifier.size(64.dp).clip(CircleShape))
                } else {
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(Teal),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text((user?.displayName ?: user?.email ?: "?").take(1).uppercase(), color = BgBase, fontWeight = FontWeight.Bold)
                    }
                }
                Column {
                    Text(user?.displayName ?: "Profile", fontWeight = FontWeight.SemiBold)
                    Text(user?.email ?: "", color = TextSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            DField(displayName, { displayName = it; saved = false }, "Display name")
            Spacer(Modifier.height(12.dp))
            CurrencyField(currency, { currency = it; saved = false }, "Home currency")
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = if (saved) "Saved ✓" else "Save changes",
                enabled = user != null && displayName.isNotBlank() && currency.isNotBlank(),
                loading = saving,
                onClick = {
                    val uid = user?.uid ?: return@PrimaryButton
                    scope.launch {
                        saving = true
                        try {
                            container.userRepository.updateProfile(uid, displayName, currency)
                            saved = true
                        } finally {
                            saving = false
                        }
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
            Text("Backup & restore", fontWeight = FontWeight.SemiBold)
            Text("Exports all accessible trips, one worksheet per trip. Imports always create new trips.", color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = user != null && !backingUp,
                    modifier = Modifier.weight(1f),
                    onClick = { exportLauncher.launch("duitrip-backup-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.xlsx") },
                ) { Text(if (backingUp) "Working…" else "Export XLSX") }
                OutlinedButton(
                    enabled = user != null && !backingUp,
                    modifier = Modifier.weight(1f),
                    onClick = { importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
                ) { Text("Import XLSX") }
            }
            backupMessage?.let { Text(it, color = TextSecondary) }
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onSignOut) { Text("Sign out", color = Danger) }
            Spacer(Modifier.height(24.dp))
            Text("Danger zone", fontWeight = FontWeight.SemiBold, color = Danger)
            Text(
                "Permanently deletes your profile. Trips you own (and their expenses/settlements) are deleted entirely; you're just removed from trips owned by others.",
                color = TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                enabled = user != null && !deleting,
                onClick = { deleteConfirmText = ""; deleteError = null; showDeleteDialog = true },
            ) { Text("Delete account", color = Danger) }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteDialog = false },
            title = { Text("Delete account permanently?") },
            text = {
                Column {
                    Text("This cannot be undone. Type DELETE to confirm.")
                    Spacer(Modifier.height(12.dp))
                    DField(deleteConfirmText, { deleteConfirmText = it }, "Type DELETE")
                    deleteError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Danger)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deleteConfirmText == "DELETE" && !deleting,
                    onClick = { showDeleteDialog = false; deleteAccount() },
                ) { Text(if (deleting) "Deleting…" else "Delete permanently", color = Danger) }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (needsPasswordReauth) {
        AlertDialog(
            onDismissRequest = { if (!deleting) needsPasswordReauth = false },
            title = { Text("Confirm your password") },
            text = {
                Column {
                    Text("For your security, please re-enter your password to finish deleting your account.")
                    Spacer(Modifier.height(12.dp))
                    DField(reauthPassword, { reauthPassword = it }, "Password", isPassword = true)
                    deleteError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Danger)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = reauthPassword.isNotBlank() && !deleting,
                    onClick = { retryAuthDeletionWithPassword() },
                ) { Text(if (deleting) "Deleting…" else "Confirm & delete", color = Danger) }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { needsPasswordReauth = false }) { Text("Cancel") }
            },
        )
    }
}
