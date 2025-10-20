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
    val selectedTime: String = "",
    val todayRecord: TransportRecord? = null,
    val todayRecords: List<TransportRecord> = emptyList(),
    val allRecords: List<TransportRecord> = emptyList(), // Para debug
    val paginatedRecords: List<TransportRecord> = emptyList(), // Registros paginados
    val currentDaysBack: Int = 15, // Días hacia atrás actuales
    val isLoadingMore: Boolean = false, // Para cargar más registros
    val showConfirmation: Boolean = false,
    val showSuccessSnackbar: Boolean = false, // Para mensaje flotante de éxito
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
    
    fun selectTime(time: String) {
        _uiState.value = _uiState.value.copy(selectedTime = time)
    }
    
    fun saveTransportRecord(userId: String) {
        val selectedTransport = _uiState.value.selectedTransport ?: return
        val selectedTime = _uiState.value.selectedTime
        
        if (selectedTime.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Por favor selecciona una hora"
            )
            return
        }
        
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        
        viewModelScope.launch {
            repository.saveTransportRecord(
                userId = userId,
                transportType = selectedTransport,
                hour = selectedTime,
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        showSuccessSnackbar = true,
                        successMessage = "✓ Registro guardado exitosamente",
                        selectedTime = "" // Reiniciar la hora seleccionada
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
                println("DEBUG: Cargando registros para userId: $userId")
                
                // Primero intentemos obtener todos los registros para debug
                val allRecords = repository.getAllUserTransportRecords(userId)
                println("DEBUG: Todos los registros obtenidos: ${allRecords.size}")
                
                val todayRecord = repository.getTodayTransportRecord(userId)
                val todayRecords = repository.getTodayTransportRecords(userId)
                
                println("DEBUG: Registros de hoy obtenidos: ${todayRecords.size}")
                
                _uiState.value = _uiState.value.copy(
                    todayRecord = todayRecord,
                    todayRecords = todayRecords,
                    allRecords = allRecords
                )
            } catch (e: Exception) {
                println("DEBUG: Error en loadTodayRecord: ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al cargar el registro del día: ${e.message}"
                )
            }
        }
    }
    
    fun dismissConfirmation() {
        _uiState.value = _uiState.value.copy(showConfirmation = false, successMessage = null)
    }
    
    fun dismissSuccessSnackbar() {
        _uiState.value = _uiState.value.copy(showSuccessSnackbar = false, successMessage = null)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun loadPaginatedRecords(userId: String, daysBack: Int = 15) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
                
                val records = repository.getUserTransportRecordsPaginated(userId, daysBack)
                
                _uiState.value = _uiState.value.copy(
                    paginatedRecords = records,
                    currentDaysBack = daysBack,
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al cargar registros: ${e.message}",
                    isLoadingMore = false
                )
            }
        }
    }
    
    fun loadMoreRecords(userId: String) {
        val currentDaysBack = _uiState.value.currentDaysBack
        val newDaysBack = currentDaysBack + 15 // Cargar 15 días más
        
        loadPaginatedRecords(userId, newDaysBack)
    }
}
