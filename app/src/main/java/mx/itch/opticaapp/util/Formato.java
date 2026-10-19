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

    /**
     * Lee un número escrito por el usuario aceptando coma o punto decimal y el signo $.
     * Devuelve null si está vacío o mal escrito. Solo acepta dígitos con signo y punto opcionales:
     * rechaza "NaN", "Infinity", "1e5" o "0x10", que Double.parseDouble sí aceptaría.
     */
    public static Double leerNumero(String texto) {
        if (texto == null) return null;
        String limpio = texto.trim().replace(',', '.').replace("$", "").replace(" ", "");
        if (!limpio.matches("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)")) return null;
        try {
            return Double.parseDouble(limpio);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * "Microfibra, suave ,15x15 cm" → ["Microfibra", "Suave", "15x15 cm"]: separa por comas,
     * quita espacios y vacíos, pone mayúscula inicial y elimina repetidas.
     */
    public static java.util.List<String> separarEtiquetas(String texto) {
        java.util.List<String> lista = new java.util.ArrayList<>();
        if (texto == null) return lista;
        for (String parte : texto.split(",")) {
            String e = parte.trim().replaceAll("\\s+", " ");
            if (e.isEmpty()) continue;
            e = e.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + e.substring(1);
            boolean repetida = false;
            for (String otra : lista) {
                if (otra.equalsIgnoreCase(e)) repetida = true;
            }
            if (!repetida) lista.add(e);
        }
        return lista;
    }

    /** "7471234567" → "747 123 4567". Si no tiene 10 dígitos lo devuelve tal cual. */
    public static String telefono(String telefono) {
        if (telefono == null) return "";
        String d = telefono.replaceAll("[^0-9]", "");
        if (d.length() != 10) return telefono.trim();
        return d.substring(0, 3) + " " + d.substring(3, 6) + " " + d.substring(6);
    }

    /** Igual que leerNumero pero para enteros (null si tiene decimales o no cabe en un int). */
    public static Integer leerEntero(String texto) {
        Double d = leerNumero(texto);
        if (d == null || d != Math.floor(d) || Math.abs(d) > Integer.MAX_VALUE) return null;
        return d.intValue();
    }
}
