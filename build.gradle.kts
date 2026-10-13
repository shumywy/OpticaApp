// Archivo de nivel superior: solo declara los plugins que usa el módulo :app.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
}
