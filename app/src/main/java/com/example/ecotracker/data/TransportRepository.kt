package com.example.ecotracker.data

import android.util.Log
import com.example.ecotracker.data.model.TransportRecord
import com.example.ecotracker.data.model.TransportType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TransportRepository {
    private val db = FirebaseFirestore.getInstance()
    private val transportCollection = "transport_records"

    suspend fun saveTransportRecord(
        userId: String,
        transportType: TransportType,
        hour: String,
        distance: Double?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val currentTime = System.currentTimeMillis()

            val record = TransportRecord(
                userId = userId,
                transportType = transportType,
                date = currentDate,
                timestamp = currentTime,
                hour = hour,
                distance = distance,
                emissionFactor = transportType.emissionFactor,
                createdAt = currentTime
            )

            db.collection(transportCollection)
                .add(record)
                .await()

            onSuccess()
        } catch (e: Exception) {
            onError("Error al guardar el registro: ${e.message}")
        }
    }

    suspend fun saveAutoDetectedTrip(
        trip: TransportRecord,
        onSuccess: (String) -> Unit, // Cambiado para pasar el ID de Firestore
        onError: (String) -> Unit
    ) {
        try {
            Log.d("TransportRepository", "💾💾💾 INICIANDO GUARDADO DE TRAYECTO 💾💾💾")
            Log.d("TransportRepository", "   📅 Fecha: ${trip.date}")
            Log.d("TransportRepository", "   📏 Distancia: ${trip.distance} km")
            Log.d("TransportRepository", "   👤 UserId: ${trip.userId}")
            Log.d("TransportRepository", "   🚗 Tipo: ${trip.transportType?.displayName ?: "Pendiente"}")
            Log.d("TransportRepository", "   ✅ isAutoDetected: ${trip.isAutoDetected}")
            Log.d("TransportRepository", "   ✅ isConfirmed: ${trip.isConfirmed}")
            Log.d("TransportRepository", "   📍 Puntos GPS: ${trip.routePoints?.size ?: 0}")
            Log.d("TransportRepository", "   📍 StartLocation: ${trip.startLocation?.latitude}, ${trip.startLocation?.longitude}")
            Log.d("TransportRepository", "   📍 EndLocation: ${trip.endLocation?.latitude}, ${trip.endLocation?.longitude}")
            Log.d("TransportRepository", "   ⏰ StartTime: ${trip.startTime}")
            Log.d("TransportRepository", "   ⏰ EndTime: ${trip.endTime}")
            Log.d("TransportRepository", "   ⏱️ Duración: ${trip.duration} ms")
            Log.d("TransportRepository", "   🚗 Velocidad promedio: ${trip.averageSpeed} km/h")
            
            Log.d("TransportRepository", "   🔄 Agregando documento a Firestore...")
            Log.d("TransportRepository", "   ⏳ Esperando respuesta de Firestore...")
            val result = db.collection(transportCollection)
                .add(trip)
                .await()
            
            Log.d("TransportRepository", "✅✅✅ TRAYECTO GUARDADO EXITOSAMENTE EN FIRESTORE ✅✅✅")
            Log.d("TransportRepository", "   🆔 Firestore ID: ${result.id}")
            Log.d("TransportRepository", "   📍 Colección: $transportCollection")
            Log.d("TransportRepository", "   📅 Fecha guardada: ${trip.date}")
            Log.d("TransportRepository", "   👤 UserId guardado: ${trip.userId}")
            Log.d("TransportRepository", "   ✅ Llamando onSuccess() con ID: ${result.id}...")
            
            try {
                onSuccess(result.id)
                Log.d("TransportRepository", "   ✅ onSuccess() completado correctamente")
            } catch (e: Exception) {
                Log.e("TransportRepository", "   ❌ Error al llamar onSuccess(): ${e.message}", e)
                throw e
            }
        } catch (e: Exception) {
            Log.e("TransportRepository", "❌❌❌ ERROR AL GUARDAR TRAYECTO EN FIRESTORE ❌❌❌")
            Log.e("TransportRepository", "   Tipo de excepción: ${e.javaClass.simpleName}")
            Log.e("TransportRepository", "   Mensaje: ${e.message}")
            Log.e("TransportRepository", "   Causa: ${e.cause?.message}")
            Log.e("TransportRepository", "   Stack trace completo:")
            e.printStackTrace()
            
            val errorMessage = "Error al guardar el trayecto detectado: ${e.message ?: "Error desconocido"}"
            Log.e("TransportRepository", "   📤 Llamando onError() con mensaje: $errorMessage")
            onError(errorMessage)
        }
    }
    
    suspend fun updateTripTransportType(
        tripId: String,
        transportType: TransportType
    ): Boolean {
        return try {
            Log.d("TransportRepository", "🔄 Actualizando tipo de transporte del trayecto")
            Log.d("TransportRepository", "   🆔 Trip ID: $tripId")
            Log.d("TransportRepository", "   🚗 Tipo: ${transportType.displayName}")
            
            // Firestore usa "confirmed" en lugar de "isConfirmed"
            db.collection(transportCollection)
                .document(tripId)
                .update(
                    "transportType", transportType.name,
                    "emissionFactor", transportType.emissionFactor,
                    "confirmed", true // Usar "confirmed" para Firestore
                )
                .await()
            
            Log.d("TransportRepository", "✅✅✅ TRAYECTO ACTUALIZADO EXITOSAMENTE ✅✅✅")
            true
        } catch (e: Exception) {
            Log.e("TransportRepository", "❌ Error al actualizar trayecto: ${e.message}", e)
            false
        }
    }

    suspend fun getTodayTransportRecord(userId: String): TransportRecord? {
        return try {
            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            val query = db.collection(transportCollection)
                .whereEqualTo("userId", userId)
                .whereEqualTo("date", currentDate)

            val snapshot = query.get().await()

            if (snapshot.isEmpty) {
                null
            } else {
                // Obtener el registro más reciente ordenando localmente
                val records = snapshot.documents.mapNotNull { doc ->
                    val record = doc.toObject(TransportRecord::class.java)
                    record?.id = doc.id
                    record
                }.sortedByDescending { it.timestamp }
                records.firstOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getTodayTransportRecords(userId: String): List<TransportRecord> {
        return try {
            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            println("DEBUG: Buscando registros para userId: $userId, fecha: $currentDate")

            // Query simplificada sin orderBy para evitar el problema del índice
            val query = db.collection(transportCollection)
                .whereEqualTo("userId", userId)
                .whereEqualTo("date", currentDate)

            val snapshot = query.get().await()
            println("DEBUG: Encontrados ${snapshot.documents.size} documentos para hoy")

            val records = snapshot.documents.mapNotNull { doc ->
                val record = doc.toObject(TransportRecord::class.java)
                record?.id = doc.id
                println("DEBUG: Registro encontrado: ${record?.transportType?.displayName} a las ${record?.hour}")
                record
            }.sortedByDescending { it.timestamp } // Ordenar localmente

            println("DEBUG: Total registros procesados: ${records.size}")
            records
        } catch (e: Exception) {
            println("DEBUG: Error al obtener registros: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getUserTransportHistory(userId: String, limit: Int = 30): List<TransportRecord> {
        return try {
            val query = db.collection(transportCollection)
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())

            val snapshot = query.get().await()
            snapshot.documents.mapNotNull { doc ->
                val record = doc.toObject(TransportRecord::class.java)
                record?.id = doc.id
                record
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Método alternativo para obtener registros sin filtro de fecha
    suspend fun getAllUserTransportRecords(userId: String): List<TransportRecord> {
        return try {
            println("DEBUG: Obteniendo TODOS los registros del usuario: $userId")

            // Query simplificada sin orderBy para evitar el problema del índice
            val query = db.collection(transportCollection)
                .whereEqualTo("userId", userId)

            val snapshot = query.get().await()
            println("DEBUG: Total registros encontrados: ${snapshot.documents.size}")

            val records = snapshot.documents.mapNotNull { doc ->
                val record = doc.toObject(TransportRecord::class.java)
                record?.id = doc.id
                println("DEBUG: Registro - Fecha: ${record?.date}, Tipo: ${record?.transportType?.displayName}, Hora: ${record?.hour}")
                record
            }.sortedByDescending { it.timestamp } // Ordenar localmente

            records
        } catch (e: Exception) {
            println("DEBUG: Error al obtener todos los registros: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    // Método para obtener registros con paginación (últimos 15 días)
    suspend fun getUserTransportRecordsPaginated(userId: String, daysBack: Int = 15): List<TransportRecord> {
        return try {
            val currentDate = Date()
            val calendar = Calendar.getInstance()
            calendar.time = currentDate
            calendar.add(Calendar.DAY_OF_YEAR, -daysBack)
            val startDate = calendar.time

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startDateString = dateFormat.format(startDate)
            val currentDateString = dateFormat.format(currentDate)

            Log.d("TransportRepository", "🔍 Obteniendo registros desde $startDateString hasta $currentDateString")
            Log.d("TransportRepository", "   👤 UserId: $userId")

            // Query simplificada sin orderBy para evitar el problema del índice
            val query = db.collection(transportCollection)
                .whereEqualTo("userId", userId)

            Log.d("TransportRepository", "   ⏳ Ejecutando query en Firestore...")
            val snapshot = query.get().await()
            Log.d("TransportRepository", "   ✅ Query completada - Total documentos encontrados: ${snapshot.documents.size}")

            val records = snapshot.documents.mapNotNull { doc ->
                val record = doc.toObject(TransportRecord::class.java)
                if (record != null) {
                    // Asignar el ID del documento al registro
                    record.id = doc.id
                    
                    Log.d("TransportRepository", "📋 Registro encontrado:")
                    Log.d("TransportRepository", "   🆔 ID: ${doc.id}")
                    Log.d("TransportRepository", "   📅 Fecha: ${record.date}")
                    Log.d("TransportRepository", "   👤 UserId: ${record.userId}")
                    Log.d("TransportRepository", "   🚗 Tipo: ${record.transportType?.displayName ?: "Pendiente"}")
                    Log.d("TransportRepository", "   ✅ isAutoDetected: ${record.isAutoDetected}")
                    Log.d("TransportRepository", "   ✅ isConfirmed: ${record.isConfirmed}")
                    Log.d("TransportRepository", "   ⏰ Timestamp: ${record.timestamp}")
                    Log.d("TransportRepository", "   📍 Puntos GPS: ${record.routePoints?.size ?: 0}")
                } else {
                    Log.e("TransportRepository", "   ❌ Error: No se pudo deserializar el documento ${doc.id}")
                }
                record
            }.filter { record ->
                // Filtrar por rango de fechas
                val recordDate = record.date
                val isInRange = recordDate != null && recordDate >= startDateString && recordDate <= currentDateString
                if (!isInRange && recordDate != null) {
                    Log.d("TransportRepository", "   ⚠️ Registro fuera de rango: $recordDate (rango: $startDateString - $currentDateString)")
                }
                isInRange
            }.sortedByDescending { it.timestamp } // Ordenar localmente

            Log.d("TransportRepository", "✅ Registros filtrados por fecha: ${records.size} de ${snapshot.documents.size} totales")
            records
        } catch (e: Exception) {
            println("DEBUG: Error al obtener registros paginados: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}
