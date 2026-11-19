import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing comes from the git-ignored local.properties (release.storeFile, release.storePassword,
// release.keyAlias, release.keyPassword). Without it the release build stays unsigned.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.rateforx.t9tv"
    compileSdk = 36

    defaultConfig {
        // Final: already published on GitHub, so updates must keep this id (it can never change on Play either).
        applicationId = "io.github.rateforx.t9tv"
        minSdk = 21
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.1"
    }

    signingConfigs {
        localProps.getProperty("release.storeFile")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Lets the debug build live next to the release one on the same TV.
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
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
    // org.json is built into Android but only stubbed in JVM unit tests (used to parse layout files).
    testImplementation("org.json:json:20260814")
}
