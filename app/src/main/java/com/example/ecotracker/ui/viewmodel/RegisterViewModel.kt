package com.example.ecotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecotracker.data.AuthRepository
import com.example.ecotracker.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RegisterUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: UserProfile? = null
)

class RegisterViewModel(private val repo: AuthRepository = AuthRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState

    fun register(fullName: String?, phone: String?, email: String, password: String) {
        _uiState.value = RegisterUiState(isLoading = true)
        viewModelScope.launch {
            val result = repo.registerWithEmail(fullName, phone, email, password)
            _uiState.value = result.fold(
                onSuccess = { RegisterUiState(success = it) },
                onFailure = { RegisterUiState(error = it.message ?: "Error registrando usuario") }
            )
        }
    }

    fun registerWithGoogle(idToken: String) {
        _uiState.value = RegisterUiState(isLoading = true)
        viewModelScope.launch {
            val result = repo.signInWithGoogleIdToken(idToken)
            _uiState.value = result.fold(
                onSuccess = { RegisterUiState(success = it) },
                onFailure = { RegisterUiState(error = it.message ?: "Error registrando con Google") }
            )
        }
    }
}
