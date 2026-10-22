package mx.itch.opticaapp.model;

/** Un renglón de una venta. Se guarda dentro de la lista "items" del documento Venta. */
public class ItemVenta {

    public String productoId;
    public String nombre;
    public String categoria;
    public double precio;
    public int cantidad;

    public ItemVenta() {
    }

    public ItemVenta(Producto p, int cantidad) {
        this.productoId = p.id;
        this.nombre = p.nombre;
        this.categoria = p.categoria;
        this.precio = p.precio;
        this.cantidad = cantidad;
    }

    /**
     * Renglón con detalles elegidos en el detalle del producto: el detalle se agrega al nombre
     * ("Paño Twins · Gris Oxford · logo estampado") y el costo extra al precio unitario.
     */
    public ItemVenta(Producto p, int cantidad, String detalle, double extra) {
        this(p, cantidad);
        if (detalle != null && !detalle.isEmpty()) this.nombre = p.nombre + " · " + detalle;
        this.precio = Math.round((p.precio + extra) * 100) / 100.0;
    }

    /** precio × cantidad. No empieza con "get" para que Firestore no lo guarde. */
    public double subtotal() {
        return precio * cantidad;
    }
}
