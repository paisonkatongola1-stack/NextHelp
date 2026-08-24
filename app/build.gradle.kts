import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    // alias(libs.plugins.google.services)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(FileInputStream(file))
}

fun secret(key: String): String = "\"${localProperties.getProperty(key) ?: ""}\""

android {
    namespace = "com.example.nexthelp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.nexthelp"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Secrets live in local.properties (gitignored), never in source control.
        buildConfigField("String", "FIREBASE_API_KEY", secret("nexthelp.firebase.apiKey"))
        buildConfigField("String", "FIREBASE_APPLICATION_ID", secret("nexthelp.firebase.applicationId"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", secret("nexthelp.firebase.projectId"))
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", secret("nexthelp.firebase.storageBucket"))
        buildConfigField("String", "ADMIN_EMAILS", secret("nexthelp.adminEmails"))
        buildConfigField("String", "WEB_CLIENT_ID", secret("nexthelp.webClientId"))
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEV_ADMIN_EMAIL", secret("nexthelp.dev.adminEmail"))
            buildConfigField("String", "DEV_ADMIN_PASSWORD", secret("nexthelp.dev.adminPassword"))
        }
        release {
            buildConfigField("String", "DEV_ADMIN_EMAIL", "\"\"")
            buildConfigField("String", "DEV_ADMIN_PASSWORD", "\"\"")

            optimization {
                enable = true
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = true
        includeInBundle = false
    }
    buildToolsVersion = "36.0.0"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.storage)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.google.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    // Adaptive UI & Animations
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)

    // Credentials
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}