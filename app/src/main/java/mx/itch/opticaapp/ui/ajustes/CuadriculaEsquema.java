package mx.itch.opticaapp.ui.ajustes;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import mx.itch.opticaapp.util.EsquemaM3;

import static mx.itch.opticaapp.util.EsquemaM3.*;

/**
 * Cuadrícula con los 48 roles de color de Material 3, organizada como la tarjeta
 * "Light Scheme / Dark Scheme" de Material Theme Builder (adaptada de la app Atrium).
 * Cada celda está pintada con su rol y escribe su nombre y código con el color que va encima.
 */
final class CuadriculaEsquema {

    interface AlTocarRol {
        void alTocar(int rol);
    }

    private CuadriculaEsquema() {
    }

    /** Los 48 roles: familias, fijos, superficies y el resto. */
    static void construir(LinearLayout padre, int[] roles, AlTocarRol alTocar) {
        padre.removeAllViews();
        Context c = padre.getContext();

        padre.addView(familias(c, roles, alTocar));
        padre.addView(espacio(c, 10));

        // Fijos: [Fixed | Fixed Dim] / On Fixed / On Fixed Variant, para primario, secundario y terciario
        LinearLayout fijos = new LinearLayout(c);
        fijos.setOrientation(LinearLayout.HORIZONTAL);
        int[][] rolesFijos = {
                {PRIMARY_FIXED, PRIMARY_FIXED_DIM, ON_PRIMARY_FIXED, ON_PRIMARY_FIXED_VARIANT},
                {SECONDARY_FIXED, SECONDARY_FIXED_DIM, ON_SECONDARY_FIXED, ON_SECONDARY_FIXED_VARIANT},
                {TERTIARY_FIXED, TERTIARY_FIXED_DIM, ON_TERTIARY_FIXED, ON_TERTIARY_FIXED_VARIANT}};
        for (int i = 0; i < rolesFijos.length; i++) {
            int[] f = rolesFijos[i];
            LinearLayout columna = vertical(c);
            columna.addView(fila(c, roles, alTocar, 64, f[0], f[1]));
            columna.addView(celda(c, roles, f[2], 40, alTocar));
            columna.addView(celda(c, roles, f[3], 40, alTocar));
            fijos.addView(redondear(c, columna), parametrosColumna(c, i));
        }
        padre.addView(fijos);
        padre.addView(espacio(c, 10));

        padre.addView(redondear(c, fila(c, roles, alTocar, 60, SURFACE_DIM, SURFACE, SURFACE_BRIGHT)));
        padre.addView(espacio(c, 8));
        padre.addView(redondear(c, fila(c, roles, alTocar, 60, SURFACE_CONTAINER_LOWEST, SURFACE_CONTAINER_LOW,
                SURFACE_CONTAINER, SURFACE_CONTAINER_HIGH, SURFACE_CONTAINER_HIGHEST)));
        padre.addView(espacio(c, 8));
        padre.addView(redondear(c, fila(c, roles, alTocar, 48, ON_SURFACE, ON_SURFACE_VARIANT, OUTLINE, OUTLINE_VARIANT)));
        padre.addView(espacio(c, 8));
        padre.addView(redondear(c, fila(c, roles, alTocar, 48, INVERSE_SURFACE, INVERSE_ON_SURFACE, INVERSE_PRIMARY)));
        padre.addView(espacio(c, 8));
        padre.addView(redondear(c, fila(c, roles, alTocar, 48, BACKGROUND, ON_BACKGROUND, SURFACE_VARIANT, SCRIM, SHADOW)));
    }

    /** Cuatro columnas: [X, On X] y [X Container, On X Container] para primario, secundario, terciario y error. */
    private static View familias(Context c, int[] roles, AlTocarRol alTocar) {
        LinearLayout fila = new LinearLayout(c);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        int[] bases = {PRIMARY, SECONDARY, TERTIARY, ERROR};
        for (int i = 0; i < bases.length; i++) {
            int b = bases[i];
            LinearLayout columna = vertical(c);
            LinearLayout arriba = vertical(c);
            arriba.addView(celda(c, roles, b, 64, alTocar));
            arriba.addView(celda(c, roles, b + 1, 40, alTocar));
            LinearLayout abajo = vertical(c);
            abajo.addView(celda(c, roles, b + 2, 64, alTocar));
            abajo.addView(celda(c, roles, b + 3, 40, alTocar));
            columna.addView(redondear(c, arriba));
            columna.addView(espacio(c, 6));
            columna.addView(redondear(c, abajo));
            fila.addView(columna, parametrosColumna(c, i));
        }
        return fila;
    }

    private static LinearLayout fila(Context c, int[] roles, AlTocarRol alTocar, int altoDp, int... rolesFila) {
        LinearLayout fila = new LinearLayout(c);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        for (int rol : rolesFila) {
            fila.addView(celda(c, roles, rol, altoDp, alTocar), new LinearLayout.LayoutParams(0, dp(c, altoDp), 1f));
        }
        return fila;
    }

    /** Una celda: fondo = color del rol; texto = nombre y código en su color "On". */
    private static View celda(Context c, int[] roles, int rol, int altoDp, AlTocarRol alTocar) {
        FrameLayout celda = new FrameLayout(c);
        celda.setBackgroundColor(roles[rol]);
        celda.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, altoDp)));
        int margen = dp(c, 6);
        celda.setPadding(margen, margen - 2, margen, margen - 2);
        int texto = EsquemaM3.textoSobre(rol, roles);

        TextView nombre = new TextView(c);
        nombre.setText(EsquemaM3.nombreCorto(rol));
        nombre.setTextColor(texto);
        nombre.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        nombre.setMaxLines(altoDp >= 56 ? 3 : 2);
        nombre.setLineSpacing(0f, 0.95f);
        celda.addView(nombre, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));

        if (altoDp >= 48) {
            TextView hex = new TextView(c);
            hex.setText(EsquemaM3.hex(roles[rol]));
            hex.setTextColor(texto);
            hex.setAlpha(0.85f);
            hex.setTypeface(Typeface.MONOSPACE);
            hex.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
            celda.addView(hex, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END));
        }

        TypedValue onda = new TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, onda, true);
        celda.setForeground(c.getDrawable(onda.resourceId));
        celda.setClickable(true);
        celda.setContentDescription(EsquemaM3.NOMBRES[rol] + " " + EsquemaM3.hex(roles[rol]));
        celda.setOnClickListener(v -> {
            v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()).start();
            if (alTocar != null) alTocar.alTocar(rol);
        });
        return celda;
    }

    /** Grupo con esquinas redondeadas que recorta a sus celdas. */
    static View redondear(Context c, View contenido) {
        float radio = dp(c, 12);
        contenido.setClipToOutline(true);
        contenido.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View vista, Outline contorno) {
                contorno.setRoundRect(0, 0, vista.getWidth(), vista.getHeight(), radio);
            }
        });
        return contenido;
    }

    private static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private static LinearLayout.LayoutParams parametrosColumna(Context c, int indice) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (indice > 0) lp.setMarginStart(dp(c, 6));
        return lp;
    }

    private static View espacio(Context c, int alto) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, alto)));
        return v;
    }

    static int dp(Context c, float dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }
}
