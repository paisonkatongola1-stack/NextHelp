package com.example.nexthelp.domain.repository

import android.net.Uri
import com.example.nexthelp.core.util.Resource

interface AttachmentUploader {
    suspend fun upload(ticketId: String, localUri: Uri): Resource<String>
}
