package com.example.ecotracker.data.model

import androidx.annotation.Keep

@Keep
data class TransportRecord(
    var id: String? = null,
    var userId: String? = null,
    var transportType: TransportType? = null,
    var date: String? = null, // Formato: YYYY-MM-DD
    var timestamp: Long? = null,
    var createdAt: Long? = null
) {
    constructor() : this(null, null, null, null, null, null)
}

enum class TransportType(val displayName: String, val icon: String) {
    CAR("Auto", "🚗"),
    MOTORCYCLE("Moto", "🏍️"),
    BUS("Bus", "🚌"),
    BICYCLE("Bicicleta", "🚲"),
    WALKING("Caminar", "🚶")
}
