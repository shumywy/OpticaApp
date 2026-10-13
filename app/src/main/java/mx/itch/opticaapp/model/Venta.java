package mx.itch.opticaapp.model;

import com.google.firebase.firestore.DocumentId;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Documento /opticas/{opticaId}/ventas/{ventaId}. */
public class Venta {

    @DocumentId
    public String id;
    /** Vacío cuando la venta es a "Público general". */
    public String pacienteId;
    public String pacienteNombre;
    public List<ItemVenta> items = new ArrayList<>();
    public double total;
    public double anticipo;
    public double saldo;
    public String metodoPago;
    public String vendedorId;
    public String vendedorNombre;
    public Date fecha;

    public Venta() {
    }
}
