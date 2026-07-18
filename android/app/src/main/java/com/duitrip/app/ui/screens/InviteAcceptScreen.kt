package com.duitrip.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duitrip.app.data.model.Trip
import com.duitrip.app.ui.LocalContainer
import com.duitrip.app.ui.LocalCurrentUser
import com.duitrip.app.ui.components.CenteredMessage
import com.duitrip.app.ui.components.PrimaryButton
import com.duitrip.app.ui.components.ScreenScaffold
import com.duitrip.app.ui.theme.Danger
import com.duitrip.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun InviteAcceptScreen(tripId: String, onBack: () -> Unit, onAccepted: (String) -> Unit) {
    val container = LocalContainer.current
    val user = LocalCurrentUser.current
    val scope = rememberCoroutineScope()

    var trip by remember { mutableStateOf<Trip?>(null) }
    var loading by remember { mutableStateOf(true) }
    var accepting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tripId) {
        trip = container.tripRepository.getTrip(tripId)
        loading = false
    }

    ScreenScaffold(title = "Trip invite", onBack = onBack) { pad ->
        if (loading) { CenteredMessage("", loading = true); return@ScreenScaffold }
        val t = trip
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (t == null) {
                Text("This invite is no longer available.", color = TextSecondary)
                return@Column
            }
            Text(t.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(t.destination, color = TextSecondary)
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = "Join this trip",
                enabled = user != null,
                loading = accepting,
                onClick = {
                    val u = user ?: return@PrimaryButton
                    scope.launch {
                        accepting = true; error = null
                        try {
                            container.tripRepository.acceptInvite(tripId, u)
                            onAccepted(tripId)
                        } catch (e: Exception) {
                            error = e.localizedMessage ?: "Could not accept invite"
                        } finally {
                            accepting = false
                        }
                    }
                },
            )
            error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = Danger) }
        }
    }
}
