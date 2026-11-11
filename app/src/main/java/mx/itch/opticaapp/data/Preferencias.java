package mx.itch.opticaapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

import java.io.File;

import mx.itch.opticaapp.util.Temas;

/**
 * Preferencias guardadas en el teléfono (SharedPreferences, archivo "ajustes"):
 * apariencia (tema de color, modo claro / oscuro y tipografía), modo sin imágenes, texto grande y fondo.
 *
 * La apariencia se guarda por usuario: "paleta_{uid}", "tema_{uid}" y "fuente_{uid}". Además,
 * "paleta", "tema" y "fuente" guardan la que está en pantalla, para pintar el inicio de sesión
 * antes de saber quién entra.
 */
public final class Preferencias {

    private static final String ARCHIVO = "ajustes";
    private static final String TEMA = "tema";
    private static final String PALETA = "paleta";
    private static final String FUENTE = "fuente";
    private static final String SIN_IMAGENES = "sin_imagenes";
    private static final String TEXTO_GRANDE = "texto_grande";
    private static final String NOMBRE_FONDO = "fondo.jpg";

    private Preferencias() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE);
    }

    // ---------- Apariencia: tema de color (Cristal, Clínica, Carey) y modo ----------
    // Modo: 0 = según el teléfono, 1 = claro, 2 = oscuro (Temas.MODO_...)

    /** Modo que está en pantalla. */
    public static int getTema(Context c) {
        return prefs(c).getInt(TEMA, Temas.MODO_SISTEMA);
    }

    /** Tema de color que está en pantalla (Cristal si nunca se ha elegido otro). */
    public static Temas.Paleta getPaleta(Context c) {
        return Temas.Paleta.desdeId(prefs(c).getString(PALETA, Temas.Paleta.CRISTAL.id));
    }

    /** Tipografía que está en pantalla (Lexend si nunca se ha elegido otra). */
    public static Temas.Fuente getFuente(Context c) {
        return Temas.Fuente.desdeId(prefs(c).getString(FUENTE, Temas.Fuente.LEXEND.id));
    }

    public static Temas.Fuente getFuenteDe(Context c, String uid) {
        return Temas.Fuente.desdeId(prefs(c).getString(FUENTE + "_" + uid, getFuente(c).id));
    }

    /** true si ese usuario ya eligió su apariencia en este teléfono. */
    public static boolean tieneApariencia(Context c, String uid) {
        return prefs(c).contains(PALETA + "_" + uid);
    }

    public static Temas.Paleta getPaletaDe(Context c, String uid) {
        return Temas.Paleta.desdeId(prefs(c).getString(PALETA + "_" + uid, getPaleta(c).id));
    }

    public static int getTemaDe(Context c, String uid) {
        return prefs(c).getInt(TEMA + "_" + uid, getTema(c));
    }

    /**
     * Guarda la apariencia del usuario y la deja como la actual del teléfono.
     * Solo guarda: la aplica MainActivity.aplicarApariencia().
     */
    public static void guardarApariencia(Context c, String uid, Temas.Paleta paleta, int modo, Temas.Fuente fuente) {
        SharedPreferences.Editor e = prefs(c).edit()
                .putString(PALETA, paleta.id)
                .putInt(TEMA, modo)
                .putString(FUENTE, fuente.id);
        if (uid != null) {
            e.putString(PALETA + "_" + uid, paleta.id)
                    .putInt(TEMA + "_" + uid, modo)
                    .putString(FUENTE + "_" + uid, fuente.id);
        }
        e.apply();
    }

    /** Aplica el modo guardado a toda la app (lo llama OpticaApp al arrancar). */
    public static void aplicarTema(Context c) {
        AppCompatDelegate.setDefaultNightMode(Temas.modoAppCompat(getTema(c)));
    }

    // ---------- Modo sin imágenes (ahorro de datos) ----------

    public static boolean sinImagenes(Context c) {
        return prefs(c).getBoolean(SIN_IMAGENES, false);
    }

    public static void setSinImagenes(Context c, boolean valor) {
        prefs(c).edit().putBoolean(SIN_IMAGENES, valor).apply();
    }

    // ---------- Texto grande ----------

    public static boolean textoGrande(Context c) {
        return prefs(c).getBoolean(TEXTO_GRANDE, false);
    }

    public static void setTextoGrande(Context c, boolean valor) {
        prefs(c).edit().putBoolean(TEXTO_GRANDE, valor).apply();
    }

    /** Devuelve un Context con las letras 30 % más grandes si la opción está activa. */
    public static Context conTamanoDeTexto(Context base) {
        if (!textoGrande(base)) return base;
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.fontScale = config.fontScale * 1.3f;
        return base.createConfigurationContext(config);
    }

    // ---------- Fondo de pantalla: se guarda una copia de la imagen en la app ----------

    public static File archivoFondo(Context c) {
        return new File(c.getFilesDir(), NOMBRE_FONDO);
    }
}
