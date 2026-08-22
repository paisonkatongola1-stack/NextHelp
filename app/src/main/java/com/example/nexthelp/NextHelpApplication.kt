package com.example.nexthelp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NextHelpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Manual initialization in case google-services.json is missing
        try {
            FirebaseApp.getInstance()
        } catch (e: Exception) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyAsCwB5VMWjTkf2rs2gpiTUEqiZ1HnMXUI")
                .setApplicationId("1:599443092163:android:da77e393f7055cf7d46876") // Guessed from web ID
                .setProjectId("nexthelp-af2c5")
                .setStorageBucket("nexthelp-af2c5.firebasestorage.app")
                .build()
            FirebaseApp.initializeApp(this, options)
        }
    }
}
