package mx.itch.opticaapp.ui.venta;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import mx.itch.opticaapp.databinding.ItemCarritoBinding;
import mx.itch.opticaapp.model.ItemVenta;
import mx.itch.opticaapp.util.Formato;

/** Muestra los productos agregados a la venta. Tocar un renglón lo quita. */
public class CarritoAdapter extends RecyclerView.Adapter<CarritoAdapter.Fila> {

    public interface AlTocar {
        void alTocar(int posicion);
    }

    private final List<ItemVenta> carrito;
    private final AlTocar alTocar;

    /** Recibe la misma lista que maneja VentaFragment. */
    public CarritoAdapter(List<ItemVenta> carrito, AlTocar alTocar) {
        this.carrito = carrito;
        this.alTocar = alTocar;
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Fila(ItemCarritoBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        ItemVenta i = carrito.get(posicion);
        fila.b.tvDescripcion.setText(i.cantidad + " × " + i.nombre + " (" + Formato.moneda(i.precio) + ")");
        fila.b.tvImporte.setText(Formato.moneda(i.subtotal()));
        fila.itemView.setOnClickListener(v -> alTocar.alTocar(fila.getBindingAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return carrito.size();
    }

    static class Fila extends RecyclerView.ViewHolder {
        final ItemCarritoBinding b;

        Fila(ItemCarritoBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
