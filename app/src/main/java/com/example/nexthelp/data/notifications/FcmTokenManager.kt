package com.example.nexthelp.data.notifications

import android.content.Context
import com.example.nexthelp.core.util.ApplicationScope
import com.example.nexthelp.data.session.SessionManager
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the device's FCM registration token in sync with the signed-in user by
 * storing it under `fcmTokens/{token}` in Firestore so a backend (Cloud Functions,
 * Admin SDK, etc.) can target pushes per user.
 */
@Singleton
class FcmTokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val sessionManager: SessionManager,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private var currentToken: String? = null

    /** Starts observing sessions; every sign-in re-syncs (or fetches) the token. */
    fun start() {
        appScope.launch {
            sessionManager.currentUser.collect { user ->
                if (user != null) persist()
            }
        }
    }

    fun onNewToken(token: String) {
        currentToken = token
        appScope.launch { persist() }
    }

    private suspend fun persist() {
        if (FirebaseApp.getApps(context).isEmpty()) return
        try {
            val token = currentToken ?: FirebaseMessaging.getInstance().token.await()
                .also { currentToken = it }
            val userId = sessionManager.currentUser.value?.id ?: return
            firestore.collection(TOKENS_COLLECTION)
                .document(token)
                .set(
                    mapOf(
                        TOKEN_FIELD to token,
                        USER_ID_FIELD to userId,
                        UPDATED_AT_FIELD to System.currentTimeMillis()
                    )
                )
                .await()
        } catch (_: Exception) {
            // Non-fatal; retried on next sign-in or token rotation.
        }
    }

    companion object {
        const val TOKENS_COLLECTION = "fcmTokens"
        const val TOKEN_FIELD = "token"
        const val USER_ID_FIELD = "userId"
        const val UPDATED_AT_FIELD = "updatedAt"
    }
}
