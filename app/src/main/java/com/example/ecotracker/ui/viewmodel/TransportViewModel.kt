package com.example.ecotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecotracker.data.TransportRepository
import com.example.ecotracker.data.model.TransportRecord
import com.example.ecotracker.data.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TransportUiState(
    val isLoading: Boolean = false,
    val selectedTransport: TransportType? = null,
    val todayRecord: TransportRecord? = null,
    val showConfirmation: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class TransportViewModel : ViewModel() {
    private val repository = TransportRepository()
    
    private val _uiState = MutableStateFlow(TransportUiState())
    val uiState: StateFlow<TransportUiState> = _uiState.asStateFlow()
    
    fun selectTransport(transportType: TransportType) {
        _uiState.value = _uiState.value.copy(selectedTransport = transportType)
    }
    
    fun saveTransportRecord(userId: String) {
        val selectedTransport = _uiState.value.selectedTransport ?: return
        
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        
        viewModelScope.launch {
            repository.saveTransportRecord(
                userId = userId,
                transportType = selectedTransport,
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        showConfirmation = true,
                        successMessage = "¡Registro guardado exitosamente!"
                    )
                    loadTodayRecord(userId)
                },
                onError = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                }
            )
        }
    }
    
    fun loadTodayRecord(userId: String) {
        viewModelScope.launch {
            try {
                val todayRecord = repository.getTodayTransportRecord(userId)
                _uiState.value = _uiState.value.copy(todayRecord = todayRecord)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al cargar el registro del día: ${e.message}"
                )
            }
        }
    }
    
    fun dismissConfirmation() {
        _uiState.value = _uiState.value.copy(showConfirmation = false, successMessage = null)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
