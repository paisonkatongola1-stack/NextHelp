package com.example.nexthelp.data.tickets

import android.content.Context
import android.net.Uri
import com.example.nexthelp.core.util.ImageCompressor
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.data.session.SessionManager
import com.example.nexthelp.domain.repository.AttachmentUploader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces ticket attachments as compressed base64 data URLs stored directly on
 * the comment document — no Firebase Storage required.
 */
@Singleton
class TicketAttachmentUploader @Inject constructor(
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context
) : AttachmentUploader {

    override suspend fun upload(ticketId: String, localUri: Uri): Resource<String> {
        if (sessionManager.currentUser.value == null) {
            return Resource.Error("You are signed out.")
        }
        val dataUrl = ImageCompressor.toDataUrl(context, localUri, maxDim = 1280, quality = 75)
            ?: return Resource.Error("Couldn't read that image. Try another one.")
        return Resource.Success(dataUrl)
    }
}
