package mx.itch.opticaapp.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import androidx.annotation.ArrayRes;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.FontRes;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import mx.itch.opticaapp.R;

/**
 * Temas de color de la app (Ajustes › Apariencia).
 *
 * Hay tres paletas y cada una tiene versión clara y oscura:
 *   Cristal  azul cielo · violeta · rosa (la original, por defecto)
 *   Clínica  verde azulado · azul clínico · coral
 *   Carey    azul marino · ámbar · vino
 *
 * Los layouts no usan colores fijos sino atributos del tema (?attr/colorTexto, ?attr/colorAcento...,
 * declarados en values/attrs.xml). Cada paleta es un estilo de themes.xml que llena esos atributos,
 * y MainActivity llama a setTheme() con la paleta guardada antes de dibujar la pantalla.
 * El modo claro / oscuro lo resuelve Android con values/ y values-night/colors_temas.xml.
 *
 * Cada paleta tiene además su esquema completo de Material 3 (48 roles, 6 paletas tonales y
 * 6 colores núcleo, colors_esquema.xml) que muestra la pantalla Esquema de color, y el usuario
 * puede elegir la tipografía (enum Fuente), que se aplica encima del tema con un ThemeOverlay.
 */
public final class Temas {

    /** Modo de color guardado en Preferencias: 0 = el del teléfono, 1 = claro, 2 = oscuro. */
    public static final int MODO_SISTEMA = 0;
    public static final int MODO_CLARO = 1;
    public static final int MODO_OSCURO = 2;

    public enum Paleta {
        CRISTAL("cristal", "Cristal", "Azul cielo · Violeta · Rosa",
                "Fresco y luminoso, como un lente recién limpio.", R.style.Theme_OpticaApp,
                R.array.cristal_roles, R.array.cristal_nucleo, R.array.cristal_tonos),
        CLINICA("clinica", "Clínica", "Verde azulado · Azul clínico · Coral",
                "Limpio y profesional, con aire de consultorio.", R.style.Theme_OpticaApp_Clinica,
                R.array.clinica_roles, R.array.clinica_nucleo, R.array.clinica_tonos),
        CAREY("carey", "Carey", "Azul marino · Ámbar · Vino",
                "Elegante y cálido, como una boutique de armazones.", R.style.Theme_OpticaApp_Carey,
                R.array.carey_roles, R.array.carey_nucleo, R.array.carey_tonos);

        /** Lo que se guarda en SharedPreferences. */
        public final String id;
        public final String nombre;
        /** Sus tres colores principales (primario · secundario · terciario). */
        public final String lema;
        public final String descripcion;
        @StyleRes
        public final int estilo;
        /** Esquema de Material 3: 48 roles, 6 colores núcleo y 6 × 14 tonos (arrays_esquema.xml). */
        @ArrayRes
        final int roles, nucleo, tonos;

        Paleta(String id, String nombre, String lema, String descripcion, @StyleRes int estilo,
               @ArrayRes int roles, @ArrayRes int nucleo, @ArrayRes int tonos) {
            this.id = id;
            this.nombre = nombre;
            this.lema = lema;
            this.descripcion = descripcion;
            this.estilo = estilo;
            this.roles = roles;
            this.nucleo = nucleo;
            this.tonos = tonos;
        }

        /** La paleta con ese id; Cristal si no existe (por ejemplo, un valor viejo o vacío). */
        @NonNull
        public static Paleta desdeId(String id) {
            for (Paleta p : values()) {
                if (p.id.equals(id)) return p;
            }
            return CRISTAL;
        }
    }

    /** Tipografías que puede elegir cada usuario (las de la app Atrium más Lexend, la original). */
    public enum Fuente {
        LEXEND("lexend", "Lexend", "Redonda · muy legible", R.font.lexend_regular, R.style.ThemeOverlay_OpticaApp_Fuente_Lexend),
        INTER("inter", "Inter", "Moderna · corporativa", R.font.inter_regular, R.style.ThemeOverlay_OpticaApp_Fuente_Inter),
        MANROPE("manrope", "Manrope", "Geométrica · cálida", R.font.manrope_regular, R.style.ThemeOverlay_OpticaApp_Fuente_Manrope),
        MONTSERRAT("montserrat", "Montserrat", "Amplia · urbana", R.font.montserrat_regular, R.style.ThemeOverlay_OpticaApp_Fuente_Montserrat),
        SPACE_GROTESK("space_grotesk", "Space Grotesk", "Técnica · con carácter", R.font.space_grotesk_regular, R.style.ThemeOverlay_OpticaApp_Fuente_SpaceGrotesk),
        PLAYFAIR("playfair", "Playfair Display", "Editorial · elegante", R.font.playfair_display_regular, R.style.ThemeOverlay_OpticaApp_Fuente_Playfair),
        LORA("lora", "Lora", "Serif · literaria", R.font.lora_regular, R.style.ThemeOverlay_OpticaApp_Fuente_Lora),
        SISTEMA("sistema", "Roboto", "La del sistema · neutra", 0, R.style.ThemeOverlay_OpticaApp_Fuente_Sistema);

        public final String id;
        public final String nombre;
        public final String descripcion;
        @FontRes
        private final int archivo;   // 0 = la del sistema
        @StyleRes
        public final int estilo;

        Fuente(String id, String nombre, String descripcion, @FontRes int archivo, @StyleRes int estilo) {
            this.id = id;
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.archivo = archivo;
            this.estilo = estilo;
        }

        /** La letra normal de esta fuente, para las tarjetas "Aa" de Ajustes. */
        @NonNull
        public Typeface tipo(@NonNull Context ctx) {
            if (archivo == 0) return Typeface.SANS_SERIF;
            Typeface t = ResourcesCompat.getFont(ctx, archivo);
            return t != null ? t : Typeface.SANS_SERIF;
        }

        @NonNull
        public static Fuente desdeId(String id) {
            for (Fuente f : values()) {
                if (f.id.equals(id)) return f;
            }
            return LEXEND;
        }
    }

    /** Colores de la vista previa de Ajustes, en este orden (ver coloresDeVistaPrevia). */
    public static final int[] ATRIBUTOS_VISTA_PREVIA = {
            R.attr.colorDegradadoInicio, R.attr.colorDegradadoFin, R.attr.colorSuperficie, R.attr.colorBorde,
            R.attr.colorTexto, R.attr.colorTextoSecundario, R.attr.colorAcento, R.attr.colorSobreAcento,
            R.attr.colorTonal, R.attr.colorMarca, R.attr.colorMarcaSuave, R.attr.colorTercero};
    public static final int V_DEGRADADO_INICIO = 0, V_DEGRADADO_FIN = 1, V_SUPERFICIE = 2, V_BORDE = 3,
            V_TEXTO = 4, V_TEXTO_SECUNDARIO = 5, V_ACENTO = 6, V_SOBRE_ACENTO = 7, V_TONAL = 8,
            V_MARCA = 9, V_MARCA_SUAVE = 10, V_TERCERO = 11, V_ERROR = 12, TOTAL_VISTA_PREVIA = 13;

    private Temas() {
    }

    /**
     * Color de un atributo del tema de esa pantalla, por ejemplo Temas.color(ctx, R.attr.colorAcento).
     * Reemplaza a ContextCompat.getColor(ctx, R.color.cielo): así el color sigue al tema elegido.
     */
    @ColorInt
    public static int color(@NonNull Context ctx, @AttrRes int atributo) {
        TypedValue valor = new TypedValue();
        if (!ctx.getTheme().resolveAttribute(atributo, valor, true)) {
            throw new IllegalArgumentException("El tema no define el atributo " + atributo);
        }
        if (valor.resourceId != 0) return ContextCompat.getColor(ctx, valor.resourceId);
        return valor.data;
    }

    /**
     * Tipografía del tema para un grosor (R.attr.fuenteNormal, fuenteMedia...), para el texto
     * que se dibuja desde código (por ejemplo el aviso flotante).
     */
    @NonNull
    public static Typeface fuente(@NonNull Context ctx, @AttrRes int atributo) {
        TypedValue valor = new TypedValue();
        if (ctx.getTheme().resolveAttribute(atributo, valor, true)) {
            if (valor.resourceId != 0) {
                try {
                    Typeface t = ResourcesCompat.getFont(ctx, valor.resourceId);
                    if (t != null) return t;
                } catch (Resources.NotFoundException ignorada) {
                    // No es un archivo de fuente: se intenta como nombre de familia
                }
            }
            if (valor.string != null) return Typeface.create(valor.string.toString(), Typeface.NORMAL);
        }
        return Typeface.DEFAULT;
    }

    /** true si la pantalla se está dibujando en modo oscuro. */
    public static boolean esNoche(@NonNull Context ctx) {
        return (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** true si con ese modo la app se vería oscura (en "Sistema" depende del teléfono). */
    public static boolean esNoche(@NonNull Context ctx, int modo) {
        if (modo == MODO_CLARO) return false;
        if (modo == MODO_OSCURO) return true;
        // Resources.getSystem() trae la configuración del teléfono, sin la que fija la app
        return (Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Traduce el modo guardado al de AppCompat. */
    public static int modoAppCompat(int modo) {
        if (modo == MODO_CLARO) return AppCompatDelegate.MODE_NIGHT_NO;
        if (modo == MODO_OSCURO) return AppCompatDelegate.MODE_NIGHT_YES;
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    /**
     * Un Context con otra paleta y otro modo, sin cambiar la pantalla actual.
     * Lo usa la vista previa de Ajustes para pintar cómo se vería cada tema.
     */
    @NonNull
    public static Context contextoDe(@NonNull Context base, @NonNull Paleta paleta, boolean oscuro) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (oscuro ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return new ContextThemeWrapper(base.createConfigurationContext(config), paleta.estilo);
    }

    /** Los 48 roles de Material 3 de una paleta en claro u oscuro (orden de EsquemaM3). */
    @NonNull
    public static int[] roles(@NonNull Context base, @NonNull Paleta paleta, boolean oscuro) {
        return leerColores(contextoDe(base, paleta, oscuro), paleta.roles);
    }

    /** Colores núcleo: Primary, Secondary, Tertiary, Error, Neutral y Neutral Variant. */
    @NonNull
    public static int[] nucleo(@NonNull Context base, @NonNull Paleta paleta) {
        return leerColores(base, paleta.nucleo);
    }

    /** Paletas tonales: [paleta][tono], con las paletas y tonos de EsquemaM3. */
    @NonNull
    public static int[][] tonos(@NonNull Context base, @NonNull Paleta paleta) {
        int[] todos = leerColores(base, paleta.tonos);
        int n = EsquemaM3.TONOS.length;
        int[][] tabla = new int[todos.length / n][n];
        for (int i = 0; i < todos.length; i++) tabla[i / n][i % n] = todos[i];
        return tabla;
    }

    private static int[] leerColores(Context ctx, @ArrayRes int arreglo) {
        TypedArray a = ctx.getResources().obtainTypedArray(arreglo);
        try {
            int[] colores = new int[a.length()];
            for (int i = 0; i < colores.length; i++) colores[i] = a.getColor(i, 0);
            return colores;
        } finally {
            a.recycle();
        }
    }

    /** Los colores de la vista previa (índices V_...) para una paleta en claro u oscuro. */
    @NonNull
    public static int[] coloresDeVistaPrevia(@NonNull Context base, @NonNull Paleta paleta, boolean oscuro) {
        Context ctx = contextoDe(base, paleta, oscuro);
        int[] colores = new int[TOTAL_VISTA_PREVIA];
        for (int i = 0; i < ATRIBUTOS_VISTA_PREVIA.length; i++) {
            colores[i] = color(ctx, ATRIBUTOS_VISTA_PREVIA[i]);
        }
        colores[V_ERROR] = ContextCompat.getColor(ctx, R.color.rojo);   // el rojo no cambia con el tema
        return colores;
    }
}
