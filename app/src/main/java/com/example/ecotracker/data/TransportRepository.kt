package com.example.ecotracker.data

import com.example.ecotracker.data.model.TransportRecord
import com.example.ecotracker.data.model.TransportType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransportRepository {
    private val db = FirebaseFirestore.getInstance()
    private val transportCollection = "transport_records"
    
    suspend fun saveTransportRecord(
        userId: String,
        transportType: TransportType,
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
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
            
            val snapshot = query.get().await()
            
            if (snapshot.isEmpty) {
                null
            } else {
                snapshot.documents.first().toObject(TransportRecord::class.java)
            }
        } catch (e: Exception) {
            null
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
}
