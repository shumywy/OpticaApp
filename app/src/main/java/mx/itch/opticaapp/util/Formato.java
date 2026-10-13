package mx.itch.opticaapp.util;

import java.text.NumberFormat;
import java.util.Locale;

/** Formatos de texto que se repiten en varias pantallas. */
public final class Formato {

    private static final NumberFormat MONEDA = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"));

    private Formato() {
    }

    /** 1450 -> "$1,450.00" */
    public static String moneda(double cantidad) {
        return MONEDA.format(cantidad);
    }

    /** Dioptrías con signo: -1.25 -> "-1.25", 1 -> "+1.00". */
    public static String dioptria(double valor) {
        return String.format(Locale.US, "%+.2f", valor);
    }

    /** "OD -1.25 / OI -1.50": resumen corto de la graduación para listas. */
    public static String resumenGraduacion(double odEsfera, double oiEsfera) {
        return "OD " + dioptria(odEsfera) + " / OI " + dioptria(oiEsfera);
    }

    /** "María Fernanda López" -> "MF". Se usa en los círculos de iniciales. */
    public static String iniciales(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) return "?";
        String[] partes = nombre.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length && sb.length() < 2; i++) {
            sb.append(Character.toUpperCase(partes[i].charAt(0)));
        }
        return sb.toString();
    }

    /** Lee un número escrito por el usuario aceptando coma o punto. Devuelve null si está vacío o mal escrito. */
    public static Double leerNumero(String texto) {
        if (texto == null) return null;
        String limpio = texto.trim().replace(',', '.').replace("$", "");
        if (limpio.isEmpty()) return null;
        try {
            return Double.parseDouble(limpio);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Igual que leerNumero pero para enteros. */
    public static Integer leerEntero(String texto) {
        Double d = leerNumero(texto);
        if (d == null || d != Math.floor(d)) return null;
        return d.intValue();
    }
}
