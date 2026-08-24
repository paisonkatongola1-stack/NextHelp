package com.example.nexthelp.data.profile

import android.net.Uri
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.data.session.SessionManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

enum class ProfileImageKind(val storageName: String, val firestoreField: String) {
    AVATAR("avatar", "profileImageUrl"),
    COVER("cover", "coverImageUrl")
}

/**
 * Uploads profile images to Firebase Storage under `users/{uid}/` and records the
 * download URL on the user's Firestore profile (which the session listener picks up).
 *
 * Storage is resolved lazily so the app keeps working (without uploads) when
 * Firebase is not configured.
 */
@Singleton
class ProfileImageUploader @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sessionManager: SessionManager
) {

    private val storageRef get() = runCatching { FirebaseStorage.getInstance().reference }.getOrNull()

    suspend fun upload(kind: ProfileImageKind, localUri: Uri): Resource<String> {
        val user = sessionManager.currentUser.value
            ?: return Resource.Error("You are signed out.")
        if (user.id == DEV_USER_ID) {
            return Resource.Error("Image uploads need a real account, not a dev session.")
        }
        val ref = storageRef?.child("users/${user.id}/${kind.storageName}.jpg")
            ?: return Resource.Error("Firebase Storage is not configured.")

        return try {
            ref.putFile(localUri).await()
            val url = ref.downloadUrl.await().toString()

            firestore.collection(USERS_COLLECTION).document(user.id)
                .set(mapOf(kind.firestoreField to url), SetOptions.merge())
                .await()

            // Optimistic session update; the profile listener will confirm it.
            sessionManager.update(
                if (kind == ProfileImageKind.AVATAR) user.copy(profileImageUrl = url)
                else user.copy(coverImageUrl = url)
            )
            Resource.Success(url)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Upload failed. Check your connection.")
        }
    }

    suspend fun remove(kind: ProfileImageKind): Resource<Unit> {
        val user = sessionManager.currentUser.value
            ?: return Resource.Error("You are signed out.")
        if (user.id == DEV_USER_ID) {
            return Resource.Error("Not available in a dev session.")
        }

        return try {
            storageRef?.child("users/${user.id}/${kind.storageName}.jpg")
                ?.let { runCatching { it.delete().await() } }
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
        private const val DEV_USER_ID = "admin-dev-id"
    }
}
