package com.example.nexthelp.data.profile

import android.content.Context
import android.net.Uri
import com.example.nexthelp.core.util.ImageCompressor
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.data.session.SessionManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

enum class ProfileImageKind(val firestoreField: String) {
    AVATAR("profileImageUrl"),
    COVER("coverImageUrl")
}

/**
 * Stores profile images directly on the user's Firestore document as compressed
 * base64 data URLs (avatars ~60KB). This avoids a Firebase Storage dependency so
 * image uploads work on any project, with or without a Storage bucket.
 */
@Singleton
class ProfileImageUploader @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context
) {

    suspend fun upload(kind: ProfileImageKind, localUri: Uri): Resource<String> {
        val user = sessionManager.currentUser.value
            ?: return Resource.Error("You are signed out.")
        val dataUrl = ImageCompressor.toDataUrl(context, localUri, maxDim = 512, quality = 82)
            ?: return Resource.Error("Couldn't read that image. Try another photo.")

        return try {
            firestore.collection(USERS_COLLECTION).document(user.id)
                .set(mapOf(kind.firestoreField to dataUrl), SetOptions.merge())
                .await()

            // Optimistic session update; the profile listener will confirm it.
            sessionManager.update(
                if (kind == ProfileImageKind.AVATAR) user.copy(profileImageUrl = dataUrl)
                else user.copy(coverImageUrl = dataUrl)
            )
            Resource.Success(dataUrl)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Upload failed. Check your connection.")
        }
    }

    suspend fun remove(kind: ProfileImageKind): Resource<Unit> {
        val user = sessionManager.currentUser.value
            ?: return Resource.Error("You are signed out.")

        return try {
            firestore.collection(USERS_COLLECTION).document(user.id)
                .set(mapOf(kind.firestoreField to null), SetOptions.merge())
                .await()
            sessionManager.update(
                if (kind == ProfileImageKind.AVATAR) user.copy(profileImageUrl = null)
                else user.copy(coverImageUrl = null)
            )
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to remove image")
        }
    }

    companion object {
        private const val USERS_COLLECTION = "users"
    }
}
