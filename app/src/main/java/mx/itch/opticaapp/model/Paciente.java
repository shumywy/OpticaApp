package mx.itch.opticaapp.model;

import com.google.firebase.firestore.DocumentId;

import java.util.Date;

/** Documento /opticas/{opticaId}/pacientes/{pacienteId}. */
public class Paciente {

    @DocumentId
    public String id;
    public String nombre;
    public String telefono;
    public int edad;
    public Date fechaRegistro;
    /** Fecha de la última consulta guardada; null si aún no tiene. */
    public Date ultimaConsulta;
    /** Texto corto de la última graduación, por ejemplo "OD -1.25 / OI -1.50". */
    public String resumenGraduacion;

    public Paciente() {
    }

    /** Lo usa el Spinner del punto de venta para mostrar el nombre. */
    @Override
    public String toString() {
        return nombre;
    }
}
