package com.duitrip.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duitrip.app.R
import com.duitrip.app.data.GithubRelease
import com.duitrip.app.data.TripRepository
import com.duitrip.app.data.model.Trip
import com.duitrip.app.data.model.User
import kotlinx.coroutines.launch
import com.duitrip.app.ui.LocalContainer
import com.duitrip.app.ui.LocalCurrentUser
import com.duitrip.app.ui.VMFactory
import com.duitrip.app.ui.components.CenteredMessage
import com.duitrip.app.ui.components.EmptyState
import com.duitrip.app.ui.components.SurfaceCard
import com.duitrip.app.ui.theme.BgBase
import com.duitrip.app.ui.theme.Danger
import com.duitrip.app.ui.theme.Teal
import com.duitrip.app.ui.theme.TextPrimary
import com.duitrip.app.ui.theme.TextSecondary
import com.duitrip.app.ui.util.Format
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

class DashboardViewModel(repo: TripRepository, user: User) : ViewModel() {
    val trips: StateFlow<List<Trip>?> =
        repo.observeUserTrips(user.uid).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Self-heal old-backend trips missing the memberUids field so they show up.
        viewModelScope.launch {
            try { repo.backfillMyTrips(user) } catch (_: Exception) { /* ignore */ }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenTrip: (String) -> Unit,
    onNewTrip: () -> Unit,
    onProfile: () -> Unit,
) {
    val container = LocalContainer.current
    val user = LocalCurrentUser.current ?: return
    val vm: DashboardViewModel = viewModel(factory = VMFactory { DashboardViewModel(container.tripRepository, user) })
    val trips by vm.trips.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val firstName = user.displayName.trim().substringBefore(" ").ifBlank { null }

    var availableUpdate by remember { mutableStateOf<GithubRelease?>(null) }
    var updating by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        availableUpdate = container.updateRepository.checkForUpdate()
    }

    Scaffold(
        containerColor = BgBase,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.duitrip_logo),
                            contentDescription = "Duitrip",
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Duitrip", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgBase, titleContentColor = TextPrimary),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewTrip, containerColor = Teal, contentColor = BgBase) {
                Icon(Icons.Default.Add, contentDescription = "New trip")
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    if (firstName != null) "Hello, $firstName," else "Hello,",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text("Where are you planning to go?", color = TextSecondary)
            }
            when (val list = trips) {
                null -> CenteredMessage("", loading = true)
                else -> if (list.isEmpty()) {
                    EmptyState("No trips yet", "Tap + to start your first trip.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(list) { trip -> TripCard(trip, onClick = { onOpenTrip(trip.tripId) }) }
                    }
                }
            }
        }
    }

    val update = availableUpdate
    if (update != null) {
        AlertDialog(
            onDismissRequest = { if (!updating) availableUpdate = null },
            title = { Text("Update available") },
            text = {
                Column {
                    Text("Version ${update.version} is ready to install.")
                    updateError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Danger)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !updating,
                    onClick = {
                        scope.launch {
                            updating = true
                            updateError = null
                            try {
                                val file = container.updateRepository.downloadApk(update)
                                container.updateRepository.installApk(file)
                                availableUpdate = null
                            } catch (e: Exception) {
                                updateError = e.localizedMessage ?: "Download failed"
                            } finally {
                                updating = false
                            }
                        }
                    },
                ) { Text(if (updating) "Downloading…" else "Update now") }
            },
            dismissButton = {
                TextButton(enabled = !updating, onClick = { availableUpdate = null }) { Text("Later") }
            },
        )
    }
}

@Composable
private fun TripCard(trip: Trip, onClick: () -> Unit) {
    SurfaceCard(onClick = onClick) {
        Text("${Format.currencyFlag(trip.destinationCurrency)}  ${trip.name}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(trip.destination, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        val memberCount = trip.members.size
        Text(
            "${Format.dateRange(trip.startDate, trip.endDate)}  ·  $memberCount ${if (memberCount == 1) "member" else "members"}  ·  ${Format.tripDays(trip.startDate, trip.endDate)} days",
            color = TextSecondary,
            fontSize = 13.sp,
        )
    }
}
