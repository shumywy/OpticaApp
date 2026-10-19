package mx.itch.opticaapp.util;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import mx.itch.opticaapp.R;

/**
 * Animaciones de entrada de cada pantalla (fragmento).
 *
 * Todas las pantallas siguen la misma "coreografía", pero cada una anima sus propias piezas:
 * 1) el encabezado sobre el degradado aparece en cascada (cada texto baja un poco y aparece);
 * 2) la hoja blanca sube desde abajo;
 * 3) después entran los elementos propios: mosaicos, renglones de la lista, foto, botón +, pie de precio.
 *
 * Se usan ViewPropertyAnimator (view.animate()), que respeta la opción del teléfono
 * "Escala de animación": si el usuario quitó las animaciones, todo aparece al instante.
 */
public final class Animaciones {

    /** Curva suave: arranca rápido y frena al final (la que recomienda Material para entradas). */
    private static final Interpolator SUAVE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    /**
     * Fragmentos que ya hicieron su entrada. Al regresar con "atrás" el fragmento vuelve a crear su vista,
     * pero no queremos repetir la entrada (ya hay una transición de regreso). Es "débil" para no
     * retener fragmentos que ya no existen.
     */
    private static final Set<Fragment> yaAnimados = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Fragment> listasAnimadas = Collections.newSetFromMap(new WeakHashMap<>());

    private Animaciones() {
    }

    /**
     * Entrada estándar de una pantalla: el encabezado en cascada y la hoja blanca subiendo.
     * Devuelve true si se animó (la primera vez) para que el fragmento anime también sus piezas propias.
     */
    public static boolean entrada(Fragment fragmento, @Nullable ViewGroup encabezado, @Nullable View hoja) {
        if (!yaAnimados.add(fragmento)) return false;
        float dp = fragmento.getResources().getDisplayMetrics().density;
        if (encabezado != null) {
            cascada(encabezado, -16 * dp, 0);
        }
        if (hoja != null) {
            subir(hoja, 56 * dp, 120);
        }
        return true;
    }

    /** Los hijos visibles del grupo bajan un poco y aparecen uno tras otro (60 ms de diferencia). */
    public static void cascada(ViewGroup grupo, float desdeY, long retraso) {
        int orden = 0;
        for (int i = 0; i < grupo.getChildCount(); i++) {
            View hijo = grupo.getChildAt(i);
            if (hijo.getVisibility() != View.VISIBLE) continue;
            hijo.setAlpha(0f);
            hijo.setTranslationY(desdeY);
            hijo.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(retraso + 60L * orden++)
                    .setDuration(420)
                    .setInterpolator(SUAVE)
                    .start();
        }
    }

    /** La vista sube desde "desde" píxeles más abajo mientras aparece (hojas blancas y pies de precio). */
    public static void subir(View vista, float desde, long retraso) {
        vista.setAlpha(0f);
        vista.setTranslationY(desde);
        vista.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(retraso)
                .setDuration(480)
                .setInterpolator(SUAVE)
                .start();
    }

    /** La vista crece desde 85 % con un pequeño rebote mientras aparece (fotos, mosaicos, botón +). */
    public static void aparecer(View vista, long retraso) {
        if (vista.getVisibility() != View.VISIBLE) return;
        vista.setAlpha(0f);
        vista.setScaleX(0.85f);
        vista.setScaleY(0.85f);
        vista.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(retraso)
                .setDuration(380)
                .setInterpolator(new OvershootInterpolator(1.4f))
                .start();
    }

    /** Varias vistas aparecen una tras otra (50 ms de diferencia), saltándose las ocultas. */
    public static void aparecerEnCascada(List<? extends View> vistas, long retraso) {
        int orden = 0;
        for (View v : vistas) {
            if (v.getVisibility() != View.VISIBLE) continue;
            aparecer(v, retraso + 50L * orden++);
        }
    }

    /**
     * Los renglones de una lista entran uno tras otro (res/anim/lista_entrada.xml).
     * Se llama cuando llegan los datos de Firestore; solo anima la primera carga de cada pantalla,
     * no cada vez que se escribe en el buscador.
     */
    public static void listaUnaVez(Fragment fragmento, RecyclerView lista) {
        if (lista.getAdapter() == null || lista.getAdapter().getItemCount() == 0) return;
        if (!listasAnimadas.add(fragmento)) return;
        lista.setLayoutAnimation(AnimationUtils.loadLayoutAnimation(lista.getContext(), R.anim.lista_entrada));
        lista.scheduleLayoutAnimation();
    }
}
