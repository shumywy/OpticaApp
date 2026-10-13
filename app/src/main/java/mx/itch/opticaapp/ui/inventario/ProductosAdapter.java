package mx.itch.opticaapp.ui.inventario;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.databinding.ItemProductoBinding;
import mx.itch.opticaapp.model.Producto;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.Imagenes;

/** Adaptador del catálogo de productos. */
public class ProductosAdapter extends RecyclerView.Adapter<ProductosAdapter.Fila> {

    public interface AlTocar {
        void alTocar(Producto producto);
    }

    private final List<Producto> lista = new ArrayList<>();
    private final AlTocar alTocar;

    public ProductosAdapter(AlTocar alTocar) {
        this.alTocar = alTocar;
    }

    public void mostrar(List<Producto> nuevos) {
        lista.clear();
        lista.addAll(nuevos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Fila(ItemProductoBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        Producto p = lista.get(posicion);
        fila.b.tvNombre.setText(p.nombre);
        fila.b.tvCategoria.setText(p.categoria);
        fila.b.tvPrecio.setText(Formato.moneda(p.precio));
        mostrarEstado(fila.b.tvStock, p);
        Imagenes.mostrar(fila.b.ivFoto, p.fotoUrl);
        fila.itemView.setOnClickListener(v -> alTocar.alTocar(p));
    }

    /** Píldora de color: verde = en stock, naranja = stock bajo, rojo = agotado. */
    private void mostrarEstado(TextView vista, Producto p) {
        Context c = vista.getContext();
        String estado = p.estadoStock();
        int fondo;
        int color;
        if ("Agotado".equals(estado)) {
            vista.setText("Agotado");
            fondo = R.drawable.bg_pildora_rojo;
            color = R.color.rojo;
        } else if ("Stock bajo".equals(estado)) {
            vista.setText("Stock bajo · " + p.stock);
            fondo = R.drawable.bg_pildora_naranja;
            color = R.color.naranja;
        } else {
            vista.setText(p.stock + " en stock");
            fondo = R.drawable.bg_pildora_verde;
            color = R.color.verde;
        }
        vista.setBackgroundResource(fondo);
        vista.setTextColor(ContextCompat.getColor(c, color));
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    static class Fila extends RecyclerView.ViewHolder {
        final ItemProductoBinding b;

        Fila(ItemProductoBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
