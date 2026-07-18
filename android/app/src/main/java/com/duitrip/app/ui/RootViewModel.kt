package com.duitrip.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duitrip.app.data.AuthRepository
import com.duitrip.app.data.UserRepository
import com.duitrip.app.data.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Top-level auth/onboarding gate state. */
sealed interface AuthGate {
    data object Loading : AuthGate
    data object SignedOut : AuthGate
    data class NeedsOnboarding(val user: User) : AuthGate
    data class Ready(val user: User) : AuthGate
}

class RootViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AuthGate> =
        authRepository.authState()
            .onEach { fbUser -> if (fbUser != null) userRepository.ensureUserDoc(fbUser) }
            .flatMapLatest { fbUser ->
                if (fbUser == null) flowOf<AuthGate>(AuthGate.SignedOut)
                else userRepository.userFlow(fbUser.uid).map { user ->
                    when {
                        user == null -> AuthGate.Loading
                        user.homeCurrency.isBlank() -> AuthGate.NeedsOnboarding(user)
                        else -> AuthGate.Ready(user)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthGate.Loading)

    fun signOut() = authRepository.signOut()
}
