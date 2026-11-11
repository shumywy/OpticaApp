package mx.itch.opticaapp.util;

import androidx.core.graphics.ColorUtils;

/**
 * Los 48 roles de color de Material 3, en el MISMO orden que los arreglos <tema>_roles de
 * res/values/arrays_esquema.xml (adaptado de la app Atrium).
 *
 * Para cada rol se guarda: nombre (como aparece en Material Theme Builder), para qué se usa,
 * de qué paleta tonal sale y qué color conviene para el texto que va encima. Lo usan la pantalla
 * Esquema de color y la ficha de cada rol.
 */
public final class EsquemaM3 {

    private EsquemaM3() {
    }

    // Paletas tonales (mismo orden que <tema>_nucleo y los renglones de <tema>_tonos)
    public static final int PAL_PRIMARIO = 0, PAL_SECUNDARIO = 1, PAL_TERCIARIO = 2,
            PAL_ERROR = 3, PAL_NEUTRO = 4, PAL_NEUTRO_VARIANTE = 5;

    public static final String[] NOMBRES_PALETAS_EN = {
            "Primary", "Secondary", "Tertiary", "Error", "Neutral", "Neutral Variant"};
    public static final String[] NOMBRES_PALETAS = {
            "Primario", "Secundario", "Terciario", "Error", "Neutro", "Neutro variante"};

    public static final int[] TONOS = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 98, 99, 100};

    // Índices de los roles (orden de arrays_esquema.xml)
    public static final int PRIMARY = 0, ON_PRIMARY = 1, PRIMARY_CONTAINER = 2, ON_PRIMARY_CONTAINER = 3,
            SECONDARY = 4, ON_SECONDARY = 5, SECONDARY_CONTAINER = 6, ON_SECONDARY_CONTAINER = 7,
            TERTIARY = 8, ON_TERTIARY = 9, TERTIARY_CONTAINER = 10, ON_TERTIARY_CONTAINER = 11,
            ERROR = 12, ON_ERROR = 13, ERROR_CONTAINER = 14, ON_ERROR_CONTAINER = 15,
            BACKGROUND = 16, ON_BACKGROUND = 17, SURFACE = 18, ON_SURFACE = 19,
            SURFACE_VARIANT = 20, ON_SURFACE_VARIANT = 21, OUTLINE = 22, OUTLINE_VARIANT = 23,
            SCRIM = 24, SHADOW = 25, INVERSE_SURFACE = 26, INVERSE_ON_SURFACE = 27, INVERSE_PRIMARY = 28,
            PRIMARY_FIXED = 29, ON_PRIMARY_FIXED = 30, PRIMARY_FIXED_DIM = 31, ON_PRIMARY_FIXED_VARIANT = 32,
            SECONDARY_FIXED = 33, ON_SECONDARY_FIXED = 34, SECONDARY_FIXED_DIM = 35, ON_SECONDARY_FIXED_VARIANT = 36,
            TERTIARY_FIXED = 37, ON_TERTIARY_FIXED = 38, TERTIARY_FIXED_DIM = 39, ON_TERTIARY_FIXED_VARIANT = 40,
            SURFACE_DIM = 41, SURFACE_BRIGHT = 42, SURFACE_CONTAINER_LOWEST = 43, SURFACE_CONTAINER_LOW = 44,
            SURFACE_CONTAINER = 45, SURFACE_CONTAINER_HIGH = 46, SURFACE_CONTAINER_HIGHEST = 47;

    public static final int COUNT = 48;

    /** Nombre del rol tal como lo muestra Material Theme Builder. */
    public static final String[] NOMBRES = {
            "Primary", "On Primary", "Primary Container", "On Primary Container",
            "Secondary", "On Secondary", "Secondary Container", "On Secondary Container",
            "Tertiary", "On Tertiary", "Tertiary Container", "On Tertiary Container",
            "Error", "On Error", "Error Container", "On Error Container",
            "Background", "On Background", "Surface", "On Surface",
            "Surface Variant", "On Surface Variant", "Outline", "Outline Variant",
            "Scrim", "Shadow", "Inverse Surface", "Inverse On Surface", "Inverse Primary",
            "Primary Fixed", "On Primary Fixed", "Primary Fixed Dim", "On Primary Fixed Variant",
            "Secondary Fixed", "On Secondary Fixed", "Secondary Fixed Dim", "On Secondary Fixed Variant",
            "Tertiary Fixed", "On Tertiary Fixed", "Tertiary Fixed Dim", "On Tertiary Fixed Variant",
            "Surface Dim", "Surface Bright", "Surface Container Lowest", "Surface Container Low",
            "Surface Container", "Surface Container High", "Surface Container Highest",
    };

    /** Nombre corto para las celdas pequeñas de la cuadrícula. */
    public static String nombreCorto(int rol) {
        return NOMBRES[rol].replace("Surface Container", "Surf. Container").replace("Variant", "Var.");
    }

    /** Paleta tonal de la que sale cada rol. */
    public static int paletaDe(int rol) {
        if (rol <= ON_PRIMARY_CONTAINER || rol == INVERSE_PRIMARY || (rol >= PRIMARY_FIXED && rol <= ON_PRIMARY_FIXED_VARIANT)) {
            return PAL_PRIMARIO;
        }
        if (rol <= ON_SECONDARY_CONTAINER || (rol >= SECONDARY_FIXED && rol <= ON_SECONDARY_FIXED_VARIANT)) {
            return PAL_SECUNDARIO;
        }
        if (rol <= ON_TERTIARY_CONTAINER || (rol >= TERTIARY_FIXED && rol <= ON_TERTIARY_FIXED_VARIANT)) {
            return PAL_TERCIARIO;
        }
        if (rol <= ON_ERROR_CONTAINER) return PAL_ERROR;
        if (rol >= SURFACE_VARIANT && rol <= OUTLINE_VARIANT) return PAL_NEUTRO_VARIANTE;
        return PAL_NEUTRO;
    }

    /** Rol "pareja" pensado para el texto encima (-1 = elegir por contraste). */
    private static final int[] PAREJA = {
            ON_PRIMARY, PRIMARY, ON_PRIMARY_CONTAINER, PRIMARY_CONTAINER,
            ON_SECONDARY, SECONDARY, ON_SECONDARY_CONTAINER, SECONDARY_CONTAINER,
            ON_TERTIARY, TERTIARY, ON_TERTIARY_CONTAINER, TERTIARY_CONTAINER,
            ON_ERROR, ERROR, ON_ERROR_CONTAINER, ERROR_CONTAINER,
            ON_BACKGROUND, BACKGROUND, ON_SURFACE, SURFACE,
            ON_SURFACE_VARIANT, SURFACE_VARIANT, -1, ON_SURFACE,
            -1, -1, INVERSE_ON_SURFACE, INVERSE_SURFACE, -1,
            ON_PRIMARY_FIXED, PRIMARY_FIXED, ON_PRIMARY_FIXED, PRIMARY_FIXED,
            ON_SECONDARY_FIXED, SECONDARY_FIXED, ON_SECONDARY_FIXED, SECONDARY_FIXED,
            ON_TERTIARY_FIXED, TERTIARY_FIXED, ON_TERTIARY_FIXED, TERTIARY_FIXED,
            ON_SURFACE, ON_SURFACE, ON_SURFACE, ON_SURFACE, ON_SURFACE, ON_SURFACE, ON_SURFACE,
    };

    /** Color legible para escribir sobre el rol: su pareja o, si no tiene, el de mejor contraste. */
    public static int textoSobre(int rol, int[] roles) {
        int fondo = roles[rol];
        if (PAREJA[rol] >= 0) return roles[PAREJA[rol]];
        int a = roles[ON_SURFACE], b = roles[SURFACE];
        return ColorUtils.calculateContrast(a, opaque(fondo)) >= ColorUtils.calculateContrast(b, opaque(fondo)) ? a : b;
    }

    /** Rol con el que se compara el contraste en la ficha de detalle (-1 si no aplica). */
    public static int parejaDe(int rol) {
        return PAREJA[rol];
    }

    /** Para qué se usa cada rol (resumen de m3.material.io/styles/color/roles). */
    public static final String[] USO = {
            "Componentes principales: botones rellenos, FAB, estados activos, indicadores de progreso.",
            "Texto e íconos que van encima de Primary.",
            "Rellenos que destacan menos que Primary: FAB, chips seleccionados, tarjetas importantes.",
            "Texto e íconos sobre Primary Container.",
            "Componentes de menor énfasis: chips de filtro, botones tonales.",
            "Texto e íconos que van encima de Secondary.",
            "Botones tonales, indicador activo de la barra de navegación y del menú lateral.",
            "Texto e íconos sobre Secondary Container.",
            "Acentos que contrastan con primario y secundario: insignias, campos destacados.",
            "Texto e íconos que van encima de Tertiary.",
            "Contenedores de acento: FAB grande, etiquetas, ilustraciones.",
            "Texto e íconos sobre Tertiary Container.",
            "Errores y acciones destructivas: validaciones, eliminar, cerrar sesión.",
            "Texto e íconos que van encima de Error.",
            "Fondo de mensajes de error y avisos.",
            "Texto e íconos sobre Error Container.",
            "Fondo general de la ventana (equivalente a Surface).",
            "Texto sobre Background.",
            "Fondo de pantallas, hojas y barras.",
            "Texto principal e íconos sobre cualquier superficie.",
            "Rol antiguo de superficie con tinte; hoy se usan los Surface Container.",
            "Texto secundario: subtítulos, etiquetas, íconos inactivos.",
            "Bordes importantes: campos de texto, botones delineados.",
            "Bordes decorativos y divisores.",
            "Capa oscura detrás de diálogos y menús modales.",
            "Sombras de elevación.",
            "Fondo de elementos que contrastan con la pantalla: Snackbar, tooltips.",
            "Texto sobre Inverse Surface.",
            "Acción dentro de una Snackbar (primario sobre fondo invertido).",
            "Relleno fijo que es igual en claro y oscuro.",
            "Texto e íconos sobre Primary Fixed y Primary Fixed Dim.",
            "Versión más intensa de Primary Fixed (igual en claro y oscuro).",
            "Texto de menor énfasis sobre los Primary Fixed.",
            "Relleno fijo del secundario, igual en claro y oscuro.",
            "Texto e íconos sobre Secondary Fixed.",
            "Versión más intensa de Secondary Fixed.",
            "Texto de menor énfasis sobre los Secondary Fixed.",
            "Relleno fijo del terciario, igual en claro y oscuro.",
            "Texto e íconos sobre Tertiary Fixed.",
            "Versión más intensa de Tertiary Fixed.",
            "Texto de menor énfasis sobre los Tertiary Fixed.",
            "Superficie más oscura (en claro) para dar profundidad.",
            "Superficie más clara (en oscuro) para dar realce.",
            "Contenedor más bajo: fondos detrás de tarjetas.",
            "Contenedor bajo: tarjetas rellenas, menú lateral.",
            "Contenedor normal: barras de navegación, menús.",
            "Contenedor alto: diálogos, campos rellenos.",
            "Contenedor más alto: campos de texto rellenos, chips.",
    };

    /** Tono HCT (0–100) de un color: coincide con la luminosidad L* de CIELAB. */
    public static int tonoDe(int color) {
        double[] lab = new double[3];
        ColorUtils.colorToLAB(opaque(color), lab);
        return (int) Math.round(lab[0]);
    }

    public static String hex(int color) {
        return String.format(java.util.Locale.ROOT, "#%06X", 0xFFFFFF & color);
    }

    private static int opaque(int color) {
        return color | 0xFF000000;
    }
}
