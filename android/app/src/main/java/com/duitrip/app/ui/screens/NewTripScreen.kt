package com.duitrip.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.duitrip.app.ui.components.SurfaceCard
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

    // Destination autocomplete via Nominatim (parity with the PWA), debounced.
    var suggestions by remember { mutableStateOf<List<com.duitrip.app.data.PlaceSuggestion>>(emptyList()) }
    var suppressSuggestions by remember { mutableStateOf(false) }
    LaunchedEffect(destination) {
        if (suppressSuggestions || destination.length < 3) { suggestions = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(400)
        suggestions = com.duitrip.app.data.NominatimClient.search(destination)
    }

    ScreenScaffold(title = "New trip", onBack = onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            DField(name, { name = it }, "Trip name")
            Spacer(Modifier.height(12.dp))
            DField(destination, { destination = it; suppressSuggestions = false }, "Destination (e.g. Tokyo, Japan)")
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                SurfaceCard {
                    suggestions.forEach { s ->
                        Text(
                            s.displayName,
                            color = TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    suppressSuggestions = true
                                    destination = s.displayName.split(",").let {
                                        if (it.size >= 2) "${it.first().trim()}, ${it.last().trim()}" else s.displayName
                                    }
                                    s.countryCode?.let { cc ->
                                        if (!currencyTouched) {
                                            currency = CountryCurrency.countryCodeToCurrency(cc)
                                            // Country code from the picked place is authoritative —
                                            // don't let the name-based heuristic override it.
                                            currencyTouched = true
                                        }
                                    }
                                    suggestions = emptyList()
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
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
