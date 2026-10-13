package mx.itch.opticaapp.util;

import android.util.Patterns;

/**
 * Reglas de validación que usan varias pantallas.
 * Cada método devuelve null si el dato es correcto, o el mensaje de error.
 */
public final class Validaciones {

    private Validaciones() {
    }

    public static String correo(String correo) {
        if (correo == null || correo.trim().isEmpty()) return "Escribe tu correo";
        if (!Patterns.EMAIL_ADDRESS.matcher(correo.trim()).matches()) return "Correo no válido";
        return null;
    }

    /** Contraseña segura: 8+ caracteres, mayúscula, minúscula, número y símbolo. */
    public static String contrasena(String c) {
        if (c == null || c.length() < 8) return "Mínimo 8 caracteres";
        if (!c.matches(".*[A-Z].*")) return "Agrega al menos una mayúscula";
        if (!c.matches(".*[a-z].*")) return "Agrega al menos una minúscula";
        if (!c.matches(".*[0-9].*")) return "Agrega al menos un número";
        if (!c.matches(".*[^A-Za-z0-9].*")) return "Agrega al menos un símbolo (por ejemplo # o !)";
        return null;
    }

    /** Lista de reglas con ✓ o ✗ para mostrarla mientras el usuario escribe. */
    public static String reglasContrasena(String c) {
        if (c == null) c = "";
        return marca(c.length() >= 8) + " 8 o más caracteres\n"
                + marca(c.matches(".*[A-Z].*")) + " Una mayúscula\n"
                + marca(c.matches(".*[a-z].*")) + " Una minúscula\n"
                + marca(c.matches(".*[0-9].*")) + " Un número\n"
                + marca(c.matches(".*[^A-Za-z0-9].*")) + " Un símbolo";
    }

    private static String marca(boolean cumple) {
        return cumple ? "✓" : "✗";
    }

    public static String requerido(String texto, String nombreCampo) {
        if (texto == null || texto.trim().isEmpty()) return "Escribe " + nombreCampo;
        return null;
    }

    // ---------------- Graduación óptica ----------------

    /** true si el valor es múltiplo de 0.25 (las dioptrías van de cuarto en cuarto). */
    private static boolean esCuarto(double valor) {
        double cuartos = valor * 4;
        return Math.abs(cuartos - Math.round(cuartos)) < 0.0001;
    }

    /** Esfera obligatoria entre -20.00 y +20.00 en pasos de 0.25. */
    public static String esfera(Double valor) {
        if (valor == null) return "Obligatorio";
        if (valor < -20 || valor > 20) return "Entre -20.00 y +20.00";
        if (!esCuarto(valor)) return "En pasos de 0.25";
        return null;
    }

    /** Cilindro opcional entre -10.00 y +10.00 en pasos de 0.25. */
    public static String cilindro(Double valor) {
        if (valor == null) return null;
        if (valor < -10 || valor > 10) return "Entre -10.00 y +10.00";
        if (!esCuarto(valor)) return "En pasos de 0.25";
        return null;
    }

    /** El eje es obligatorio si hay cilindro, y va de 1° a 180°. */
    public static String eje(Integer eje, Double cilindro) {
        boolean hayCilindro = cilindro != null && cilindro != 0;
        if (eje == null) return hayCilindro ? "Obligatorio con cilindro" : null;
        if (eje < 1 || eje > 180) return "El eje debe estar entre 1° y 180°";
        return null;
    }

    /** Adición opcional entre +0.25 y +4.00 en pasos de 0.25. */
    public static String adicion(Double valor) {
        if (valor == null || valor == 0) return null;
        if (valor < 0.25 || valor > 4) return "Entre +0.25 y +4.00";
        if (!esCuarto(valor)) return "En pasos de 0.25";
        return null;
    }

    /** Distancia pupilar opcional entre 40 y 80 mm. */
    public static String distanciaPupilar(Double valor) {
        if (valor == null) return null;
        if (valor < 40 || valor > 80) return "Entre 40 y 80 mm";
        return null;
    }
}
