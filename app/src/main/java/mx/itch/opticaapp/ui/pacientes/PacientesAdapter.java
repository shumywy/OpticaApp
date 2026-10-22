package mx.itch.opticaapp.ui.pacientes;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.databinding.ItemPacienteBinding;
import mx.itch.opticaapp.model.Paciente;
import mx.itch.opticaapp.util.Fechas;
import mx.itch.opticaapp.util.Formato;

/** Adaptador del RecyclerView de pacientes: cada renglón es una ficha de expediente. */
public class PacientesAdapter extends RecyclerView.Adapter<PacientesAdapter.Fila> {

    /** Qué hacer al tocar un paciente. */
    public interface AlTocar {
        void alTocar(Paciente paciente);
    }

    private final List<Paciente> lista = new ArrayList<>();
    private final AlTocar alTocar;
    /** Al mantener presionado (hoja de opciones del paciente). Puede ser null. */
    @Nullable
    private final AlTocar alMantener;

    public PacientesAdapter(AlTocar alTocar, @Nullable AlTocar alMantener) {
        this.alTocar = alTocar;
        this.alMantener = alMantener;
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
        fila.b.tvNombre.setText(p.nombre);

        // Arriba a la derecha: fecha de la última consulta
        if (p.ultimaConsulta != null) {
            fila.b.tvFecha.setText("Últ. consulta " + Fechas.diaMes(p.ultimaConsulta));
            fila.b.tvFecha.setContentDescription("Última consulta " + Fechas.corta(p.ultimaConsulta));
        } else {
            fila.b.tvFecha.setText("Sin consultas");
            fila.b.tvFecha.setContentDescription(null);
        }

        boolean conTelefono = p.telefono != null && !p.telefono.trim().isEmpty();
        fila.b.tvTelefono.setText(conTelefono ? Formato.telefono(p.telefono) : "Sin teléfono");

        mostrarGraduacion(fila.b.tvGraduacion, p.resumenGraduacion);
        fila.itemView.setOnClickListener(v -> alTocar.alTocar(p));
        if (alMantener != null) {
            fila.itemView.setOnLongClickListener(v -> {
                alMantener.alTocar(p);
                return true;
            });
        }
    }

    /** Insignia lila con "OD … / OI …" si ya tiene graduación; gris con "Sin graduación" si no. */
    private void mostrarGraduacion(TextView vista, String resumen) {
        boolean tiene = resumen != null && !resumen.trim().isEmpty();
        vista.setText(tiene ? resumen : "Sin graduación");
        vista.setBackgroundResource(tiene ? R.drawable.bg_pildora_morado : R.drawable.bg_pildora_gris);
        vista.setTextColor(ContextCompat.getColor(vista.getContext(),
                tiene ? R.color.morado : R.color.texto_secundario));
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
