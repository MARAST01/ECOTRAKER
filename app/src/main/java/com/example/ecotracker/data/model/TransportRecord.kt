package com.example.ecotracker.data.model

import androidx.annotation.Keep

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
    var createdAt: Long? = null
) {
    constructor() : this(null, null, null, null, null, null, null)
}

enum class TransportType(val displayName: String, val icon: String, val emissionFactor: Double) {
    CAR("Auto", "🚗", 180.0),
    MOTORCYCLE("Moto", "🏍️", 90.0),
    BUS("Bus", "🚌", 80.0),
    BICYCLE("Bicicleta", "🚲", 0.0),
    WALKING("Caminar", "🚶", 0.0)
}
