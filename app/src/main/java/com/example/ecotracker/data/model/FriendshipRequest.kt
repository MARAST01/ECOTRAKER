package com.example.ecotracker.data.model

import androidx.annotation.Keep
import com.google.firebase.firestore.PropertyName

/**
 * Modelo para representar una solicitud de amistad entre dos usuarios.
 * Soporta relaciones bidireccionales (A ↔ B).
 * 
 * Estructura en Firestore:
 * - Colección: "friendships"
 * - Documento ID: generado automáticamente por Firestore
 * 
 * Para consultar el estado entre dos usuarios, se busca:
 * - (requesterId = A AND receiverId = B) OR (requesterId = B AND receiverId = A)
 */
@Keep
data class FriendshipRequest(
    var id: String? = null,
    
    /**
     * ID del usuario que envía la solicitud
     */
    var requesterId: String? = null,
    
    /**
     * ID del usuario que recibe la solicitud
     */
    var receiverId: String? = null,
    
    /**
     * Estado de la solicitud:
     * - pending: Pendiente de respuesta
     * - accepted: Aceptada (amistad establecida)
     * - rejected: Rechazada
     *
     * Se almacena como String para coincidir exactamente con los filtros en Firestore.
     */
    @get:PropertyName("status")
    @set:PropertyName("status")
    var status: String = FriendshipStatus.PENDING.value,
    
    /**
     * Fecha de creación de la solicitud (timestamp en milisegundos)
     */
    var createdAt: Long? = null,
    
    /**
     * Fecha de actualización del estado (timestamp en milisegundos)
     */
    var updatedAt: Long? = null
) {
    constructor() : this(null, null, null, FriendshipStatus.PENDING.value, null, null)
    
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Enum que representa los estados posibles de una solicitud de amistad
 */
enum class FriendshipStatus(val value: String) {
    PENDING("pending"),
    ACCEPTED("accepted"),
    REJECTED("rejected");
    
    companion object {
        fun fromString(value: String?): FriendshipStatus {
            return when (value?.lowercase()) {
                "pending" -> PENDING
                "accepted" -> ACCEPTED
                "rejected" -> REJECTED
                else -> PENDING
            }
        }
    }
}

