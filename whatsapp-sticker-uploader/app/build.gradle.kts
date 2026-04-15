plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.stickeruploader"
    compileSdk = 23

    defaultConfig {
        applicationId = "com.stickeruploader"
        minSdk = 21
        targetSdk = 23
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // SOLO Android SDK nativo - nessuna libreria esterna
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.21")
}
