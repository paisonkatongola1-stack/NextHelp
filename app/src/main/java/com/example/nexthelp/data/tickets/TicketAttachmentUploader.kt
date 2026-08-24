package com.example.nexthelp.data.tickets

import android.net.Uri
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.data.session.SessionManager
import com.example.nexthelp.domain.repository.AttachmentUploader
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads ticket attachments to Firebase Storage under `tickets/{ticketId}/attachments/`.
 *
 * Storage is resolved lazily so the app keeps working (without uploads) when
 * Firebase is not configured.
 */
@Singleton
class TicketAttachmentUploader @Inject constructor(
    private val sessionManager: SessionManager
) : AttachmentUploader {

    private val storageRef get() = runCatching { FirebaseStorage.getInstance().reference }.getOrNull()

    override suspend fun upload(ticketId: String, localUri: Uri): Resource<String> {
        if (sessionManager.currentUser.value == null) {
            return Resource.Error("You are signed out.")
        }
        val ref = storageRef?.child("tickets/$ticketId/attachments/${UUID.randomUUID()}.jpg")
            ?: return Resource.Error("Firebase Storage is not configured.")

        return try {
            ref.putFile(localUri).await()
            Resource.Success(ref.downloadUrl.await().toString())
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Upload failed. Check your connection.")
        }
    }
}
