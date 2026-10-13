package mx.itch.opticaapp.ui.pacientes;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.DialogPacienteBinding;
import mx.itch.opticaapp.databinding.FragmentPacientesBinding;
import mx.itch.opticaapp.model.Paciente;
import mx.itch.opticaapp.ui.expediente.ExpedienteFragment;
import mx.itch.opticaapp.util.AlEscribir;
import mx.itch.opticaapp.util.Formato;

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

        adaptador = new PacientesAdapter(this::abrirExpediente);
        binding.rvPacientes.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPacientes.setAdapter(adaptador);

        // Control segmentado "Recientes | Nombre (A-Z)"
        binding.rgOrden.setOnCheckedChangeListener((grupo, idMarcado) -> aplicarFiltro());
        binding.etBuscar.addTextChangedListener(new AlEscribir(texto -> aplicarFiltro()));

        // El vendedor solo consulta: no puede dar de alta pacientes
        binding.fabAgregar.setVisibility(Sesion.esVendedor() ? View.GONE : View.VISIBLE);
        binding.fabAgregar.setOnClickListener(v -> dialogoNuevoPaciente());
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

    /** Filtra por el texto buscado y ordena según el Spinner. */
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
        if (binding.rgOrden.getCheckedRadioButtonId() == R.id.rbNombre) {
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

    private void dialogoNuevoPaciente() {
        DialogPacienteBinding d = DialogPacienteBinding.inflate(getLayoutInflater());
        AlertDialog dialogo = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Nuevo paciente")
                .setView(d.getRoot())
                .setPositiveButton("Guardar", null)   // se configura abajo para validar antes de cerrar
                .setNegativeButton("Cancelar", null)
                .create();
        dialogo.setOnShowListener(x -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nombre = d.etNombre.getText().toString().trim();
            if (nombre.isEmpty()) {
                d.etNombre.setError("Escribe el nombre");
                return;
            }
            Integer edad = Formato.leerEntero(d.etEdad.getText().toString());
            Paciente p = new Paciente();
            p.nombre = nombre;
            p.telefono = d.etTelefono.getText().toString().trim();
            p.edad = edad != null ? edad : 0;
            p.fechaRegistro = new Date();
            Sesion.coleccion("pacientes").add(p)
                    .addOnFailureListener(e -> mensaje("No se pudo guardar: " + e.getMessage()));
            dialogo.dismiss();
        }));
        dialogo.show();
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
