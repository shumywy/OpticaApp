package mx.itch.opticaapp.model;

import com.google.firebase.firestore.DocumentId;

import java.util.Date;

/** Documento /opticas/{opticaId}/ordenes/{ordenId}: un trabajo enviado al laboratorio. */
public class Orden {

    public static final String ENVIADO = "ENVIADO";
    public static final String EN_PROCESO = "EN_PROCESO";
    public static final String RECIBIDO = "RECIBIDO";
    public static final String ENTREGADO = "ENTREGADO";

    /** Orden de las etapas; sirve para saber cuál sigue. */
    public static final String[] ETAPAS = {ENVIADO, EN_PROCESO, RECIBIDO, ENTREGADO};

    @DocumentId
    public String id;
    public String ventaId;
    public String pacienteId;
    public String pacienteNombre;
    public String descripcion;
    public String estatus;
    public Date fechaCreacion;
    public Date fechaEnvio;
    public Date fechaEnProceso;
    public Date fechaRecibido;
    public Date fechaEntregado;

    public Orden() {
    }

    /** Texto para mostrar: "EN_PROCESO" -> "En proceso". */
    public static String nombreEstatus(String estatus) {
        if (ENVIADO.equals(estatus)) return "Enviado";
        if (EN_PROCESO.equals(estatus)) return "En proceso";
        if (RECIBIDO.equals(estatus)) return "Recibido";
        if (ENTREGADO.equals(estatus)) return "Entregado";
        return "Sin estatus";
    }

    /** Posición de la etapa (0 a 3), o -1 si no se reconoce. */
    public static int indiceEtapa(String estatus) {
        for (int i = 0; i < ETAPAS.length; i++) {
            if (ETAPAS[i].equals(estatus)) return i;
        }
        return -1;
    }

    /** Nombre del campo de fecha que se llena al llegar a cada etapa. */
    public static String campoFecha(String estatus) {
        if (ENVIADO.equals(estatus)) return "fechaEnvio";
        if (EN_PROCESO.equals(estatus)) return "fechaEnProceso";
        if (RECIBIDO.equals(estatus)) return "fechaRecibido";
        return "fechaEntregado";
    }
}
