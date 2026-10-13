package mx.itch.opticaapp.ui.laboratorio;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.databinding.ItemOrdenBinding;
import mx.itch.opticaapp.model.Orden;
import mx.itch.opticaapp.util.Fechas;

/** Adaptador de las órdenes de laboratorio. */
public class OrdenesAdapter extends RecyclerView.Adapter<OrdenesAdapter.Fila> {

    public interface AlTocar {
        void alTocar(Orden orden);
    }

    private final List<Orden> lista = new ArrayList<>();
    private final AlTocar alTocar;

    public OrdenesAdapter(AlTocar alTocar) {
        this.alTocar = alTocar;
    }

    public void mostrar(List<Orden> nuevas) {
        lista.clear();
        lista.addAll(nuevas);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Fila(ItemOrdenBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        Orden o = lista.get(posicion);
        String folio = o.id != null && o.id.length() >= 5 ? o.id.substring(0, 5).toUpperCase(Locale.ROOT) : "";
        fila.b.tvTitulo.setText("#" + folio + " · " + o.pacienteNombre);
        fila.b.tvDescripcion.setText(o.descripcion);
        mostrarEstatus(fila.b.tvEstatus, o.estatus);
        // Cada etapa avanza 25 %: Enviado 25, En proceso 50, Recibido 75, Entregado 100
        fila.b.barraEtapas.setProgress((Orden.indiceEtapa(o.estatus) + 1) * 25);
        fila.b.tvEtapas.setText(lineaDeEtapas(o.estatus));
        fila.b.tvFechas.setText("Enviada: " + Fechas.corta(o.fechaEnvio)
                + (o.fechaEntregado != null ? "  ·  Entregada: " + Fechas.corta(o.fechaEntregado) : ""));
        fila.itemView.setOnClickListener(v -> alTocar.alTocar(o));
    }

    /** Píldora de color: azul enviado, naranja en proceso, morado recibido, verde entregado. */
    private void mostrarEstatus(TextView vista, String estatus) {
        int fondo = R.drawable.bg_pildora_azul;
        int color = R.color.azul;
        if (Orden.EN_PROCESO.equals(estatus)) {
            fondo = R.drawable.bg_pildora_naranja;
            color = R.color.naranja;
        } else if (Orden.RECIBIDO.equals(estatus)) {
            fondo = R.drawable.bg_pildora_morado;
            color = R.color.morado;
        } else if (Orden.ENTREGADO.equals(estatus)) {
            fondo = R.drawable.bg_pildora_verde;
            color = R.color.verde;
        }
        vista.setText(Orden.nombreEstatus(estatus));
        vista.setBackgroundResource(fondo);
        vista.setTextColor(ContextCompat.getColor(vista.getContext(), color));
    }

    /** "● Enviado — ● En proceso — ○ Recibido — ○ Entregado" */
    private String lineaDeEtapas(String estatus) {
        int actual = Orden.indiceEtapa(estatus);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Orden.ETAPAS.length; i++) {
            if (i > 0) sb.append(" — ");
            sb.append(i <= actual ? "● " : "○ ").append(Orden.nombreEstatus(Orden.ETAPAS[i]));
        }
        return sb.toString();
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    static class Fila extends RecyclerView.ViewHolder {
        final ItemOrdenBinding b;

        Fila(ItemOrdenBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
