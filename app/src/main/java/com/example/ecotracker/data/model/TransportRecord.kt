package com.example.ecotracker.data.model

import androidx.annotation.Keep
import com.google.firebase.firestore.PropertyName
import java.io.Serializable

@Keep
data class LocationPoint(
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var timestamp: Long = 0L,
    var accuracy: Float? = null,
    var speed: Float? = null
) : Serializable {
    // Constructor sin argumentos requerido por Firestore
    constructor() : this(0.0, 0.0, 0L, null, null)
    
    companion object {
        private const val serialVersionUID = 1L
    }
}

@Keep
data class TransportRecord(
    var id: String? = null,
    var userId: String? = null,
    var transportType: TransportType? = null,
    var date: String? = null, // Formato: YYYY-MM-DD
    var timestamp: Long? = null,
    var hour: String? = null, // Formato: HH:mm
    val distance: Double? = null,
    var emissionFactor: Double? = null,
    var createdAt: Long? = null,
    // Campos para detección automática de trayectos
    // Firestore guarda como "autoDetected" y "confirmed", pero usamos "isAutoDetected" e "isConfirmed" en el código
    @get:PropertyName("autoDetected")
    @set:PropertyName("autoDetected")
    var isAutoDetected: Boolean = false,
    @get:PropertyName("confirmed")
    @set:PropertyName("confirmed")
    var isConfirmed: Boolean = false,
    var startTime: Long? = null,
    var endTime: Long? = null,
    var duration: Long? = null, // Duración en milisegundos
    var averageSpeed: Double? = null, // Velocidad promedio en km/h
    var routePoints: List<LocationPoint>? = null, // Puntos GPS del trayecto
    var startLocation: LocationPoint? = null,
    var endLocation: LocationPoint? = null
) : Serializable {
    constructor() : this(null, null, null, null, null, null, null, null, null, false, false, null, null, null, null, null, null, null)
    
    companion object {
        private const val serialVersionUID = 1L
    }
}

enum class TransportType(val displayName: String, val icon: String, val emissionFactor: Double) {
    CAR("Auto", "🚗", 180.0),
    MOTORCYCLE("Moto", "🏍️", 90.0),
    BUS("Bus", "🚌", 80.0),
    BICYCLE("Bicicleta", "🚲", 0.0),
    WALKING("Caminar", "🚶", 0.0)
}
