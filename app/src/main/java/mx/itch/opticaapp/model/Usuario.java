package mx.itch.opticaapp.model;

import com.google.firebase.firestore.DocumentId;

/**
 * Documento /users/{uid}. Firestore llena los campos públicos automáticamente.
 * El id del documento es el uid de Firebase Authentication.
 */
public class Usuario {

    public static final String ROL_ADMIN = "ADMIN";
    public static final String ROL_OPTOMETRISTA = "OPTOMETRISTA";
    public static final String ROL_VENDEDOR = "VENDEDOR";

    @DocumentId
    public String id;
    public String nombre;
    public String email;
    public String rol;
    public String opticaId;
    /** Foto de perfil como texto "data:image/jpeg;base64,..." (máx. 320 px), o vacío. Ver util/Imagenes. */
    public String fotoUrl;
    /** Código con el que se unió a la óptica (vacío para el dueño). Lo revisan las reglas de seguridad. */
    public String codigoUsado;

    /** Firestore necesita un constructor vacío. */
    public Usuario() {
    }

    /** Convierte el rol guardado ("ADMIN") en el texto que ve el usuario ("Administrador"). */
    public static String nombreRol(String rol) {
        if (ROL_ADMIN.equals(rol)) return "Administrador";
        if (ROL_OPTOMETRISTA.equals(rol)) return "Optometrista";
        if (ROL_VENDEDOR.equals(rol)) return "Vendedor";
        return "Sin rol";
    }

    /** Convierte el texto de un Spinner ("Optometrista") en el valor que se guarda ("OPTOMETRISTA"). */
    public static String rolDesdeTexto(String texto) {
        if ("Administrador".equals(texto)) return ROL_ADMIN;
        if ("Optometrista".equals(texto)) return ROL_OPTOMETRISTA;
        return ROL_VENDEDOR;
    }
}
