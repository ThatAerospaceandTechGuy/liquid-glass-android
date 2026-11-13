plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.glass.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.glass.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // We ship no translations, so strip every other locale table from resources.
        resourceConfigurations += listOf("en")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // No reflection-heavy stdlib helpers, please.
        freeCompilerArgs += listOf("-Xno-param-assertions", "-Xno-call-assertions")
    }

    buildFeatures {
        buildConfig = false
        viewBinding = false
        aidl = false
        renderScript = false
        shaders = false
        resValues = false
    }

    // Drop the dependency metadata block Play inserts; it is a few KB of noise here.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/*.kotlin_module",
                "/META-INF/*.version",
                "/META-INF/MANIFEST.MF",
                "/META-INF/*.RSA",
                "/META-INF/*.SF",
                "/META-INF/version-control-info.textproto",
                // Kotlin metadata is only needed by kotlin-reflect, which this app
                // does not ship. ~30 KB of dead weight removed from the APK.
                "/kotlin/**",
                "/kotlin-tooling-metadata.json",
                "/DebugProbesKt.bin"
            )
        }
    }
}

// Zero. Third-party. Dependencies.
dependencies {
}
