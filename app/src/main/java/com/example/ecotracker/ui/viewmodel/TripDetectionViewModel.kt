package com.example.ecotracker.ui.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
    val errorMessage: String? = null,
    val currentSpeed: Float = 0f, // en m/s
    val isTracking: Boolean = false
)

class TripDetectionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TransportRepository()
    private val _uiState = MutableStateFlow(TripDetectionUiState())
    val uiState: StateFlow<TripDetectionUiState> = _uiState.asStateFlow()
    
    private val tripReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                TripDetectionReceiver.ACTION_TRIP_DETECTED -> {
                    android.util.Log.d("TripDetectionViewModel", "📨 Broadcast recibido: ACTION_TRIP_DETECTED")
                    val trip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getSerializableExtra(TripDetectionReceiver.EXTRA_TRIP, TransportRecord::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getSerializableExtra(TripDetectionReceiver.EXTRA_TRIP) as? TransportRecord
                    }
                    if (trip != null) {
                        android.util.Log.d("TripDetectionViewModel", "✅ Trayecto deserializado correctamente")
                        onTripDetected(trip)
                    } else {
                        android.util.Log.e("TripDetectionViewModel", "❌ Error: Trayecto es null después de deserializar")
                    }
                }
                TripDetectionReceiver.ACTION_SPEED_UPDATE -> {
                    val speed = intent.getFloatExtra(TripDetectionReceiver.EXTRA_SPEED, 0f)
                    val isTracking = intent.getBooleanExtra(TripDetectionReceiver.EXTRA_IS_TRACKING, false)
                    _uiState.value = _uiState.value.copy(
                        currentSpeed = speed,
                        isTracking = isTracking
                    )
                }
            }
        }
    }
    
    init {
        // Registrar el receiver para recibir trayectos detectados y actualizaciones de velocidad
        val filter = IntentFilter().apply {
            addAction(TripDetectionReceiver.ACTION_TRIP_DETECTED)
            addAction(TripDetectionReceiver.ACTION_SPEED_UPDATE)
            priority = 1000 // Alta prioridad
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(
                tripReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            getApplication<Application>().registerReceiver(tripReceiver, filter)
        }
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
        android.util.Log.d("TripDetectionViewModel", "🚀 startTripDetection llamado")
        val intent = Intent(context, TripDetectionService::class.java).apply {
            action = TripDetectionService.ACTION_START_TRACKING
        }
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.util.Log.d("TripDetectionViewModel", "📱 Iniciando foreground service (Android O+)")
                context.startForegroundService(intent)
            } else {
                android.util.Log.d("TripDetectionViewModel", "📱 Iniciando servicio normal")
                context.startService(intent)
            }
            android.util.Log.d("TripDetectionViewModel", "✅ Servicio iniciado correctamente")
        } catch (e: Exception) {
            android.util.Log.e("TripDetectionViewModel", "❌ Error al iniciar servicio: ${e.message}", e)
        }
        
        _uiState.value = _uiState.value.copy(isTrackingActive = true)
    }
    
    fun startTripDetectionAutomatically(context: Context) {
        android.util.Log.d("TripDetectionViewModel", "🔄 startTripDetectionAutomatically llamado - isTrackingActive: ${_uiState.value.isTrackingActive}")
        // Verificar si el servicio ya está corriendo
        if (_uiState.value.isTrackingActive) {
            android.util.Log.d("TripDetectionViewModel", "⏭️ Servicio ya está activo, saltando inicio")
            return
        }
        
        // Iniciar el servicio automáticamente
        android.util.Log.d("TripDetectionViewModel", "▶️ Iniciando servicio automáticamente")
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
        android.util.Log.d("TripDetectionViewModel", "📥 Trayecto recibido - Distancia: ${trip.distance} km, Puntos: ${trip.routePoints?.size ?: 0}")
        val currentPending = _uiState.value.pendingTrips.toMutableList()
        currentPending.add(0, trip) // Agregar al inicio
        _uiState.value = _uiState.value.copy(pendingTrips = currentPending)
        android.util.Log.d("TripDetectionViewModel", "✅ Trayecto agregado a pendientes. Total pendientes: ${currentPending.size}")
    }
    
    fun confirmTrip(
        userId: String,
        trip: TransportRecord,
        transportType: TransportType
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                android.util.Log.d("TripDetectionViewModel", "✅ Confirmando trayecto - Distancia: ${trip.distance} km, Tipo: ${transportType.displayName}")
                
                // Asegurar que la fecha esté correcta (usar la fecha del startTime del trayecto)
                val tripDate = trip.date ?: java.text.SimpleDateFormat(
                    "yyyy-MM-dd",
                    java.util.Locale.getDefault()
                ).format(java.util.Date(trip.startTime ?: System.currentTimeMillis()))
                
                val confirmedTrip = trip.copy(
                    userId = userId,
                    transportType = transportType,
                    emissionFactor = transportType.emissionFactor,
                    isConfirmed = true,
                    date = tripDate, // Asegurar que la fecha esté presente
                    hour = trip.hour ?: java.text.SimpleDateFormat(
                        "HH:mm",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date(trip.startTime ?: System.currentTimeMillis()))
                )
                
                android.util.Log.d("TripDetectionViewModel", "📤 Guardando trayecto confirmado - Fecha: $tripDate, UserId: $userId")
                
                repository.saveAutoDetectedTrip(
                    confirmedTrip,
                    onSuccess = {
                        android.util.Log.d("TripDetectionViewModel", "✅ Trayecto guardado exitosamente en Firestore")
                        // Remover de la lista de pendientes
                        val updatedPending = _uiState.value.pendingTrips.filter { it != trip }
                        _uiState.value = _uiState.value.copy(
                            pendingTrips = updatedPending,
                            isLoading = false
                        )
                    },
                    onError = { error ->
                        android.util.Log.e("TripDetectionViewModel", "❌ Error al guardar trayecto: $error")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = error
                        )
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("TripDetectionViewModel", "❌ Excepción al confirmar trayecto: ${e.message}", e)
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

