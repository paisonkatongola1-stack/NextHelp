package com.example.nexthelp.data.repository

import android.util.Log
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.domain.models.User
import com.example.nexthelp.domain.models.UserRole
import com.example.nexthelp.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthRepository"

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    private val _mockUser = MutableStateFlow<User?>(null)

    override val currentUser: Flow<User?> = callbackFlow<User?> {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            if (firebaseUser != null) {
                val email = firebaseUser.email ?: ""
                val role = if (email.trim().equals("paisonkatongola1@gmail.com", ignoreCase = true)) UserRole.ADMIN else UserRole.USER
                trySend(User(firebaseUser.uid, firebaseUser.displayName ?: "User", email, role))
            } else {
                trySend(null)
            }
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.combine(_mockUser) { firebaseUser, mockUser ->
        mockUser ?: firebaseUser
    }

    override suspend fun loginWithEmail(email: String, password: String): Resource<User> {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        
        Log.d(TAG, "Login attempt: '$trimmedEmail'")

        // THE BYPASS logic
        val isAdminEmail = trimmedEmail.equals("paisonkatongola1@gmail.com", ignoreCase = true)
        val isAdminPass = trimmedPassword == "009720@!"

        if (isAdminEmail && isAdminPass) {
            Log.d(TAG, "ADMIN BYPASS SUCCESSFUL")
            val adminUser = User(
                id = "admin-dev-id",
                fullName = "Underrated",
                email = trimmedEmail,
                role = UserRole.ADMIN
            )
            _mockUser.value = adminUser
            return Resource.Success(adminUser)
        }

        return try {
            Log.d(TAG, "Sending to Firebase Auth...")
            val result = firebaseAuth.signInWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
            val firebaseUser = result.user!!
            val role = if (trimmedEmail.equals("paisonkatongola1@gmail.com", ignoreCase = true)) UserRole.ADMIN else UserRole.USER
            val user = User(firebaseUser.uid, firebaseUser.displayName ?: "User", trimmedEmail, role)
            Resource.Success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Login Error: ${e.message}")
            Resource.Error(e.localizedMessage ?: "Login failed")
        }
    }

    override suspend fun registerWithEmail(email: String, fullName: String, password: String): Resource<User> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.equals("paisonkatongola1@gmail.com", ignoreCase = true)) {
            val user = User("admin-dev-id", "Underrated", trimmedEmail, UserRole.ADMIN)
            _mockUser.value = user
            return Resource.Success(user)
        }

        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            val user = User(result.user!!.uid, fullName, trimmedEmail, UserRole.USER)
            firestore.collection("users").document(user.id).set(user).await()
            Resource.Success(user)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Registration failed")
        }
    }

    override suspend fun logout() {
        _mockUser.value = null
        firebaseAuth.signOut()
    }

    override suspend fun loginWithGoogle(idToken: String): Resource<User> {
        return Resource.Error("Google login requires google-services.json configuration.")
    }

    override suspend fun sendPasswordResetEmail(email: String): Resource<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email.trim()).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to send reset email")
        }
    }
}
