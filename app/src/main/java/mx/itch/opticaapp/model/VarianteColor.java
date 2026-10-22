package mx.itch.opticaapp.model;

import android.graphics.Color;

/**
 * Variante de color de un producto ("Gris Oxford", "#3C434A").
 * Se guarda dentro de la lista "colores" del documento Producto.
 */
public class VarianteColor {

    public String nombre;
    /** Color exacto en formato #RRGGBB. */
    public String hex;

    /** Firestore necesita un constructor vacío. */
    public VarianteColor() {
    }

    public VarianteColor(String nombre, String hex) {
        this.nombre = nombre;
        this.hex = hex;
    }

    /** El color como entero de Android; gris si el texto no es válido. No empieza con "get" para que Firestore no lo guarde. */
    public int colorAndroid() {
        try {
            return Color.parseColor(hex);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Color.GRAY;
        }
    }

    /** "Gris Oxford (#3C434A)". */
    public String descripcion() {
        return nombre + " (" + hex + ")";
    }
}
