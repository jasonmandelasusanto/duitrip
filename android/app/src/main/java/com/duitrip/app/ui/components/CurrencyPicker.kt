package com.duitrip.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.duitrip.app.domain.Currencies
import com.duitrip.app.ui.theme.BgSurface
import com.duitrip.app.ui.theme.TextMuted
import com.duitrip.app.ui.theme.TextPrimary
import com.duitrip.app.ui.util.Format

/** A field-styled button that opens a searchable currency list. */
@Composable
fun CurrencyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Currency",
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val display = if (value.isBlank()) "Select…" else "${Format.currencyFlag(value)} $value".trim()

    OutlinedCard(
        onClick = { open = true },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BgSurface),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, color = TextMuted)
            Text(display, color = if (value.isBlank()) TextMuted else TextPrimary)
        }
    }

    if (open) {
        CurrencyPickerDialog(
            onPick = { onValueChange(it); open = false },
            onDismiss = { open = false },
        )
    }
}

@Composable
fun CurrencyPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) Currencies.COMMON + Currencies.ALL.filter { it !in Currencies.COMMON }
        else Currencies.ALL.filter { it.contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Select currency") },
        text = {
            Column {
                DField(value = query, onValueChange = { query = it }, label = "Search", keyboardType = KeyboardType.Text)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(filtered) { code ->
                        Text(
                            "${Format.currencyFlag(code)}  $code",
                            color = TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(code) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
    )
}
