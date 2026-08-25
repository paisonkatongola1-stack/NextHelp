package com.example.nexthelp.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Compresses an image picked from the gallery into a base64 data URL that can be
 * stored directly in Firestore. Used instead of Firebase Storage so uploads work
 * on projects without a provisioned Storage bucket.
 *
 * [maxDim] caps the longest edge; [quality] is the JPEG quality (0-100).
 */
object ImageCompressor {

    fun toDataUrl(context: Context, uri: Uri, maxDim: Int, quality: Int): String? {
        val decoded = decodeScaled(context, uri, maxDim) ?: return null
        val baos = ByteArrayOutputStream()
        decoded.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$encoded"
    }

    private fun decodeScaled(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        } ?: return null

        val scale = maxDim.toFloat() / maxOf(decoded.width, decoded.height)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }
    }
}
