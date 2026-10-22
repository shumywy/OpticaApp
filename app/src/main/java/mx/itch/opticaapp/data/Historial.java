package mx.itch.opticaapp.data;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.WriteBatch;

import mx.itch.opticaapp.model.Movimiento;
import mx.itch.opticaapp.model.Usuario;

/**
 * Agrega registros al historial de un producto dentro de un WriteBatch, para que el cambio
 * y su registro se guarden juntos (o ninguno, si algo falla).
 */
public final class Historial {

    private Historial() {
    }

    /** /opticas/{opticaId}/productos/{productoId}/movimientos */
    public static DocumentReference nuevoMovimiento(String productoId) {
        return Sesion.coleccion("productos").document(productoId).collection("movimientos").document();
    }

    /** Agrega al lote un movimiento hecho por el usuario de la sesión. La fecha la pone el servidor. */
    public static void registrar(WriteBatch lote, String productoId, String tipo, String descripcion,
                                 int cantidad, int existenciasAntes, String ventaId) {
        Movimiento m = new Movimiento();
        m.tipo = tipo;
        m.descripcion = descripcion;
        m.cantidad = cantidad;
        m.existenciasAntes = existenciasAntes;
        m.existenciasDespues = existenciasAntes + cantidad;
        m.ventaId = ventaId;
        Usuario u = Sesion.getUsuario();
        m.usuarioId = u != null ? u.id : "";
        m.usuarioNombre = u != null ? u.nombre : "";
        lote.set(nuevoMovimiento(productoId), m);
    }
}
