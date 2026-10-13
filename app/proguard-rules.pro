# Reglas de ProGuard/R8. La minificación está desactivada (isMinifyEnabled = false).
# Si se activa, conserva los modelos que Firestore convierte por reflexión:
-keep class mx.itch.opticaapp.model.** { *; }
