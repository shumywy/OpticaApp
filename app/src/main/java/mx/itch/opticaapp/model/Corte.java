package mx.itch.opticaapp.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/** Corte de caja guardado: /opticas/{opticaId}/cortes/{corteId}. */
public class Corte {

    @DocumentId
    public String id;
    /** Día del corte en formato yyyy-MM-dd (permite buscar los cortes de un día). */
    public String dia;
    public int ventas;
    public double totalVendido;
    /** Dinero recibido en el día (suma de anticipos). */
    public double cobrado;
    /** Lo que quedó pendiente de cobrar de las ventas del día. */
    public double saldoPendiente;
    /** Dinero cobrado por método de pago: {"Efectivo": 800.0, "Tarjeta": 450.0}. */
    public Map<String, Double> porMetodo = new LinkedHashMap<>();
    public String realizadoPorId;
    public String realizadoPorNombre;
    @ServerTimestamp
    public Date fecha;

    public Corte() {
    }
}
