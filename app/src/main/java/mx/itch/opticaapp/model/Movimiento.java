package mx.itch.opticaapp.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Un registro del historial de un producto:
 * /opticas/{opticaId}/productos/{productoId}/movimientos/{movimientoId}.
 * Se crea en la misma escritura (WriteBatch) que el cambio que describe.
 */
public class Movimiento {

    public static final String ALTA = "ALTA";
    public static final String EDICION = "EDICION";
    public static final String AJUSTE = "AJUSTE";
    public static final String VENTA = "VENTA";
    public static final String BAJA = "BAJA";

    @DocumentId
    public String id;
    /** ALTA, EDICION, AJUSTE, VENTA o BAJA. */
    public String tipo;
    /** Qué cambió, en texto: "Precio: $40.00 → $45.00". */
    public String descripcion;
    /** Cambio en existencias: positivo si entran piezas, negativo si salen, 0 si no cambian. */
    public int cantidad;
    public int existenciasAntes;
    public int existenciasDespues;
    /** Solo en VENTA: la venta que lo originó. */
    public String ventaId;
    public String usuarioId;
    public String usuarioNombre;
    /** La pone el servidor de Firestore al guardar. */
    @ServerTimestamp
    public Date fecha;

    public Movimiento() {
    }

    /** Nombre para mostrar: "Alta", "Edición", "Ajuste de existencias", "Venta" o "Baja". */
    public static String nombreTipo(String tipo) {
        if (ALTA.equals(tipo)) return "Alta";
        if (EDICION.equals(tipo)) return "Edición";
        if (AJUSTE.equals(tipo)) return "Ajuste de existencias";
        if (VENTA.equals(tipo)) return "Venta";
        if (BAJA.equals(tipo)) return "Baja";
        return "Movimiento";
    }
}
