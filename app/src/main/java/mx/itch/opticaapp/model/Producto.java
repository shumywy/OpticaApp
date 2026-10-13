package mx.itch.opticaapp.model;

import com.google.firebase.firestore.DocumentId;

import java.util.Locale;

/** Documento /opticas/{opticaId}/productos/{productoId}. */
public class Producto {

    public static final String CATEGORIA_MICAS = "Micas";

    @DocumentId
    public String id;
    public String nombre;
    public String categoria;
    public String descripcion;
    public double precio;
    public int stock;
    public int stockMinimo;
    public String fotoUrl;

    public Producto() {
    }

    /** "Agotado", "Stock bajo" o "En stock". No empieza con "get" para que Firestore no lo guarde. */
    public String estadoStock() {
        if (stock <= 0) return "Agotado";
        if (stock <= stockMinimo) return "Stock bajo";
        return "En stock";
    }

    /** Texto que muestra el Spinner del punto de venta. */
    @Override
    public String toString() {
        return String.format(Locale.US, "%s — $%.2f (quedan %d)", nombre, precio, stock);
    }
}
