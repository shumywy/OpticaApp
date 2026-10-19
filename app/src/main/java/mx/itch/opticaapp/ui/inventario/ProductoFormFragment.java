package mx.itch.opticaapp.ui.inventario;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
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
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Locale;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentProductoFormBinding;
import mx.itch.opticaapp.model.Producto;
import mx.itch.opticaapp.util.Animaciones;
import mx.itch.opticaapp.util.Aviso;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.Imagenes;
import mx.itch.opticaapp.util.Validaciones;

/**
 * Agrega o edita un producto. Si recibe un id en los argumentos, edita; si no, crea uno nuevo.
 * La foto se toma con la cámara (o se elige de la galería), se reduce a 640 px y se guarda
 * como texto en el campo fotoUrl del mismo documento del producto.
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
        // El costo del estampado solo se pide si el producto se puede personalizar
        binding.swPersonalizable.setOnCheckedChangeListener((boton, activo) ->
                binding.etCostoPersonalizacion.setVisibility(activo ? View.VISIBLE : View.GONE));

        if (productoId != null) {
            cargarProducto();
        } else {
            Imagenes.mostrar(binding.ivFoto, null);   // ícono mientras no haya foto
        }

        // Animación de entrada: el título baja en cascada y la hoja blanca sube
        Animaciones.entrada(this, binding.encabezado, binding.hoja);
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
                    if (original.etiquetas != null) {
                        binding.etEtiquetas.setText(String.join(", ", original.etiquetas));
                    }
                    binding.swPersonalizable.setChecked(original.personalizable);
                    if (original.costoPersonalizacion > 0) {
                        binding.etCostoPersonalizacion.setText(String.format(Locale.US, "%.2f", original.costoPersonalizacion));
                    }
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
        String nombre = texto(binding.etNombre);
        String descripcion = texto(binding.etDescripcion);
        String precioTexto = binding.etPrecio.getText().toString();
        String stockTexto = binding.etStock.getText().toString();
        String minimoTexto = binding.etStockMinimo.getText().toString();
        String etiquetasTexto = binding.etEtiquetas.getText().toString();
        boolean personalizable = binding.swPersonalizable.isChecked();
        String costoTexto = binding.etCostoPersonalizacion.getText().toString();

        // Se revisan todos los campos para marcar todos los errores a la vez
        boolean ok = marcar(binding.etNombre, Validaciones.nombreComercial(nombre, "el nombre del producto"));
        ok &= marcar(binding.etDescripcion, Validaciones.textoLibre(descripcion, false, 0,
                Validaciones.LARGO_DESCRIPCION, "la descripción"));
        ok &= marcar(binding.etPrecio, Validaciones.precio(precioTexto));
        ok &= marcar(binding.etStock, Validaciones.entero(stockTexto, 0, 99_999, "las existencias"));
        ok &= marcar(binding.etStockMinimo, minimoTexto.trim().isEmpty() ? null
                : Validaciones.entero(minimoTexto, 0, 9_999, "el stock mínimo"));
        ok &= marcar(binding.etEtiquetas, Validaciones.etiquetas(etiquetasTexto));
        ok &= marcar(binding.etCostoPersonalizacion, personalizable
                ? Validaciones.importe(costoTexto, 9_999.99) : null);
        if (!ok) return;

        Producto p = new Producto();
        p.nombre = nombre;
        p.categoria = binding.spCategoria.getSelectedItem().toString();
        p.descripcion = descripcion;
        p.precio = Formato.leerNumero(precioTexto);
        p.stock = Formato.leerEntero(stockTexto);
        Integer minimo = Formato.leerEntero(minimoTexto);
        p.stockMinimo = minimo != null ? minimo : 0;
        p.fotoUrl = original != null && original.fotoUrl != null ? original.fotoUrl : "";
        p.etiquetas = Formato.separarEtiquetas(etiquetasTexto);
        p.personalizable = personalizable;
        Double costo = Formato.leerNumero(costoTexto);
        p.costoPersonalizacion = personalizable && costo != null ? costo : 0;
        // Los colores se agregan en el detalle; aquí se conservan para no borrarlos al guardar
        if (original != null && original.colores != null) p.colores = original.colores;

        // No se permiten dos productos con el mismo nombre en el inventario
        mostrarCargando(true);
        Sesion.coleccion("productos").whereEqualTo("nombre", nombre).limit(2).get()
                .addOnSuccessListener(resultado -> {
                    if (binding == null) return;
                    for (DocumentSnapshot doc : resultado.getDocuments()) {
                        if (!doc.getId().equals(productoId)) {
                            mostrarCargando(false);
                            binding.etNombre.setError("Ya existe un producto con este nombre");
                            binding.etNombre.requestFocus();
                            return;
                        }
                    }
                    guardarProducto(p);
                })
                .addOnFailureListener(e -> error("No se pudo revisar el inventario", e));
    }

    /** Escribe el producto (con su foto nueva, si se eligió una) después de validar. */
    private void guardarProducto(Producto p) {
        // Si es nuevo, Firestore genera el id aquí mismo
        DocumentReference ref = productoId != null
                ? Sesion.coleccion("productos").document(productoId)
                : Sesion.coleccion("productos").document();

        if (fotoNueva == null) {
            escribir(ref, p);
            return;
        }
        Imagenes.convertir(fotoNueva, Imagenes.LADO_PRODUCTO,
                datos -> {
                    p.fotoUrl = datos;
                    escribir(ref, p);
                },
                e -> {
                    mensaje("No se pudo preparar la foto (se guarda sin ella): " + e.getMessage());
                    escribir(ref, p);
                });
    }

    /** Texto del campo sin espacios al inicio o al final, ni espacios dobles en medio. */
    private static String texto(EditText campo) {
        return campo.getText().toString().trim().replaceAll("\\s+", " ");
    }

    /** Pone o quita el error de un campo. Devuelve true si no hay error. */
    private static boolean marcar(EditText campo, String error) {
        campo.setError(error);
        return error == null;
    }

    private void escribir(DocumentReference ref, Producto p) {
        ref.set(p)
                .addOnSuccessListener(x -> {
                    Aviso.mostrar(getActivity(), "Producto guardado");
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
                            Aviso.mostrar(getActivity(), "Producto eliminado");
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
