package mx.itch.opticaapp.ui.pacientes;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentPacientesBinding;
import mx.itch.opticaapp.model.Paciente;
import mx.itch.opticaapp.ui.expediente.ExpedienteFragment;
import mx.itch.opticaapp.util.AlEscribir;
import mx.itch.opticaapp.util.Animaciones;

/** Pantalla 4: lista de pacientes con búsqueda y orden. */
public class PacientesFragment extends Fragment {

    private FragmentPacientesBinding binding;
    private PacientesAdapter adaptador;
    private ListenerRegistration escucha;
    /** Todos los pacientes que llegan de Firestore (sin filtrar). */
    private final List<Paciente> todos = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPacientesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!Sesion.estaActiva()) return;

        // Tocar abre el expediente; mantener presionado permite editar los datos (salvo al vendedor)
        adaptador = new PacientesAdapter(this::abrirExpediente,
                Sesion.esVendedor() ? null : p -> DialogoPaciente.mostrar(this, p));
        binding.rvPacientes.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPacientes.setAdapter(adaptador);

        // Píldoras "Recientes" y "Nombre A-Z"
        binding.chipsOrden.setOnCheckedStateChangeListener((grupo, elegidos) -> aplicarFiltro());
        binding.etBuscar.addTextChangedListener(new AlEscribir(texto -> aplicarFiltro()));

        // El vendedor solo consulta: no puede dar de alta pacientes
        binding.fabAgregar.setVisibility(Sesion.esVendedor() ? View.GONE : View.VISIBLE);
        binding.fabAgregar.setOnClickListener(v -> DialogoPaciente.mostrar(this, null));

        // Animación de entrada: título en cascada, hoja que sube y el botón + que aparece con rebote
        if (Animaciones.entrada(this, binding.encabezado, binding.hoja)) {
            Animaciones.aparecer(binding.fabAgregar, 450);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!Sesion.estaActiva()) return;
        // Escucha en tiempo real: si otro dispositivo agrega un paciente, aparece aquí
        escucha = Sesion.coleccion("pacientes").addSnapshotListener((resultado, error) -> {
            if (binding == null) return;
            if (error != null) {
                mensaje("No se pudieron leer los pacientes: " + error.getMessage());
                return;
            }
            if (resultado == null) return;
            todos.clear();
            todos.addAll(resultado.toObjects(Paciente.class));
            aplicarFiltro();
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (escucha != null) {
            escucha.remove();
            escucha = null;
        }
    }

    /** Filtra por el texto buscado, ordena según la píldora elegida y actualiza el total. */
    private void aplicarFiltro() {
        if (binding == null || adaptador == null) return;
        String buscado = binding.etBuscar.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<Paciente> visibles = new ArrayList<>();
        for (Paciente p : todos) {
            String nombre = p.nombre == null ? "" : p.nombre.toLowerCase(Locale.ROOT);
            String telefono = p.telefono == null ? "" : p.telefono;
            if (buscado.isEmpty() || nombre.contains(buscado) || telefono.contains(buscado)) {
                visibles.add(p);
            }
        }
        if (binding.chipsOrden.getCheckedChipId() == R.id.chipNombre) {
            // Nombre A-Z
            Collections.sort(visibles, (a, b) -> nombreParaOrdenar(a).compareTo(nombreParaOrdenar(b)));
        } else {
            // Recientes primero (los que no tienen consulta van al final)
            Collections.sort(visibles, (a, b) -> {
                Date fa = a.ultimaConsulta != null ? a.ultimaConsulta : a.fechaRegistro;
                Date fb = b.ultimaConsulta != null ? b.ultimaConsulta : b.fechaRegistro;
                if (fa == null && fb == null) return 0;
                if (fa == null) return 1;
                if (fb == null) return -1;
                return fb.compareTo(fa);
            });
        }
        adaptador.mostrar(visibles);
        Animaciones.listaUnaVez(this, binding.rvPacientes);   // los renglones entran uno tras otro

        // Total a la derecha de los filtros: "12 pacientes", o "3 de 12" mientras se busca
        int total = todos.size();
        binding.tvTotal.setText(buscado.isEmpty()
                ? total + (total == 1 ? " paciente" : " pacientes")
                : visibles.size() + " de " + total);
        binding.tvVacio.setText(total == 0 ? "Todavía no hay pacientes registrados"
                : "Ningún paciente coincide con la búsqueda");
        binding.tvVacio.setVisibility(visibles.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String nombreParaOrdenar(Paciente p) {
        return p.nombre == null ? "" : p.nombre.toLowerCase(Locale.ROOT);
    }

    private void abrirExpediente(Paciente paciente) {
        Bundle args = new Bundle();
        args.putString(ExpedienteFragment.ARG_PACIENTE_ID, paciente.id);
        NavHostFragment.findNavController(this).navigate(R.id.action_pacientes_to_expediente, args);
    }

    private void mensaje(String texto) {
        if (getContext() != null) {
            Toast.makeText(getContext(), texto, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
