package com.example.ecotracker.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.ecotracker.MainActivity
import com.example.ecotracker.R
import com.example.ecotracker.data.model.LocationPoint
import com.example.ecotracker.data.model.TransportRecord
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlin.math.*

class TripDetectionService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Estado del trayecto actual
    private var currentTrip: MutableList<LocationPoint> = mutableListOf()
    private var tripStartTime: Long? = null
    private var lastLocation: Location? = null
    private var isTracking = false
    private var stationaryStartTime: Long? = null
    private val STATIONARY_THRESHOLD_MS = 30000L // 30 segundos sin movimiento
    private val MIN_DISTANCE_METERS = 50.0 // Mínimo 50 metros para considerar un trayecto
    private val MOVEMENT_SPEED_THRESHOLD = 1.0 // m/s (3.6 km/h) - velocidad mínima para considerar movimiento
    
    // Callback para notificar cuando se detecta un trayecto
    var onTripDetected: ((TransportRecord) -> Unit)? = null
    
    private fun notifyTripDetected(trip: TransportRecord) {
        // Notificar mediante callback si está disponible
        onTripDetected?.invoke(trip)
        
        // También enviar broadcast
        val intent = android.content.Intent(TripDetectionReceiver.ACTION_TRIP_DETECTED).apply {
            putExtra(TripDetectionReceiver.EXTRA_TRIP, trip)
        }
        sendBroadcast(intent)
    }
    
    companion object {
        private const val CHANNEL_ID = "TripDetectionChannel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START_TRACKING = "com.example.ecotracker.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.ecotracker.STOP_TRACKING"
    }
    
    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        setupLocationRequest()
        acquireWakeLock()
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "EcoTracker::TripDetectionWakeLock"
        ).apply {
            acquire(10 * 60 * 60 * 1000L) // 10 horas máximo
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Detección de Trayectos",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación para el seguimiento de trayectos en segundo plano"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun setupLocationRequest() {
        // Request de alta frecuencia cuando hay movimiento
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L // 5 segundos
        ).apply {
            setMinUpdateIntervalMillis(2000L) // Mínimo 2 segundos
            setMaxUpdateDelayMillis(10000L) // Máximo 10 segundos
            setWaitForAccurateLocation(false)
        }.build()
        
        // Request de baja frecuencia cuando está quieto
        val lowFrequencyRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            30000L // 30 segundos
        ).apply {
            setMinUpdateIntervalMillis(15000L) // Mínimo 15 segundos
            setMaxUpdateDelayMillis(60000L) // Máximo 60 segundos
        }.build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    processLocationUpdate(location)
                }
            }
        }
    }
    
    private fun processLocationUpdate(location: Location) {
        serviceScope.launch {
            val currentTime = System.currentTimeMillis()
            
            if (lastLocation == null) {
                // Primera ubicación
                lastLocation = location
                return@launch
            }
            
            val distance = lastLocation!!.distanceTo(location)
            val timeDiff = currentTime - (lastLocation!!.time)
            val speed = if (timeDiff > 0) (distance / timeDiff) * 1000 else 0f // m/s
            
            // Detectar si hay movimiento significativo
            val isMoving = speed >= MOVEMENT_SPEED_THRESHOLD && distance >= 10.0
            
            if (isMoving) {
                // Hay movimiento - activar seguimiento de alta frecuencia
                if (!isTracking) {
                    startTrip(location, currentTime)
                } else {
                    // Continuar el trayecto
                    addLocationPoint(location)
                    stationaryStartTime = null
                }
            } else {
                // No hay movimiento significativo
                if (isTracking) {
                    // Estamos en un trayecto pero ahora estamos quietos
                    if (stationaryStartTime == null) {
                        stationaryStartTime = currentTime
                    } else {
                        // Si llevamos más de STATIONARY_THRESHOLD_MS quietos, finalizar trayecto
                        if (currentTime - stationaryStartTime!! >= STATIONARY_THRESHOLD_MS) {
                            endTrip()
                            // Cambiar a modo de baja frecuencia
                            switchToLowFrequencyTracking()
                        }
                    }
                } else {
                    // No estamos en un trayecto, mantener modo de baja frecuencia
                    stationaryStartTime = currentTime
                }
            }
            
            lastLocation = location
        }
    }
    
    private fun startTrip(location: Location, time: Long) {
        isTracking = true
        tripStartTime = time
        currentTrip.clear()
        stationaryStartTime = null
        
        addLocationPoint(location)
        
        // Cambiar a modo de alta frecuencia
        switchToHighFrequencyTracking()
    }
    
    private fun addLocationPoint(location: Location) {
        val point = LocationPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = location.time,
            accuracy = location.accuracy,
            speed = location.speed
        )
        currentTrip.add(point)
    }
    
    private fun endTrip() {
        if (currentTrip.size < 2 || tripStartTime == null) {
            resetTrip()
            return
        }
        
        val totalDistance = calculateTotalDistance(currentTrip)
        
        // Solo crear trayecto si la distancia es significativa
        if (totalDistance < MIN_DISTANCE_METERS) {
            resetTrip()
            return
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - tripStartTime!!
        val averageSpeed = if (duration > 0) {
            (totalDistance / duration) * 3.6 // Convertir m/ms a km/h
        } else 0.0
        
        val trip = TransportRecord(
            userId = null, // Se asignará cuando se confirme
            transportType = null, // Pendiente de confirmación
            date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date(tripStartTime!!)),
            timestamp = tripStartTime,
            hour = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(tripStartTime!!)),
            distance = totalDistance / 1000.0, // Convertir a kilómetros
            startTime = tripStartTime,
            endTime = endTime,
            duration = duration,
            averageSpeed = averageSpeed,
            routePoints = currentTrip.toList(),
            startLocation = currentTrip.firstOrNull(),
            endLocation = currentTrip.lastOrNull(),
            isAutoDetected = true,
            isConfirmed = false,
            createdAt = System.currentTimeMillis()
        )
        
        // Notificar el trayecto detectado
        notifyTripDetected(trip)
        
        resetTrip()
    }
    
    private fun calculateTotalDistance(points: List<LocationPoint>): Double {
        if (points.size < 2) return 0.0
        
        var totalDistance = 0.0
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            totalDistance += calculateDistance(
                prev.latitude, prev.longitude,
                curr.latitude, curr.longitude
            )
        }
        return totalDistance
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }
    
    private fun resetTrip() {
        isTracking = false
        tripStartTime = null
        currentTrip.clear()
        stationaryStartTime = null
    }
    
    private fun switchToHighFrequencyTracking() {
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).apply {
            setMinUpdateIntervalMillis(2000L)
            setMaxUpdateDelayMillis(10000L)
        }.build()
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }
    
    private fun switchToLowFrequencyTracking() {
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            30000L
        ).apply {
            setMinUpdateIntervalMillis(15000L)
            setMaxUpdateDelayMillis(60000L)
        }.build()
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                startForegroundService()
                startLocationTracking()
            }
            ACTION_STOP_TRACKING -> {
                stopLocationTracking()
                stopSelf()
            }
            else -> {
                // Si se inicia sin acción específica, iniciar automáticamente
                startForegroundService()
                startLocationTracking()
            }
        }
        // START_STICKY hace que el servicio se reinicie automáticamente si se detiene
        return START_STICKY
    }
    
    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Detección de Trayectos Activa")
            .setContentText("El sistema está monitoreando tus desplazamientos")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun startLocationTracking() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        
        // Iniciar con modo de baja frecuencia
        switchToLowFrequencyTracking()
    }
    
    private fun stopLocationTracking() {
        if (isTracking) {
            endTrip()
        }
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopLocationTracking()
        releaseWakeLock()
        serviceScope.cancel()
    }
}

