plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.reeltracker"
    compileSdk = 35

    defaultConfig {
        // Not "com.reeltracker": the original app (upstream download page) uses that id with another key,
        // and Android refuses to install over it. Code namespaces stay com.reeltracker.
        applicationId = "com.kishore.brainrot"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }

    buildFeatures { compose = true }

    // As Pano Scrobbler builds its release (androidApp/build.gradle.kts): R8 shrinks and optimises
    // the code and drops unused resources. Signed with the debug key (~/.android/debug.keystore;
    // CI writes it from the DEBUG_KEYSTORE_BASE64 secret), so a release installs over a debug build.
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    packaging {
        resources.excludes += listOf("/META-INF/**/*.txt", "DebugProbesKt.bin")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":service"))
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
