package com.example.ecotracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.ecotracker.data.model.TransportRecord

class TripDetectionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TRIP_DETECTED = "com.example.ecotracker.TRIP_DETECTED"
        const val ACTION_SPEED_UPDATE = "com.example.ecotracker.SPEED_UPDATE"
        const val EXTRA_TRIP = "trip"
        const val EXTRA_SPEED = "speed" // en m/s
        const val EXTRA_IS_TRACKING = "is_tracking"
    }
    
    var onTripDetected: ((TransportRecord) -> Unit)? = null
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_TRIP_DETECTED) {
            val trip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(EXTRA_TRIP, TransportRecord::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra(EXTRA_TRIP) as? TransportRecord
            }
            trip?.let { onTripDetected?.invoke(it) }
        }
    }
}

