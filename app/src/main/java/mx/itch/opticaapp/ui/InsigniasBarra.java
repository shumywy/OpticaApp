package mx.itch.opticaapp.ui;

import android.content.Context;
import android.util.Log;

import androidx.annotation.ColorRes;
import androidx.annotation.IdRes;
import androidx.annotation.PluralsRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Arrays;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.model.Orden;

/**
 * Numeritos (insignias, BadgeDrawable de Material) de la barra inferior:
 *  - Laboratorio: órdenes que todavía no se entregan al paciente (Enviado, En proceso o Recibido).
 *  - Inventario: productos con existencias iguales o menores a su stock mínimo
 *    (solo administrador y vendedor; el optometrista no ve Inventario).
 *
 * Son listeners en tiempo real de Firestore: si otra persona cambia una orden o vende la última
 * pieza, el número cambia solo. MainActivity los inicia con la sesión (onStart / al entrar)
 * y los detiene en onStop o al cerrar sesión.
 */
public final class InsigniasBarra {

    private static final String TAG = "InsigniasBarra";

    private final BottomNavigationView barra;
    private ListenerRegistration ordenes;
    private ListenerRegistration productos;

    public InsigniasBarra(BottomNavigationView barra) {
        this.barra = barra;
    }

    public void iniciar() {
        detener();
        if (!Sesion.estaActiva()) {
            quitar();
            return;
        }

        // whereIn sobre un solo campo no necesita índice compuesto
        ordenes = Sesion.coleccion("ordenes")
                .whereIn("estatus", Arrays.asList(Orden.ENVIADO, Orden.EN_PROCESO, Orden.RECIBIDO))
                .addSnapshotListener((resultado, error) -> {
                    if (error != null) {
                        Log.w(TAG, "No se pudieron contar las órdenes pendientes", error);
                        return;
                    }
                    if (resultado != null) {
                        mostrar(R.id.laboratorioFragment, resultado.size(), R.color.morado, R.plurals.insignia_ordenes);
                    }
                });

        if (Sesion.esOptometrista()) {
            barra.removeBadge(R.id.inventarioFragment);
            return;
        }
        // Firestore no compara dos campos del mismo documento (stock <= stockMinimo), así que se
        // escucha el inventario y se cuenta aquí; después solo llegan los productos que cambian.
        productos = Sesion.coleccion("productos")
                .addSnapshotListener((resultado, error) -> {
                    if (error != null) {
                        Log.w(TAG, "No se pudo revisar el stock", error);
                        return;
                    }
                    if (resultado == null) return;
                    int bajos = 0;
                    for (DocumentSnapshot d : resultado.getDocuments()) {
                        Long stock = d.getLong("stock");
                        Long minimo = d.getLong("stockMinimo");
                        if (stock != null && minimo != null && stock <= minimo) bajos++;
                    }
                    mostrar(R.id.inventarioFragment, bajos, R.color.rojo, R.plurals.insignia_stock_bajo);
                });
    }

    /** Deja de escuchar Firestore (los números se quedan como estaban). */
    public void detener() {
        if (ordenes != null) {
            ordenes.remove();
            ordenes = null;
        }
        if (productos != null) {
            productos.remove();
            productos = null;
        }
    }

    /** Sin sesión: quita los dos números de la barra. */
    public void quitar() {
        barra.removeBadge(R.id.laboratorioFragment);
        barra.removeBadge(R.id.inventarioFragment);
    }

    private void mostrar(@IdRes int seccion, int cantidad, @ColorRes int color, @PluralsRes int descripcion) {
        if (cantidad <= 0) {
            BadgeDrawable existente = barra.getBadge(seccion);
            if (existente != null) existente.setVisible(false);
            return;
        }
        Context ctx = barra.getContext();
        BadgeDrawable insignia = barra.getOrCreateBadge(seccion);
        insignia.setBackgroundColor(ContextCompat.getColor(ctx, color));
        insignia.setBadgeTextColor(ContextCompat.getColor(ctx, R.color.blanco));
        insignia.setTextAppearance(R.style.TextoInsignia);
        insignia.setMaxNumber(99);   // de 100 en adelante muestra "99+"
        insignia.setContentDescriptionQuantityStringsResource(descripcion);
        insignia.setContentDescriptionExceedsMaxBadgeNumberStringResource(R.string.insignia_mas_de_99);
        insignia.setNumber(cantidad);
        insignia.setVisible(true);
    }
}
