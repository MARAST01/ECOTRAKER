package com.example.ecotracker.service

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.*

/**
 * Gestor de fusión de sensores para detectar movimiento y clasificar el tipo de transporte.
 * Similar a Life360, usa GPS + acelerómetro + giroscopio para detectar patrones de movimiento.
 */
class SensorFusionManager(
    private val sensorManager: SensorManager,
    private val onMovementDetected: (isMoving: Boolean, movementType: MovementType) -> Unit
) : SensorEventListener {
    
    enum class MovementType {
        STATIONARY,    // Quieto
        WALKING,       // Caminando
        RUNNING,       // Corriendo
        VEHICLE,       // En vehículo
        UNKNOWN        // Desconocido
    }
    
    // Sensores
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    
    // Datos del acelerómetro
    private val accelerometerData = FloatArray(3)
    private val accelerometerHistory = mutableListOf<FloatArray>()
    private val MAX_HISTORY_SIZE = 50 // Mantener últimos 50 valores
    
    // Datos del giroscopio
    private val gyroscopeData = FloatArray(3)
    private val gyroscopeHistory = mutableListOf<FloatArray>()
    
    // Detección de pasos
    private var stepCount = 0
    private var lastStepTime = 0L
    private var lastAccelerationMagnitude = 0f
    private var stepThreshold = 9.5f // m/s² - umbral para detectar pasos
    private val MIN_STEP_INTERVAL_MS = 200L // Mínimo 200ms entre pasos
    
    // Detección de movimiento
    private var isMoving = false
    private var currentMovementType = MovementType.STATIONARY
    private var lastMovementDetectionTime = 0L
    private val MOVEMENT_DETECTION_INTERVAL_MS = 1000L // Evaluar cada segundo
    
    // Filtros y promedios
    private val accelerationMagnitudeHistory = mutableListOf<Float>()
    private val MAX_MAGNITUDE_HISTORY = 20
    
    // Umbrales para clasificación
    private val WALKING_ACCELERATION_THRESHOLD = 2.0f // m/s²
    private val RUNNING_ACCELERATION_THRESHOLD = 5.0f // m/s²
    private val VEHICLE_ACCELERATION_THRESHOLD = 1.5f // m/s² (más suave)
    private val WALKING_GYROSCOPE_THRESHOLD = 0.5f // rad/s
    private val VEHICLE_GYROSCOPE_THRESHOLD = 0.2f // rad/s (más suave)
    
    init {
        initializeSensors()
    }
    
    private fun initializeSensors() {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        if (accelerometer == null) {
            Log.w("SensorFusion", "⚠️ Acelerómetro no disponible")
        } else {
            Log.d("SensorFusion", "✅ Acelerómetro disponible")
        }
        
        if (gyroscope == null) {
            Log.w("SensorFusion", "⚠️ Giroscopio no disponible")
        } else {
            Log.d("SensorFusion", "✅ Giroscopio disponible")
        }
    }
    
    fun startListening() {
        accelerometer?.let {
            val success = sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_UI // ~60Hz
            )
            if (success) {
                Log.d("SensorFusion", "✅ Escuchando acelerómetro")
            } else {
                Log.e("SensorFusion", "❌ Error al registrar acelerómetro")
            }
        }
        
        gyroscope?.let {
            val success = sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_UI // ~60Hz
            )
            if (success) {
                Log.d("SensorFusion", "✅ Escuchando giroscopio")
            } else {
                Log.e("SensorFusion", "❌ Error al registrar giroscopio")
            }
        }
    }
    
    fun stopListening() {
        sensorManager.unregisterListener(this)
        Log.d("SensorFusion", "🛑 Detenido escucha de sensores")
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                processAccelerometerData(event.values)
            }
            Sensor.TYPE_GYROSCOPE -> {
                processGyroscopeData(event.values)
            }
        }
        
        // Evaluar movimiento periódicamente
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastMovementDetectionTime >= MOVEMENT_DETECTION_INTERVAL_MS) {
            detectMovement()
            lastMovementDetectionTime = currentTime
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No necesario para nuestra implementación
    }
    
    private fun processAccelerometerData(values: FloatArray) {
        // Copiar valores
        System.arraycopy(values, 0, accelerometerData, 0, 3)
        
        // Calcular magnitud del vector de aceleración (sin gravedad)
        val magnitude = sqrt(
            values[0] * values[0] +
            values[1] * values[1] +
            values[2] * values[2]
        )
        
        // Filtrar gravedad usando promedio móvil
        accelerationMagnitudeHistory.add(magnitude)
        if (accelerationMagnitudeHistory.size > MAX_MAGNITUDE_HISTORY) {
            accelerationMagnitudeHistory.removeAt(0)
        }
        
        val averageMagnitude = accelerationMagnitudeHistory.average().toFloat()
        val filteredMagnitude = abs(magnitude - averageMagnitude)
        
        // Detectar pasos
        detectSteps(filteredMagnitude)
        
        // Guardar en historial
        accelerometerHistory.add(values.clone())
        if (accelerometerHistory.size > MAX_HISTORY_SIZE) {
            accelerometerHistory.removeAt(0)
        }
    }
    
    private fun processGyroscopeData(values: FloatArray) {
        // Copiar valores
        System.arraycopy(values, 0, gyroscopeData, 0, 3)
        
        // Guardar en historial
        gyroscopeHistory.add(values.clone())
        if (gyroscopeHistory.size > MAX_HISTORY_SIZE) {
            gyroscopeHistory.removeAt(0)
        }
    }
    
    private fun detectSteps(filteredMagnitude: Float) {
        val currentTime = System.currentTimeMillis()
        
        // Detectar pico de aceleración (paso)
        if (filteredMagnitude > stepThreshold &&
            lastAccelerationMagnitude < stepThreshold &&
            currentTime - lastStepTime > MIN_STEP_INTERVAL_MS) {
            
            stepCount++
            lastStepTime = currentTime
            
            // Log cada 10 pasos
            if (stepCount % 10 == 0) {
                Log.d("SensorFusion", "👣 Paso detectado - Total: $stepCount, Magnitud: ${String.format("%.2f", filteredMagnitude)} m/s²")
            }
        }
        
        lastAccelerationMagnitude = filteredMagnitude
    }
    
    private fun detectMovement() {
        if (accelerometerHistory.size < 10 || gyroscopeHistory.size < 10) {
            // No hay suficientes datos aún
            return
        }
        
        // Calcular estadísticas de aceleración
        val recentAccelerations = accelerometerHistory.takeLast(20)
        val accelerationVariance = calculateVariance(recentAccelerations) { values ->
            sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        }
        val averageAcceleration = recentAccelerations.map { values ->
            sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        }.average().toFloat()
        
        // Calcular estadísticas de giroscopio
        val recentGyroscopes = gyroscopeHistory.takeLast(20)
        val gyroscopeVariance = calculateVariance(recentGyroscopes) { values ->
            sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        }
        val averageGyroscope = recentGyroscopes.map { values ->
            sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        }.average().toFloat()
        
        // Clasificar tipo de movimiento
        val previousIsMoving = isMoving
        val previousMovementType = currentMovementType
        
        // Detectar si hay movimiento basado en pasos
        val stepsPerSecond = if (System.currentTimeMillis() - lastStepTime < 3000) {
            // Si hubo un paso en los últimos 3 segundos, calcular tasa
            val timeSinceLastStep = System.currentTimeMillis() - lastStepTime
            if (timeSinceLastStep > 0) {
                1000.0 / timeSinceLastStep
            } else {
                0.0
            }
        } else {
            0.0
        }
        
        // Clasificar movimiento
        currentMovementType = when {
            // Quieto: baja variación en aceleración y giroscopio, sin pasos
            accelerationVariance < 0.5f && gyroscopeVariance < 0.1f && stepsPerSecond < 0.5 -> {
                MovementType.STATIONARY
            }
            
            // Caminando: pasos detectados, variación moderada en aceleración
            stepsPerSecond > 0.5 && stepsPerSecond < 3.0 &&
            accelerationVariance > WALKING_ACCELERATION_THRESHOLD &&
            gyroscopeVariance > WALKING_GYROSCOPE_THRESHOLD -> {
                MovementType.WALKING
            }
            
            // Corriendo: pasos rápidos, alta variación en aceleración
            stepsPerSecond >= 3.0 &&
            accelerationVariance > RUNNING_ACCELERATION_THRESHOLD -> {
                MovementType.RUNNING
            }
            
            // Vehículo: baja variación en aceleración y giroscopio, pero hay movimiento GPS
            // (esto se combinará con datos GPS en el servicio principal)
            accelerationVariance < VEHICLE_ACCELERATION_THRESHOLD &&
            gyroscopeVariance < VEHICLE_GYROSCOPE_THRESHOLD &&
            averageAcceleration < 2.0f -> {
                MovementType.VEHICLE
            }
            
            else -> MovementType.UNKNOWN
        }
        
        isMoving = currentMovementType != MovementType.STATIONARY
        
        // Notificar cambios
        if (isMoving != previousIsMoving || currentMovementType != previousMovementType) {
            Log.d("SensorFusion", "🔄 Movimiento detectado: $currentMovementType, Pasos/seg: ${String.format("%.2f", stepsPerSecond)}, AccVar: ${String.format("%.2f", accelerationVariance)}, GyrVar: ${String.format("%.2f", gyroscopeVariance)}")
            onMovementDetected(isMoving, currentMovementType)
        }
    }
    
    private fun <T> calculateVariance(data: List<T>, valueExtractor: (T) -> Float): Float {
        if (data.isEmpty()) return 0f
        
        val values = data.map(valueExtractor)
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }
    
    fun getStepCount(): Int = stepCount
    
    fun resetStepCount() {
        stepCount = 0
        lastStepTime = 0L
    }
    
    /**
     * Obtiene la tasa de pasos por segundo basada en los pasos recientes
     */
    fun getStepsPerSecond(): Double {
        val currentTime = System.currentTimeMillis()
        if (lastStepTime == 0L || currentTime - lastStepTime > 5000) {
            // Si no hay pasos recientes (últimos 5 segundos), retornar 0
            return 0.0
        }
        
        // Calcular basado en el tiempo desde el último paso
        // Asumir que los pasos están distribuidos uniformemente
        val timeSinceLastStep = currentTime - lastStepTime
        if (timeSinceLastStep < MIN_STEP_INTERVAL_MS) {
            // Si el último paso fue muy reciente, estimar alta tasa
            return 2.0 // ~2 pasos por segundo (caminando rápido)
        }
        
        // Estimar basado en intervalo promedio entre pasos
        // Si el último paso fue hace X ms, estimar que el siguiente será en ~X ms
        return 1000.0 / timeSinceLastStep
    }
    
    fun getCurrentMovementType(): MovementType = currentMovementType
    
    fun isCurrentlyMoving(): Boolean = isMoving
}

