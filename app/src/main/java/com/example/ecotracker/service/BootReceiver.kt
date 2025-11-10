package com.example.ecotracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            // Solo iniciar si el usuario está autenticado
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                val serviceIntent = Intent(context, TripDetectionService::class.java).apply {
                    action = TripDetectionService.ACTION_START_TRACKING
                }
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}

