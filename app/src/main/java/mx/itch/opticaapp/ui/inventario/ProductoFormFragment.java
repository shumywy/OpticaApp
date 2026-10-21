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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Historial;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentProductoFormBinding;
import mx.itch.opticaapp.model.Movimiento;
import mx.itch.opticaapp.model.Producto;
import mx.itch.opticaapp.model.Usuario;
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

    /** Prepara la foto nueva (si se eligió una) y después escribe el producto. */
    private void guardarProducto(Producto p) {
        if (fotoNueva == null) {
            escribir(p);
            return;
        }
        Imagenes.convertir(fotoNueva, Imagenes.LADO_PRODUCTO,
                datos -> {
                    p.fotoUrl = datos;
                    escribir(p);
                },
                e -> {
                    mensaje("No se pudo preparar la foto (se guarda sin ella): " + e.getMessage());
                    escribir(p);
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

    /**
     * Guarda el producto y su registro en el historial en una sola escritura (WriteBatch).
     * - Nuevo: documento completo con quién lo registró, y un movimiento ALTA.
     * - Edición: solo los campos que cambiaron; las existencias con FieldValue.increment para no
     *   perder una venta hecha al mismo tiempo en otro teléfono; y un movimiento EDICION o AJUSTE.
     * El aviso "Producto guardado" aparece cuando Firestore confirma que ya está en el servidor.
     */
    private void escribir(Producto p) {
        Usuario u = Sesion.getUsuario();
        WriteBatch lote = FirebaseFirestore.getInstance().batch();

        if (productoId == null) {
            DocumentReference ref = Sesion.coleccion("productos").document();
            p.registradoPorId = u.id;
            p.registradoPorNombre = u.nombre;
            p.actualizadoPorId = u.id;
            p.actualizadoPorNombre = u.nombre;
            // fechaRegistro y fechaActualizacion van vacías: las llena el servidor (@ServerTimestamp)
            lote.set(ref, p);
            Historial.registrar(lote, ref.getId(), Movimiento.ALTA,
                    "Alta con " + piezas(p.stock) + " a " + Formato.moneda(p.precio) + " c/u",
                    p.stock, 0, null);
        } else {
            Map<String, Object> cambios = new HashMap<>();
            List<String> detalle = new ArrayList<>();
            compararCambios(p, cambios, detalle);
            int diferencia = p.stock - original.stock;
            if (cambios.isEmpty() && diferencia == 0) {
                mostrarCargando(false);
                mensaje("No hay cambios que guardar");
                return;
            }
            if (diferencia != 0) {
                cambios.put("stock", FieldValue.increment(diferencia));
                detalle.add(0, "Existencias: " + original.stock + " → " + p.stock);
            }
            cambios.put("fechaActualizacion", FieldValue.serverTimestamp());
            cambios.put("actualizadoPorId", u.id);
            cambios.put("actualizadoPorNombre", u.nombre);
            lote.update(Sesion.coleccion("productos").document(productoId), cambios);
            Historial.registrar(lote, productoId, diferencia != 0 ? Movimiento.AJUSTE : Movimiento.EDICION,
                    String.join("\n", detalle), diferencia, original.stock, null);
        }

        lote.commit()
                .addOnSuccessListener(x -> {
                    Aviso.mostrar(getActivity(), "Producto guardado");
                    regresar();
                })
                .addOnFailureListener(e -> error("No se pudo guardar", e));
    }

    /** Llena "cambios" con los campos distintos al original y "detalle" con su descripción. */
    private void compararCambios(Producto p, Map<String, Object> cambios, List<String> detalle) {
        if (!igual(p.nombre, original.nombre)) {
            cambios.put("nombre", p.nombre);
            detalle.add("Nombre: " + original.nombre + " → " + p.nombre);
        }
        if (!igual(p.categoria, original.categoria)) {
            cambios.put("categoria", p.categoria);
            detalle.add("Categoría: " + original.categoria + " → " + p.categoria);
        }
        if (p.precio != original.precio) {
            cambios.put("precio", p.precio);
            detalle.add("Precio: " + Formato.moneda(original.precio) + " → " + Formato.moneda(p.precio));
        }
        if (p.stockMinimo != original.stockMinimo) {
            cambios.put("stockMinimo", p.stockMinimo);
            detalle.add("Stock mínimo: " + original.stockMinimo + " → " + p.stockMinimo);
        }
        if (!igual(p.descripcion, original.descripcion)) {
            cambios.put("descripcion", p.descripcion);
            detalle.add("Descripción actualizada");
        }
        List<String> etiquetasAntes = original.etiquetas != null ? original.etiquetas : new ArrayList<>();
        if (!p.etiquetas.equals(etiquetasAntes)) {
            cambios.put("etiquetas", p.etiquetas);
            detalle.add(p.etiquetas.isEmpty() ? "Características eliminadas"
                    : "Características: " + String.join(", ", p.etiquetas));
        }
        if (p.personalizable != original.personalizable || p.costoPersonalizacion != original.costoPersonalizacion) {
            cambios.put("personalizable", p.personalizable);
            cambios.put("costoPersonalizacion", p.costoPersonalizacion);
            detalle.add(p.personalizable
                    ? "Estampado de logo: sí (+" + Formato.moneda(p.costoPersonalizacion) + ")"
                    : "Estampado de logo: no");
        }
        if (!igual(p.fotoUrl, original.fotoUrl)) {
            cambios.put("fotoUrl", p.fotoUrl);
            detalle.add("Foto nueva");
        }
    }

    private static boolean igual(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
    }

    private static String piezas(int n) {
        return n + (n == 1 ? " pieza" : " piezas");
    }

    /**
     * Elimina el producto. En la misma escritura se guarda un movimiento BAJA con sus últimos datos:
     * el historial queda en Firestore aunque el documento del producto ya no exista.
     */
    private void confirmarEliminar() {
        if (original == null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar producto")
                .setMessage("¿Seguro que quieres eliminar \"" + original.nombre + "\"? Su historial se conserva en Firebase.")
                .setPositiveButton("Eliminar", (d, w) -> {
                    WriteBatch lote = FirebaseFirestore.getInstance().batch();
                    Historial.registrar(lote, productoId, Movimiento.BAJA,
                            "Producto eliminado: " + original.nombre + " (" + Formato.moneda(original.precio)
                                    + ", " + piezas(original.stock) + ")",
                            -original.stock, original.stock, null);
                    lote.delete(Sesion.coleccion("productos").document(productoId));
                    lote.commit()
                            .addOnSuccessListener(x -> {
                                Aviso.mostrar(getActivity(), "Producto eliminado");
                                regresar();
                            })
                            .addOnFailureListener(e -> error("No se pudo eliminar", e));
                })
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
