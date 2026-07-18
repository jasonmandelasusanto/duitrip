package com.duitrip.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duitrip.app.domain.CountryCurrency
import com.duitrip.app.ui.LocalContainer
import com.duitrip.app.ui.LocalCurrentUser
import com.duitrip.app.ui.components.CurrencyField
import com.duitrip.app.ui.components.DField
import com.duitrip.app.ui.components.PrimaryButton
import com.duitrip.app.ui.components.ScreenScaffold
import com.duitrip.app.ui.theme.Danger
import com.duitrip.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun NewTripScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val container = LocalContainer.current
    val user = LocalCurrentUser.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Auto-resolve currency from destination unless the user overrode it.
    var currencyTouched by remember { mutableStateOf(false) }
    LaunchedEffect(destination) {
        if (!currencyTouched && destination.isNotBlank()) currency = CountryCurrency.resolveCurrency(destination)
    }

    ScreenScaffold(title = "New trip", onBack = onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            DField(name, { name = it }, "Trip name")
            Spacer(Modifier.height(12.dp))
            DField(destination, { destination = it }, "Destination (e.g. Tokyo, Japan)")
            Spacer(Modifier.height(12.dp))
            DField(startDate, { startDate = it }, "Start date (YYYY-MM-DD)")
            Spacer(Modifier.height(12.dp))
            DField(endDate, { endDate = it }, "End date (YYYY-MM-DD)")
            Spacer(Modifier.height(12.dp))
            CurrencyField(currency, { currency = it; currencyTouched = true }, "Destination currency")
            Spacer(Modifier.height(4.dp))
            Text("Auto-detected from destination; tap to change.", color = TextSecondary)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Create trip",
                enabled = name.isNotBlank() && destination.isNotBlank() && startDate.isNotBlank() && endDate.isNotBlank() && user != null,
                loading = saving,
                onClick = {
                    val u = user ?: return@PrimaryButton
                    scope.launch {
                        saving = true; error = null
                        try {
                            val id = container.tripRepository.createTrip(u, name, destination, currency, startDate, endDate)
                            onCreated(id)
                        } catch (e: Exception) {
                            error = e.localizedMessage ?: "Could not create trip"
                        } finally {
                            saving = false
                        }
                    }
                },
            )
            error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = Danger) }
        }
    }
}
