package com.example.ecotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecotracker.data.AuthRepository
import com.example.ecotracker.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: UserProfile? = null
)

class LoginViewModel(private val repo: AuthRepository = AuthRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun loginEmail(email: String, password: String) {
        _uiState.value = LoginUiState(isLoading = true)
        viewModelScope.launch {
            val result = repo.loginWithEmail(email, password)
            _uiState.value = result.fold(
                onSuccess = { LoginUiState(success = it) },
                onFailure = { LoginUiState(error = it.message ?: "Error iniciando sesión") }
            )
        }
    }

    fun loginGoogle(idToken: String) {
        _uiState.value = LoginUiState(isLoading = true)
        viewModelScope.launch {
            val result = repo.signInWithGoogleIdToken(idToken)
            _uiState.value = result.fold(
                onSuccess = { LoginUiState(success = it) },
                onFailure = { LoginUiState(error = it.message ?: "Error con Google Sign-In") }
            )
        }
    }
}
