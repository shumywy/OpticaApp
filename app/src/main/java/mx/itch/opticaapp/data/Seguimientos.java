package mx.itch.opticaapp.data;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;

import mx.itch.opticaapp.model.Optica;
import mx.itch.opticaapp.model.Orden;
import mx.itch.opticaapp.model.Seguimiento;
import mx.itch.opticaapp.model.Venta;
import mx.itch.opticaapp.util.Formato;

/**
 * Seguimiento público del pedido: el QR del ticket abre https://{proyecto}.web.app/s/{token},
 * una página de Firebase Hosting (carpeta firebase/hosting) que lee /seguimiento/{token}.
 */
public final class Seguimientos {

    public static final String COLECCION = "seguimiento";

    /** Por si el google-services.json no trae el id del proyecto. */
    private static final String PROYECTO_POR_OMISION = "opticaapp-moviles12";

    private Seguimientos() {
    }

    /** Referencia nueva con un id aleatorio: ese id es el token que va en el QR. */
    public static DocumentReference nuevo() {
        return FirebaseFirestore.getInstance().collection(COLECCION).document();
    }

    public static DocumentReference ref(String token) {
        return FirebaseFirestore.getInstance().collection(COLECCION).document(token);
    }

    /** Dirección que lleva el QR. Usa el dominio gratuito de Hosting del mismo proyecto de Firebase. */
    public static String url(String token) {
        String proyecto = null;
        try {
            proyecto = FirebaseApp.getInstance().getOptions().getProjectId();
        } catch (IllegalStateException ignorado) {
            // Firebase aún no está inicializado: se usa el proyecto de la app
        }
        if (proyecto == null || proyecto.isEmpty()) proyecto = PROYECTO_POR_OMISION;
        return "https://" + proyecto + ".web.app/s/" + token;
    }

    /** Arma el documento público a partir de la venta (y su orden, si la hay). */
    public static Seguimiento desde(Venta v, @Nullable Orden orden, Optica optica) {
        Seguimiento s = new Seguimiento();
        s.opticaId = optica.id;
        s.optica = optica.nombre;
        s.opticaTelefono = optica.telefono != null ? optica.telefono : "";
        String direccion = optica.direccion != null ? optica.direccion.trim() : "";
        if (optica.estado != null && !optica.estado.trim().isEmpty()) {
            direccion = direccion.isEmpty() ? optica.estado.trim() : direccion + ", " + optica.estado.trim();
        }
        s.opticaDireccion = direccion;
        s.folio = v.folio;
        s.cliente = Formato.nombreCorto(v.pacienteNombre);
        s.fechaVenta = v.fecha;
        s.total = v.total;
        s.anticipo = v.anticipo;
        s.saldo = v.saldo;
        if (orden != null) {
            s.ordenFolio = orden.folio;
            s.trabajo = orden.descripcion;
            s.estatus = orden.estatus;
            s.fechaEntrega = orden.fechaEntrega;
            s.fechaEnvio = orden.fechaEnvio;
        } else {
            s.ordenFolio = "";
            s.trabajo = "";
            s.estatus = "";
        }
        return s;
    }

    /*
     * El seguimiento se escribe APARTE del lote de la venta o de la orden, a propósito: si algo falla
     * con él (por ejemplo, si todavía no se publican las reglas nuevas de Firestore), la venta y el
     * cambio de estatus se guardan igual. Lo único que pasa es que el QR muestra "No encontramos
     * tu pedido". Sin conexión, Firestore lo guarda en el teléfono y lo sube al volver la red.
     */

    /** Crea el documento público. */
    public static void publicar(DocumentReference ref, Seguimiento s) {
        ref.set(s).addOnFailureListener(e -> Log.w("Seguimientos", "No se pudo publicar el seguimiento", e));
    }

    /** Cambio de estatus de la orden para que el cliente lo vea al escanear. */
    public static void cambiarEstatus(String token, String estatus, Date cuando) {
        ref(token).update(
                        "estatus", estatus,
                        Orden.campoFecha(estatus), cuando,
                        "actualizado", FieldValue.serverTimestamp())
                .addOnFailureListener(e -> Log.w("Seguimientos", "No se pudo actualizar el seguimiento", e));
    }
}
