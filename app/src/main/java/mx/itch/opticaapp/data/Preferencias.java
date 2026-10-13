package mx.itch.opticaapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

import java.io.File;

/**
 * Preferencias del usuario guardadas en el teléfono (SharedPreferences):
 * tema, modo sin imágenes, texto grande y fondo de pantalla.
 */
public final class Preferencias {

    private static final String ARCHIVO = "ajustes";
    private static final String TEMA = "tema";
    private static final String SIN_IMAGENES = "sin_imagenes";
    private static final String TEXTO_GRANDE = "texto_grande";
    private static final String NOMBRE_FONDO = "fondo.jpg";

    private Preferencias() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE);
    }

    // ---------- Tema: 0 = según el sistema, 1 = claro, 2 = oscuro ----------

    public static int getTema(Context c) {
        return prefs(c).getInt(TEMA, 0);
    }

    public static void setTema(Context c, int posicion) {
        prefs(c).edit().putInt(TEMA, posicion).apply();
        aplicarTema(c);
    }

    public static void aplicarTema(Context c) {
        int modo;
        switch (getTema(c)) {
            case 1:
                modo = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case 2:
                modo = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            default:
                modo = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(modo);
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
