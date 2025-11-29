package com.example.ecotracker.data

import android.util.Log
import com.example.ecotracker.data.model.FriendshipRequest
import com.example.ecotracker.data.model.FriendshipStatus
import com.example.ecotracker.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Repositorio para gestionar relaciones de amistad entre usuarios.
 * Soporta relaciones bidireccionales (A ↔ B).
 */
class FriendshipRepository {
    private val db = FirebaseFirestore.getInstance()
    private val friendshipsCollection = "friendships"
    private val usersCollection = "users"
    
    /**
     * Consulta el estado de amistad entre dos usuarios.
     * Busca en ambas direcciones (A → B y B → A) para soportar relaciones bidireccionales.
     * 
     * @param userId1 ID del primer usuario
     * @param userId2 ID del segundo usuario
     * @return FriendshipRequest si existe una relación, null si no hay relación
     */
    suspend fun getFriendshipStatus(userId1: String, userId2: String): FriendshipRequest? {
        return try {
            Log.d("FriendshipRepository", "🔍 Consultando estado de amistad entre $userId1 y $userId2")
            
            // Buscar en ambas direcciones: (A → B) o (B → A)
            val query1 = db.collection(friendshipsCollection)
                .whereEqualTo("requesterId", userId1)
                .whereEqualTo("receiverId", userId2)
                .limit(1)
            
            val query2 = db.collection(friendshipsCollection)
                .whereEqualTo("requesterId", userId2)
                .whereEqualTo("receiverId", userId1)
                .limit(1)
            
            // Ejecutar ambas consultas
            val result1 = query1.get().await()
            val result2 = query2.get().await()
            
            // Si hay resultado en la primera consulta
            if (!result1.isEmpty) {
                val doc = result1.documents.first()
                val friendship = doc.toObject(FriendshipRequest::class.java)
                friendship?.id = doc.id
                Log.d("FriendshipRepository", "✅ Relación encontrada (dirección 1): ${friendship?.status}")
                return friendship
            }
            
            // Si hay resultado en la segunda consulta
            if (!result2.isEmpty) {
                val doc = result2.documents.first()
                val friendship = doc.toObject(FriendshipRequest::class.java)
                friendship?.id = doc.id
                Log.d("FriendshipRepository", "✅ Relación encontrada (dirección 2): ${friendship?.status}")
                return friendship
            }
            
            Log.d("FriendshipRepository", "ℹ️ No se encontró relación entre los usuarios")
            null
        } catch (e: Exception) {
            Log.e("FriendshipRepository", "❌ Error al consultar estado de amistad: ${e.message}", e)
            null
        }
    }
    
    /**
     * Crea una nueva solicitud de amistad.
     * 
     * @param requesterId ID del usuario que envía la solicitud
     * @param receiverId ID del usuario que recibe la solicitud
     * @return Result con el ID del documento creado o error
     */
    suspend fun sendFriendshipRequest(requesterId: String, receiverId: String): Result<String> {
        return try {
            // Verificar que no exista ya una solicitud
            val existing = getFriendshipStatus(requesterId, receiverId)
            if (existing != null) {
                return Result.failure(Exception("Ya existe una solicitud entre estos usuarios"))
            }
            
            val now = System.currentTimeMillis()
            val friendship = FriendshipRequest(
                requesterId = requesterId,
                receiverId = receiverId,
                status = FriendshipStatus.PENDING.value,
                createdAt = now,
                updatedAt = now
            )
            
            val docRef = db.collection(friendshipsCollection).add(friendship).await()
            Log.d("FriendshipRepository", "✅ Solicitud de amistad creada: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("FriendshipRepository", "❌ Error al enviar solicitud: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Acepta una solicitud de amistad.
     * 
     * @param friendshipId ID del documento de la solicitud
     * @return Result indicando éxito o error
     */
    suspend fun acceptFriendshipRequest(friendshipId: String): Result<Unit> {
        return try {
            db.collection(friendshipsCollection)
                .document(friendshipId)
                .update(
                    mapOf(
                        "status" to FriendshipStatus.ACCEPTED.value,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
            
            Log.d("FriendshipRepository", "✅ Solicitud aceptada: $friendshipId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FriendshipRepository", "❌ Error al aceptar solicitud: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Rechaza una solicitud de amistad.
     * 
     * @param friendshipId ID del documento de la solicitud
     * @return Result indicando éxito o error
     */
    suspend fun rejectFriendshipRequest(friendshipId: String): Result<Unit> {
        return try {
            db.collection(friendshipsCollection)
                .document(friendshipId)
                .update(
                    mapOf(
                        "status" to FriendshipStatus.REJECTED.value,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
            
            Log.d("FriendshipRepository", "✅ Solicitud rechazada: $friendshipId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FriendshipRepository", "❌ Error al rechazar solicitud: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtiene todas las solicitudes recibidas pendientes de un usuario.
     * 
     * @param userId ID del usuario
     * @return Lista de solicitudes pendientes
     */
    suspend fun getPendingRequests(userId: String): List<FriendshipRequest> {
        return try {
            val result = db.collection(friendshipsCollection)
                .whereEqualTo("receiverId", userId)
                .whereEqualTo("status", FriendshipStatus.PENDING.value)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            
            result.documents.mapNotNull { doc ->
                val friendship = doc.toObject(FriendshipRequest::class.java)
                friendship?.id = doc.id
                friendship
            }
        } catch (e: Exception) {
            Log.e("FriendshipRepository", "❌ Error al obtener solicitudes pendientes: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Obtiene todas las solicitudes enviadas pendientes de un usuario.
     * 
     * @param userId ID del usuario
     * @return Lista de solicitudes pendientes
     */
    suspend fun getSentRequests(userId: String): List<FriendshipRequest> {
        return try {
            val result = db.collection(friendshipsCollection)
                .whereEqualTo("requesterId", userId)
                .whereEqualTo("status", FriendshipStatus.PENDING.value)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            
            result.documents.mapNotNull { doc ->
                val friendship = doc.toObject(FriendshipRequest::class.java)
                friendship?.id = doc.id
                friendship
            }
        } catch (e: Exception) {
            Log.e("FriendshipRepository", "❌ Error al obtener solicitudes enviadas: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Obtiene todas las amistades aceptadas de un usuario.
     * 
     * @param userId ID del usuario
     * @return Lista de amistades aceptadas
     */
    suspend fun getAcceptedFriendships(userId: String): List<FriendshipRequest> {
        return try {
            // Buscar en ambas direcciones: donde el usuario es requester o receiver
            val result1 = db.collection(friendshipsCollection)
                .whereEqualTo("requesterId", userId)
                .whereEqualTo("status", FriendshipStatus.ACCEPTED.value)
                .get()
                .await()
            
            val result2 = db.collection(friendshipsCollection)
                .whereEqualTo("receiverId", userId)
                .whereEqualTo("status", FriendshipStatus.ACCEPTED.value)
                .get()
                .await()
            
            val allFriendships = mutableListOf<FriendshipRequest>()
            
            result1.documents.forEach { doc ->
                val friendship = doc.toObject(FriendshipRequest::class.java)
                friendship?.id = doc.id
                friendship?.let { allFriendships.add(it) }
            }
            
            result2.documents.forEach { doc ->
                val friendship = doc.toObject(FriendshipRequest::class.java)
                friendship?.id = doc.id
                friendship?.let { allFriendships.add(it) }
            }
            
            // Ordenar por fecha de actualización (más recientes primero)
            allFriendships.sortedByDescending { it.updatedAt ?: it.createdAt ?: 0L }
        } catch (e: Exception) {
            Log.e("FriendshipRepository", "❌ Error al obtener amistades: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Obtiene el perfil de un usuario por su ID.
     * 
     * @param userId ID del usuario
     * @return UserProfile o null si no existe
     */
    suspend fun getUserProfile(userId: String): UserProfile? {
        return try {
            val doc = db.collection(usersCollection).document(userId).get().await()
            doc.toObject(UserProfile::class.java)
        } catch (e: Exception) {
            Log.e("FriendshipRepository", "❌ Error al obtener perfil: ${e.message}", e)
            null
        }
    }
    
    /**
     * Busca usuarios por email o nombre.
     * 
     * @param query Texto de búsqueda
     * @param currentUserId ID del usuario actual (para excluirlo de los resultados)
     * @return Lista de perfiles de usuario que coinciden
     */
    suspend fun searchUsers(query: String, currentUserId: String): List<UserProfile> {
        return try {
            // Firestore no soporta búsqueda de texto completo, así que buscamos por email
            // En una implementación real, podrías usar Algolia o Cloud Functions
            val result = db.collection(usersCollection)
                .whereGreaterThanOrEqualTo("email", query.lowercase())
                .whereLessThanOrEqualTo("email", query.lowercase() + "\uf8ff")
                .limit(20)
                .get()
                .await()
            
            result.documents.mapNotNull { doc ->
                val profile = doc.toObject(UserProfile::class.java)
                // Excluir al usuario actual
                if (profile?.uid != currentUserId) profile else null
            }
        } catch (e: Exception) {
            Log.e("FriendshipRepository", "❌ Error al buscar usuarios: ${e.message}", e)
            emptyList()
        }
    }
}

