package mx.itch.opticaapp.ui.panel;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import mx.itch.opticaapp.R;

/**
 * Fondo del panel: unos lentes grandes con efecto de vidrio y cuatro animaciones.
 *
 * VIDRIO ESMERILADO (sin librerías, en cualquier versión de Android):
 * se dibujan DOS copias de la misma imagen en el mismo lugar.
 *  - La nítida (lentes_hero) solo FUERA de la hoja de vidrio.
 *  - La desenfocada (lentes_hero_difuso) solo DENTRO de la hoja.
 *
 * ANIMACIONES:
 *  1) Entrada: llegan desde la derecha mientras aparecen.
 *  2) Balanceo: flotan arriba y abajo y se ladean un poco, en un ciclo de 6 segundos.
 *  3) Destello: una franja de luz cruza los lentes en cada ciclo, como reflejo en el acrílico.
 *  4) Inclinación: se mueven un poco hacia el lado al que se inclina el teléfono (acelerómetro).
 *  5) Toque: al tocarlos dan una vuelta completa sobre sí mismos.
 *
 * Todo se pausa cuando la pantalla no se ve, y si el teléfono tiene las animaciones
 * desactivadas solo se muestran quietos.
 */
public class FondoLentes extends View implements SensorEventListener {

    /** Ancho de los lentes respecto al ancho de la pantalla (se salen un poco por la derecha). */
    private static final float ESCALA = 1.10f;
    /** Qué tanto de la imagen queda arriba del final del espacio reservado (el resto lo tapa la tarjeta). */
    private static final float SOBRE_EL_ANCLA = 0.30f;

    /** Duración de un ciclo de balanceo (y cada cuánto pasa el destello). */
    private static final long CICLO_MS = 6000;
    /** Cuánto suben y bajan (dp) y cuánto se ladean (grados). */
    private static final float FLOTACION_DP = 9f;
    private static final float GIRO_GRADOS = 2.5f;
    /** Parte del ciclo en la que cruza el destello (de 0.10 a 0.32 = poco más de 1 segundo). */
    private static final float DESTELLO_INICIO = 0.10f;
    private static final float DESTELLO_FIN = 0.32f;
    /** Cuánto se mueven al inclinar el teléfono (dp). */
    private static final float INCLINACION_DP = 16f;

    private final float dp;
    private final float radioHoja;
    private final Path formaHoja = new Path();
    private final RectF rectHoja = new RectF();
    private final RectF rectHojaAnterior = new RectF();
    private final Rect rectImagen = new Rect();
    private final RectF rectImagenF = new RectF();
    private final int[] posicionPropia = new int[2];
    private final int[] posicionOtra = new int[2];

    // Destello: franja diagonal blanca que solo se pinta encima de los lentes (SRC_ATOP)
    private final Paint pinturaDestello = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrizDestello = new Matrix();
    private float anchoFranja = -1f;

    @Nullable private Drawable nitida;
    @Nullable private Drawable difusa;
    @Nullable private View ancla;
    @Nullable private View hoja;
    private float anclaAbajo = Float.NaN;

    // Estado de las animaciones
    private float entrada = 1f;        // 0 = todavía no aparecen, 1 = en su lugar
    private float ciclo = 0f;          // 0..1 dentro del balanceo
    private float vuelta = 0f;         // 0..1 durante el giro al tocarlos
    private float inclinacionX = 0f;   // -1..1, filtrada
    private float inclinacionY = 0f;

    @Nullable private ValueAnimator animBalanceo;
    @Nullable private ValueAnimator animEntrada;
    @Nullable private ValueAnimator animVuelta;

    @Nullable private final SensorManager sensores;
    @Nullable private final Sensor acelerometro;
    private boolean escuchandoSensor = false;

    /** Antes de cada cuadro revisa si la hoja se movió, para que el recorte la siga. */
    private final ViewTreeObserver.OnPreDrawListener vigilante = () -> {
        if (medir()) invalidate();
        return true;
    };

    public FondoLentes(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        radioHoja = 36 * dp;   // igual que las esquinas de bg_hoja_vidrio
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        pinturaDestello.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        sensores = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        acelerometro = sensores != null ? sensores.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
    }

    /** Lo llama PanelFragment: dónde se acomodan los lentes y cuál es la hoja de vidrio. */
    public void configurar(@NonNull View ancla, @NonNull View hoja) {
        // mutate(): cada copia tiene su propia transparencia sin afectar a otras pantallas
        nitida = ContextCompat.getDrawable(getContext(), R.drawable.lentes_hero);
        difusa = ContextCompat.getDrawable(getContext(), R.drawable.lentes_hero_difuso);
        if (nitida != null) nitida = nitida.mutate();
        if (difusa != null) difusa = difusa.mutate();
        this.ancla = ancla;
        this.hoja = hoja;
        invalidate();
    }

    // ======================= Animaciones =======================

    /** 1) Entrada: los lentes llegan desde la derecha mientras aparecen. */
    public void entrar(long retraso) {
        if (animEntrada != null) animEntrada.cancel();
        entrada = 0f;
        animEntrada = ValueAnimator.ofFloat(0f, 1f);
        animEntrada.setStartDelay(retraso);
        animEntrada.setDuration(900);
        animEntrada.setInterpolator(new DecelerateInterpolator(2f));
        animEntrada.addUpdateListener(a -> {
            entrada = (float) a.getAnimatedValue();
            invalidate();
        });
        animEntrada.start();
        invalidate();
    }

    /** 2) y 3) Balanceo y destello: un solo reloj que da vueltas de 0 a 1 cada 6 segundos. */
    private void iniciarBalanceo() {
        if (!animacionesActivas()) return;
        if (animBalanceo != null) {
            if (animBalanceo.isPaused()) animBalanceo.resume();
            return;
        }
        animBalanceo = ValueAnimator.ofFloat(0f, 1f);
        animBalanceo.setDuration(CICLO_MS);
        animBalanceo.setRepeatCount(ValueAnimator.INFINITE);
        animBalanceo.setRepeatMode(ValueAnimator.RESTART);
        animBalanceo.setInterpolator(new LinearInterpolator());
        animBalanceo.addUpdateListener(a -> {
            ciclo = (float) a.getAnimatedValue();
            invalidate();
        });
        animBalanceo.start();
    }

    /** 5) Toque: una vuelta completa (se "voltean" y regresan) con un pequeño salto. */
    private void darVuelta() {
        if (animVuelta != null && animVuelta.isRunning()) return;
        animVuelta = ValueAnimator.ofFloat(0f, 1f);
        animVuelta.setDuration(950);
        animVuelta.setInterpolator(new OvershootInterpolator(0.8f));
        animVuelta.addUpdateListener(a -> {
            vuelta = (float) a.getAnimatedValue();
            invalidate();
        });
        animVuelta.start();
    }

    /** 4) Inclinación: el acelerómetro dice hacia dónde está inclinado el teléfono. */
    @Override
    public void onSensorChanged(SensorEvent evento) {
        // x: positivo cuando el teléfono se inclina a la izquierda; y: casi 9.8 cuando está de pie
        float objetivoX = clamp(-evento.values[0] / 6f, -1f, 1f);
        float objetivoY = clamp((evento.values[1] - 7f) / 6f, -1f, 1f);
        // Filtro suave para que no tiemble
        float nuevoX = inclinacionX + (objetivoX - inclinacionX) * 0.08f;
        float nuevoY = inclinacionY + (objetivoY - inclinacionY) * 0.08f;
        boolean cambio = Math.abs(nuevoX - inclinacionX) * INCLINACION_DP * dp > 0.2f
                || Math.abs(nuevoY - inclinacionY) * INCLINACION_DP * dp > 0.2f;
        inclinacionX = nuevoX;
        inclinacionY = nuevoY;
        if (cambio && (animBalanceo == null || !animBalanceo.isRunning())) invalidate();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int precision) {
    }

    private void escucharSensor(boolean escuchar) {
        if (sensores == null || acelerometro == null) return;
        if (escuchar && !escuchandoSensor && animacionesActivas()) {
            sensores.registerListener(this, acelerometro, SensorManager.SENSOR_DELAY_GAME);
            escuchandoSensor = true;
        } else if (!escuchar && escuchandoSensor) {
            sensores.unregisterListener(this);
            escuchandoSensor = false;
        }
    }

    /** Respeta la opción del teléfono "Escala de animación" (si está en 0, los lentes quedan quietos). */
    private boolean animacionesActivas() {
        float escala = Settings.Global.getFloat(getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        return escala > 0f;
    }

    // ======================= Ciclo de vida de la vista =======================

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(vigilante);
        iniciarBalanceo();
        escucharSensor(true);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibilidad) {
        super.onWindowVisibilityChanged(visibilidad);
        // La app pasó a segundo plano o volvió: se pausan o reanudan las animaciones y el sensor
        if (visibilidad == VISIBLE) {
            iniciarBalanceo();
            escucharSensor(true);
        } else {
            if (animBalanceo != null) animBalanceo.pause();
            escucharSensor(false);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        // Se detiene todo para no gastar batería ni retener la pantalla
        if (animBalanceo != null) animBalanceo.cancel();
        if (animEntrada != null) animEntrada.cancel();
        if (animVuelta != null) animVuelta.cancel();
        animBalanceo = null;
        animEntrada = null;
        animVuelta = null;
        escucharSensor(false);
        ViewTreeObserver observador = getViewTreeObserver();
        if (observador.isAlive()) observador.removeOnPreDrawListener(vigilante);
        super.onDetachedFromWindow();
    }

    // ======================= Toque =======================

    @Override
    public boolean onTouchEvent(MotionEvent evento) {
        // Solo responde si se toca encima de los lentes
        boolean sobreLosLentes = rectImagen.contains((int) evento.getX(), (int) evento.getY());
        if (evento.getAction() == MotionEvent.ACTION_DOWN) return sobreLosLentes;
        if (evento.getAction() == MotionEvent.ACTION_UP && sobreLosLentes) {
            performClick();
            return true;
        }
        return super.onTouchEvent(evento);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        darVuelta();
        return true;
    }

    // ======================= Dibujo =======================

    /**
     * Calcula dónde están la hoja y el ancla respecto a esta vista (incluye sus animaciones).
     * Devuelve true si algo cambió desde el último dibujo.
     */
    private boolean medir() {
        if (hoja == null || ancla == null || !hoja.isLaidOut() || !ancla.isLaidOut()) return false;
        getLocationInWindow(posicionPropia);

        hoja.getLocationInWindow(posicionOtra);
        float izquierda = posicionOtra[0] - posicionPropia[0];
        float arriba = posicionOtra[1] - posicionPropia[1];
        // La forma se alarga hacia abajo para que las esquinas inferiores no queden redondeadas
        rectHoja.set(izquierda, arriba, izquierda + hoja.getWidth(), arriba + hoja.getHeight() + radioHoja);

        ancla.getLocationInWindow(posicionOtra);
        float nuevoAncla = posicionOtra[1] - posicionPropia[1] + ancla.getHeight();

        boolean cambio = !rectHoja.equals(rectHojaAnterior) || nuevoAncla != anclaAbajo;
        rectHojaAnterior.set(rectHoja);
        anclaAbajo = nuevoAncla;
        return cambio;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (nitida == null || difusa == null || getWidth() == 0) return;
        medir();
        if (Float.isNaN(anclaAbajo)) return;

        // ---- Posición base de los lentes ----
        float ancho = getWidth() * ESCALA;
        float alto = ancho * nitida.getIntrinsicHeight() / (float) nitida.getIntrinsicWidth();
        int izquierda = Math.round((1f - entrada) * 48 * dp);
        int arriba = Math.round(anclaAbajo - alto * SOBRE_EL_ANCLA);
        rectImagen.set(izquierda, arriba, Math.round(izquierda + ancho), Math.round(arriba + alto));
        rectImagenF.set(rectImagen);
        int alfa = Math.round(255 * entrada);

        // Forma de la hoja: solo las esquinas de arriba redondeadas
        formaHoja.reset();
        formaHoja.addRoundRect(rectHoja,
                new float[]{radioHoja, radioHoja, radioHoja, radioHoja, 0, 0, 0, 0},
                Path.Direction.CW);

        // 1) Lentes nítidos (con destello): en todo, MENOS dentro de la hoja
        canvas.save();
        canvas.clipPath(formaHoja, Region.Op.DIFFERENCE);
        aplicarMovimiento(canvas);
        nitida.setBounds(rectImagen);
        nitida.setAlpha(alfa);
        float destello = progresoDestello();
        if (destello >= 0f) {
            // Capa aparte para que la luz solo se pinte donde hay lentes
            int capa = canvas.saveLayer(rectImagenF, null);
            nitida.draw(canvas);
            pintarDestello(canvas, destello);
            canvas.restoreToCount(capa);
        } else {
            nitida.draw(canvas);
        }
        canvas.restore();

        // 2) Lentes desenfocados: SOLO dentro de la hoja (se ven a través del vidrio)
        canvas.save();
        canvas.clipPath(formaHoja);
        aplicarMovimiento(canvas);
        difusa.setBounds(rectImagen);
        difusa.setAlpha(alfa);
        difusa.draw(canvas);
        canvas.restore();
    }

    /** Balanceo + inclinación + vuelta, aplicados alrededor del centro de los lentes. */
    private void aplicarMovimiento(Canvas canvas) {
        float cx = rectImagenF.centerX();
        float cy = rectImagenF.centerY();
        double angulo = 2 * Math.PI * ciclo;

        // Balanceo: suben y bajan, y se ladean con un desfase (dibuja una especie de "ocho")
        float flotar = (float) Math.sin(angulo) * FLOTACION_DP * dp;
        float ladeo = (float) Math.sin(angulo + Math.PI / 2) * GIRO_GRADOS;

        // Inclinación del teléfono
        float movX = inclinacionX * INCLINACION_DP * dp;
        float movY = inclinacionY * INCLINACION_DP * 0.6f * dp;
        float ladeoInclinacion = inclinacionX * 2f;

        // Vuelta al tocarlos: el ancho va de 1 a -1 y regresa (como girar sobre su eje) y dan un saltito
        float escalaX = (float) Math.cos(2 * Math.PI * vuelta);
        float salto = (float) Math.sin(Math.PI * Math.min(1f, vuelta)) * -14 * dp;

        canvas.translate(movX, flotar + movY + salto);
        canvas.rotate(ladeo + ladeoInclinacion, cx, cy);
        if (vuelta > 0f) {
            canvas.scale(escalaX, 1f, cx, cy);   // al terminar vale 1, así que no se nota el corte
        }
    }

    /** Devuelve de 0 a 1 mientras cruza el destello, o -1 si no toca destello. */
    private float progresoDestello() {
        if (animBalanceo == null || entrada < 1f) return -1f;
        if (ciclo < DESTELLO_INICIO || ciclo > DESTELLO_FIN) return -1f;
        return (ciclo - DESTELLO_INICIO) / (DESTELLO_FIN - DESTELLO_INICIO);
    }

    /** Franja diagonal de luz que cruza los lentes de izquierda a derecha. */
    private void pintarDestello(Canvas canvas, float progreso) {
        float ancho = rectImagenF.width();
        if (anchoFranja != ancho * 0.28f) {
            anchoFranja = ancho * 0.28f;
            pinturaDestello.setShader(new LinearGradient(
                    -anchoFranja / 2f, -anchoFranja * 0.35f, anchoFranja / 2f, anchoFranja * 0.35f,
                    new int[]{0x00FFFFFF, 0xA6FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP));
        }
        float x = rectImagenF.left - anchoFranja + progreso * (ancho + 2 * anchoFranja);
        matrizDestello.setTranslate(x, rectImagenF.centerY());
        pinturaDestello.getShader().setLocalMatrix(matrizDestello);
        canvas.drawRect(rectImagenF, pinturaDestello);
    }

    private static float clamp(float valor, float min, float max) {
        return Math.max(min, Math.min(max, valor));
    }
}
