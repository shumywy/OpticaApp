package mx.itch.opticaapp.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Documento público /seguimiento/{token}: lo que ve el cliente al escanear el QR de su ticket.
 * El token es un id aleatorio de 20 caracteres (imposible de adivinar); las reglas permiten leer
 * un documento exacto, pero no listar la colección. Solo lleva lo necesario para el cliente:
 * nada de teléfonos de pacientes ni ids internos.
 */
public class Seguimiento {

    /** Óptica dueña; las reglas la usan para saber quién puede modificarlo. */
    public String opticaId;
    public String optica;
    public String opticaTelefono;
    public String opticaDireccion;

    /** Folio de la venta ("V-000123"). */
    public String folio;
    /** Folio de la orden de laboratorio ("L-000045"), o vacío si la venta no llevó orden. */
    public String ordenFolio;
    /** Nombre abreviado: "María F. L.". */
    public String cliente;
    /** Trabajo para el laboratorio ("Mica progresiva AR"). */
    public String trabajo;
    /** ENVIADO, EN_PROCESO, RECIBIDO o ENTREGADO; vacío si no hay orden. */
    public String estatus;

    public Date fechaVenta;
    public Date fechaEntrega;
    public Date fechaEnvio;
    public Date fechaEnProceso;
    public Date fechaRecibido;
    public Date fechaEntregado;

    public double total;
    public double anticipo;
    public double saldo;

    @ServerTimestamp
    public Date actualizado;

    public Seguimiento() {
    }
}
