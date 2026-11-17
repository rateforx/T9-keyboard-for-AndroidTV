plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.rateforx.t9tv"
    compileSdk = 36

    defaultConfig {
        // TODO(M3): settle the final applicationId before the first Play upload (it can never change).
        applicationId = "io.github.rateforx.t9tv"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // No runtime dependencies on purpose (no AndroidX) — framework APIs only.
    testImplementation("junit:junit:4.13.2")
}
