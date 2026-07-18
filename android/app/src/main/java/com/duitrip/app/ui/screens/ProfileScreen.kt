package com.duitrip.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
fun ProfileScreen(onBack: () -> Unit, onSignOut: () -> Unit) {
    val container = LocalContainer.current
    val user = LocalCurrentUser.current
    val scope = rememberCoroutineScope()

    var displayName by remember(user) { mutableStateOf(user?.displayName ?: "") }
    var currency by remember(user) { mutableStateOf(user?.homeCurrency ?: "") }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Profile", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Text(user?.email ?: "", color = TextSecondary)
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
            OutlinedButton(onClick = onSignOut) { Text("Sign out", color = Danger) }
        }
    }
}
