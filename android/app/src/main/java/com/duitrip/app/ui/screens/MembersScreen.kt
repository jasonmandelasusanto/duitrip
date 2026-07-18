package com.duitrip.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duitrip.app.data.model.Trip
import com.duitrip.app.data.model.TripMember
import com.duitrip.app.ui.LocalContainer
import com.duitrip.app.ui.LocalCurrentUser
import com.duitrip.app.ui.VMFactory
import com.duitrip.app.ui.components.CenteredMessage
import com.duitrip.app.ui.components.CurrencyField
import com.duitrip.app.ui.components.DField
import com.duitrip.app.ui.components.PrimaryButton
import com.duitrip.app.ui.components.ScreenScaffold
import com.duitrip.app.ui.components.SurfaceCard
import com.duitrip.app.ui.theme.Danger
import com.duitrip.app.ui.theme.TextPrimary
import com.duitrip.app.ui.theme.TextSecondary
import com.duitrip.app.ui.util.Format
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitrip.app.data.TripRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MembersViewModel(private val repo: TripRepository, val tripId: String) : ViewModel() {
    val trip: StateFlow<Trip?> =
        repo.observeTrip(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun addGhost(name: String, currency: String, onError: (String) -> Unit) = launch(onError) { repo.addGhost(tripId, name, currency) }
    fun invite(email: String, invitedBy: String, onError: (String) -> Unit) = launch(onError) { repo.inviteMember(tripId, email, invitedBy) }
    fun promote(ghostId: String, email: String, invitedBy: String, onError: (String) -> Unit) = launch(onError) { repo.promoteGhost(tripId, ghostId, email, invitedBy) }
    fun remove(memberId: String, onError: (String) -> Unit) = launch(onError) { repo.removeMember(tripId, memberId) }

    private fun launch(onError: (String) -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: Exception) { onError(e.localizedMessage ?: "Action failed") }
        }
    }
}

@Composable
fun MembersScreen(tripId: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val currentUid = LocalCurrentUser.current?.uid ?: return
    val vm: MembersViewModel = viewModel(factory = VMFactory { MembersViewModel(container.tripRepository, tripId) })
    val trip by vm.trip.collectAsStateWithLifecycle()

    var showGhost by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var promoteFor by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(title = "Members", onBack = onBack) { pad ->
        val t = trip ?: run { CenteredMessage("", loading = true); return@ScreenScaffold }
        val isOwner = t.createdBy == currentUid

        Column(Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(t.members) { m ->
                    MemberCard(
                        member = m,
                        canRemove = isOwner && m.memberId != t.createdBy,
                        onRemove = { m.memberId?.let { vm.remove(it) { e -> error = e } } },
                        onPromote = { if (m.isGhost) promoteFor = m.ghostId },
                        canPromote = isOwner && m.isGhost,
                    )
                }
            }
            Column(Modifier.padding(16.dp)) {
                PrimaryButton("Add ghost member", onClick = { showGhost = true })
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Invite by email", onClick = { showInvite = true })
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Danger) }
            }
        }
    }

    if (showGhost) {
        GhostDialog(
            onDismiss = { showGhost = false },
            onConfirm = { name, cur -> vm.addGhost(name, cur) { error = it }; showGhost = false },
        )
    }
    if (showInvite) {
        EmailDialog(title = "Invite member", onDismiss = { showInvite = false }) { email ->
            vm.invite(email, currentUid) { error = it }; showInvite = false
        }
    }
    promoteFor?.let { ghostId ->
        EmailDialog(title = "Promote to real member", onDismiss = { promoteFor = null }) { email ->
            vm.promote(ghostId, email, currentUid) { error = it }; promoteFor = null
        }
    }
}

@Composable
private fun MemberCard(
    member: TripMember,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onPromote: () -> Unit,
    canPromote: Boolean,
) {
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(member.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "${member.role}  ·  ${Format.currencyFlag(member.homeCurrency)} ${member.homeCurrency}",
                    color = TextSecondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canPromote) TextButton(onClick = onPromote) { Text("Promote") }
                if (canRemove) TextButton(onClick = onRemove) { Text("Remove", color = Danger) }
            }
        }
    }
}

@Composable
private fun GhostDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("USD") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name, currency) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add ghost member") },
        text = {
            Column {
                DField(name, { name = it }, "Name")
                Spacer(Modifier.height(12.dp))
                CurrencyField(currency, { currency = it }, "Home currency")
            }
        },
    )
}

@Composable
private fun EmailDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(enabled = email.isNotBlank(), onClick = { onConfirm(email) }) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = { DField(email, { email = it }, "Email", keyboardType = KeyboardType.Email) },
    )
}
