package com.example.nexthelp

import android.app.Application
import com.example.nexthelp.BuildConfig
import com.example.nexthelp.data.notifications.FcmTokenManager
import com.example.nexthelp.data.notifications.TicketNotifications
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NextHelpApplication : Application() {

    @Inject lateinit var fcmTokenManager: FcmTokenManager

    override fun onCreate() {
        super.onCreate()

        // Fallback manual initialization for when google-services.json is not present.
        // Values are injected from local.properties via BuildConfig, never hardcoded.
        try {
            FirebaseApp.getInstance()
        } catch (e: IllegalStateException) {
            if (BuildConfig.FIREBASE_API_KEY.isBlank()) return
            val options = FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
                .build()
            FirebaseApp.initializeApp(this, options)
        }

        TicketNotifications.ensureChannel(this)
        fcmTokenManager.start()
    }
}
