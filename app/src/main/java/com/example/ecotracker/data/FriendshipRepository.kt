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
            // Verificar que no exista ya una solicitud pendiente o aceptada
            val existing = getFriendshipStatus(requesterId, receiverId)
            if (existing != null) {
                Log.d("FriendshipRepository", "🔍 Solicitud existente encontrada: ID=${existing.id}, Status=${existing.status}, Requester=${existing.requesterId}, Receiver=${existing.receiverId}")
                // Solo rechazar si la solicitud está pendiente o aceptada
                // Permitir crear nueva solicitud si la anterior fue rechazada
                val status = FriendshipStatus.fromString(existing.status)
                if (status == FriendshipStatus.PENDING || status == FriendshipStatus.ACCEPTED) {
                    Log.d("FriendshipRepository", "⚠️ Solicitud ${status.value} ya existe, rechazando nueva solicitud")
                    return Result.failure(Exception("Ya existe una solicitud entre estos usuarios"))
                }
                // Si la solicitud anterior fue rechazada, podemos crear una nueva
                // pero primero eliminamos la anterior para mantener la base de datos limpia
                if (existing.id != null) {
                    db.collection(friendshipsCollection).document(existing.id!!).delete().await()
                    Log.d("FriendshipRepository", "🗑️ Solicitud rechazada anterior eliminada: ${existing.id}")
                } else {
                    Log.w("FriendshipRepository", "⚠️ Solicitud rechazada encontrada pero sin ID, no se puede eliminar")
                }
            }
            
            val now = System.currentTimeMillis()
            Log.d("FriendshipRepository", "📤 Creando nueva solicitud: Requester=$requesterId, Receiver=$receiverId")
            
            // Crear referencia de documento primero para obtener el ID
            val docRef = db.collection(friendshipsCollection).document()
            val friendship = FriendshipRequest(
                id = docRef.id, // Asignar el ID antes de guardar
                requesterId = requesterId,
                receiverId = receiverId,
                status = FriendshipStatus.PENDING.value,
                createdAt = now,
                updatedAt = now
            )
            
            // Guardar el documento con el ID incluido
            docRef.set(friendship).await()
            Log.d("FriendshipRepository", "✅ Solicitud de amistad creada: ID=${docRef.id}, Requester=$requesterId, Receiver=$receiverId, Status=${friendship.status}")
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
     * Elimina una amistad (borra el documento de la base de datos).
     * 
     * @param friendshipId ID del documento de la amistad
     * @return Result indicando éxito o error
     */
    suspend fun deleteFriendship(friendshipId: String): Result<Unit> {
        return try {
            db.collection(friendshipsCollection)
                .document(friendshipId)
                .delete()
                .await()
            
            Log.d("FriendshipRepository", "✅ Amistad eliminada: $friendshipId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FriendshipRepository", "❌ Error al eliminar amistad: ${e.message}", e)
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
            Log.d("FriendshipRepository", "🔍 Obteniendo solicitudes pendientes recibidas para usuario: $userId")
            
            // Obtener todas las solicitudes donde el usuario es el receiver
            // No filtramos por status aquí porque puede haber inconsistencias de mayúsculas/minúsculas
            val result = db.collection(friendshipsCollection)
                .whereEqualTo("receiverId", userId)
                .get()
                .await()
            
            Log.d("FriendshipRepository", "📋 Total documentos encontrados (receiverId=$userId): ${result.documents.size}")
            
            // Filtrar localmente por status pendiente (normalizando a minúsculas para comparar)
            val requests = result.documents.mapNotNull { doc ->
                val friendship = doc.toObject(FriendshipRequest::class.java)
                friendship?.id = doc.id
                
                // Normalizar el status a minúsculas para comparar
                val normalizedStatus = friendship?.status?.lowercase()
                val isPending = normalizedStatus == FriendshipStatus.PENDING.value.lowercase()
                
                Log.d("FriendshipRepository", "📄 Solicitud encontrada: ID=${friendship?.id}, Requester=${friendship?.requesterId}, Receiver=${friendship?.receiverId}, Status=${friendship?.status} (normalized=$normalizedStatus, isPending=$isPending)")
                
                // Solo incluir si es pendiente (comparando en minúsculas)
                if (isPending) {
                    // Normalizar el status en el objeto para consistencia
                    friendship.status = FriendshipStatus.PENDING.value
                    friendship
                } else {
                    null
                }
            }.sortedByDescending { it.createdAt ?: 0L }
            
            Log.d("FriendshipRepository", "✅ Solicitudes pendientes recibidas ordenadas: ${requests.size}")
            
            return requests
        } catch (e: Exception) {
            Log.e("FriendshipRepository", "❌ Error al obtener solicitudes pendientes: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Obtiene todas las solicitudes enviadas de un usuario (pendientes y rechazadas).
     * No incluye solicitudes aceptadas porque esas ya están en la lista de amigos.
     * 
     * @param userId ID del usuario
     * @return Lista de solicitudes enviadas (pendientes y rechazadas)
     */
    suspend fun getSentRequests(userId: String): List<FriendshipRequest> {
        return try {
            Log.d("FriendshipRepository", "🔍 Obteniendo solicitudes enviadas para usuario: $userId")
            
            // Query simplificada sin orderBy para evitar necesidad de índice compuesto
            // Obtener todas las solicitudes donde el usuario es el requester
            val allSentResult = db.collection(friendshipsCollection)
                .whereEqualTo("requesterId", userId)
                .get()
                .await()
            
            Log.d("FriendshipRepository", "📋 Total documentos encontrados: ${allSentResult.documents.size}")
            
            // Filtrar solo pendientes y rechazadas, excluyendo aceptadas
            val filteredRequests = allSentResult.documents.mapNotNull { doc ->
                val friendship = doc.toObject(FriendshipRequest::class.java)
                friendship?.id = doc.id
                val status = FriendshipStatus.fromString(friendship?.status)
                
                Log.d("FriendshipRepository", "📄 Solicitud encontrada: ID=${friendship?.id}, Status=$status, Receiver=${friendship?.receiverId}")
                
                // Solo incluir si es pendiente o rechazada
                if (status == FriendshipStatus.PENDING || status == FriendshipStatus.REJECTED) {
                    friendship
                } else {
                    null
                }
            }
            
            Log.d("FriendshipRepository", "✅ Solicitudes filtradas (pendientes + rechazadas): ${filteredRequests.size}")
            
            // Ordenar localmente por fecha de creación (más recientes primero)
            val sortedRequests = filteredRequests.sortedByDescending { it.createdAt ?: 0L }
            Log.d("FriendshipRepository", "✅ Solicitudes ordenadas: ${sortedRequests.size}")
            
            return sortedRequests
        } catch (e: Exception) {
            Log.e("FriendshipRepository", "❌ Error al obtener solicitudes enviadas: ${e.message}", e)
            e.printStackTrace()
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
            val profile = doc.toObject(UserProfile::class.java)
            // Asegurar que el uid esté asignado desde el ID del documento
            profile?.uid = doc.id
            profile
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
            Log.d("FriendshipRepository", "🔍 Buscando usuarios con query: $query")
            // Firestore no soporta búsqueda de texto completo, así que buscamos por email
            // En una implementación real, podrías usar Algolia o Cloud Functions
            val result = db.collection(usersCollection)
                .whereGreaterThanOrEqualTo("email", query.lowercase())
                .whereLessThanOrEqualTo("email", query.lowercase() + "\uf8ff")
                .limit(20)
                .get()
                .await()
            
            Log.d("FriendshipRepository", "📋 Usuarios encontrados: ${result.documents.size}")
            
            result.documents.mapNotNull { doc ->
                val profile = doc.toObject(UserProfile::class.java)
                // Asignar el uid desde el ID del documento (importante!)
                profile?.uid = doc.id
                Log.d("FriendshipRepository", "👤 Usuario encontrado: UID=${doc.id}, Email=${profile?.email}, FullName=${profile?.fullName}")
                // Excluir al usuario actual
                if (profile?.uid != currentUserId) {
                    profile
                } else {
                    Log.d("FriendshipRepository", "⏭️ Excluyendo usuario actual: ${doc.id}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("FriendshipRepository", "❌ Error al buscar usuarios: ${e.message}", e)
            emptyList()
        }
    }
}

