package mx.itch.opticaapp.ui.inventario;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import mx.itch.opticaapp.R;

/**
 * Círculo de una variante de color en el detalle del producto.
 *
 * - Normal: círculo del color con un borde muy fino.
 * - Seleccionada: además, un anillo doble (gris por fuera y blanco por dentro) y una textura
 *   de tela tejida encima del color, como vista previa del material.
 * - Botón agregar: círculo de borde punteado gris con un signo "+".
 */
public class MuestraColor extends View {

    /** Textura de tejido diagonal (sarga) que se repite; se crea una sola vez. */
    private static Bitmap tejido;

    private final Paint relleno = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textura = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint contorno = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint anillo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blanco = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint punteado = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;

    private int color = Color.GRAY;
    private boolean seleccionada;
    private boolean botonAgregar;

    public MuestraColor(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;

        contorno.setStyle(Paint.Style.STROKE);
        contorno.setStrokeWidth(dp);
        contorno.setColor(0x1F000000);

        anillo.setStyle(Paint.Style.STROKE);
        anillo.setStrokeWidth(2 * dp);
        anillo.setColor(ContextCompat.getColor(context, R.color.texto_tenue));

        blanco.setColor(ContextCompat.getColor(context, R.color.superficie));

        punteado.setStyle(Paint.Style.STROKE);
        punteado.setStrokeWidth(1.5f * dp);
        punteado.setStrokeCap(Paint.Cap.ROUND);
        punteado.setColor(ContextCompat.getColor(context, R.color.texto_tenue));

        if (tejido == null) tejido = crearTejido();
        BitmapShader sombreado = new BitmapShader(tejido, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        Matrix escala = new Matrix();
        escala.setScale(dp / 2f, dp / 2f);   // cada hilo mide medio dp
        sombreado.setLocalMatrix(escala);
        textura.setShader(sombreado);
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    public void setSeleccionada(boolean seleccionada) {
        this.seleccionada = seleccionada;
        setSelected(seleccionada);
        invalidate();
    }

    public void setBotonAgregar(boolean botonAgregar) {
        this.botonAgregar = botonAgregar;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radio = Math.min(getWidth(), getHeight()) / 2f;

        if (botonAgregar) {
            float r = radio - 4 * dp;
            punteado.setPathEffect(new DashPathEffect(new float[]{4 * dp, 3 * dp}, 0));
            canvas.drawCircle(cx, cy, r, punteado);
            punteado.setPathEffect(null);
            float brazo = r * 0.38f;
            canvas.drawLine(cx - brazo, cy, cx + brazo, cy, punteado);
            canvas.drawLine(cx, cy - brazo, cx, cy + brazo, punteado);
            return;
        }

        float radioColor = radio - 6 * dp;
        if (seleccionada) {
            canvas.drawCircle(cx, cy, radio - dp, anillo);         // anillo gris exterior
            canvas.drawCircle(cx, cy, radio - 2 * dp, blanco);     // separación blanca
        }
        relleno.setColor(color);
        canvas.drawCircle(cx, cy, radioColor, relleno);
        if (seleccionada) canvas.drawCircle(cx, cy, radioColor, textura);
        canvas.drawCircle(cx, cy, radioColor, contorno);
    }

    /** Patrón de 8 × 8 píxeles con líneas diagonales claras y oscuras muy suaves (tela de microfibra). */
    private static Bitmap crearTejido() {
        int lado = 8;
        Bitmap b = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < lado; x++) {
            for (int y = 0; y < lado; y++) {
                int banda = (x + y) % 4;
                int pixel = banda < 2 ? Color.argb(34, 255, 255, 255) : Color.argb(30, 0, 0, 0);
                if ((x * 3 + y * 5) % 7 == 0) pixel = Color.argb(46, 255, 255, 255);   // brillo irregular
                b.setPixel(x, y, pixel);
            }
        }
        return b;
    }
}
