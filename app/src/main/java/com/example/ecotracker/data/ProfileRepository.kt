package com.example.ecotracker.data

import android.util.Log
import com.example.ecotracker.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ProfileRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val usersCollection = "users"

    suspend fun getCurrentUserProfile(): UserProfile? {
        val userUid = auth.currentUser?.uid ?: return null
        return try {
            val snapshot = firestore.collection(usersCollection).document(userUid).get().await()
            snapshot.toObject(UserProfile::class.java)?.apply { uid = userUid }
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Error getting profile: ${e.message}", e)
            null
        }
    }

    suspend fun updateProfile(fullName: String?, email: String?): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Usuario no autenticado"))
        val uid = user.uid

        return try {
            // Actualizar email en Firebase Auth si cambió
            if (!email.isNullOrBlank() && email != user.email) {
                user.updateEmail(email).await()
            }

            // Actualizar documento en Firestore
            val updates = mutableMapOf<String, Any>()
            fullName?.let { updates["fullName"] = it }
            email?.let { updates["email"] = it }

            if (updates.isNotEmpty()) {
                firestore.collection(usersCollection).document(uid).update(updates).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Error updating profile: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}

