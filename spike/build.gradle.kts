plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.reeltracker.spike"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.reeltracker.spike"
        minSdk = 28 // AccessibilityEvent.scrollDeltaY
        targetSdk = 35
        versionCode = 1
        versionName = "m0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
