package mx.itch.opticaapp.ui.laboratorio;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.DialogOrdenBinding;
import mx.itch.opticaapp.databinding.FragmentLaboratorioBinding;
import mx.itch.opticaapp.model.Orden;
import mx.itch.opticaapp.util.Animaciones;

/**
 * Pantalla 8: seguimiento de órdenes de laboratorio en tiempo real.
 * Si otro teléfono cambia un estatus, la lista se actualiza sola (addSnapshotListener).
 */
public class LaboratorioFragment extends Fragment {

    private FragmentLaboratorioBinding binding;
    private OrdenesAdapter adaptador;
    private ListenerRegistration escucha;
    private final List<Orden> todas = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLaboratorioBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!Sesion.estaActiva()) return;

        adaptador = new OrdenesAdapter(this::elegirEstatus);
        binding.rvOrdenes.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvOrdenes.setAdapter(adaptador);

        // Chips de estatus: al elegir uno se vuelve a filtrar la lista
        binding.chipsEstatus.setOnCheckedStateChangeListener((grupo, elegidos) -> aplicarFiltro());

        // El optometrista consulta; administrador y vendedor pueden crear órdenes a mano
        binding.fabAgregar.setVisibility(Sesion.esOptometrista() ? View.GONE : View.VISIBLE);
        binding.fabAgregar.setOnClickListener(v -> dialogoNuevaOrden());

        // Animación de entrada: título en cascada, hoja que sube y el botón + que aparece con rebote
        if (Animaciones.entrada(this, binding.encabezado, binding.hoja)) {
            Animaciones.aparecer(binding.fabAgregar, 450);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!Sesion.estaActiva()) return;
        escucha = Sesion.coleccion("ordenes")
                .orderBy("fechaCreacion", Query.Direction.DESCENDING)
                .addSnapshotListener((resultado, error) -> {
                    if (binding == null) return;
                    if (error != null) {
                        mensaje("No se pudieron leer las órdenes: " + error.getMessage());
                        return;
                    }
                    if (resultado == null) return;
                    todas.clear();
                    todas.addAll(resultado.toObjects(Orden.class));
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

    /** Estatus del chip elegido, o null si está elegido "Todas". */
    @Nullable
    private String estatusElegido() {
        int id = binding.chipsEstatus.getCheckedChipId();
        if (id == R.id.chipEnviado) return Orden.ENVIADO;
        if (id == R.id.chipEnProceso) return Orden.EN_PROCESO;
        if (id == R.id.chipRecibido) return Orden.RECIBIDO;
        if (id == R.id.chipEntregado) return Orden.ENTREGADO;
        return null;
    }

    private void aplicarFiltro() {
        if (binding == null || adaptador == null) return;
        String estatus = estatusElegido();
        List<Orden> visibles = new ArrayList<>();
        for (Orden o : todas) {
            if (estatus == null || estatus.equals(o.estatus)) {
                visibles.add(o);
            }
        }
        adaptador.mostrar(visibles);
        Animaciones.listaUnaVez(this, binding.rvOrdenes);   // las órdenes entran una tras otra
        binding.tvVacio.setVisibility(visibles.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Muestra las 4 etapas para elegir el nuevo estatus. */
    private void elegirEstatus(Orden orden) {
        String[] nombres = new String[Orden.ETAPAS.length];
        for (int i = 0; i < nombres.length; i++) {
            nombres[i] = Orden.nombreEstatus(Orden.ETAPAS[i]);
        }
        int actual = Orden.indiceEtapa(orden.estatus);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Estatus de la orden de " + orden.pacienteNombre)
                .setSingleChoiceItems(nombres, actual, (dialogo, elegido) -> {
                    cambiarEstatus(orden, Orden.ETAPAS[elegido]);
                    dialogo.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Guarda el estatus y la fecha en que se llegó a esa etapa. */
    private void cambiarEstatus(Orden orden, String nuevo) {
        if (nuevo.equals(orden.estatus)) return;
        Sesion.coleccion("ordenes").document(orden.id)
                .update("estatus", nuevo, Orden.campoFecha(nuevo), new Date())
                .addOnFailureListener(e -> mensaje("No se pudo actualizar: " + e.getMessage()));
    }

    /** Orden creada a mano (por ejemplo, un trabajo que no salió de una venta en la app). */
    private void dialogoNuevaOrden() {
        DialogOrdenBinding d = DialogOrdenBinding.inflate(getLayoutInflater());
        AlertDialog dialogo = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Nueva orden de laboratorio")
                .setView(d.getRoot())
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        dialogo.setOnShowListener(x -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String paciente = d.etPaciente.getText().toString().trim();
            String trabajo = d.etDescripcion.getText().toString().trim();
            if (paciente.isEmpty()) {
                d.etPaciente.setError("Escribe el nombre");
                return;
            }
            if (trabajo.isEmpty()) {
                d.etDescripcion.setError("Describe el trabajo");
                return;
            }
            Orden o = new Orden();
            o.pacienteId = "";
            o.pacienteNombre = paciente;
            o.descripcion = trabajo;
            o.ventaId = "";
            o.estatus = Orden.ENVIADO;
            o.fechaCreacion = new Date();
            o.fechaEnvio = o.fechaCreacion;
            Sesion.coleccion("ordenes").add(o)
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
