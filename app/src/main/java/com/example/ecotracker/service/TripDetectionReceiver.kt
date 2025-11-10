package com.example.ecotracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ecotracker.data.model.TransportRecord

class TripDetectionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TRIP_DETECTED = "com.example.ecotracker.TRIP_DETECTED"
        const val EXTRA_TRIP = "trip"
    }
    
    var onTripDetected: ((TransportRecord) -> Unit)? = null
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_TRIP_DETECTED) {
            val trip = intent.getSerializableExtra(EXTRA_TRIP) as? TransportRecord
            trip?.let { onTripDetected?.invoke(it) }
        }
    }
}

