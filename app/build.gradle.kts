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
        versionCode = 4
        versionName = "1.3"
    }

    buildFeatures { compose = true }

    // The release key: the file in SIGNING_KEYSTORE (CI writes it from the DEBUG_KEYSTORE_BASE64
    // secret); without it (local builds, the CI install check), this machine's debug key. It is a debug-type keystore, so its passwords and
    // alias are Android's standard debug ones. A named path, because CI runners may keep the
    // default debug keystore somewhere else and would then sign with a new key.
    signingConfigs {
        System.getenv("SIGNING_KEYSTORE")?.let { path ->
            create("release") {
                val debug = getByName("debug")
                storeFile = file(path)
                storePassword = debug.storePassword
                keyAlias = debug.keyAlias
                keyPassword = debug.keyPassword
            }
        }
    }

    // As Pano Scrobbler builds its release (androidApp/build.gradle.kts): R8 shrinks and optimises
    // the code and drops unused resources. Signed with the release key above, so it installs over
    // a debug build made on the same machine.
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
