package mx.itch.opticaapp.model;

import com.google.firebase.firestore.DocumentId;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Documento /opticas/{opticaId}/ventas/{ventaId}.
 * Es Serializable para pasarla completa a la pantalla "Venta registrada" (ticket).
 */
public class Venta implements Serializable {

    private static final long serialVersionUID = 1L;

    @DocumentId
    public String id;
    /** Folio consecutivo de la óptica: "V-000123". */
    public String folio;
    /** Vacío cuando la venta es a "Público general". */
    public String pacienteId;
    public String pacienteNombre;
    public List<ItemVenta> items = new ArrayList<>();
    public double total;
    public double anticipo;
    public double saldo;
    public String metodoPago;
    /** Efectivo que entregó el cliente (0 si no lo capturaron o si pagó con tarjeta o transferencia). */
    public double recibido;
    /** recibido − anticipo. */
    public double cambio;
    public String vendedorId;
    public String vendedorNombre;
    public Date fecha;

    // Orden de laboratorio que salió de esta venta (vacío si no se mandó)
    public String ordenFolio;
    public String ordenDescripcion;
    /** Fecha estimada de entrega de los lentes. */
    public Date fechaEntrega;

    /** Token del documento público /seguimiento/{token} al que lleva el QR del ticket (vacío si no hay). */
    public String seguimiento;

    public Venta() {
    }

    public boolean tieneOrden() {
        return ordenFolio != null && !ordenFolio.isEmpty();
    }

    public boolean tieneSeguimiento() {
        return seguimiento != null && !seguimiento.isEmpty();
    }
}
