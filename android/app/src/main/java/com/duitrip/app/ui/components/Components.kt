package com.duitrip.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.duitrip.app.ui.theme.BgBase
import com.duitrip.app.ui.theme.BgBorder
import com.duitrip.app.ui.theme.BgElevated
import com.duitrip.app.ui.theme.BgSurface
import com.duitrip.app.ui.theme.Teal
import com.duitrip.app.ui.theme.TextMuted
import com.duitrip.app.ui.theme.TextSecondary

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = BgBase),
    ) {
        if (loading) {
            CircularProgressIndicator(color = BgBase, strokeWidth = 2.dp, modifier = Modifier.padding(2.dp))
        } else {
            Text(text)
        }
    }
}

@Composable
fun DField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformationCompat else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Teal,
            unfocusedBorderColor = BgBorder,
            focusedContainerColor = BgSurface,
            unfocusedContainerColor = BgSurface,
            focusedLabelColor = Teal,
            unfocusedLabelColor = TextMuted,
        ),
    )
}

private val PasswordVisualTransformationCompat =
    androidx.compose.ui.text.input.PasswordVisualTransformation()

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = BgElevated)
    val shape = RoundedCornerShape(16.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}

/** A field-styled, clickable card showing a label + value (for dropdown triggers). */
@Composable
fun FieldCard(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BgSurface),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, color = TextMuted)
            Text(value, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun CenteredMessage(text: String, loading: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator(color = Teal)
        } else {
            Text(text, color = TextSecondary)
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = TextSecondary)
        Text(subtitle, color = TextMuted)
    }
}
