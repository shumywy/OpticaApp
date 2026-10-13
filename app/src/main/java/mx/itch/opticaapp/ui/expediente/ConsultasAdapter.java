package mx.itch.opticaapp.ui.expediente;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import mx.itch.opticaapp.databinding.ItemConsultaBinding;
import mx.itch.opticaapp.model.Consulta;
import mx.itch.opticaapp.util.Fechas;
import mx.itch.opticaapp.util.Formato;

/** Adaptador del historial de consultas de un paciente. */
public class ConsultasAdapter extends RecyclerView.Adapter<ConsultasAdapter.Fila> {

    public interface AlTocar {
        void alTocar(Consulta consulta);
    }

    private final List<Consulta> lista = new ArrayList<>();
    private final AlTocar alTocar;

    public ConsultasAdapter(AlTocar alTocar) {
        this.alTocar = alTocar;
    }

    public void mostrar(List<Consulta> nuevas) {
        lista.clear();
        lista.addAll(nuevas);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Fila(ItemConsultaBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        Consulta c = lista.get(posicion);
        fila.b.tvFecha.setText(Fechas.conHora(c.fecha));
        fila.b.tvGraduacion.setText("OD " + Formato.dioptria(c.odEsfera) + " " + Formato.dioptria(c.odCilindro) + " x " + c.odEje
                + "   ·   OI " + Formato.dioptria(c.oiEsfera) + " " + Formato.dioptria(c.oiCilindro) + " x " + c.oiEje);
        String tipo = c.tipoLente == null ? "" : c.tipoLente;
        String quien = c.optometristaNombre == null ? "" : " · " + c.optometristaNombre;
        fila.b.tvDetalle.setText(tipo + quien);
        fila.itemView.setOnClickListener(v -> alTocar.alTocar(c));
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    static class Fila extends RecyclerView.ViewHolder {
        final ItemConsultaBinding b;

        Fila(ItemConsultaBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
