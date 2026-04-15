plugins {
    id("com.android.application")
}

android {
    namespace = "com.stickeruploader"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.stickeruploader"
        minSdk = 21
        targetSdk = 33
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
}

dependencies {
    // Nessuna dipendenza esterna!
    // Usando solo Android SDK nativo
}
