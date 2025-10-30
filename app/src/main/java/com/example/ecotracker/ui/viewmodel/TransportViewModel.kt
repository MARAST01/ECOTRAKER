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
    val distance: String = "",
    val todayRecord: TransportRecord? = null,
    val todayRecords: List<TransportRecord> = emptyList(),
    val allRecords: List<TransportRecord> = emptyList(),
    val paginatedRecords: List<TransportRecord> = emptyList(),
    val currentDaysBack: Int = 15,
    val isLoadingMore: Boolean = false,
    val showConfirmation: Boolean = false,
    val showSuccessSnackbar: Boolean = false,
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

    fun updateDistance(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*\$"))) {
            _uiState.value = _uiState.value.copy(distance = value)
        }
    }

    fun saveTransportRecord(userId: String) {
        val currentState = _uiState.value
        val selectedTransport = currentState.selectedTransport
        val selectedTime = currentState.selectedTime
        val distanceText = currentState.distance

        if (selectedTransport == null) {
            _uiState.value = currentState.copy(errorMessage = "Selecciona un medio de transporte.")
            return
        }

        if (selectedTime.isEmpty()) {
            _uiState.value = currentState.copy(errorMessage = "Selecciona una hora válida.")
            return
        }

        val distanceKm = distanceText.toDoubleOrNull()
        if (distanceKm == null || distanceKm <= 0.0) {
            _uiState.value = currentState.copy(errorMessage = "Ingresa una distancia válida mayor a 0.")
            return
        }

        _uiState.value = currentState.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            repository.saveTransportRecord(
                userId = userId,
                transportType = selectedTransport,
                hour = selectedTime,
                distance = distanceKm,
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        showSuccessSnackbar = true,
                        successMessage = "✓ Registro guardado exitosamente",
                        selectedTime = "",
                        distance = ""
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
                val allRecords = repository.getAllUserTransportRecords(userId)
                val todayRecord = repository.getTodayTransportRecord(userId)
                val todayRecords = repository.getTodayTransportRecords(userId)

                _uiState.value = _uiState.value.copy(
                    todayRecord = todayRecord,
                    todayRecords = todayRecords,
                    allRecords = allRecords
                )
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
        val newDaysBack = _uiState.value.currentDaysBack + 15
        loadPaginatedRecords(userId, newDaysBack)
    }
}
