package com.example.nexthelp.domain.repository

import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.domain.models.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<User?>
    suspend fun loginWithEmail(email: String, password: String): Resource<User>
    suspend fun registerWithEmail(email: String, fullName: String, password: String): Resource<User>
    suspend fun loginWithGoogle(idToken: String): Resource<User>
    suspend fun sendPasswordResetEmail(email: String): Resource<Unit>
    suspend fun logout()
}
