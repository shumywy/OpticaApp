package mx.itch.opticaapp.util;

import android.util.Patterns;

/**
 * Reglas de validación que usan varias pantallas.
 * Cada método devuelve null si el dato es correcto, o el mensaje de error para mostrarlo con setError().
 *
 * Los límites de longitud coinciden con los android:maxLength de los layouts (LARGO_*),
 * así el teclado ya no deja escribir de más y la validación cubre lo que se pegue o cargue.
 */
public final class Validaciones {

    // Longitudes máximas (también en los layouts como android:maxLength)
    public static final int LARGO_NOMBRE = 60;
    public static final int LARGO_CORREO = 100;
    public static final int LARGO_DIRECCION = 150;
    public static final int LARGO_DESCRIPCION = 300;
    public static final int LARGO_TRABAJO = 200;
    public static final int LARGO_OBSERVACIONES = 500;

    /** Teléfono en México: 10 dígitos (lada + número), sin espacios ni guiones. */
    public static final int DIGITOS_TELEFONO = 10;

    /** Letras (con acentos, ñ y ü), espacios, punto, apóstrofo y guion. */
    private static final String PATRON_NOMBRE = "[\\p{L} .'-]+";

    private Validaciones() {
    }

    // ---------------- Cuenta ----------------

    public static String correo(String correo) {
        if (correo == null || correo.trim().isEmpty()) return "Escribe el correo";
        String c = correo.trim();
        if (c.length() > LARGO_CORREO) return "Máximo " + LARGO_CORREO + " caracteres";
        if (c.contains(" ")) return "El correo no lleva espacios";
        if (!Patterns.EMAIL_ADDRESS.matcher(c).matches()) return "Correo no válido (ejemplo: nombre@dominio.com)";
        return null;
    }

    /** Contraseña segura: 8 a 64 caracteres, mayúscula, minúscula, número y símbolo, sin espacios. */
    public static String contrasena(String c) {
        if (c == null || c.length() < 8) return "Mínimo 8 caracteres";
        if (c.length() > 64) return "Máximo 64 caracteres";
        if (c.contains(" ")) return "La contraseña no lleva espacios";
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

    // ---------------- Personas y óptica ----------------

    /** Nombre de una persona: obligatorio, 3 a 60 caracteres, solo letras y al menos 2 de ellas. */
    public static String nombrePersona(String nombre) {
        String n = nombre == null ? "" : nombre.trim();
        if (n.isEmpty()) return "Escribe el nombre";
        if (n.length() < 3) return "Mínimo 3 caracteres";
        if (n.length() > LARGO_NOMBRE) return "Máximo " + LARGO_NOMBRE + " caracteres";
        if (!n.matches(PATRON_NOMBRE)) return "Solo letras y espacios (sin números ni símbolos)";
        if (n.replaceAll("[^\\p{L}]", "").length() < 2) return "Escribe un nombre válido";
        return null;
    }

    /** Nombre comercial de la óptica o de un producto: obligatorio, 2 a 60 caracteres. */
    public static String nombreComercial(String nombre, String campo) {
        String n = nombre == null ? "" : nombre.trim();
        if (n.isEmpty()) return "Escribe " + campo;
        if (n.length() < 2) return "Mínimo 2 caracteres";
        if (n.length() > LARGO_NOMBRE) return "Máximo " + LARGO_NOMBRE + " caracteres";
        if (!n.matches(".*[\\p{L}\\p{N}].*")) return "Escribe " + campo + " con letras o números";
        return null;
    }

    /**
     * Teléfono de exactamente 10 dígitos. Acepta que el usuario lo escriba con espacios o guiones
     * (se quitan con soloDigitos). Si no es obligatorio, vacío es válido.
     */
    public static String telefono(String telefono, boolean obligatorio) {
        String digitos = soloDigitos(telefono);
        if (digitos.isEmpty()) return obligatorio ? "Escribe el teléfono" : null;
        if (telefono.trim().matches(".*[^0-9 ()-].*")) return "Solo números";
        if (digitos.length() != DIGITOS_TELEFONO) {
            return "Debe tener " + DIGITOS_TELEFONO + " dígitos (tiene " + digitos.length() + ")";
        }
        if (digitos.matches("(\\d)\\1{9}")) return "Teléfono no válido";
        return null;
    }

    /** "747 123-4567" → "7471234567". Es lo que se guarda en Firestore. */
    public static String soloDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("[^0-9]", "");
    }

    /** Dirección opcional: si se escribe, de 5 a 150 caracteres. */
    public static String direccion(String direccion, boolean obligatoria) {
        String d = direccion == null ? "" : direccion.trim();
        if (d.isEmpty()) return obligatoria ? "Escribe la dirección" : null;
        if (d.length() < 5) return "Escribe la dirección completa";
        if (d.length() > LARGO_DIRECCION) return "Máximo " + LARGO_DIRECCION + " caracteres";
        return null;
    }

    /** Edad en años cumplidos: obligatoria, de 0 a 120. */
    public static String edad(String texto) {
        if (texto == null || texto.trim().isEmpty()) return "Escribe la edad";
        Integer edad = Formato.leerEntero(texto);
        if (edad == null) return "Solo números enteros";
        if (edad < 0 || edad > 120) return "Entre 0 y 120 años";
        return null;
    }

    /** Código de invitación con el formato que genera la app: OPT- y 6 letras o números. */
    public static String codigoInvitacion(String codigo) {
        String c = codigo == null ? "" : codigo.trim().toUpperCase(java.util.Locale.ROOT);
        if (c.isEmpty()) return "Escribe el código que te dio el administrador";
        if (!c.matches("OPT-[A-Z0-9]{6}")) return "El código tiene el formato OPT-XXXXXX";
        return null;
    }

    /** Texto libre con largo máximo; obligatorio con un mínimo de caracteres. */
    public static String textoLibre(String texto, boolean obligatorio, int minimo, int maximo, String campo) {
        String t = texto == null ? "" : texto.trim();
        if (t.isEmpty()) return obligatorio ? "Escribe " + campo : null;
        if (t.length() < minimo) return "Mínimo " + minimo + " caracteres";
        if (t.length() > maximo) return "Máximo " + maximo + " caracteres";
        return null;
    }

    // ---------------- Producto: características y colores ----------------

    /** Características separadas por coma: hasta 5, de 2 a 20 caracteres cada una. Opcional. */
    public static String etiquetas(String texto) {
        java.util.List<String> lista = Formato.separarEtiquetas(texto);
        if (lista.isEmpty()) return null;
        if (lista.size() > 5) return "Máximo 5 características";
        for (String e : lista) {
            if (e.length() < 2) return "Cada característica lleva al menos 2 caracteres";
            if (e.length() > 20) return "\"" + e + "\" es muy larga (máximo 20 caracteres)";
        }
        return null;
    }

    /** Nombre de un color ("Gris Oxford"): obligatorio, de 2 a 20 caracteres, letras, números y espacios. */
    public static String nombreColor(String nombre) {
        String n = nombre == null ? "" : nombre.trim();
        if (n.isEmpty()) return "Escribe el nombre del color";
        if (n.length() < 2) return "Mínimo 2 caracteres";
        if (n.length() > 20) return "Máximo 20 caracteres";
        if (!n.matches("[\\p{L}\\p{N} ]+")) return "Solo letras, números y espacios";
        return null;
    }

    /** Color exacto en hexadecimal: #RRGGBB (también se acepta sin #). */
    public static String colorHex(String hex) {
        String h = hex == null ? "" : hex.trim();
        if (h.isEmpty()) return "Escribe el código del color";
        if (!h.startsWith("#")) h = "#" + h;
        if (!h.matches("#[0-9A-Fa-f]{6}")) return "Formato #RRGGBB (ejemplo: #3C434A)";
        return null;
    }

    /** "3c434a" → "#3C434A". Usar solo después de validar con colorHex. */
    public static String normalizarHex(String hex) {
        String h = hex.trim().toUpperCase(java.util.Locale.ROOT);
        return h.startsWith("#") ? h : "#" + h;
    }

    // ---------------- Dinero y cantidades ----------------

    /** Precio obligatorio mayor a 0, máximo $999,999.99 y con 2 decimales como máximo. */
    public static String precio(String texto) {
        if (texto == null || texto.trim().isEmpty()) return "Escribe el precio";
        Double precio = Formato.leerNumero(texto);
        if (precio == null) return "Solo números (ejemplo: 1250.50)";
        if (precio <= 0) return "Debe ser mayor a $0";
        if (precio > 999_999.99) return "Máximo $999,999.99";
        if (!dosDecimales(precio)) return "Máximo 2 decimales";
        return null;
    }

    /** Cantidad de dinero opcional (meta, anticipo): de 0 al máximo indicado, con 2 decimales como máximo. */
    public static String importe(String texto, double maximo) {
        if (texto == null || texto.trim().isEmpty()) return null;
        Double valor = Formato.leerNumero(texto);
        if (valor == null) return "Solo números (ejemplo: 1250.50)";
        if (valor < 0) return "No puede ser negativo";
        if (valor > maximo) return "Máximo " + Formato.moneda(maximo);
        if (!dosDecimales(valor)) return "Máximo 2 decimales";
        return null;
    }

    /** Número entero obligatorio entre minimo y maximo (existencias, cantidades). */
    public static String entero(String texto, int minimo, int maximo, String campo) {
        if (texto == null || texto.trim().isEmpty()) return "Escribe " + campo;
        Integer valor = Formato.leerEntero(texto);
        if (valor == null) return "Solo números enteros";
        if (valor < minimo || valor > maximo) return "Entre " + minimo + " y " + maximo;
        return null;
    }

    private static boolean dosDecimales(double valor) {
        double centavos = valor * 100;
        return Math.abs(centavos - Math.round(centavos)) < 0.0001;
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

    /** El eje es obligatorio si hay cilindro, solo se usa con cilindro y va de 1° a 180°. */
    public static String eje(Integer eje, Double cilindro) {
        boolean hayCilindro = cilindro != null && cilindro != 0;
        if (eje == null) return hayCilindro ? "Obligatorio con cilindro" : null;
        if (!hayCilindro) return "El eje solo se usa con cilindro";
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

    /** Distancia pupilar opcional entre 40 y 80 mm, en pasos de medio milímetro. */
    public static String distanciaPupilar(Double valor) {
        if (valor == null) return null;
        if (valor < 40 || valor > 80) return "Entre 40 y 80 mm";
        if (Math.abs(valor * 2 - Math.round(valor * 2)) > 0.0001) return "En pasos de 0.5 mm";
        return null;
    }

    /** Agudeza visual opcional en notación de Snellen, por ejemplo 20/20 o 20/40. */
    public static String agudezaVisual(String texto) {
        String t = texto == null ? "" : texto.trim();
        if (t.isEmpty()) return null;
        if (!t.matches("\\d{1,2}/\\d{1,3}")) return "Formato 20/20";
        return null;
    }
}
