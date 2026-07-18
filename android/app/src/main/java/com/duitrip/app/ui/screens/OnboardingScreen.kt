package com.duitrip.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.duitrip.app.ui.LocalContainer
import com.duitrip.app.ui.LocalCurrentUser
import com.duitrip.app.ui.components.CurrencyField
import com.duitrip.app.ui.components.PrimaryButton
import com.duitrip.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen() {
    val container = LocalContainer.current
    val user = LocalCurrentUser.current
    val scope = rememberCoroutineScope()
    var currency by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Welcome! 👋", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick your home currency — we'll convert every expense back to it.",
            color = TextSecondary,
        )
        Spacer(Modifier.height(24.dp))
        CurrencyField(value = currency, onValueChange = { currency = it }, label = "Home currency")
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = "Get started",
            enabled = currency.isNotBlank() && user != null,
            loading = saving,
            onClick = {
                val uid = user?.uid ?: return@PrimaryButton
                scope.launch {
                    saving = true
                    try {
                        container.userRepository.setHomeCurrency(uid, currency)
                    } finally {
                        saving = false
                    }
                }
            },
        )
    }
}
