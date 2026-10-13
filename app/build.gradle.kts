plugins {
    alias(libs.plugins.android.application)
    // Lee app/google-services.json (se descarga de la consola de Firebase)
    alias(libs.plugins.google.services)
}

android {
    namespace = "mx.itch.opticaapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "mx.itch.opticaapp"
        minSdk = 26          // Android 8.0
        targetSdk = 36       // Android 16, requisito actual de Google Play
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        // Genera una clase "Binding" por cada layout para no usar findViewById
        viewBinding = true
    }
}

dependencies {
    // Interfaz
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)

    // Navegación entre Fragments
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Firebase: la BoM fija versiones compatibles entre sí
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)

    // Inicio de sesión con Google (Credential Manager)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

    // Carga de imágenes desde internet
    implementation(libs.glide)
}
