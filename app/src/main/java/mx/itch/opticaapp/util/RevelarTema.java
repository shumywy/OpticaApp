package mx.itch.opticaapp.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

import androidx.annotation.Nullable;

/**
 * Transición al cambiar de tema (la misma idea que en la app Atrium).
 *
 * Para cambiar de paleta hay que volver a crear la Activity, y eso hace un parpadeo. Antes de
 * recrearla se toma una foto de la pantalla (PixelCopy); la Activity nueva la pone encima y le abre un hueco
 * redondo que crece desde el botón que se tocó, así el tema nuevo aparece como una onda.
 */
public final class RevelarTema {

    /** Si la Activity no se recrea en este tiempo, la foto se descarta. */
    private static final long VIGENCIA_MS = 3000;
    private static final long DURACION_MS = 600;

    private static Bitmap foto;
    private static float origenX, origenY;
    private static long tomadaEn;

    private RevelarTema() {
    }

    /**
     * Toma la foto de la pantalla y después ejecuta "luego" (el cambio de tema).
     * Usa PixelCopy, que copia lo que se ve en la ventana tal cual (incluidas las fotos que Glide
     * carga como bitmaps de hardware, que no se pueden dibujar en un Canvas normal).
     * Si la foto falla, el tema se cambia igual, solo que sin animación.
     *
     * @param origen vista de donde sale la onda (el botón que se tocó); null = centro de la pantalla
     */
    public static void capturarYLuego(Activity actividad, @Nullable View origen, Runnable luego) {
        limpiar();
        View decor = actividad.getWindow().getDecorView();
        int ancho = decor.getWidth(), alto = decor.getHeight();
        if (ancho == 0 || alto == 0) {
            luego.run();
            return;
        }
        float x = ancho / 2f, y = alto / 2f;
        if (origen != null && origen.isAttachedToWindow()) {
            int[] pos = new int[2];
            origen.getLocationInWindow(pos);
            x = pos[0] + origen.getWidth() / 2f;
            y = pos[1] + origen.getHeight() / 2f;
        }
        final float fx = x, fy = y;
        try {
            Bitmap b = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888);
            PixelCopy.request(actividad.getWindow(), b, resultado -> {
                if (resultado == PixelCopy.SUCCESS) {
                    foto = b;
                    origenX = fx;
                    origenY = fy;
                    tomadaEn = SystemClock.elapsedRealtime();
                } else {
                    b.recycle();
                }
                luego.run();
            }, new Handler(Looper.getMainLooper()));
        } catch (RuntimeException | OutOfMemoryError e) {
            limpiar();
            luego.run();
        }
    }

    /** Lo llama MainActivity.onCreate: si hay una foto reciente, la pone encima y la desvanece en círculo. */
    public static void reproducirSiHay(Activity actividad) {
        if (foto == null) return;
        if (SystemClock.elapsedRealtime() - tomadaEn > VIGENCIA_MS) {
            limpiar();
            return;
        }
        Bitmap b = foto;
        float x = origenX, y = origenY;
        foto = null;

        ViewGroup decor = (ViewGroup) actividad.getWindow().getDecorView();
        CapaConHueco capa = new CapaConHueco(actividad, b, x, y);
        decor.addView(capa, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Radio hasta la esquina más lejana del punto de origen
        float radio = (float) Math.hypot(Math.max(x, b.getWidth() - x), Math.max(y, b.getHeight() - y));
        ValueAnimator onda = ValueAnimator.ofFloat(0f, radio);
        onda.setDuration(DURACION_MS);
        onda.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        onda.addUpdateListener(a -> capa.setRadio((float) a.getAnimatedValue()));
        onda.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animacion) {
                decor.removeView(capa);
                b.recycle();
            }
        });
        onda.setStartDelay(60);   // deja que la pantalla nueva se dibuje una vez debajo
        onda.start();
    }

    /**
     * La foto de la pantalla anterior con un hueco redondo que va creciendo: por el hueco se ve
     * la pantalla nueva. Bloquea los toques mientras dura la animación.
     */
    private static final class CapaConHueco extends View {

        private final Bitmap foto;
        private final float cx, cy;
        private final Path hueco = new Path();
        private float radio;

        CapaConHueco(Activity actividad, Bitmap foto, float cx, float cy) {
            super(actividad);
            this.foto = foto;
            this.cx = cx;
            this.cy = cy;
            setClickable(true);
        }

        void setRadio(float r) {
            radio = r;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            if (radio > 0) {
                hueco.reset();
                hueco.addCircle(cx, cy, radio, Path.Direction.CW);
                canvas.clipOutPath(hueco);   // API 26+, el mínimo de la app
            }
            canvas.drawBitmap(foto, 0, 0, null);
            canvas.restore();
        }
    }

    private static void limpiar() {
        if (foto != null && !foto.isRecycled()) foto.recycle();
        foto = null;
    }
}
