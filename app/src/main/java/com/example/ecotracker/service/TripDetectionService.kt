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
    private val STATIONARY_THRESHOLD_MS = 240000L // 4 minutos sin movimiento (240 segundos)
    private val MIN_DISTANCE_METERS = 10.0 // Mínimo 10 metros para considerar un trayecto (reducido para detectar trayectos cortos a pie)
    private val MOVEMENT_SPEED_THRESHOLD_WALKING = 0.3 // m/s (1.08 km/h) - para caminar (reducido para ser más sensible)
    private val MOVEMENT_SPEED_THRESHOLD_VEHICLE = 2.0 // m/s (7.2 km/h) - para vehículos
    private val MOVEMENT_DISTANCE_THRESHOLD = 3.0 // Mínimo 3 metros de desplazamiento (reducido para caminar)
    private val VEHICLE_SPEED_THRESHOLD = 5.0 // m/s (18 km/h) - velocidad típica de vehículo
    
    // Callback para notificar cuando se detecta un trayecto
    var onTripDetected: ((TransportRecord) -> Unit)? = null
    
    private fun notifyTripDetected(trip: TransportRecord) {
        Log.d("TripDetection", "📨 Notificando trayecto detectado - ID: ${trip.id}, Distancia: ${trip.distance} km")
        
        // Notificar mediante callback si está disponible
        onTripDetected?.invoke(trip)
        
        // También enviar broadcast
        val intent = android.content.Intent(TripDetectionReceiver.ACTION_TRIP_DETECTED).apply {
            putExtra(TripDetectionReceiver.EXTRA_TRIP, trip)
            setPackage(packageName) // Especificar el paquete para seguridad
        }
        
        // Enviar broadcast sin restricciones ya que es interno a la app
        try {
            sendBroadcast(intent)
            Log.d("TripDetection", "✅ Broadcast enviado correctamente")
        } catch (e: Exception) {
            Log.e("TripDetection", "❌ Error al enviar broadcast: ${e.message}")
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
                Log.d("TripDetection", "📍 onLocationResult - ${result.locations.size} ubicaciones recibidas")
                // Procesar todas las ubicaciones del resultado, no solo la última
                result.locations.forEach { location ->
                    processLocationUpdate(location)
                }
            }
        }
        Log.d("TripDetection", "✅ LocationCallback configurado")
    }
    
    private fun processLocationUpdate(location: Location) {
        serviceScope.launch {
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
            
            // Usar umbrales diferentes según si es vehículo o caminando
            val speedThreshold = if (isLikelyVehicle) {
                MOVEMENT_SPEED_THRESHOLD_VEHICLE
            } else {
                MOVEMENT_SPEED_THRESHOLD_WALKING
            }
            
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
                // IMPORTANTE: Para finalizar trayectos, confiar más en GPS que en sensores
                // Los sensores pueden detectar movimiento del teléfono aunque estés quieto
                if (isTracking) {
                    // Estamos en un trayecto pero ahora estamos quietos según GPS
                    // NO agregar más puntos al trayecto cuando está quieto
                    
                    // Verificar si realmente no hay movimiento GPS (no solo sensores)
                    val gpsIsStationary = !gpsBasedMovement && distance < 5.0 && reportedSpeed < 0.5
                    
                    if (gpsIsStationary) {
                        if (stationaryStartTime == null) {
                            stationaryStartTime = currentTime
                            val waitTimeMinutes = STATIONARY_THRESHOLD_MS / 60000
                            Log.d("TripDetection", "⏸️⏸️⏸️ GPS DETECTA QUIETO - Esperando ${waitTimeMinutes} minutos para finalizar trayecto ⏸️⏸️⏸️")
                            Log.d("TripDetection", "   📍 Última ubicación con movimiento: Lat: ${lastMovingLocation?.latitude ?: location.latitude}, Lng: ${lastMovingLocation?.longitude ?: location.longitude}")
                            Log.d("TripDetection", "   📍 Ubicación actual: Lat: ${location.latitude}, Lng: ${location.longitude}")
                            Log.d("TripDetection", "   📊 Estado: Puntos: ${currentTrip.size}, Distancia: ${String.format("%.2f", calculateTotalDistance(currentTrip))}m")
                            Log.d("TripDetection", "   📱 Sensores: Movimiento: $sensorMovementDetected, Tipo: $sensorMovementType (ignorado para finalización)")
                        } else {
                            val timeStationary = currentTime - stationaryStartTime!!
                            val remainingTime = STATIONARY_THRESHOLD_MS - timeStationary
                            
                            // Log cada 30 segundos para monitorear el progreso
                            if (timeStationary % 30000 < 2000) {
                                val minutesStationary = timeStationary / 60000
                                val secondsStationary = (timeStationary % 60000) / 1000
                                val minutesRemaining = remainingTime / 60000
                                val secondsRemaining = (remainingTime % 60000) / 1000
                                Log.d("TripDetection", "⏸️ GPS QUIETO: ${minutesStationary}m ${secondsStationary}s - Faltan ${minutesRemaining}m ${secondsRemaining}s para finalizar")
                            }
                            
                            // Si llevamos más de STATIONARY_THRESHOLD_MS quietos según GPS, finalizar trayecto
                            if (timeStationary >= STATIONARY_THRESHOLD_MS) {
                                val minutesStationary = timeStationary / 60000
                                val secondsStationary = (timeStationary % 60000) / 1000
                                Log.d("TripDetection", "⏹️⏹️⏹️⏹️⏹️ TIEMPO COMPLETADO (${minutesStationary}m ${secondsStationary}s) - FINALIZANDO TRAYECTO ⏹️⏹️⏹️⏹️⏹️")
                                Log.d("TripDetection", "   📊 Estado final: Puntos: ${currentTrip.size}, Distancia: ${String.format("%.2f", calculateTotalDistance(currentTrip))}m")
                                
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
                            }
                        }
                    } else {
                        // El GPS aún muestra movimiento, resetear contador estacionario
                        if (stationaryStartTime != null) {
                            Log.d("TripDetection", "🔄 GPS detecta movimiento nuevamente - Cancelando finalización")
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
        }
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
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopLocationTracking()
        sensorFusionManager?.stopListening()
        releaseWakeLock()
        serviceScope.cancel()
        Log.d("TripDetection", "🛑 Servicio destruido completamente")
    }
}

