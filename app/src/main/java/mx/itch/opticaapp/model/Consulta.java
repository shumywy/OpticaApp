package mx.itch.opticaapp.model;

import com.google.firebase.firestore.DocumentId;

import java.util.Date;

/**
 * Documento /opticas/{opticaId}/pacientes/{pacienteId}/consultas/{consultaId}.
 * Guarda la graduación de cada ojo: OD (ojo derecho) y OI (ojo izquierdo).
 */
public class Consulta {

    @DocumentId
    public String id;
    public Date fecha;

    // Ojo derecho
    public double odEsfera;
    public double odCilindro;
    public int odEje;
    public double odAdicion;

    // Ojo izquierdo
    public double oiEsfera;
    public double oiCilindro;
    public int oiEje;
    public double oiAdicion;

    /** Distancia pupilar en milímetros (0 si no se capturó). */
    public double distanciaPupilar;
    public String agudezaVisual;
    public String tipoLente;
    public String observaciones;
    public String optometristaId;
    public String optometristaNombre;

    public Consulta() {
    }
}
