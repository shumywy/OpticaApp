package mx.itch.opticaapp.ui.inventario;

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
import java.util.List;
import java.util.Locale;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentInventarioBinding;
import mx.itch.opticaapp.model.Producto;
import mx.itch.opticaapp.util.AlEscribir;

/** Pantalla 6: catálogo de productos con búsqueda y filtro por categoría. */
public class InventarioFragment extends Fragment {

    private FragmentInventarioBinding binding;
    private ProductosAdapter adaptador;
    private ListenerRegistration escucha;
    private final List<Producto> todos = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentInventarioBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!Sesion.estaActiva()) return;

        adaptador = new ProductosAdapter(producto -> abrirFormulario(producto.id));
        binding.rvProductos.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvProductos.setAdapter(adaptador);

        // Chips de categoría: al elegir uno se vuelve a filtrar la lista
        binding.chipsCategoria.setOnCheckedStateChangeListener((grupo, elegidos) -> aplicarFiltro());
        binding.etBuscar.addTextChangedListener(new AlEscribir(texto -> aplicarFiltro()));
        binding.fabAgregar.setOnClickListener(v -> abrirFormulario(null));
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!Sesion.estaActiva()) return;
        // Tiempo real: cuando se vende algo, el stock cambia aquí sin recargar
        escucha = Sesion.coleccion("productos").addSnapshotListener((resultado, error) -> {
            if (binding == null) return;
            if (error != null) {
                Toast.makeText(requireContext(), "No se pudo leer el inventario: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (resultado == null) return;
            todos.clear();
            todos.addAll(resultado.toObjects(Producto.class));
            Collections.sort(todos, (a, b) -> nombre(a).compareTo(nombre(b)));
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

    private String nombre(Producto p) {
        return p.nombre == null ? "" : p.nombre.toLowerCase(Locale.ROOT);
    }

    private void aplicarFiltro() {
        if (binding == null || adaptador == null) return;
        String buscado = binding.etBuscar.getText().toString().trim().toLowerCase(Locale.ROOT);
        String categoria = categoriaElegida();
        List<Producto> visibles = new ArrayList<>();
        for (Producto p : todos) {
            boolean coincideTexto = buscado.isEmpty() || nombre(p).contains(buscado);
            boolean coincideCategoria = categoria == null || categoria.equals(p.categoria);
            if (coincideTexto && coincideCategoria) {
                visibles.add(p);
            }
        }
        adaptador.mostrar(visibles);
        binding.tvVacio.setVisibility(visibles.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Texto de la categoría del chip elegido, o null si está elegido "Todas". */
    @Nullable
    private String categoriaElegida() {
        int id = binding.chipsCategoria.getCheckedChipId();
        if (id == R.id.chipArmazones) return "Armazones";
        if (id == R.id.chipMicas) return "Micas";
        if (id == R.id.chipContacto) return "Lentes de contacto";
        if (id == R.id.chipAccesorios) return "Accesorios";
        return null;
    }

    /** Abre el formulario: con id edita un producto, sin id crea uno nuevo. */
    private void abrirFormulario(@Nullable String productoId) {
        Bundle args = new Bundle();
        args.putString(ProductoFormFragment.ARG_PRODUCTO_ID, productoId);
        NavHostFragment.findNavController(this).navigate(R.id.action_inventario_to_productoForm, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
