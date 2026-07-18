package com.duitrip.app.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duitrip.app.R
import com.duitrip.app.data.AuthRepository
import com.duitrip.app.ui.LocalContainer
import com.duitrip.app.ui.VMFactory
import com.duitrip.app.ui.components.DField
import com.duitrip.app.ui.components.PrimaryButton
import com.duitrip.app.ui.theme.Danger
import com.duitrip.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    fun signIn(email: String, password: String) = attempt {
        auth.signInWithEmail(email, password)
    }

    fun register(email: String, password: String) = attempt {
        auth.registerWithEmail(email, password)
    }

    fun google(context: Context, webClientId: String) = attempt {
        auth.signInWithGoogle(context, webClientId)
    }

    // On success the AuthStateListener in RootViewModel re-gates and navigates for us.
    private fun attempt(block: suspend () -> Unit) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                block()
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Something went wrong"
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun LandingScreen() {
    val container = LocalContainer.current
    val vm: AuthViewModel = viewModel(factory = VMFactory { AuthViewModel(container.authRepository) })
    val context = LocalContext.current
    val webClientId = stringResource(R.string.default_web_client_id)

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.duitrip_logo),
            contentDescription = "Duitrip",
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Duitrip", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Split trip expenses, effortlessly.", color = TextSecondary)
        Spacer(Modifier.height(32.dp))

        DField(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        DField(password, { password = it }, "Password", isPassword = true)
        Spacer(Modifier.height(20.dp))

        PrimaryButton(
            text = if (isRegister) "Create account" else "Sign in",
            onClick = { if (isRegister) vm.register(email, password) else vm.signIn(email, password) },
            enabled = email.isNotBlank() && password.isNotBlank(),
            loading = vm.loading,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { vm.google(context, webClientId) }) {
            Text("Continue with Google")
        }

        vm.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Danger)
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { isRegister = !isRegister }) {
            Text(
                if (isRegister) "Have an account? Sign in" else "New here? Create an account",
                color = TextSecondary,
            )
        }
    }
}
