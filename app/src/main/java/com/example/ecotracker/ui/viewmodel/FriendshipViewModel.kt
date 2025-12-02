package com.example.ecotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecotracker.data.FriendshipRepository
import com.example.ecotracker.data.model.FriendshipRequest
import com.example.ecotracker.data.model.FriendshipStatus
import com.example.ecotracker.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FriendshipUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val pendingRequests: List<FriendshipRequest> = emptyList(),
    val sentRequests: List<FriendshipRequest> = emptyList(),
    val acceptedFriendships: List<FriendshipRequest> = emptyList(),
    val searchResults: List<UserProfile> = emptyList(),
    val searchQuery: String = "",
    val selectedTab: FriendshipTab = FriendshipTab.PENDING
)

enum class FriendshipTab {
    PENDING,    // Solicitudes pendientes recibidas
    SENT,       // Solicitudes enviadas
    FRIENDS     // Amistades aceptadas
}

class FriendshipViewModel(
    private val repository: FriendshipRepository = FriendshipRepository()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(FriendshipUiState())
    val uiState: StateFlow<FriendshipUiState> = _uiState.asStateFlow()
    
    /**
     * Carga todas las solicitudes y amistades del usuario actual
     */
    fun loadFriendships(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val pending = repository.getPendingRequests(userId)
                val sent = repository.getSentRequests(userId)
                val accepted = repository.getAcceptedFriendships(userId)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pendingRequests = pending,
                    sentRequests = sent,
                    acceptedFriendships = accepted
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar amistades: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Envía una solicitud de amistad
     */
    fun sendFriendshipRequest(requesterId: String, receiverId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = repository.sendFriendshipRequest(requesterId, receiverId)
                result.fold(
                    onSuccess = {
                        // Recargar solicitudes
                        loadFriendships(requesterId)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Error al enviar solicitud"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al enviar solicitud: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Acepta una solicitud de amistad
     */
    fun acceptFriendshipRequest(friendshipId: String, userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = repository.acceptFriendshipRequest(friendshipId)
                result.fold(
                    onSuccess = {
                        // Recargar amistades
                        loadFriendships(userId)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Error al aceptar solicitud"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al aceptar solicitud: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Rechaza una solicitud de amistad
     */
    fun rejectFriendshipRequest(friendshipId: String, userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = repository.rejectFriendshipRequest(friendshipId)
                result.fold(
                    onSuccess = {
                        // Recargar solicitudes
                        loadFriendships(userId)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Error al rechazar solicitud"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al rechazar solicitud: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Busca usuarios
     */
    fun searchUsers(query: String, currentUserId: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _uiState.value = _uiState.value.copy(searchResults = emptyList(), searchQuery = "")
                return@launch
            }
            
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, searchQuery = query)
            try {
                val results = repository.searchUsers(query, currentUserId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    searchResults = results
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al buscar usuarios: ${e.message}",
                    searchResults = emptyList()
                )
            }
        }
    }
    
    /**
     * Consulta el estado de amistad entre dos usuarios
     */
    suspend fun getFriendshipStatus(userId1: String, userId2: String): FriendshipRequest? {
        return try {
            repository.getFriendshipStatus(userId1, userId2)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Obtiene el perfil de un usuario
     */
    suspend fun getUserProfile(userId: String): UserProfile? {
        return try {
            repository.getUserProfile(userId)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Cambia la pestaña seleccionada
     */
    fun selectTab(tab: FriendshipTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }
    
    /**
     * Limpia el error
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

