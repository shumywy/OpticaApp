package mx.itch.opticaapp.model;

import com.google.firebase.firestore.DocumentId;

/** Documento /opticas/{opticaId}: los datos de un negocio. */
public class Optica {

    @DocumentId
    public String id;
    public String nombre;
    public String telefono;
    public String estado;
    public String direccion;
    public double metaSemanal;
    public String codigoInvitacion;
    /** Logotipo como texto "data:image/jpeg;base64,..." (máx. 400 px), o vacío. Ver util/Imagenes. */
    public String logoUrl;
    /** uid del dueño (administrador que la registró). */
    public String ownerId;

    public Optica() {
    }
}
