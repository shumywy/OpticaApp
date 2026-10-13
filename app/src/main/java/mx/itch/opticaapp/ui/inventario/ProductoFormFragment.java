package mx.itch.opticaapp.ui.inventario;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentReference;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentProductoFormBinding;
import mx.itch.opticaapp.model.Producto;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.Imagenes;

/**
 * Agrega o edita un producto. Si recibe un id en los argumentos, edita; si no, crea uno nuevo.
 * La foto se toma con la cámara (o se elige de la galería) y se sube a Firebase Storage.
 */
public class ProductoFormFragment extends Fragment {

    public static final String ARG_PRODUCTO_ID = "productoId";

    private FragmentProductoFormBinding binding;
    private String productoId;          // null = producto nuevo
    private Producto original;          // datos cargados al editar
    private Uri fotoNueva;              // foto elegida en esta pantalla
    private Uri uriCamara;

    private final ActivityResultLauncher<Uri> tomarFoto =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), seTomo -> {
                if (Boolean.TRUE.equals(seTomo)) mostrarFoto(uriCamara);
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> elegirImagen =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) mostrarFoto(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProductoFormBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!Sesion.estaActiva()) return;
        productoId = getArguments() != null ? getArguments().getString(ARG_PRODUCTO_ID) : null;

        ArrayAdapter<CharSequence> categorias = ArrayAdapter.createFromResource(requireContext(),
                R.array.categorias_producto, android.R.layout.simple_spinner_item);
        categorias.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spCategoria.setAdapter(categorias);

        binding.btnCamara.setOnClickListener(v -> {
            uriCamara = Imagenes.nuevaUriParaFoto(requireContext());
            tomarFoto.launch(uriCamara);
        });
        binding.btnGaleria.setOnClickListener(v -> elegirImagen.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        binding.btnGuardar.setOnClickListener(v -> guardar());
        binding.btnEliminar.setOnClickListener(v -> confirmarEliminar());

        if (productoId != null) {
            cargarProducto();
        } else {
            Imagenes.mostrar(binding.ivFoto, null);   // ícono mientras no haya foto
        }
    }

    private void cargarProducto() {
        mostrarCargando(true);
        Sesion.coleccion("productos").document(productoId).get()
                .addOnSuccessListener(doc -> {
                    if (binding == null) return;
                    mostrarCargando(false);
                    original = doc.toObject(Producto.class);
                    if (original == null) return;
                    binding.etNombre.setText(original.nombre);
                    binding.etDescripcion.setText(original.descripcion);
                    binding.etPrecio.setText(String.valueOf(original.precio));
                    binding.etStock.setText(String.valueOf(original.stock));
                    binding.etStockMinimo.setText(String.valueOf(original.stockMinimo));
                    String[] lista = getResources().getStringArray(R.array.categorias_producto);
                    for (int i = 0; i < lista.length; i++) {
                        if (lista[i].equals(original.categoria)) binding.spCategoria.setSelection(i);
                    }
                    Imagenes.mostrar(binding.ivFoto, original.fotoUrl);
                    // Solo el administrador puede borrar productos
                    binding.btnEliminar.setVisibility(Sesion.esAdmin() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> error("No se pudo cargar el producto", e));
    }

    private void mostrarFoto(Uri uri) {
        fotoNueva = uri;
        if (binding != null) {
            Imagenes.mostrarUri(binding.ivFoto, uri);
        }
    }

    private void guardar() {
        String nombre = binding.etNombre.getText().toString().trim();
        Double precio = Formato.leerNumero(binding.etPrecio.getText().toString());
        Integer stock = Formato.leerEntero(binding.etStock.getText().toString());
        Integer minimo = Formato.leerEntero(binding.etStockMinimo.getText().toString());
        boolean ok = true;
        if (nombre.isEmpty()) {
            binding.etNombre.setError("Escribe el nombre");
            ok = false;
        }
        if (precio == null || precio < 0) {
            binding.etPrecio.setError("Precio no válido");
            ok = false;
        }
        if (stock == null || stock < 0) {
            binding.etStock.setError("Escribe un número entero");
            ok = false;
        }
        if (!ok) return;

        Producto p = new Producto();
        p.nombre = nombre;
        p.categoria = binding.spCategoria.getSelectedItem().toString();
        p.descripcion = binding.etDescripcion.getText().toString().trim();
        p.precio = precio;
        p.stock = stock;
        p.stockMinimo = minimo != null ? minimo : 0;
        p.fotoUrl = original != null && original.fotoUrl != null ? original.fotoUrl : "";

        // Si es nuevo, Firestore genera el id aquí mismo
        DocumentReference ref = productoId != null
                ? Sesion.coleccion("productos").document(productoId)
                : Sesion.coleccion("productos").document();

        mostrarCargando(true);
        if (fotoNueva == null) {
            escribir(ref, p);
            return;
        }
        String ruta = "opticas/" + Sesion.getOptica().id + "/productos/" + ref.getId() + ".jpg";
        Imagenes.subir(fotoNueva, ruta,
                url -> {
                    p.fotoUrl = url;
                    escribir(ref, p);
                },
                e -> {
                    mensaje("No se pudo subir la foto (se guarda sin ella): " + e.getMessage());
                    escribir(ref, p);
                });
    }

    private void escribir(DocumentReference ref, Producto p) {
        ref.set(p)
                .addOnSuccessListener(x -> {
                    mensaje("Producto guardado");
                    regresar();
                })
                .addOnFailureListener(e -> error("No se pudo guardar", e));
    }

    private void confirmarEliminar() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar producto")
                .setMessage("¿Seguro que quieres eliminar \"" + binding.etNombre.getText() + "\"?")
                .setPositiveButton("Eliminar", (d, w) -> Sesion.coleccion("productos").document(productoId).delete()
                        .addOnSuccessListener(x -> {
                            mensaje("Producto eliminado");
                            regresar();
                        })
                        .addOnFailureListener(e -> error("No se pudo eliminar", e)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void regresar() {
        if (isAdded()) {
            NavHostFragment.findNavController(this).popBackStack();
        }
    }

    private void mostrarCargando(boolean cargando) {
        if (binding == null) return;
        binding.progreso.setVisibility(cargando ? View.VISIBLE : View.GONE);
        binding.btnGuardar.setEnabled(!cargando);
    }

    private void error(String texto, Exception e) {
        mostrarCargando(false);
        mensaje(texto + ": " + e.getMessage());
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
