package mx.itch.opticaapp.ui.administracion;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.databinding.ItemPersonaBinding;
import mx.itch.opticaapp.model.Usuario;
import mx.itch.opticaapp.util.Formato;

/** Renglones de Administración · Personal: iniciales, nombre, correo y rol en píldora de color. */
public class PersonalAdapter extends RecyclerView.Adapter<PersonalAdapter.Fila> {

    public interface AlTocar {
        void alTocar(Usuario persona);
    }

    private final List<Usuario> lista = new ArrayList<>();
    private final String miId;
    private final AlTocar alTocar;

    public PersonalAdapter(String miId, AlTocar alTocar) {
        this.miId = miId;
        this.alTocar = alTocar;
    }

    public void mostrar(List<Usuario> personas) {
        lista.clear();
        lista.addAll(personas);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Fila(ItemPersonaBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        Usuario p = lista.get(posicion);
        String nombre = p.nombre != null && !p.nombre.isEmpty() ? p.nombre : "Sin nombre";
        boolean soyYo = p.id != null && p.id.equals(miId);
        fila.b.tvIniciales.setText(Formato.iniciales(nombre));
        fila.b.tvNombre.setText(soyYo ? nombre + " (tú)" : nombre);
        fila.b.tvCorreo.setText(p.email != null ? p.email : "");
        mostrarRol(fila.b.tvRol, p.rol);
        fila.itemView.setOnClickListener(v -> alTocar.alTocar(p));
    }

    /** Morado administrador, azul optometrista y verde vendedor. */
    private void mostrarRol(TextView vista, String rol) {
        int fondo = R.drawable.bg_pildora_verde;
        int color = R.color.verde;
        if (Usuario.ROL_ADMIN.equals(rol)) {
            fondo = R.drawable.bg_pildora_morado;
            color = R.color.morado;
        } else if (Usuario.ROL_OPTOMETRISTA.equals(rol)) {
            fondo = R.drawable.bg_pildora_azul;
            color = R.color.azul;
        }
        vista.setText(Usuario.nombreRol(rol));
        vista.setBackgroundResource(fondo);
        vista.setTextColor(ContextCompat.getColor(vista.getContext(), color));
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    static class Fila extends RecyclerView.ViewHolder {
        final ItemPersonaBinding b;

        Fila(ItemPersonaBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
