package mx.itch.opticaapp.data;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import mx.itch.opticaapp.model.Optica;
import mx.itch.opticaapp.model.Usuario;

/**
 * Guarda en memoria quién inició sesión y de qué óptica es.
 * Así cualquier pantalla puede saber el rol y a qué colecciones escribir
 * sin volver a consultar Firestore.
 */
public final class Sesion {

    private static Usuario usuario;
    private static Optica optica;

    private Sesion() {
    }

    public static void iniciar(Usuario u, Optica o) {
        usuario = u;
        optica = o;
    }

    public static void cerrar() {
        usuario = null;
        optica = null;
    }

    public static boolean estaActiva() {
        return usuario != null && optica != null;
    }

    public static Usuario getUsuario() {
        return usuario;
    }

    public static Optica getOptica() {
        return optica;
    }

    public static boolean esAdmin() {
        return usuario != null && Usuario.ROL_ADMIN.equals(usuario.rol);
    }

    public static boolean esOptometrista() {
        return usuario != null && Usuario.ROL_OPTOMETRISTA.equals(usuario.rol);
    }

    public static boolean esVendedor() {
        return usuario != null && Usuario.ROL_VENDEDOR.equals(usuario.rol);
    }

    /** Documento de la óptica actual: /opticas/{opticaId}. */
    public static DocumentReference opticaRef() {
        return FirebaseFirestore.getInstance().collection("opticas").document(optica.id);
    }

    /** Subcolección de la óptica actual, por ejemplo coleccion("pacientes"). */
    public static CollectionReference coleccion(String nombre) {
        return opticaRef().collection(nombre);
    }
}
