package mx.itch.opticaapp.data;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import mx.itch.opticaapp.model.Usuario;

/**
 * Folios consecutivos por óptica ("V-000123" para ventas, "L-000045" para órdenes de laboratorio).
 * El último número usado vive en /opticas/{opticaId}/contadores/folios y se aumenta dentro de una
 * transacción: si dos teléfonos cobran al mismo tiempo, Firestore repite la transacción de uno de
 * ellos y nunca se entrega el mismo número dos veces.
 *
 * Además de los dos contadores, el documento guarda el formato de los folios, cuál fue el último
 * folio de cada tipo, a qué venta u orden se le dio, para qué cliente, quién lo sacó y cuándo.
 */
public final class Folios {

    /** Sin conexión una transacción tarda en rendirse; después de esto se sigue con folio provisional. */
    private static final long ESPERA_MS = 6000;

    public static final String PREFIJO_VENTA = "V-";
    public static final String PREFIJO_ORDEN = "L-";
    public static final int DIGITOS = 6;

    private Folios() {
    }

    private static DocumentReference contador() {
        return Sesion.coleccion("contadores").document("folios");
    }

    /**
     * Reserva el siguiente número de venta y/o de orden.
     *
     * @param ventaId id del documento de la venta que recibirá el folio, o null si no se pide folio de venta
     * @param ordenId id del documento de la orden, o null si no se pide folio de orden
     * @param cliente nombre del cliente (se guarda como dato del último folio)
     * @param total   total de la venta (0 si solo es una orden)
     * @return {venta, orden}; trae 0 en lo que no se pidió
     */
    public static Task<long[]> reservar(@Nullable String ventaId, @Nullable String ordenId,
                                        String cliente, double total) {
        DocumentReference ref = contador();
        Usuario usuario = Sesion.getUsuario();
        String usuarioId = usuario != null && usuario.id != null ? usuario.id : "";
        String usuarioNombre = usuario != null && usuario.nombre != null ? usuario.nombre : "";
        String nombreCliente = cliente != null ? cliente : "";

        Task<long[]> transaccion = FirebaseFirestore.getInstance().runTransaction(tx -> {
            DocumentSnapshot actual = tx.get(ref);
            long numeroVenta = valor(actual, "ventas");
            long numeroOrden = valor(actual, "ordenes");
            Map<String, Object> nuevos = new HashMap<>();
            if (ventaId != null) {
                numeroVenta++;
                nuevos.put("ventas", numeroVenta);
                nuevos.put("ultimoFolioVenta", venta(numeroVenta));
                nuevos.put("ultimaVentaId", ventaId);
                nuevos.put("ultimaVentaCliente", nombreCliente);
                nuevos.put("ultimaVentaTotal", total);
                nuevos.put("fechaUltimaVenta", FieldValue.serverTimestamp());
            }
            if (ordenId != null) {
                numeroOrden++;
                nuevos.put("ordenes", numeroOrden);
                nuevos.put("ultimoFolioOrden", orden(numeroOrden));
                nuevos.put("ultimaOrdenId", ordenId);
                nuevos.put("ultimaOrdenCliente", nombreCliente);
                nuevos.put("fechaUltimaOrden", FieldValue.serverTimestamp());
            }
            // Quién sacó el último folio y cuándo
            nuevos.put("ultimoUsuarioId", usuarioId);
            nuevos.put("ultimoUsuarioNombre", usuarioNombre);
            nuevos.put("actualizado", FieldValue.serverTimestamp());
            // Formato de los folios (queda escrito para que se entienda el documento en la consola)
            nuevos.put("prefijoVenta", PREFIJO_VENTA);
            nuevos.put("prefijoOrden", PREFIJO_ORDEN);
            nuevos.put("digitos", DIGITOS);
            if (!actual.exists()) nuevos.put("creado", FieldValue.serverTimestamp());
            tx.set(ref, nuevos, SetOptions.merge());
            return new long[]{ventaId != null ? numeroVenta : 0, ordenId != null ? numeroOrden : 0};
        });

        TaskCompletionSource<long[]> resultado = new TaskCompletionSource<>();
        Handler principal = new Handler(Looper.getMainLooper());
        Runnable sinRespuesta = () -> resultado.trySetException(
                new TimeoutException("El servidor no respondió a tiempo"));
        principal.postDelayed(sinRespuesta, ESPERA_MS);
        transaccion.addOnCompleteListener(t -> {
            principal.removeCallbacks(sinRespuesta);
            if (t.isSuccessful() && t.getResult() != null) {
                resultado.trySetResult(t.getResult());
            } else {
                resultado.trySetException(t.getException() != null
                        ? t.getException() : new IllegalStateException("No se reservó el folio"));
            }
        });
        return resultado.getTask();
    }

    private static long valor(DocumentSnapshot documento, String campo) {
        Long numero = documento.getLong(campo);
        return numero != null ? numero : 0;
    }

    /** 123 → "V-000123". */
    public static String venta(long numero) {
        return PREFIJO_VENTA + String.format(Locale.ROOT, "%0" + DIGITOS + "d", numero);
    }

    /** 45 → "L-000045". */
    public static String orden(long numero) {
        return PREFIJO_ORDEN + String.format(Locale.ROOT, "%0" + DIGITOS + "d", numero);
    }

    /**
     * Folio provisional (sin conexión): el prefijo con las primeras letras del id del documento,
     * por ejemplo "V-K3F9QA". Se distingue de los consecutivos porque lleva letras.
     */
    public static String provisional(String prefijo, String idDocumento) {
        String parte = idDocumento.length() > 6 ? idDocumento.substring(0, 6) : idDocumento;
        return prefijo + "-" + parte.toUpperCase(Locale.ROOT);
    }
}
