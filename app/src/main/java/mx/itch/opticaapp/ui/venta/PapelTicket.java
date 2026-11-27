package mx.itch.opticaapp.ui.venta;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Fondo de "papel" del ticket en pantalla: un rectángulo con los bordes de arriba y de abajo en
 * zigzag, como un ticket recién cortado, con una sombra suave (Android 9+ la dibuja con aceleración).
 * La vista que lo usa debe dejar en su padding el espacio de la sombra (margen).
 */
public class PapelTicket extends Drawable {

    private final Paint papel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path forma = new Path();
    /** Ancho de cada diente y su altura. */
    private final float diente;
    private final float altoDiente;
    /** Espacio que se deja alrededor para la sombra. */
    private final float margen;

    public PapelTicket(int colorPapel, int colorSombra, float densidad) {
        diente = 12 * densidad;
        altoDiente = 6 * densidad;
        margen = 8 * densidad;
        papel.setColor(colorPapel);
        papel.setStyle(Paint.Style.FILL);
        papel.setShadowLayer(9 * densidad, 0, 3 * densidad, colorSombra);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect b) {
        super.onBoundsChange(b);
        float izq = b.left + margen;
        float der = b.right - margen;
        float arriba = b.top + margen / 2;
        float abajo = b.bottom - margen;
        forma.reset();
        if (der <= izq || abajo <= arriba + altoDiente * 2) return;

        int dientes = Math.max(1, Math.round((der - izq) / diente));
        float w = (der - izq) / dientes;

        // Borde de arriba, de izquierda a derecha
        forma.moveTo(izq, arriba + altoDiente);
        for (int i = 0; i < dientes; i++) {
            float x = izq + i * w;
            forma.lineTo(x + w / 2, arriba);
            forma.lineTo(x + w, arriba + altoDiente);
        }
        // Lado derecho y borde de abajo, de derecha a izquierda
        forma.lineTo(der, abajo - altoDiente);
        for (int i = dientes - 1; i >= 0; i--) {
            float x = izq + i * w;
            forma.lineTo(x + w / 2, abajo);
            forma.lineTo(x, abajo - altoDiente);
        }
        forma.close();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.drawPath(forma, papel);
    }

    @Override
    public void setAlpha(int alpha) {
        papel.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter filtro) {
        papel.setColorFilter(filtro);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
