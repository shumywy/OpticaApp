package mx.itch.opticaapp.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Registro de un PDF generado (receta o nota de venta): /opticas/{opticaId}/documentos/{documentoId}.
 * Si el archivo pesa 700 KB o menos, se guarda completo en el campo "pdf".
 */
public class Documento {

    public static final String RECETA = "RECETA";
    public static final String NOTA_VENTA = "NOTA_VENTA";

    @DocumentId
    public String id;
    /** RECETA o NOTA_VENTA. */
    public String tipo;
    public String descripcion;
    /** Paciente de la receta, o venta de la nota. */
    public String referenciaId;
    public String pacienteNombre;
    public String nombreArchivo;
    public long tamanoBytes;
    /** El PDF como texto "data:application/pdf;base64,..."; vacío si era demasiado grande. */
    public String pdf;
    public boolean archivoIncluido;
    public String generadoPorId;
    public String generadoPorNombre;
    @ServerTimestamp
    public Date fecha;

    public Documento() {
    }
}
