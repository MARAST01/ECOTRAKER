package com.example.ecotracker.data

import com.example.ecotracker.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.util.Log

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun registerWithEmail(
        fullName: String?,
        phone: String?,
        email: String,
        password: String
    ): Result<UserProfile> = try {
        auth.createUserWithEmailAndPassword(email, password).await()
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("No UID"))

        val profile = UserProfile(
            uid = uid,
            fullName = fullName,
            phone = phone,
            email = email,
            createdAt = System.currentTimeMillis()
        )
        firestore.collection("users").document(uid).set(profile).await()
        Result.success(profile)
    } catch (e: Exception) {
        Log.e("AuthRepository", "registerWithEmail failed: ${e.message}", e)
        val translatedError = translateFirebaseError(e.message ?: "")
        Result.failure(Exception(translatedError))
    }

    suspend fun loginWithEmail(email: String, password: String): Result<UserProfile> = try {
        auth.signInWithEmailAndPassword(email, password).await()
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("No UID"))

        val snap = firestore.collection("users").document(uid).get().await()
        val profile = snap.toObject(UserProfile::class.java) ?: UserProfile(uid = uid, email = email)
        Result.success(profile)
    } catch (e: Exception) {
        Log.e("AuthRepository", "loginWithEmail failed: ${e.message}", e)
        val translatedError = translateFirebaseError(e.message ?: "")
        Result.failure(Exception(translatedError))
    }

    suspend fun signInWithGoogleIdToken(idToken: String): Result<UserProfile> = try {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("No UID"))
        val email = auth.currentUser?.email

        val profile = UserProfile(
            uid = uid,
            email = email,
            createdAt = System.currentTimeMillis()
        )
        firestore.collection("users").document(uid).set(profile).await()
        Result.success(profile)
    } catch (e: Exception) {
        Log.e("AuthRepository", "signInWithGoogleIdToken failed: ${e.message}", e)
        val translatedError = translateFirebaseError(e.message ?: "")
        Result.failure(Exception(translatedError))
    }

    suspend fun currentProfile(): UserProfile? {
        val uid = auth.currentUser?.uid ?: return null
        val snap = firestore.collection("users").document(uid).get().await()
        return snap.toObject(UserProfile::class.java) ?: UserProfile(uid = uid, email = auth.currentUser?.email)
    }
    
    private fun translateFirebaseError(errorMessage: String): String {
        return when {
            errorMessage.contains("Given String is empty or null") -> "Los campos no pueden estar vacíos"
            errorMessage.contains("The email address is badly formatted") -> "El formato del correo electrónico no es válido"
            errorMessage.contains("The password is too weak") -> "La contraseña es muy débil"
            errorMessage.contains("The email address is already in use") -> "Cuenta ya existente"
            errorMessage.contains("Password should be at least 6 characters") -> "La contraseña debe tener al menos 6 caracteres"
            errorMessage.contains("Invalid email") -> "Correo electrónico inválido"
            errorMessage.contains("User not found") -> "Usuario no encontrado"
            errorMessage.contains("Wrong password") -> "Contraseña incorrecta"
            errorMessage.contains("Too many attempts") -> "Demasiados intentos fallidos. Intenta más tarde"
            else -> errorMessage
        }
    }
}
