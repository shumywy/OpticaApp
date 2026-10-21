package mx.itch.opticaapp.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
    /** Foto como texto "data:image/jpeg;base64,..." (máx. 640 px), o vacío. Ver util/Imagenes. */
    public String fotoUrl;
    /** Características cortas que se muestran como etiquetas ("Microfibra", "Suave", "15x15 cm"). */
    public List<String> etiquetas = new ArrayList<>();
    /** Variantes de color disponibles. Se agregan desde el detalle del producto. */
    public List<VarianteColor> colores = new ArrayList<>();
    /** true si se le puede estampar el logotipo de la óptica. */
    public boolean personalizable;
    /** Costo adicional del estampado del logotipo. */
    public double costoPersonalizacion;

    // Registro: quién dio de alta el producto y quién lo cambió por última vez (las fechas las pone el servidor)
    @ServerTimestamp
    public Date fechaRegistro;
    public String registradoPorId;
    public String registradoPorNombre;
    @ServerTimestamp
    public Date fechaActualizacion;
    public String actualizadoPorId;
    public String actualizadoPorNombre;

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
