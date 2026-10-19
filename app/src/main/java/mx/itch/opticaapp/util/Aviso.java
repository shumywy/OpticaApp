package mx.itch.opticaapp.util;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import mx.itch.opticaapp.R;

/**
 * Aviso flotante de confirmación ("Producto guardado"): cápsula gris oscuro con un ícono,
 * centrada en la parte inferior, justo arriba de la barra inferior o del pie con el precio.
 *
 * Se agrega sobre la Activity (no sobre el Fragment), así que sigue visible aunque la pantalla
 * cambie justo después; por ejemplo, al regresar del formulario al detalle del producto.
 * Aparece, se queda 2 segundos y se desvanece.
 */
public final class Aviso {

    private static final long DURACION_MS = 2200;
    private static final String ETIQUETA = "aviso_flotante";

    private Aviso() {
    }

    /** Aviso con el ícono de lentes. */
    public static void mostrar(@Nullable Activity actividad, String texto) {
        mostrar(actividad, texto, R.drawable.ic_inventario);
    }

    public static void mostrar(@Nullable Activity actividad, String texto, @DrawableRes int icono) {
        if (actividad == null || actividad.isFinishing()) return;
        FrameLayout contenido = actividad.findViewById(android.R.id.content);
        if (contenido == null) return;
        float dp = actividad.getResources().getDisplayMetrics().density;

        // Si había un aviso anterior, se reemplaza
        View anterior = contenido.findViewWithTag(ETIQUETA);
        if (anterior != null) contenido.removeView(anterior);

        TextView aviso = new TextView(actividad);
        aviso.setTag(ETIQUETA);
        aviso.setText(texto);
        aviso.setTextColor(Color.WHITE);
        aviso.setTextSize(14);
        aviso.setTypeface(ResourcesCompat.getFont(actividad, R.font.lexend_medium));
        aviso.setMaxLines(2);
        aviso.setGravity(Gravity.CENTER_VERTICAL);
        aviso.setPadding(Math.round(18 * dp), Math.round(12 * dp), Math.round(20 * dp), Math.round(12 * dp));
        aviso.setElevation(8 * dp);
        aviso.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

        GradientDrawable fondo = new GradientDrawable();
        fondo.setColor(ContextCompat.getColor(actividad, R.color.aviso_fondo));
        fondo.setCornerRadius(16 * dp);
        aviso.setBackground(fondo);

        Drawable dibujo = ContextCompat.getDrawable(actividad, icono);
        if (dibujo != null) {
            dibujo = DrawableCompat.wrap(dibujo.mutate());
            DrawableCompat.setTint(dibujo, Color.WHITE);
            int lado = Math.round(18 * dp);
            dibujo.setBounds(0, 0, lado, lado);
            aviso.setCompoundDrawablesRelative(dibujo, null, null, null);
            aviso.setCompoundDrawablePadding(Math.round(10 * dp));
        }

        // Arriba de la barra de navegación del sistema (o del teclado) y de la barra inferior de la app
        int abajo = 0;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(contenido);
        if (insets != null) {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            abajo = barras.bottom;
        }
        FrameLayout.LayoutParams lugar = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lugar.bottomMargin = abajo + Math.round(96 * dp);
        lugar.leftMargin = Math.round(24 * dp);
        lugar.rightMargin = Math.round(24 * dp);
        contenido.addView(aviso, lugar);

        // Entra subiendo un poco, espera y se desvanece
        aviso.setAlpha(0f);
        aviso.setTranslationY(12 * dp);
        aviso.animate().alpha(1f).translationY(0f).setDuration(200).start();
        aviso.postDelayed(() -> aviso.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> contenido.removeView(aviso)).start(), DURACION_MS);
    }
}
