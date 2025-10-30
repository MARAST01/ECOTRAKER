package com.example.ecotracker.data

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
                val records = snapshot.documents.mapNotNull { it.toObject(TransportRecord::class.java) }
                    .sortedByDescending { it.timestamp }
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
            snapshot.documents.mapNotNull { it.toObject(TransportRecord::class.java) }
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

            println("DEBUG: Obteniendo registros desde $startDateString hasta $currentDateString")

            // Query simplificada sin orderBy para evitar el problema del índice
            val query = db.collection(transportCollection)
                .whereEqualTo("userId", userId)

            val snapshot = query.get().await()
            println("DEBUG: Total registros encontrados: ${snapshot.documents.size}")

            val records = snapshot.documents.mapNotNull { doc ->
                val record = doc.toObject(TransportRecord::class.java)
                record
            }.filter { record ->
                // Filtrar por rango de fechas
                val recordDate = record.date
                recordDate != null && recordDate >= startDateString && recordDate <= currentDateString
            }.sortedByDescending { it.timestamp } // Ordenar localmente

            println("DEBUG: Registros filtrados por fecha: ${records.size}")
            records
        } catch (e: Exception) {
            println("DEBUG: Error al obtener registros paginados: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}
