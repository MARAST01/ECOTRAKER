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
import kotlin.math.*

class TripDetectionService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var wakeLock: PowerManager.WakeLock? = null
    private val transportRepository = TransportRepository()
    private val firebaseAuth = FirebaseAuth.getInstance()
    
    // Sensor Fusion Manager para detectar movimiento usando acelerómetro + giroscopio
    private var sensorFusionManager: SensorFusionManager? = null
    private var sensorManager: SensorManager? = null
    private var sensorMovementDetected = false
    private var sensorMovementType = SensorFusionManager.MovementType.STATIONARY
    
    // Estado del trayecto actual
    private var currentTrip: MutableList<LocationPoint> = mutableListOf()
    private var tripStartTime: Long? = null
    private var lastLocation: Location? = null
    private var lastLocationTime: Long = 0
    private var isTracking = false
    private var stationaryStartTime: Long? = null
    private var lastMovingLocation: Location? = null // Última ubicación cuando había movimiento
    private val STATIONARY_THRESHOLD_MS = 5000L // 5 segundos sin movimiento (entorno de pruebas)
    private val MIN_DISTANCE_METERS = 10.0 // Mínimo 10 metros para considerar un trayecto (reducido para detectar trayectos cortos a pie)
    private val MOVEMENT_SPEED_THRESHOLD_WALKING = 0.3 // m/s (1.08 km/h) - para caminar (reducido para ser más sensible)
    private val MOVEMENT_SPEED_THRESHOLD_VEHICLE = 2.0 // m/s (7.2 km/h) - para vehículos
    private val MOVEMENT_DISTANCE_THRESHOLD = 3.0 // Mínimo 3 metros de desplazamiento (reducido para caminar)
    private val VEHICLE_SPEED_THRESHOLD = 5.0 // m/s (18 km/h) - velocidad típica de vehículo
    
    // Callback para notificar cuando se detecta un trayecto
    var onTripDetected: ((TransportRecord) -> Unit)? = null
    
    private fun notifyTripDetected(trip: TransportRecord) {
        Log.d("TripDetection", "📨📨📨 NOTIFICANDO TRAYECTO DETECTADO 📨📨📨")
        Log.d("TripDetection", "   🆔 ID: ${trip.id}")
        Log.d("TripDetection", "   📏 Distancia: ${trip.distance} km")
        Log.d("TripDetection", "   📍 Puntos GPS: ${trip.routePoints?.size ?: 0}")
        Log.d("TripDetection", "   📅 Fecha: ${trip.date}")
        Log.d("TripDetection", "   🕐 Hora: ${trip.hour}")
        Log.d("TripDetection", "   📦 Package: $packageName")
        
        // Notificar mediante callback si está disponible
        if (onTripDetected != null) {
            Log.d("TripDetection", "   ✅ Callback disponible, invocando...")
            onTripDetected?.invoke(trip)
        } else {
            Log.w("TripDetection", "   ⚠️ Callback no disponible")
        }
        
        // También enviar broadcast
        val intent = android.content.Intent(TripDetectionReceiver.ACTION_TRIP_DETECTED).apply {
            putExtra(TripDetectionReceiver.EXTRA_TRIP, trip)
            setPackage(packageName) // Especificar el paquete para seguridad
        }
        
        Log.d("TripDetection", "   📤 Intent creado:")
        Log.d("TripDetection", "      Action: ${intent.action}")
        Log.d("TripDetection", "      Package: ${intent.`package`}")
        Log.d("TripDetection", "      Has Extra: ${intent.hasExtra(TripDetectionReceiver.EXTRA_TRIP)}")
        
        // Enviar broadcast sin restricciones ya que es interno a la app
        try {
            sendBroadcast(intent)
            Log.d("TripDetection", "   ✅✅✅ BROADCAST ENVIADO CORRECTAMENTE ✅✅✅")
        } catch (e: Exception) {
            Log.e("TripDetection", "   ❌❌❌ ERROR AL ENVIAR BROADCAST ❌❌❌")
            Log.e("TripDetection", "      Mensaje: ${e.message}")
            Log.e("TripDetection", "      Stack trace:", e)
            e.printStackTrace()
        }
    }
    
    private fun notifySpeedUpdate(speed: Float, isTracking: Boolean) {
        val intent = android.content.Intent(TripDetectionReceiver.ACTION_SPEED_UPDATE).apply {
            putExtra(TripDetectionReceiver.EXTRA_SPEED, speed)
            putExtra(TripDetectionReceiver.EXTRA_IS_TRACKING, isTracking)
            setPackage(packageName) // Especificar el paquete para seguridad
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
        Log.d("TripDetection", "🔧 Servicio creado")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        setupLocationRequest()
        setupSensorFusion()
        acquireWakeLock()
        Log.d("TripDetection", "✅ Servicio inicializado correctamente")
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    private fun setupSensorFusion() {
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            sensorManager?.let { sm ->
                sensorFusionManager = SensorFusionManager(sm) { isMoving, movementType ->
                    sensorMovementDetected = isMoving
                    sensorMovementType = movementType
                    Log.d("TripDetection", "📱 Sensor Fusion - Movimiento: $isMoving, Tipo: $movementType")
                }
                Log.d("TripDetection", "✅ Sensor Fusion Manager inicializado")
            } ?: Log.w("TripDetection", "⚠️ SensorManager no disponible")
        } catch (e: Exception) {
            Log.e("TripDetection", "❌ Error al inicializar Sensor Fusion: ${e.message}", e)
        }
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
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun setupLocationRequest() {
        // Request de alta frecuencia cuando hay movimiento
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3000L // 3 segundos (más frecuente)
        ).apply {
            setMinUpdateIntervalMillis(1000L) // Mínimo 1 segundo
            setMaxUpdateDelayMillis(5000L) // Máximo 5 segundos
            setWaitForAccurateLocation(false)
        }.build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                try {
                    Log.d("TripDetection", "📍 onLocationResult - ${result.locations.size} ubicaciones recibidas")
                    // Procesar todas las ubicaciones del resultado, no solo la última
                    result.locations.forEach { location ->
                        try {
                            processLocationUpdate(location)
                        } catch (e: Exception) {
                            Log.e("TripDetection", "❌ Error al procesar ubicación: ${e.message}", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TripDetection", "❌ Error en onLocationResult: ${e.message}", e)
                }
            }
        }
        Log.d("TripDetection", "✅ LocationCallback configurado")
    }
    
    private fun startTrip(location: Location, time: Long) {
        isTracking = true
        tripStartTime = time
        currentTrip.clear()
        stationaryStartTime = null
        lastMovingLocation = location // Guardar primera ubicación como última con movimiento
        
        Log.d("TripDetection", "🚀🚀🚀 TRAYECTO INICIADO 🚀🚀🚀 - Lat: ${location.latitude}, Lng: ${location.longitude}, Velocidad GPS: ${location.speed * 3.6} km/h, Accuracy: ${location.accuracy}m")
        
        // Agregar la última ubicación conocida si existe (para tener el punto de inicio)
        if (lastLocation != null) {
            Log.d("TripDetection", "📍 Agregando última ubicación conocida al inicio del trayecto")
            addLocationPoint(lastLocation!!)
        }
        addLocationPoint(location)
        Log.d("TripDetection", "✅ Puntos iniciales agregados. Total puntos: ${currentTrip.size}")
        
        // Actualizar notificación
        updateNotification()
        
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
        Log.d("TripDetection", "🏁 Finalizando trayecto - Puntos: ${currentTrip.size}, StartTime: $tripStartTime")
        
        if (currentTrip.size < 2 || tripStartTime == null) {
            Log.w("TripDetection", "⚠️ Trayecto descartado - Muy pocos puntos (${currentTrip.size}) o sin startTime")
            resetTrip()
            return
        }
        
        val totalDistance = calculateTotalDistance(currentTrip)
        Log.d("TripDetection", "📏 Distancia total calculada: ${String.format("%.2f", totalDistance)}m")
        
        // Solo crear trayecto si la distancia es significativa
        if (totalDistance < MIN_DISTANCE_METERS) {
            Log.w("TripDetection", "⚠️ Trayecto descartado - Distancia insuficiente: ${String.format("%.2f", totalDistance)}m (mínimo: ${MIN_DISTANCE_METERS}m)")
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
        
        val tripDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(tripStartTime!!))
        
        Log.d("TripDetection", "✅✅✅ TRAYECTO FINALIZADO ✅✅✅")
        Log.d("TripDetection", "   📏 Distancia: ${String.format("%.2f", totalDistance / 1000.0)} km")
        Log.d("TripDetection", "   ⏱️ Duración: ${duration / 60000} min (${duration / 1000} seg)")
        Log.d("TripDetection", "   📍 Puntos GPS: ${currentTrip.size}")
        Log.d("TripDetection", "   📅 Fecha: $tripDate")
        Log.d("TripDetection", "   🚗 Velocidad promedio: ${String.format("%.2f", averageSpeed)} km/h")
        Log.d("TripDetection", "   🆔 ID: ${trip.id}")
        Log.d("TripDetection", "📤 Enviando broadcast de trayecto detectado...")
        
        // Notificar el trayecto detectado
        notifyTripDetected(trip)
        
        Log.d("TripDetection", "✅ Broadcast enviado. Trayecto debería aparecer en la app.")
        
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
        Log.d(
            "TripDetection",
            "🔁 Reset trip - puntos previos: ${currentTrip.size}, estabaTracking=$isTracking, tripStartTime=$tripStartTime"
        )
        isTracking = false
        tripStartTime = null
        currentTrip.clear()
        stationaryStartTime = null
        lastMovingLocation = null
    }
    
    private fun switchToHighFrequencyTracking() {
        // Remover actualizaciones anteriores antes de agregar nuevas
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            // Ignorar si no hay actualizaciones activas
        }
        
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            500L // 0.5 segundos (muy frecuente para emulador y movimiento real)
        ).apply {
            setMinUpdateIntervalMillis(200L) // Mínimo 0.2 segundos (muy frecuente)
            setMaxUpdateDelayMillis(1000L) // Máximo 1 segundo
            setWaitForAccurateLocation(false)
        }.build()
        
        Log.d("TripDetection", "🔄 Cambiando a modo ALTA FRECUENCIA - Intervalo: ${locationRequest.intervalMillis}ms, Min: ${locationRequest.minUpdateIntervalMillis}ms, Max: ${locationRequest.maxUpdateDelayMillis}ms")
        
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
            Log.d("TripDetection", "✅ Actualizaciones de alta frecuencia solicitadas")
        } else {
            Log.e("TripDetection", "❌ No se pueden solicitar actualizaciones - Permisos no otorgados")
        }
    }
    
    private fun switchToLowFrequencyTracking() {
        // Remover actualizaciones anteriores antes de agregar nuevas
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            // Ignorar si no hay actualizaciones activas
        }
        
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, // Cambiar a HIGH_ACCURACY para emulador
            2000L // 2 segundos (más frecuente para emulador)
        ).apply {
            setMinUpdateIntervalMillis(1000L) // Mínimo 1 segundo
            setMaxUpdateDelayMillis(3000L) // Máximo 3 segundos
            setWaitForAccurateLocation(false) // No esperar ubicación precisa
        }.build()
        
        Log.d("TripDetection", "🔄 Cambiando a modo BAJA FRECUENCIA - Intervalo: ${locationRequest.intervalMillis}ms, Min: ${locationRequest.minUpdateIntervalMillis}ms, Max: ${locationRequest.maxUpdateDelayMillis}ms")
        
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
            Log.d("TripDetection", "✅ Actualizaciones de baja frecuencia solicitadas")
        } else {
            Log.e("TripDetection", "❌ No se pueden solicitar actualizaciones - Permisos no otorgados")
        }
    }
    
    private fun updateNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val statusText = if (isTracking) {
            "Trayecto en curso - ${currentTrip.size} puntos registrados"
        } else {
            "Monitoreando desplazamientos"
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📍 EcoTracker Activo")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .apply {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun processLocationUpdate(location: Location) {
        serviceScope.launch {
            try {
                val currentTime = System.currentTimeMillis()
                
                // Validar que la ubicación sea válida (ser más permisivo para caminar)
                // Aumentar el umbral de precisión para detectar mejor el movimiento a pie
                if (!location.hasAccuracy() || location.accuracy > 200f) {
                    // Ignorar ubicaciones con muy baja precisión (aumentado a 200m para ser más permisivo)
                    Log.d("TripDetection", "⚠️ Ubicación descartada - Precisión: ${location.accuracy}m (máximo: 200m)")
                    return@launch
                }
                
                // Log cada 5 actualizaciones para no saturar
                if (System.currentTimeMillis() % 5000 < 1000) {
                    Log.d("TripDetection", "✅ Ubicación válida - Lat: ${location.latitude}, Lng: ${location.longitude}, Precisión: ${location.accuracy}m, GPS Speed: ${location.speed * 3.6} km/h")
                }
                
                if (lastLocation == null) {
                    // Primera ubicación válida
                    lastLocation = location
                    lastLocationTime = currentTime
                    return@launch
                }
                
                val distance = lastLocation!!.distanceTo(location)
                val timeDiff = if (lastLocationTime > 0) {
                    currentTime - lastLocationTime
                } else {
                    maxOf(currentTime - location.time, 1000L) // Mínimo 1 segundo
                }
                
                // Calcular velocidad usando el tiempo real, no el timestamp de la ubicación
                val calculatedSpeed = if (timeDiff > 0) {
                    (distance / timeDiff) * 1000.0 // m/s
                } else {
                    0.0
                }
                
                // Validar velocidad del GPS y usar la más confiable
                val gpsSpeed = if (location.hasSpeed() && location.speed > 0) {
                    location.speed.toDouble()
                } else {
                    0.0
                }
                
                // Filtrar velocidades incorrectas del GPS:
                // 1. Si la distancia es muy pequeña (< 1m), estamos quietos -> velocidad 0
                // 2. Si la velocidad calculada es irreal (> 200 km/h = 55.5 m/s), descartar
                // 3. Si la velocidad GPS es muy diferente de la calculada, usar la calculada
                // 4. Si el tiempo entre actualizaciones es muy grande, la velocidad calculada puede ser incorrecta
                val MAX_REALISTIC_SPEED = 55.5 // m/s (200 km/h)
                val MIN_DISTANCE_FOR_SPEED = 0.5 // metros (reducido para detectar mejor el movimiento a pie)
                val MAX_TIME_DIFF_FOR_ACCURATE_SPEED = 10000L // 10 segundos - aumentado para dar más tiempo al GPS
                
                val reportedSpeed = when {
                    // Si no hay movimiento real, velocidad es 0
                    distance < MIN_DISTANCE_FOR_SPEED -> 0.0
                    
                    // Si el tiempo entre actualizaciones es muy grande, la velocidad calculada puede ser incorrecta
                    // En este caso, usar la velocidad GPS si está disponible y es razonable
                    timeDiff > MAX_TIME_DIFF_FOR_ACCURATE_SPEED -> {
                        if (gpsSpeed > 0 && gpsSpeed <= MAX_REALISTIC_SPEED) {
                            gpsSpeed
                        } else {
                            // Si la velocidad calculada es irreal, usar 0 o un promedio conservador
                            if (calculatedSpeed > MAX_REALISTIC_SPEED) {
                                0.0 // Descartar velocidad irreal
                            } else {
                                calculatedSpeed
                            }
                        }
                    }
                    
                    // Si la velocidad calculada es irreal, descartar
                    calculatedSpeed > MAX_REALISTIC_SPEED -> {
                        // Intentar usar la velocidad GPS si es razonable
                        if (gpsSpeed > 0 && gpsSpeed <= MAX_REALISTIC_SPEED) {
                            gpsSpeed
                        } else {
                            0.0 // Descartar velocidad irreal
                        }
                    }
                    
                    // Si la velocidad GPS es irreal, usar la calculada (si es razonable)
                    gpsSpeed > MAX_REALISTIC_SPEED -> {
                        if (calculatedSpeed <= MAX_REALISTIC_SPEED) {
                            calculatedSpeed
                        } else {
                            0.0 // Ambas son irreales
                        }
                    }
                    
                    // Si la velocidad GPS difiere mucho de la calculada (más del 100%), usar la calculada
                    gpsSpeed > 0 && calculatedSpeed > 0 && 
                    abs(gpsSpeed - calculatedSpeed) > maxOf(gpsSpeed, calculatedSpeed) -> {
                        // Si la diferencia es muy grande, preferir la calculada si es razonable
                        if (calculatedSpeed <= MAX_REALISTIC_SPEED) {
                            calculatedSpeed
                        } else if (gpsSpeed <= MAX_REALISTIC_SPEED) {
                            gpsSpeed
                        } else {
                            0.0
                        }
                    }
                    
                    // Si la velocidad GPS es razonable, usarla
                    gpsSpeed > 0 && gpsSpeed <= MAX_REALISTIC_SPEED -> gpsSpeed
                    
                    // En otros casos, usar la velocidad calculada si es razonable
                    else -> {
                        if (calculatedSpeed <= MAX_REALISTIC_SPEED) {
                            calculatedSpeed
                        } else {
                            0.0
                        }
                    }
                }
                
                // Detectar si es un vehículo basado en la velocidad
                val isLikelyVehicle = reportedSpeed >= VEHICLE_SPEED_THRESHOLD
                
                // FUSIÓN DE SENSORES + GPS (similar a Life360)
                // Combinar detección GPS con sensores (acelerómetro + giroscopio)
                val gpsBasedMovement = if (isLikelyVehicle) {
                    // Para vehículos, ser más permisivo - detectar incluso con velocidades bajas
                    // si hay desplazamiento significativo
                    reportedSpeed >= MOVEMENT_SPEED_THRESHOLD_VEHICLE || 
                    (distance >= 15.0 && timeDiff < 15000) || // 15 metros en menos de 15 segundos
                    (distance >= 8.0 && timeDiff < 8000) // 8 metros en menos de 8 segundos (para arranque)
                } else {
                    // Para caminar, ser MUY permisivo - detectar movimiento con:
                    // 1. Cualquier desplazamiento >= 3 metros en menos de 30 segundos
                    // 2. O velocidad >= 0.3 m/s con desplazamiento >= 2 metros
                    // 3. O desplazamiento acumulado >= 5 metros en menos de 60 segundos
                    (distance >= MOVEMENT_DISTANCE_THRESHOLD && timeDiff < 30000) || 
                    (reportedSpeed >= MOVEMENT_SPEED_THRESHOLD_WALKING && distance >= 2.0) ||
                    (distance >= 5.0 && timeDiff < 60000) // 5 metros en menos de 60 segundos
                }
                
                // FUSIÓN: Si los sensores detectan movimiento (especialmente caminando), confiar en ellos
                // incluso si el GPS no es preciso
                val isMoving = when {
                    // Si los sensores detectan caminando/corriendo, confiar en ellos
                    sensorMovementType == SensorFusionManager.MovementType.WALKING ||
                    sensorMovementType == SensorFusionManager.MovementType.RUNNING -> {
                        // Los sensores son más confiables para detectar caminar
                        // Usar GPS solo para confirmar o si hay desplazamiento significativo
                        sensorMovementDetected || gpsBasedMovement || distance >= 2.0
                    }
                    
                    // Si los sensores detectan vehículo, combinar con GPS
                    sensorMovementType == SensorFusionManager.MovementType.VEHICLE -> {
                        // Para vehículos, el GPS es más confiable, pero los sensores ayudan
                        gpsBasedMovement || (sensorMovementDetected && reportedSpeed > 1.0)
                    }
                    
                    // Si los sensores dicen que está quieto, pero el GPS muestra movimiento significativo
                    sensorMovementType == SensorFusionManager.MovementType.STATIONARY -> {
                        // Confiar más en GPS si hay desplazamiento grande o velocidad alta
                        gpsBasedMovement && (distance >= 10.0 || reportedSpeed >= 2.0)
                    }
                    
                    // En otros casos, usar lógica GPS normal
                    else -> gpsBasedMovement || sensorMovementDetected
                }
                
                // Calcular velocidad mejorada usando fusión de sensores
                // Si los sensores detectan caminando pero el GPS muestra 0, estimar velocidad basada en pasos
                val finalSpeed = when {
                    // Si los sensores detectan caminando/corriendo y el GPS no muestra velocidad
                    (sensorMovementType == SensorFusionManager.MovementType.WALKING ||
                     sensorMovementType == SensorFusionManager.MovementType.RUNNING) &&
                    reportedSpeed < 0.5 -> {
                        // Estimar velocidad basada en pasos detectados
                        val stepsPerSecond = sensorFusionManager?.getStepsPerSecond() ?: 0.0
                        
                        // Velocidad promedio caminando: ~1.4 m/s (5 km/h), corriendo: ~3 m/s (11 km/h)
                        val estimatedSpeed = when (sensorMovementType) {
                            SensorFusionManager.MovementType.WALKING -> {
                                // Si hay pasos detectados, usar velocidad estimada
                                if (stepsPerSecond > 0.5) {
                                    maxOf(0.8, minOf(2.0, stepsPerSecond * 0.7)) // 0.8-2.0 m/s
                                } else {
                                    maxOf(reportedSpeed, 0.5) // Mínimo 0.5 m/s si hay movimiento
                                }
                            }
                            SensorFusionManager.MovementType.RUNNING -> {
                                // Corriendo: 2.5-4.5 m/s
                                if (stepsPerSecond > 1.0) {
                                    maxOf(2.5, minOf(4.5, stepsPerSecond * 1.2))
                                } else {
                                    maxOf(reportedSpeed, 1.5)
                                }
                            }
                            else -> reportedSpeed
                        }
                        estimatedSpeed
                    }
                    
                    // Si hay movimiento GPS, usar esa velocidad
                    reportedSpeed > 0.5 -> reportedSpeed
                    
                    // En otros casos, usar la velocidad reportada
                    else -> reportedSpeed
                }
                
                // Log de debugging más frecuente para diagnosticar problemas
                val logEvery = if (isTracking) 5 else 3 // Log más frecuente cuando está tracking
                if (System.currentTimeMillis() % (logEvery * 1000) < 1000) {
                    Log.d("TripDetection", "📍 Ubicación - Dist: ${String.format("%.2f", distance)}m, GPS: ${String.format("%.2f", gpsSpeed * 3.6)} km/h, Calc: ${String.format("%.2f", calculatedSpeed * 3.6)} km/h, GPS-Reported: ${String.format("%.2f", reportedSpeed * 3.6)} km/h")
                    Log.d("TripDetection", "   📱 Sensores - Movimiento: $sensorMovementDetected, Tipo: $sensorMovementType, Pasos: ${sensorFusionManager?.getStepCount() ?: 0}, Pasos/seg: ${String.format("%.2f", sensorFusionManager?.getStepsPerSecond() ?: 0.0)}")
                    Log.d("TripDetection", "   ✅ Resultado - Velocidad Final: ${String.format("%.2f", finalSpeed * 3.6)} km/h, Moviendo: $isMoving, Tracking: $isTracking, TimeDiff: ${timeDiff}ms, Accuracy: ${location.accuracy}m")
                }
                
                // Log especial cuando hay movimiento pero no está tracking (para diagnosticar por qué no inicia)
                if (isMoving && !isTracking) {
                    Log.d("TripDetection", "🚶 MOVIMIENTO DETECTADO pero NO tracking")
                    Log.d("TripDetection", "   📍 GPS - Dist: ${String.format("%.2f", distance)}m, Speed: ${String.format("%.2f", reportedSpeed * 3.6)} km/h, GPS-Based: $gpsBasedMovement")
                    Log.d("TripDetection", "   📱 Sensores - Movimiento: $sensorMovementDetected, Tipo: $sensorMovementType, Velocidad Final: ${String.format("%.2f", finalSpeed * 3.6)} km/h")
                }
                
                // Notificar actualización de velocidad (cada actualización)
                // Asegurar que la velocidad sea siempre >= 0
                notifySpeedUpdate(maxOf(0f, finalSpeed.toFloat()), isTracking)
                
                if (isMoving) {
                    // Hay movimiento - activar seguimiento de alta frecuencia
                    if (!isTracking) {
                        startTrip(location, currentTime)
                    } else {
                        // Continuar el trayecto
                        addLocationPoint(location)
                        lastMovingLocation = location // Guardar última ubicación con movimiento
                        stationaryStartTime = null
                        
                        // Actualizar notificación cada 10 puntos para no saturar
                        if (currentTrip.size % 10 == 0) {
                            updateNotification()
                        }
                    }
                } else {
                    // No hay movimiento significativo según GPS
                    // IMPORTANTE: Usar SENSORES para detectar si realmente está quieto
                    // Los sensores son más precisos que el GPS para detectar si alguien está caminando/moviéndose
                    if (isTracking) {
                        // Estamos en un trayecto - verificar si realmente está quieto usando sensores
                        // Los sensores detectan si hay movimiento real (caminando, corriendo, etc.)
                        val sensorsIndicateStationary = sensorMovementType == SensorFusionManager.MovementType.STATIONARY
                        
                        // También verificar GPS como respaldo (pero con menos peso)
                        val gpsIndicatesStationary = !gpsBasedMovement && distance < 5.0 && reportedSpeed < 0.5
                        
                        // Considerar estacionario solo si AMBOS (sensores Y GPS) indican quieto
                        // O si los sensores claramente indican quieto (más confiable)
                        val isReallyStationary = sensorsIndicateStationary && gpsIndicatesStationary
                        
                        if (isReallyStationary) {
                            if (stationaryStartTime == null) {
                                stationaryStartTime = currentTime
                                val waitTimeMinutes = STATIONARY_THRESHOLD_MS / 60000
                                Log.d("TripDetection", "⏸️⏸️⏸️ SENSORES + GPS DETECTAN QUIETO - Esperando ${waitTimeMinutes} minutos para finalizar ⏸️⏸️⏸️")
                                Log.d("TripDetection", "   📱 Sensores: Tipo: $sensorMovementType, Movimiento: $sensorMovementDetected")
                                Log.d("TripDetection", "   📍 GPS: Distancia: ${String.format("%.2f", distance)}m, Velocidad: ${String.format("%.2f", reportedSpeed * 3.6)} km/h")
                                Log.d("TripDetection", "   📍 Última ubicación con movimiento: Lat: ${lastMovingLocation?.latitude ?: location.latitude}, Lng: ${lastMovingLocation?.longitude ?: location.longitude}")
                                // Calcular distancia en background para no bloquear el hilo principal
                                serviceScope.launch(Dispatchers.Default) {
                                    val distance = calculateTotalDistance(currentTrip)
                                    Log.d("TripDetection", "   📊 Estado: Puntos: ${currentTrip.size}, Distancia: ${String.format("%.2f", distance)}m")
                                }
                            } else {
                                val timeStationary = currentTime - stationaryStartTime!!
                                val remainingTime = STATIONARY_THRESHOLD_MS - timeStationary
                                
                                // Log cada 30 segundos para monitorear el progreso
                                if (timeStationary % 30000 < 2000) {
                                    val minutesStationary = timeStationary / 60000
                                    val secondsStationary = (timeStationary % 60000) / 1000
                                    val minutesRemaining = remainingTime / 60000
                                    val secondsRemaining = (remainingTime % 60000) / 1000
                                    Log.d("TripDetection", "⏸️ QUIETO (Sensores + GPS): ${minutesStationary}m ${secondsStationary}s - Faltan ${minutesRemaining}m ${secondsRemaining}s")
                                    Log.d("TripDetection", "   📱 Sensores: $sensorMovementType, GPS: ${String.format("%.2f", reportedSpeed * 3.6)} km/h")
                                }
                                
                                // Si llevamos más de STATIONARY_THRESHOLD_MS quietos, finalizar trayecto
                                if (timeStationary >= STATIONARY_THRESHOLD_MS && !isEndingTrip) {
                                    val minutesStationary = timeStationary / 60000
                                    val secondsStationary = (timeStationary % 60000) / 1000
                                    Log.d("TripDetection", "⏹️⏹️⏹️⏹️⏹️ TIEMPO COMPLETADO (${minutesStationary}m ${secondsStationary}s) - FINALIZANDO TRAYECTO ⏹️⏹️⏹️⏹️⏹️")
                                    Log.d("TripDetection", "   📱 Confirmación final - Sensores: $sensorMovementType, GPS: ${String.format("%.2f", reportedSpeed * 3.6)} km/h")
                                    
                                    // Calcular distancia ANTES de agregar el último punto (ya se calcula en endTrip)
                                    Log.d("TripDetection", "   📊 Estado final: Puntos: ${currentTrip.size}")
                                    
                                    try {
                                        // Agregar el último punto de movimiento (si existe) antes de finalizar
                                        lastMovingLocation?.let { lastMoving ->
                                            if (currentTrip.isEmpty() || 
                                                currentTrip.last().latitude != lastMoving.latitude ||
                                                currentTrip.last().longitude != lastMoving.longitude) {
                                                addLocationPoint(lastMoving)
                                                Log.d("TripDetection", "   ✅ Agregado último punto de movimiento antes de finalizar")
                                            }
                                        }
                                        
                                        // Finalizar el trayecto ANTES de cambiar a baja frecuencia
                                        endTrip()
                                        
                                        // Cambiar a modo de baja frecuencia después de finalizar
                                        switchToLowFrequencyTracking()
                                    } catch (e: Exception) {
                                        Log.e("TripDetection", "❌❌❌ ERROR AL FINALIZAR TRAYECTO ❌❌❌")
                                        Log.e("TripDetection", "   Mensaje: ${e.message}", e)
                                        e.printStackTrace()
                                        // Resetear el trayecto para evitar estados inconsistentes
                                        resetTrip()
                                        isEndingTrip = false
                                    }
                                }
                            }
                        } else {
                            // Los sensores o GPS detectan movimiento, resetear contador estacionario
                            if (stationaryStartTime != null) {
                                Log.d("TripDetection", "🔄 Movimiento detectado - Cancelando finalización")
                                Log.d("TripDetection", "   📱 Sensores: $sensorMovementType, GPS: ${String.format("%.2f", reportedSpeed * 3.6)} km/h")
                                stationaryStartTime = null
                                lastMovingLocation = location
                            }
                        }
                    } else {
                        // No estamos en un trayecto, mantener modo de baja frecuencia
                        stationaryStartTime = currentTime
                    }
                }
                
                lastLocation = location
                lastLocationTime = currentTime
            } catch (e: Exception) {
                Log.e("TripDetection", "❌ Error en processLocationUpdate: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }
    
    private fun endTrip() {
        // Evitar múltiples llamadas simultáneas
        if (isEndingTrip) {
            Log.w("TripDetection", "⚠️ endTrip() ya está en ejecución, ignorando llamada duplicada")
            return
        }
        
        isEndingTrip = true
        
        try {
            Log.d("TripDetection", "🏁🏁🏁 FINALIZANDO TRAYECTO 🏁🏁🏁")
            Log.d("TripDetection", "   📊 Puntos GPS: ${currentTrip.size}")
            Log.d("TripDetection", "   ⏰ StartTime: $tripStartTime")
            Log.d("TripDetection", "   ⏰ EndTime: ${System.currentTimeMillis()}")
            
            if (currentTrip.size < 2 || tripStartTime == null) {
                Log.e("TripDetection", "❌❌❌ TRAYECTO DESCARTADO ❌❌❌")
                Log.e("TripDetection", "   Razón: Muy pocos puntos (${currentTrip.size}) o sin startTime")
                Log.e("TripDetection", "   Requisitos: Mínimo 2 puntos y startTime no null")
                resetTrip()
                isEndingTrip = false
                return
            }
            
            // HACER UNA COPIA de currentTrip ANTES de calcular la distancia
            // para evitar que se vacíe mientras se calcula
            val tripCopy = currentTrip.toList()
            val startTimeCopy = tripStartTime!!
            
            val totalDistance = calculateTotalDistance(tripCopy)
        Log.d("TripDetection", "📏 Distancia total calculada: ${String.format("%.2f", totalDistance)}m (${String.format("%.2f", totalDistance / 1000.0)} km)")
        Log.d("TripDetection", "   📏 Mínimo requerido: ${MIN_DISTANCE_METERS}m (${String.format("%.2f", MIN_DISTANCE_METERS / 1000.0)} km)")
        
        // Solo crear trayecto si la distancia es significativa
        if (totalDistance < MIN_DISTANCE_METERS) {
            Log.e("TripDetection", "❌❌❌ TRAYECTO DESCARTADO ❌❌❌")
            Log.e("TripDetection", "   Razón: Distancia insuficiente")
            Log.e("TripDetection", "   Distancia: ${String.format("%.2f", totalDistance)}m (${String.format("%.2f", totalDistance / 1000.0)} km)")
            Log.e("TripDetection", "   Mínimo requerido: ${MIN_DISTANCE_METERS}m (${String.format("%.2f", MIN_DISTANCE_METERS / 1000.0)} km)")
            Log.e("TripDetection", "   Diferencia: ${String.format("%.2f", MIN_DISTANCE_METERS - totalDistance)}m faltantes")
            resetTrip()
            isEndingTrip = false
            return
        }
        
        // Obtener userId del usuario actual
        val userId = firebaseAuth.currentUser?.uid
        
        if (userId == null) {
            Log.e("TripDetection", "❌❌❌ ERROR: Usuario no autenticado - No se puede guardar el trayecto ❌❌❌")
            resetTrip()
            isEndingTrip = false
            return
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTimeCopy
        val averageSpeed = if (duration > 0) {
            (totalDistance / duration) * 3.6 // Convertir m/ms a km/h
        } else 0.0
        
        // Formatear fecha usando la zona horaria local
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val hourFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val tripDate = dateFormat.format(java.util.Date(startTimeCopy))
        val tripHour = hourFormat.format(java.util.Date(startTimeCopy))
        
        Log.d("TripDetection", "   📅 Fecha formateada: $tripDate")
        Log.d("TripDetection", "   🕐 Hora formateada: $tripHour")
        Log.d("TripDetection", "   ⏰ Timestamp original: $startTimeCopy")
        
        val trip = TransportRecord(
            userId = userId, // Guardar con userId del usuario actual
            transportType = null, // El usuario puede cambiarlo después
            date = tripDate,
            timestamp = startTimeCopy,
            hour = tripHour,
            distance = totalDistance / 1000.0, // Convertir a kilómetros
            startTime = startTimeCopy,
            endTime = endTime,
            duration = duration,
            averageSpeed = averageSpeed,
            routePoints = tripCopy, // Usar la copia en lugar de currentTrip
            startLocation = tripCopy.firstOrNull(),
            endLocation = tripCopy.lastOrNull(),
            isAutoDetected = true,
            isConfirmed = false, // No confirmado hasta que el usuario seleccione el tipo de transporte
            createdAt = System.currentTimeMillis()
        )
        
        Log.d("TripDetection", "✅✅✅ TRAYECTO FINALIZADO Y VALIDADO ✅✅✅")
        Log.d("TripDetection", "   👤 UserId: $userId")
        Log.d("TripDetection", "   📏 Distancia: ${String.format("%.2f", totalDistance / 1000.0)} km")
        Log.d("TripDetection", "   ⏱️ Duración: ${duration / 60000} min (${duration / 1000} seg)")
        Log.d("TripDetection", "   📍 Puntos GPS: ${tripCopy.size}")
        Log.d("TripDetection", "   📅 Fecha: $tripDate")
        Log.d("TripDetection", "   🕐 Hora: ${trip.hour}")
        Log.d("TripDetection", "   🚗 Velocidad promedio: ${String.format("%.2f", averageSpeed)} km/h")
        Log.d("TripDetection", "   🆔 ID: ${trip.id}")
        Log.d("TripDetection", "   📍 StartLocation: ${trip.startLocation?.latitude}, ${trip.startLocation?.longitude}")
        Log.d("TripDetection", "   📍 EndLocation: ${trip.endLocation?.latitude}, ${trip.endLocation?.longitude}")
        Log.d("TripDetection", "   ✅ isAutoDetected: ${trip.isAutoDetected}")
        Log.d("TripDetection", "   ⏳ isConfirmed: ${trip.isConfirmed}")
        Log.d("TripDetection", "💾 Guardando trayecto automáticamente en Firestore...")
        
        // Resetear el estado ANTES de guardar (para evitar que otras coroutines interfieran)
        // pero mantener los datos en la copia para el guardado
        resetTrip()
        
        // Guardar automáticamente en Firestore
        serviceScope.launch {
            try {
                Log.d("TripDetection", "   🔄 Iniciando guardado en Firestore...")
                var savedTripId: String? = null
                transportRepository.saveAutoDetectedTrip(
                    trip = trip,
                    onSuccess = { firestoreId ->
                        savedTripId = firestoreId
                        Log.d("TripDetection", "✅✅✅ TRAYECTO GUARDADO EXITOSAMENTE EN FIRESTORE ✅✅✅")
                        Log.d("TripDetection", "   🆔 Firestore ID: $firestoreId")
                        Log.d("TripDetection", "   📍 El trayecto ya está disponible en la sección de Registros")
                        
                        // Actualizar el trip con el ID de Firestore
                        val tripWithId = trip.copy(id = firestoreId)
                        
                        // Notificar el trayecto detectado (para actualizar UI si está abierta)
                        // NOTA: El trayecto ya está guardado en Firestore, así que no lo agregamos a pendingTrips
                        // Solo notificamos para que la UI se recargue
                        try {
                            // Enviar un broadcast especial para indicar que se guardó exitosamente
                            val intent = android.content.Intent(TripDetectionReceiver.ACTION_TRIP_DETECTED).apply {
                                putExtra(TripDetectionReceiver.EXTRA_TRIP, tripWithId)
                                putExtra("is_saved", true) // Indicar que ya está guardado
                                setPackage(packageName)
                            }
                            sendBroadcast(intent)
                            Log.d("TripDetection", "   📤 Broadcast enviado para recargar UI")
                        } catch (e: Exception) {
                            Log.e("TripDetection", "   ⚠️ Error al notificar trayecto: ${e.message}")
                        }
                    },
                    onError = { error ->
                        Log.e("TripDetection", "❌❌❌ ERROR AL GUARDAR TRAYECTO EN FIRESTORE ❌❌❌")
                        Log.e("TripDetection", "   Mensaje: $error")
                        
                        // Aún así, notificar para que aparezca en la UI como pendiente
                        try {
                            notifyTripDetected(trip)
                        } catch (e: Exception) {
                            Log.e("TripDetection", "   ⚠️ Error al notificar trayecto: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("TripDetection", "❌❌❌ EXCEPCIÓN AL GUARDAR TRAYECTO ❌❌❌")
                Log.e("TripDetection", "   Tipo: ${e.javaClass.simpleName}")
                Log.e("TripDetection", "   Mensaje: ${e.message}")
                Log.e("TripDetection", "   Stack trace completo:")
                e.printStackTrace()
                
                // Aún así, notificar para que aparezca en la UI como pendiente
                try {
                    notifyTripDetected(trip)
                } catch (notifyException: Exception) {
                    Log.e("TripDetection", "   ⚠️ Error al notificar trayecto: ${notifyException.message}")
                }
            } finally {
                // Liberar el flag después de que el guardado termine (exitoso o con error)
                isEndingTrip = false
            }
        }
        } catch (e: Exception) {
            Log.e("TripDetection", "❌❌❌ ERROR EN endTrip() ❌❌❌")
            Log.e("TripDetection", "   Mensaje: ${e.message}", e)
            e.printStackTrace()
            resetTrip()
            isEndingTrip = false
        }
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
        lastMovingLocation = null
        // NO resetear isEndingTrip aquí, se resetea en endTrip() después del guardado
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TripDetection", "📨 onStartCommand - Action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                Log.d("TripDetection", "▶️ Iniciando seguimiento de trayectos")
                startForegroundService()
                startLocationTracking()
            }
            ACTION_STOP_TRACKING -> {
                Log.d("TripDetection", "⏹️ Deteniendo seguimiento de trayectos")
                stopLocationTracking()
                stopSelf()
            }
            else -> {
                // Si se inicia sin acción específica, iniciar automáticamente
                Log.d("TripDetection", "▶️ Iniciando automáticamente (sin acción específica)")
                startForegroundService()
                startLocationTracking()
            }
        }
        // START_STICKY hace que el servicio se reinicie automáticamente si se detiene
        return START_STICKY
    }
    
    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val statusText = if (isTracking) {
            "Trayecto en curso - ${currentTrip.size} puntos registrados"
        } else {
            "Monitoreando desplazamientos"
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📍 EcoTracker Activo")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .apply {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
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
        
        Log.d("TripDetection", "📍 Iniciando seguimiento de ubicación")
        
        // Resetear estado
        lastLocation = null
        lastLocationTime = 0
        isTracking = false
        stationaryStartTime = null
        
        // Iniciar con modo de baja frecuencia (pero más frecuente que antes)
        switchToLowFrequencyTracking()
        
        // Iniciar sensor fusion
        sensorFusionManager?.startListening()
        Log.d("TripDetection", "✅ Sensor Fusion iniciado")
        
        Log.d("TripDetection", "✅ Seguimiento de ubicación iniciado - Modo: Baja frecuencia")
    }
    
    private fun stopLocationTracking() {
        if (isTracking) {
            endTrip()
        }
        fusedLocationClient.removeLocationUpdates(locationCallback)
        
        // Detener sensor fusion
        sensorFusionManager?.stopListening()
        Log.d("TripDetection", "✅ Sensor Fusion detenido")
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w("TripDetection", "⚠️⚠️⚠️ APP CERRADA - onTaskRemoved llamado ⚠️⚠️⚠️")
        Log.w("TripDetection", "   🔄 Reiniciando servicio para mantener detección activa...")
        
        // Si hay un trayecto activo, guardarlo antes de reiniciar (en background)
        if (isTracking && currentTrip.size >= 2 && tripStartTime != null) {
            Log.w("TripDetection", "   💾 Guardando trayecto activo antes de reiniciar...")
            serviceScope.launch {
                try {
                    endTrip()
                    // Esperar un poco para que se guarde (sin bloquear el hilo principal)
                    kotlinx.coroutines.delay(2000)
                } catch (e: Exception) {
                    Log.e("TripDetection", "   ❌ Error al guardar trayecto antes de reiniciar: ${e.message}", e)
                }
            }
        }
        
        // Reiniciar el servicio para mantener la detección activa
        val restartIntent = Intent(applicationContext, TripDetectionService::class.java).apply {
            action = ACTION_START_TRACKING
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
        
        Log.d("TripDetection", "   ✅ Servicio reiniciado después de cerrar app")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.w("TripDetection", "⚠️⚠️⚠️ onDestroy llamado ⚠️⚠️⚠️")
        
        // Si hay un trayecto activo, intentar guardarlo (en background)
        if (isTracking && currentTrip.size >= 2 && tripStartTime != null) {
            Log.w("TripDetection", "   💾 Intentando guardar trayecto activo antes de destruir servicio...")
            serviceScope.launch {
                try {
                    endTrip()
                    // Esperar un poco para que se guarde (sin bloquear el hilo principal)
                    kotlinx.coroutines.delay(2000)
                } catch (e: Exception) {
                    Log.e("TripDetection", "   ❌ Error al guardar trayecto antes de destruir: ${e.message}", e)
                }
            }
        }
        
        try {
            stopLocationTracking()
            sensorFusionManager?.stopListening()
            releaseWakeLock()
            serviceScope.cancel()
            Log.d("TripDetection", "🛑 Servicio destruido completamente")
        } catch (e: Exception) {
            Log.e("TripDetection", "❌ Error en onDestroy: ${e.message}", e)
        }
    }
}

