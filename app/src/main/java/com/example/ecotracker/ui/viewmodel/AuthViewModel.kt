package com.example.ecotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthState(val isSignedIn: Boolean = false)

class AuthViewModel(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) : ViewModel() {
    private val _state = MutableStateFlow(AuthState(isSignedIn = auth.currentUser != null))
    val state: StateFlow<AuthState> = _state

    fun refresh() {
        _state.value = AuthState(isSignedIn = auth.currentUser != null)
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            _state.value = AuthState(isSignedIn = false)
        }
    }
}
