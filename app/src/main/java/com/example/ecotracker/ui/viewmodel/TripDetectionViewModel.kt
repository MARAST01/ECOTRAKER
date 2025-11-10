package com.example.ecotracker.ui.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecotracker.data.TransportRepository
import com.example.ecotracker.data.model.TransportRecord
import com.example.ecotracker.data.model.TransportType
import com.example.ecotracker.service.TripDetectionReceiver
import com.example.ecotracker.service.TripDetectionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TripDetectionUiState(
    val isTrackingActive: Boolean = false,
    val pendingTrips: List<TransportRecord> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class TripDetectionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TransportRepository()
    private val _uiState = MutableStateFlow(TripDetectionUiState())
    val uiState: StateFlow<TripDetectionUiState> = _uiState.asStateFlow()
    
    private val tripReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TripDetectionReceiver.ACTION_TRIP_DETECTED) {
                val trip = intent.getSerializableExtra(TripDetectionReceiver.EXTRA_TRIP) as? TransportRecord
                trip?.let { onTripDetected(it) }
            }
        }
    }
    
    init {
        // Registrar el receiver para recibir trayectos detectados
        val filter = IntentFilter(TripDetectionReceiver.ACTION_TRIP_DETECTED)
        getApplication<Application>().registerReceiver(tripReceiver, filter)
    }
    
    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(tripReceiver)
        } catch (e: Exception) {
            // Ignorar si ya está desregistrado
        }
    }
    
    fun startTripDetection(context: Context) {
        val intent = Intent(context, TripDetectionService::class.java).apply {
            action = TripDetectionService.ACTION_START_TRACKING
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        
        _uiState.value = _uiState.value.copy(isTrackingActive = true)
    }
    
    fun startTripDetectionAutomatically(context: Context) {
        // Verificar si el servicio ya está corriendo
        if (_uiState.value.isTrackingActive) {
            return
        }
        
        // Iniciar el servicio automáticamente
        startTripDetection(context)
    }
    
    fun stopTripDetection(context: Context) {
        val intent = Intent(context, TripDetectionService::class.java).apply {
            action = TripDetectionService.ACTION_STOP_TRACKING
        }
        context.stopService(intent)
        
        _uiState.value = _uiState.value.copy(isTrackingActive = false)
    }
    
    fun onTripDetected(trip: TransportRecord) {
        val currentPending = _uiState.value.pendingTrips.toMutableList()
        currentPending.add(0, trip) // Agregar al inicio
        _uiState.value = _uiState.value.copy(pendingTrips = currentPending)
    }
    
    fun confirmTrip(
        userId: String,
        trip: TransportRecord,
        transportType: TransportType
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                val confirmedTrip = trip.copy(
                    userId = userId,
                    transportType = transportType,
                    emissionFactor = transportType.emissionFactor,
                    isConfirmed = true,
                    hour = trip.hour ?: java.text.SimpleDateFormat(
                        "HH:mm",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date(trip.startTime ?: System.currentTimeMillis()))
                )
                
                repository.saveAutoDetectedTrip(
                    confirmedTrip,
                    onSuccess = {
                        // Remover de la lista de pendientes
                        val updatedPending = _uiState.value.pendingTrips.filter { it != trip }
                        _uiState.value = _uiState.value.copy(
                            pendingTrips = updatedPending,
                            isLoading = false
                        )
                    },
                    onError = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = error
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al confirmar trayecto: ${e.message}"
                )
            }
        }
    }
    
    fun dismissTrip(trip: TransportRecord) {
        val updatedPending = _uiState.value.pendingTrips.filter { it != trip }
        _uiState.value = _uiState.value.copy(pendingTrips = updatedPending)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

