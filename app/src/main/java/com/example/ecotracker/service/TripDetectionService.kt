package com.example.ecotracker.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.ecotracker.MainActivity
import com.example.ecotracker.R
import com.example.ecotracker.data.TransportRepository
import com.example.ecotracker.data.model.LocationPoint
import com.example.ecotracker.data.model.TransportRecord
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlin.math.abs
import com.example.ecotracker.service.TripDetectionReceiver

class TripDetectionService : Service() {

    // ────────────────────────────────────────────────────────────────────────────
    // CORE VARIABLES
    // ────────────────────────────────────────────────────────────────────────────

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var wakeLock: PowerManager.WakeLock? = null
    private val transportRepository = TransportRepository()
    private val firebaseAuth = FirebaseAuth.getInstance()

    // Sensor fusion
    private var sensorFusionManager: SensorFusionManager? = null
    private var sensorManager: SensorManager? = null
    private var sensorMovementDetected = false
    private var sensorMovementType = SensorFusionManager.MovementType.STATIONARY

    // Trip state
    private var currentTrip: MutableList<LocationPoint> = mutableListOf()
    private var tripStartTime: Long? = null
    private var lastLocation: Location? = null
    private var lastLocationTime: Long = 0
    private var isTracking = false
    private var isEndingTrip = false   // IMPORTANTE: lo agregamos
    private var stationaryStartTime: Long? = null
    private var lastMovingLocation: Location? = null

    // Thresholds
    private val STATIONARY_THRESHOLD_MS = 5000L
    private val MIN_DISTANCE_METERS = 10.0
    private val MOVEMENT_SPEED_THRESHOLD_WALKING = 0.3
    private val MOVEMENT_SPEED_THRESHOLD_VEHICLE = 2.0
    private val MOVEMENT_DISTANCE_THRESHOLD = 3.0
    private val VEHICLE_SPEED_THRESHOLD = 5.0

    // Callbacks
    var onTripDetected: ((TransportRecord) -> Unit)? = null

    // ────────────────────────────────────────────────────────────────────────────
    // COMPANION
    // ────────────────────────────────────────────────────────────────────────────

    companion object {
        private const val CHANNEL_ID = "TripDetectionChannel"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START_TRACKING = "com.example.ecotracker.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.ecotracker.STOP_TRACKING"
    }


    // ────────────────────────────────────────────────────────────────────────────
    // SERVICE LIFECYCLE
    // ────────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d("TripDetection", "🔧 Servicio creado")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()
        setupLocationRequest()
        setupSensorFusion()
        acquireWakeLock()

        Log.d("TripDetection", "✅ Servicio inicializado correctamente")
    }

    override fun onBind(intent: Intent?): IBinder? = null


    // ────────────────────────────────────────────────────────────────────────────
    // SENSOR FUSION
    // ────────────────────────────────────────────────────────────────────────────

    private fun setupSensorFusion() {
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager

            sensorManager?.let { sm ->
                sensorFusionManager = SensorFusionManager(sm) { isMoving, movementType ->
                    sensorMovementDetected = isMoving
                    sensorMovementType = movementType
                }
                Log.d("TripDetection", "✅ Sensor Fusion inicializado")
            }
        } catch (e: Exception) {
            Log.e("TripDetection", "❌ Error Sensor Fusion: ${e.message}")
        }
    }


    // ────────────────────────────────────────────────────────────────────────────
    // WAKELOCK
    // ────────────────────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "EcoTracker::TripDetectionWakeLock"
            ).apply {
                acquire(10 * 60 * 60 * 1000L)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }


    // ────────────────────────────────────────────────────────────────────────────
    // NOTIFICATION CHANNEL
    // ────────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Detección de Trayectos",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }


    // ────────────────────────────────────────────────────────────────────────────
    // LOCATION SETUP
    // ────────────────────────────────────────────────────────────────────────────

    private fun setupLocationRequest() {
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3000L
        )
            .setMinUpdateIntervalMillis(1000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    processLocationUpdate(location)
                }
            }
        }
    }


    // ────────────────────────────────────────────────────────────────────────────
    // TRIP START / END
    // ────────────────────────────────────────────────────────────────────────────

    private fun startTrip(location: Location, now: Long) {

        isTracking = true
        tripStartTime = now
        currentTrip.clear()

        addLocationPoint(location)
    }


    /**
     * VERSIÓN COMPLETA DEL endTrip (versión B)
     */
    private fun endTrip() {
        if (isEndingTrip) return
        isEndingTrip = true

        try {
            if (currentTrip.size < 2 || tripStartTime == null) {
                resetTrip()
                isEndingTrip = false
                return
            }

            // COPY trip
            val tripCopy = currentTrip.toList()
            val startTime = tripStartTime!!

            val totalDistance = calculateTotalDistance(tripCopy)

            if (totalDistance < MIN_DISTANCE_METERS) {
                resetTrip()
                isEndingTrip = false
                return
            }

            val userId = firebaseAuth.currentUser?.uid ?: run {
                resetTrip()
                isEndingTrip = false
                return
            }

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            val avgSpeed = (totalDistance / duration) * 3.6

            val dateFormatter =
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val hourFormatter =
                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())

            val date = dateFormatter.format(java.util.Date(startTime))
            val hour = hourFormatter.format(java.util.Date(startTime))

            val trip = TransportRecord(
                userId = userId,
                transportType = null,
                date = date,
                timestamp = startTime,
                hour = hour,
                distance = totalDistance / 1000.0,
                startTime = startTime,
                endTime = endTime,
                duration = duration,
                averageSpeed = avgSpeed,
                routePoints = tripCopy,
                startLocation = tripCopy.firstOrNull(),
                endLocation = tripCopy.lastOrNull(),
                isAutoDetected = true,
                isConfirmed = false,
                createdAt = System.currentTimeMillis()
            )

            // Reset state BEFORE saving
            resetTrip()

            serviceScope.launch {
                try {
                    transportRepository.saveAutoDetectedTrip(
                        trip = trip,
                        onSuccess = { firestoreId ->
                            val updated = trip.copy(id = firestoreId)
                            val intent =
                                Intent(TripDetectionReceiver.ACTION_TRIP_DETECTED).apply {
                                    putExtra(TripDetectionReceiver.EXTRA_TRIP, updated)
                                    putExtra("is_saved", true)
                                    setPackage(packageName)
                                }
                            sendBroadcast(intent)
                        },
                        onError = {
                            notifyTripDetected(trip)
                        }
                    )
                } finally {
                    isEndingTrip = false
                }
            }

        } catch (e: Exception) {
            Log.e("TripDetection", "ERROR en endTrip: ${e.message}")
            resetTrip()
            isEndingTrip = false
        }
    }


    // ────────────────────────────────────────────────────────────────────────────
    // DISTANCES — versión B (la buena)
    // ────────────────────────────────────────────────────────────────────────────

    private fun calculateTotalDistance(points: List<LocationPoint>): Double {
        if (points.size < 2) return 0.0

        var total = 0.0
        for (i in 1 until points.size) {
            val p = points[i - 1]
            val c = points[i]
            total += calculateDistance(p.latitude, p.longitude, c.latitude, c.longitude)
        }
        return total
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0].toDouble()
    }


    // ────────────────────────────────────────────────────────────────────────────
    // STATE RESET — versión B
    // ────────────────────────────────────────────────────────────────────────────

    private fun resetTrip() {
        isTracking = false
        tripStartTime = null
        currentTrip.clear()
        stationaryStartTime = null
        lastMovingLocation = null
        // isEndingTrip se resetea SOLO en endTrip()
    }

    private fun notifyTripDetected(trip: TransportRecord) {
        val intent = Intent(TripDetectionReceiver.ACTION_TRIP_DETECTED).apply {
            putExtra(TripDetectionReceiver.EXTRA_TRIP, trip)
            putExtra("is_saved", false)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }


    // ────────────────────────────────────────────────────────────────────────────
    // LOCATION PROCESSING
    // ────────────────────────────────────────────────────────────────────────────

    private fun addLocationPoint(loc: Location) {
        currentTrip.add(
            LocationPoint(
                latitude = loc.latitude,
                longitude = loc.longitude,
                timestamp = loc.time,
                accuracy = loc.accuracy,
                speed = loc.speed
            )
        )
    }

    private fun processLocationUpdate(location: Location) {
        serviceScope.launch {
            try {
                val now = System.currentTimeMillis()

                if (!location.hasAccuracy() || location.accuracy > 200f) return@launch

                if (lastLocation == null) {
                    lastLocation = location
                    lastLocationTime = now
                    return@launch
                }

                val distance = lastLocation!!.distanceTo(location)
                val timeDiff = now - lastLocationTime

                val calculatedSpeed = if (timeDiff > 0) (distance / timeDiff) * 1000.0 else 0.0
                val gpsSpeed = if (location.hasSpeed()) location.speed.toDouble() else 0.0

                val reportedSpeed = when {
                    distance < 0.5 -> 0.0
                    calculatedSpeed > 55.5 -> 0.0
                    gpsSpeed > 55.5 -> calculatedSpeed
                    else -> if (gpsSpeed > 0) gpsSpeed else calculatedSpeed
                }

                val isMoving = reportedSpeed >= MOVEMENT_SPEED_THRESHOLD_WALKING ||
                        distance >= MOVEMENT_DISTANCE_THRESHOLD

                if (isMoving) {
                    if (!isTracking) startTrip(location, now)
                    else addLocationPoint(location)

                    stationaryStartTime = null
                    lastMovingLocation = location

                } else {
                    if (isTracking) {
                        if (stationaryStartTime == null) {
                            stationaryStartTime = now
                        } else {
                            val diff = now - stationaryStartTime!!
                            if (diff >= STATIONARY_THRESHOLD_MS && !isEndingTrip) {
                                lastMovingLocation?.let { addLocationPoint(it) }
                                endTrip()
                            }
                        }
                    }
                }

                lastLocation = location
                lastLocationTime = now

            } catch (e: Exception) {
                Log.e("TripDetection", "Error en processLocationUpdate: ${e.message}")
            }
        }
    }


    // ────────────────────────────────────────────────────────────────────────────
    // TRACKING CONTROL
    // ────────────────────────────────────────────────────────────────────────────

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
                startForegroundService()
                startLocationTracking()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📍 EcoTracker Activo")
            .setContentText("Monitoreando desplazamientos")
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
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        lastLocation = null
        lastLocationTime = 0
        stationaryStartTime = null
        isTracking = false

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        sensorFusionManager?.startListening()
    }

    private fun stopLocationTracking() {
        if (isTracking) endTrip()

        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorFusionManager?.stopListening()
    }


    // ────────────────────────────────────────────────────────────────────────────
    // SERVICE END
    // ────────────────────────────────────────────────────────────────────────────

    override fun onDestroy() {
        try {
            stopLocationTracking()
            releaseWakeLock()
            serviceScope.cancel()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
