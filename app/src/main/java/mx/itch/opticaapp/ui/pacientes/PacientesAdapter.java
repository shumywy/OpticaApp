package mx.itch.opticaapp.ui.pacientes;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import mx.itch.opticaapp.databinding.ItemPacienteBinding;
import mx.itch.opticaapp.model.Paciente;
import mx.itch.opticaapp.util.Fechas;
import mx.itch.opticaapp.util.Formato;

/** Adaptador del RecyclerView de pacientes. */
public class PacientesAdapter extends RecyclerView.Adapter<PacientesAdapter.Fila> {

    /** Qué hacer al tocar un paciente. */
    public interface AlTocar {
        void alTocar(Paciente paciente);
    }

    private final List<Paciente> lista = new ArrayList<>();
    private final AlTocar alTocar;

    public PacientesAdapter(AlTocar alTocar) {
        this.alTocar = alTocar;
    }

    public void mostrar(List<Paciente> nuevos) {
        lista.clear();
        lista.addAll(nuevos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPacienteBinding b = ItemPacienteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new Fila(b);
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        Paciente p = lista.get(posicion);
        fila.b.tvIniciales.setText(Formato.iniciales(p.nombre));
        fila.b.tvNombre.setText(p.nombre);
        String telefono = p.telefono == null || p.telefono.isEmpty() ? "" : "Tel. " + p.telefono + " · ";
        fila.b.tvDetalle.setText(telefono + "Última consulta: " + Fechas.corta(p.ultimaConsulta));
        fila.b.tvGraduacion.setText(p.resumenGraduacion == null ? "Sin graduación" : p.resumenGraduacion);
        fila.itemView.setOnClickListener(v -> alTocar.alTocar(p));
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    static class Fila extends RecyclerView.ViewHolder {
        final ItemPacienteBinding b;

        Fila(ItemPacienteBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
