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
    private var isTracking: Boolean = false
    private var isEndingTrip: Boolean = false
    private var stationaryStartTime: Long? = null
    private var lastMovingLocation: Location? = null

    // Thresholds
    private val STATIONARY_THRESHOLD_MS = 5000L           // 5 segundos quieto -> fin de viaje
    private val MIN_DISTANCE_METERS = 10.0                // mínimo para aceptar un viaje
    private val MOVEMENT_SPEED_THRESHOLD_WALKING = 0.3    // m/s
    private val MOVEMENT_DISTANCE_THRESHOLD = 3.0         // m
    private val MAX_REALISTIC_SPEED = 55.5                // m/s (~200 km/h)
    private val MAX_JUMP_METERS = 80.0                    // si un punto salta >80m en poco tiempo → glitch

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
                sensorFusionManager = SensorFusionManager(sm) { moving, movementType ->
                    sensorMovementDetected = moving
                    sensorMovementType = movementType
                }
                Log.d("TripDetection", "✅ Sensor Fusion inicializado")
            }
        } catch (e: Exception) {
            Log.e("TripDetection", "❌ Error Sensor Fusion: ${e.message}", e)
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // WAKELOCK
    // ────────────────────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "EcoTracker::TripDetectionWakeLock"
        ).apply {
            acquire(10 * 60 * 60 * 1000L) // 10 horas
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
            ).apply {
                description = "Notificación para seguimiento de trayectos"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // LOCATION REQUEST
    // ────────────────────────────────────────────────────────────────────────────

    private fun setupLocationRequest() {
        // Compromiso entre precisión y batería
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            4000L // 4s entre updates cuando está activo
        )
            .setMinUpdateIntervalMillis(2000L)
            .setMaxUpdateDelayMillis(8000L)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // Forzamos tipo explícito para evitar "Cannot infer type"
                result.locations.forEach { loc: Location ->
                    processLocationUpdate(loc)
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // TRIP HELPERS
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

    private fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0].toDouble()
    }

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

    private fun resetTrip() {
        Log.d("TripDetection", "🔁 Reset trip (puntos=${currentTrip.size})")
        isTracking = false
        tripStartTime = null
        currentTrip.clear()
        stationaryStartTime = null
        lastMovingLocation = null
        // isEndingTrip se resetea solo en endTrip()
    }

    private fun startTrip(location: Location, now: Long) {
        Log.d("TripDetection", "🚀 Iniciando trayecto en ${location.latitude}, ${location.longitude}")
        isTracking = true
        tripStartTime = now
        currentTrip.clear()
        stationaryStartTime = null
        lastMovingLocation = location
        addLocationPoint(location)
    }

    private fun notifyTripDetected(trip: TransportRecord) {
        Log.d("TripDetection", "📤 notifyTripDetected -> broadcast (is_saved=false)")
        val intent = Intent(TripDetectionReceiver.ACTION_TRIP_DETECTED).apply {
            putExtra(TripDetectionReceiver.EXTRA_TRIP, trip)
            putExtra("is_saved", false)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun sendSpeedUpdate(speedMs: Float) {
        val intent = Intent(TripDetectionReceiver.ACTION_SPEED_UPDATE).apply {
            putExtra(TripDetectionReceiver.EXTRA_SPEED, speedMs)
            putExtra(TripDetectionReceiver.EXTRA_IS_TRACKING, isTracking)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // END TRIP — versión robusta
    // ────────────────────────────────────────────────────────────────────────────

    private fun endTrip() {
        if (isEndingTrip) {
            Log.w("TripDetection", "⚠️ endTrip() ya en ejecución, ignorando llamada")
            return
        }
        isEndingTrip = true

        try {
            Log.d("TripDetection", "🏁 Finalizando trayecto (puntos=${currentTrip.size})")

            if (currentTrip.size < 2 || tripStartTime == null) {
                Log.w("TripDetection", "⚠️ Trayecto descartado: pocos puntos o sin startTime")
                resetTrip()
                isEndingTrip = false
                return
            }

            val tripCopy = currentTrip.toList()
            val startTimeCopy = tripStartTime!!

            val totalDistance = calculateTotalDistance(tripCopy)
            Log.d(
                "TripDetection",
                "📏 Distancia total: ${"%.2f".format(totalDistance)} m (${ "%.2f".format(totalDistance / 1000.0)} km)"
            )

            if (totalDistance < MIN_DISTANCE_METERS) {
                Log.w("TripDetection", "⚠️ Trayecto descartado: distancia < $MIN_DISTANCE_METERS m")
                resetTrip()
                isEndingTrip = false
                return
            }

            val userId = firebaseAuth.currentUser?.uid
            if (userId == null) {
                Log.e("TripDetection", "❌ Usuario no autenticado, no se guarda el trayecto")
                resetTrip()
                isEndingTrip = false
                return
            }

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTimeCopy
            val avgSpeed = if (duration > 0) {
                (totalDistance / duration) * 3.6 // km/h
            } else 0.0

            val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val hourFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val date = dateFmt.format(java.util.Date(startTimeCopy))
            val hour = hourFmt.format(java.util.Date(startTimeCopy))

            val trip = TransportRecord(
                userId = userId,
                transportType = null,
                date = date,
                timestamp = startTimeCopy,
                hour = hour,
                distance = totalDistance / 1000.0,
                startTime = startTimeCopy,
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

            Log.d("TripDetection", "💾 Guardando trayecto en Firestore...")

            // Resetear estado antes de guardar
            resetTrip()

            serviceScope.launch {
                try {
                    transportRepository.saveAutoDetectedTrip(
                        trip = trip,
                        onSuccess = { firestoreId ->
                            Log.d("TripDetection", "✅ Trayecto guardado (id=$firestoreId)")
                            val tripWithId = trip.copy(id = firestoreId)
                            val intent =
                                Intent(TripDetectionReceiver.ACTION_TRIP_DETECTED).apply {
                                    putExtra(TripDetectionReceiver.EXTRA_TRIP, tripWithId)
                                    putExtra("is_saved", true)
                                    setPackage(packageName)
                                }
                            sendBroadcast(intent)
                        },
                        onError = { error ->
                            Log.e("TripDetection", "❌ Error al guardar trayecto: $error")
                            notifyTripDetected(trip)
                        }
                    )
                } catch (e: Exception) {
                    Log.e("TripDetection", "❌ Excepción al guardar trayecto: ${e.message}", e)
                    notifyTripDetected(trip)
                } finally {
                    isEndingTrip = false
                }
            }

        } catch (e: Exception) {
            Log.e("TripDetection", "❌ ERROR en endTrip(): ${e.message}", e)
            resetTrip()
            isEndingTrip = false
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // LOCATION PROCESSING (con filtros anti-glitch)
    // ────────────────────────────────────────────────────────────────────────────

    private fun processLocationUpdate(location: Location) {
        serviceScope.launch {
            try {
                val now = System.currentTimeMillis()

                // 1) Filtrar ubicaciones con mala precisión
                if (!location.hasAccuracy() || location.accuracy > 80f) {
                    Log.d(
                        "TripDetection",
                        "⚠️ Ubicación descartada por baja precisión (${location.accuracy} m)"
                    )
                    return@launch
                }

                // Primera ubicación
                if (lastLocation == null) {
                    lastLocation = location
                    lastLocationTime = now
                    // No empezamos trayecto aquí, solo anclamos referencia
                    sendSpeedUpdate(0f)
                    return@launch
                }

                val prev = lastLocation!!
                val timeDiff = (now - lastLocationTime).coerceAtLeast(1L) // ms
                val distance = prev.distanceTo(location)                  // m

                // 2) Calcular velocidad GPS / calculada
                val calcSpeed = (distance / timeDiff) * 1000.0 // m/s
                val gpsSpeed = if (location.hasSpeed()) location.speed.toDouble() else 0.0

                val reportedSpeed = when {
                    distance < 0.5 -> 0.0
                    calcSpeed > MAX_REALISTIC_SPEED && gpsSpeed in 0.0..MAX_REALISTIC_SPEED ->
                        gpsSpeed
                    gpsSpeed > MAX_REALISTIC_SPEED && calcSpeed in 0.0..MAX_REALISTIC_SPEED ->
                        calcSpeed
                    gpsSpeed in 0.0..MAX_REALISTIC_SPEED && gpsSpeed > 0.0 ->
                        gpsSpeed
                    calcSpeed in 0.0..MAX_REALISTIC_SPEED && calcSpeed > 0.0 ->
                        calcSpeed
                    else -> 0.0
                }

                // 3) Anti-glitch: saltos grandes imposibles (GPS "se fue" para atrás)
                if (distance > MAX_JUMP_METERS && reportedSpeed < 1.0) {
                    Log.w(
                        "TripDetection",
                        "⚠️ Glitch GPS detectado (dist=${"%.1f".format(distance)} m, speed=${"%.1f".format(reportedSpeed * 3.6)} km/h) → ignorando punto"
                    )
                    // Actualizamos referencia para que no siga acumulando desde un punto viejo
                    lastLocation = location
                    lastLocationTime = now
                    sendSpeedUpdate(0f)
                    return@launch
                }

                // 4) Combinar con sensores: si sensores dicen que hay movimiento, confía un poco más
                val gpsMoving = reportedSpeed >= MOVEMENT_SPEED_THRESHOLD_WALKING ||
                        distance >= MOVEMENT_DISTANCE_THRESHOLD
                val isMoving = gpsMoving || sensorMovementDetected

                // 5) Notificar velocidad (en m/s, nunca negativa)
                val safeSpeedMs = reportedSpeed.coerceAtLeast(0.0).toFloat()
                sendSpeedUpdate(safeSpeedMs)

                // 6) Lógica de inicio / fin de trayecto
                if (isMoving) {
                    if (!isTracking) {
                        // Iniciar trayecto
                        startTrip(location, now)
                    } else {
                        // Continuar trayecto
                        addLocationPoint(location)
                    }

                    stationaryStartTime = null
                    lastMovingLocation = location
                } else {
                    // Sin movimiento relevante
                    if (isTracking) {
                        if (stationaryStartTime == null) {
                            stationaryStartTime = now
                            Log.d("TripDetection", "⏸️ Posible parada detectada, contando tiempo...")
                        } else {
                            val still = now - stationaryStartTime!!
                            if (still >= STATIONARY_THRESHOLD_MS && !isEndingTrip) {
                                Log.d(
                                    "TripDetection",
                                    "⏹️ Usuario quieto por ${still}ms → finalizando trayecto"
                                )

                                lastMovingLocation?.let { lastMove ->
                                    if (currentTrip.isEmpty() ||
                                        currentTrip.last().latitude != lastMove.latitude ||
                                        currentTrip.last().longitude != lastMove.longitude
                                    ) {
                                        addLocationPoint(lastMove)
                                    }
                                }

                                endTrip()
                            }
                        }
                    }
                }

                lastLocation = location
                lastLocationTime = now

            } catch (e: Exception) {
                Log.e("TripDetection", "❌ Error en processLocationUpdate: ${e.message}", e)
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // TRACKING CONTROL
    // ────────────────────────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TripDetection", "📨 onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                startForegroundServiceInternal()
                startLocationTracking()
            }
            ACTION_STOP_TRACKING -> {
                stopLocationTracking()
                stopSelf()
            }
            else -> {
                startForegroundServiceInternal()
                startLocationTracking()
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceInternal() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📍 EcoTracker Activo")
            .setContentText("Monitoreando desplazamientos")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
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
            Log.e("TripDetection", "❌ Permisos de ubicación no otorgados")
            stopSelf()
            return
        }

        Log.d("TripDetection", "▶️ Iniciando seguimiento de ubicación")

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
        Log.d("TripDetection", "⏹️ Deteniendo seguimiento de ubicación")

        if (isTracking) {
            endTrip()
        }

        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorFusionManager?.stopListening()
    }

    // ────────────────────────────────────────────────────────────────────────────
    // SERVICE END
    // ────────────────────────────────────────────────────────────────────────────

    override fun onDestroy() {
        Log.w("TripDetection", "⚠️ onDestroy llamado")
        try {
            stopLocationTracking()
            releaseWakeLock()
            serviceScope.cancel()
        } catch (e: Exception) {
            Log.e("TripDetection", "Error en onDestroy: ${e.message}", e)
        }
        super.onDestroy()
    }
}
