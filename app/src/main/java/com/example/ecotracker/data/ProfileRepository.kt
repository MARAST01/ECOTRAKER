package com.example.ecotracker.data

import android.util.Log
import com.example.ecotracker.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
            val profile = snapshot.toObject(UserProfile::class.java) ?: UserProfile()
            profile.uid = userUid
            // Forzar 0 si no existe en documento
            profile.greenPoints = snapshot.getLong("greenPoints") ?: 0L
            profile
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Error getting profile: ${e.message}", e)
            null
        }
    }

    suspend fun getUserGreenPoints(userId: String): Long {
        return try {
            val doc = firestore.collection(usersCollection).document(userId).get().await()
            doc.getLong("greenPoints") ?: 0L
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Error getting points: ${e.message}", e)
            0L
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

    fun listenToUserProfile(userId: String, onData: (UserProfile?) -> Unit) {
        firestore.collection("users")
            .document(userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val profile = snapshot.toObject(UserProfile::class.java)
                    onData(profile)
                } else {
                    onData(null)
                }
            }
    }

    fun signOut() {
        auth.signOut()
    }
}

