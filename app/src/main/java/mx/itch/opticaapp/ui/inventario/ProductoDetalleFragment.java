package mx.itch.opticaapp.ui.inventario;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.DialogColorBinding;
import mx.itch.opticaapp.databinding.FragmentProductoDetalleBinding;
import mx.itch.opticaapp.databinding.ItemColorBinding;
import mx.itch.opticaapp.model.Optica;
import mx.itch.opticaapp.model.Producto;
import mx.itch.opticaapp.model.VarianteColor;
import mx.itch.opticaapp.ui.venta.VentaFragment;
import mx.itch.opticaapp.util.AlEscribir;
import mx.itch.opticaapp.util.Animaciones;
import mx.itch.opticaapp.util.Aviso;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.Imagenes;
import mx.itch.opticaapp.util.SelectorDeFoto;
import mx.itch.opticaapp.util.Validaciones;

/**
 * Detalle de un producto del inventario (al tocar un mosaico del catálogo).
 *
 * Muestra etiquetas de características, variantes de color (se elige una y se pueden agregar
 * colores exactos), la opción de estampar el logotipo de la óptica con su costo adicional y las
 * existencias. Escucha el documento en tiempo real: si alguien vende o edita el producto, la
 * pantalla se actualiza sola. "Vender" abre Punto de venta con el producto, el color elegido y la
 * personalización ya en el carrito.
 */
public class ProductoDetalleFragment extends Fragment {

    public static final String ARG_PRODUCTO_ID = "productoId";
    private static final int MAXIMO_COLORES = 12;

    private FragmentProductoDetalleBinding binding;
    private ListenerRegistration escucha;
    private String productoId;
    private Producto producto;
    /** Posición del color elegido en producto.colores. */
    private int colorElegido = 0;

    /** Cámara o galería para cambiar el logotipo de la óptica desde aquí (solo administrador). */
    private final SelectorDeFoto selectorLogo = new SelectorDeFoto(this, this::subirLogo);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProductoDetalleBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        productoId = getArguments() != null ? getArguments().getString(ARG_PRODUCTO_ID) : null;
        if (!Sesion.estaActiva() || productoId == null) return;
        if (savedInstanceState != null) colorElegido = savedInstanceState.getInt("colorElegido", 0);

        binding.btnEditar.setOnClickListener(v -> abrirFormulario());
        binding.btnVender.setOnClickListener(v -> venderProducto());
        // El optometrista no ve el inventario; aun así, solo quien vende ve el botón
        binding.btnVender.setVisibility(puedeVender() ? View.VISIBLE : View.GONE);

        binding.swEstampar.setOnCheckedChangeListener((boton, activo) -> mostrarPersonalizacion());
        binding.btnCambiarLogo.setVisibility(Sesion.esAdmin() ? View.VISIBLE : View.GONE);
        binding.btnCambiarLogo.setText(tieneLogo() ? "Cambiar" : "Subir logo");
        binding.btnCambiarLogo.setOnClickListener(v -> selectorLogo.elegir("Logotipo de la óptica"));
        mostrarLogo();

        // Animación de entrada: la foto crece, la hoja sube y al final sube el pie con el precio
        if (Animaciones.entrada(this, null, binding.hoja)) {
            float dp = getResources().getDisplayMetrics().density;
            Animaciones.aparecer(binding.ivFoto, 0);
            Animaciones.subir(binding.pie, 40 * dp, 260);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("colorElegido", colorElegido);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!Sesion.estaActiva() || productoId == null) return;
        escucha = Sesion.coleccion("productos").document(productoId).addSnapshotListener((doc, error) -> {
            if (binding == null) return;
            if (error != null) {
                mensaje("No se pudo leer el producto: " + error.getMessage());
                return;
            }
            if (doc == null || !doc.exists()) {
                // Lo borraron (por ejemplo, desde el formulario): regresamos al catálogo
                NavHostFragment.findNavController(this).popBackStack();
                return;
            }
            producto = doc.toObject(Producto.class);
            mostrar();
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

    // ---------------- Mostrar ----------------

    private void mostrar() {
        if (producto == null) return;
        binding.tvCategoria.setText(producto.categoria);
        binding.tvNombre.setText(producto.nombre);
        boolean sinDescripcion = producto.descripcion == null || producto.descripcion.trim().isEmpty();
        binding.tvDescripcion.setText(sinDescripcion ? "Sin descripción." : producto.descripcion);
        Imagenes.mostrar(binding.ivFoto, producto.fotoUrl);

        mostrarEtiquetas();
        mostrarColores();
        mostrarPersonalizacion();
        mostrarExistencias();
    }

    /** Cápsulas violeta pálido con cada característica. */
    private void mostrarEtiquetas() {
        binding.grupoEtiquetas.removeAllViews();
        if (producto.etiquetas != null) {
            for (String etiqueta : producto.etiquetas) {
                Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_etiqueta, binding.grupoEtiquetas, false);
                chip.setText(etiqueta);
                binding.grupoEtiquetas.addView(chip);
            }
        }
        binding.grupoEtiquetas.setVisibility(binding.grupoEtiquetas.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    /** Carrusel de círculos de color; el elegido lleva anillo doble y textura de tela. */
    private void mostrarColores() {
        int total = producto.colores != null ? producto.colores.size() : 0;
        boolean puedeAgregar = puedeEditar() && total < MAXIMO_COLORES;
        binding.seccionColores.setVisibility(total > 0 || puedeEditar() ? View.VISIBLE : View.GONE);
        if (colorElegido >= total) colorElegido = 0;

        binding.contenedorColores.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < total; i++) {
            VarianteColor c = producto.colores.get(i);
            ItemColorBinding item = ItemColorBinding.inflate(inflater, binding.contenedorColores, false);
            item.muestra.setColor(c.colorAndroid());
            item.muestra.setSeleccionada(i == colorElegido);
            item.tvNombreColor.setText(c.nombre);
            item.tvHexColor.setText(c.hex);
            item.getRoot().setContentDescription(c.nombre + ", " + c.hex);
            final int posicion = i;
            item.getRoot().setOnClickListener(v -> {
                colorElegido = posicion;
                mostrarColores();
            });
            if (puedeEditar()) {
                item.getRoot().setOnLongClickListener(v -> {
                    confirmarQuitarColor(c);
                    return true;
                });
            }
            binding.contenedorColores.addView(item.getRoot());
        }
        if (puedeAgregar) {
            ItemColorBinding agregar = ItemColorBinding.inflate(inflater, binding.contenedorColores, false);
            agregar.muestra.setBotonAgregar(true);
            agregar.tvNombreColor.setText("+ Agregar");
            agregar.tvHexColor.setText("color exacto");
            agregar.getRoot().setContentDescription("Agregar color exacto");
            agregar.getRoot().setOnClickListener(v -> dialogoAgregarColor());
            binding.contenedorColores.addView(agregar.getRoot());
        }

        VarianteColor activo = colorActivo();
        binding.tvColorActivo.setText(activo != null
                ? "Color activo: " + activo.descripcion()
                : "Sin variantes registradas");
    }

    /** Fila del estampado, vista previa del logotipo y precio final del pie. */
    private void mostrarPersonalizacion() {
        if (binding == null || producto == null) return;
        binding.seccionPersonalizacion.setVisibility(producto.personalizable ? View.VISIBLE : View.GONE);
        binding.tvEstampado.setText("Estampar logo de la óptica (+" + Formato.moneda(producto.costoPersonalizacion) + ")");
        boolean estampar = conEstampado();
        binding.filaLogo.setVisibility(estampar ? View.VISIBLE : View.GONE);
        binding.tvPrecio.setText(Formato.moneda(precioFinal()));
    }

    private void mostrarLogo() {
        Optica optica = Sesion.getOptica();
        if (tieneLogo()) {
            binding.ivLogoOptica.setVisibility(View.VISIBLE);
            binding.tvLogoAlterno.setVisibility(View.GONE);
            Glide.with(this).load(optica.logoUrl).into(binding.ivLogoOptica);
        } else {
            binding.ivLogoOptica.setVisibility(View.GONE);
            binding.tvLogoAlterno.setVisibility(View.VISIBLE);
            binding.tvLogoAlterno.setText(optica.nombre);
        }
    }

    /** Cápsula verde, naranja o roja con las existencias y, a la derecha, el mínimo. */
    private void mostrarExistencias() {
        ProductosAdapter.mostrarEstado(binding.tvStock, producto);
        binding.tvStock.setText(producto.stock > 0
                ? "Existencias: " + producto.stock + " en stock"
                : "Existencias: agotado");
        binding.tvMinimo.setText(String.format(Locale.US, "Mínimo: %d %s",
                producto.stockMinimo, producto.stockMinimo == 1 ? "pieza" : "piezas"));

        boolean hayExistencias = producto.stock > 0;
        binding.btnVender.setEnabled(hayExistencias);
        binding.btnVender.setText(hayExistencias ? "Vender" : "Agotado");
    }

    // ---------------- Colores ----------------

    private void dialogoAgregarColor() {
        DialogColorBinding d = DialogColorBinding.inflate(getLayoutInflater());
        d.muestraPrevia.setSeleccionada(true);
        d.muestraPrevia.setColor(ContextCompat.getColor(requireContext(), R.color.texto_tenue));
        // Vista previa en vivo mientras se escribe el código
        d.etHexColor.addTextChangedListener(new AlEscribir(texto -> {
            if (Validaciones.colorHex(texto) == null) {
                d.muestraPrevia.setColor(new VarianteColor("", Validaciones.normalizarHex(texto)).colorAndroid());
            }
        }));

        AlertDialog dialogo = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Agregar color exacto")
                .setView(d.getRoot())
                .setPositiveButton("Agregar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        dialogo.setOnShowListener(x -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nombre = d.etNombreColor.getText().toString().trim().replaceAll("\\s+", " ");
            String hex = d.etHexColor.getText().toString();
            String errorNombre = Validaciones.nombreColor(nombre);
            String errorHex = Validaciones.colorHex(hex);
            if (errorHex == null) {
                hex = Validaciones.normalizarHex(hex);
                for (VarianteColor c : producto.colores) {
                    if (hex.equalsIgnoreCase(c.hex)) errorHex = "Este color ya está registrado (" + c.nombre + ")";
                }
            }
            if (errorNombre == null) {
                for (VarianteColor c : producto.colores) {
                    if (nombre.equalsIgnoreCase(c.nombre)) errorNombre = "Ya hay un color con este nombre";
                }
            }
            d.etNombreColor.setError(errorNombre);
            d.etHexColor.setError(errorHex);
            if (errorNombre != null || errorHex != null) return;

            colorElegido = producto.colores.size();   // el nuevo queda elegido
            Sesion.coleccion("productos").document(productoId)
                    .update("colores", FieldValue.arrayUnion(mapaColor(nombre, hex)))
                    .addOnFailureListener(e -> mensaje("No se pudo agregar el color: " + e.getMessage()));
            dialogo.dismiss();
        }));
        dialogo.show();
    }

    private void confirmarQuitarColor(VarianteColor c) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Quitar color")
                .setMessage("¿Quitar " + c.descripcion() + " de las variantes de este producto?")
                .setPositiveButton("Quitar", (d, w) -> {
                    colorElegido = 0;
                    Sesion.coleccion("productos").document(productoId)
                            .update("colores", FieldValue.arrayRemove(mapaColor(c.nombre, c.hex)))
                            .addOnFailureListener(e -> mensaje("No se pudo quitar: " + e.getMessage()));
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Mismo formato que VarianteColor; arrayRemove necesita exactamente los mismos campos. */
    private static Map<String, Object> mapaColor(String nombre, String hex) {
        Map<String, Object> mapa = new HashMap<>();
        mapa.put("nombre", nombre);
        mapa.put("hex", hex);
        return mapa;
    }

    // ---------------- Logotipo ----------------

    /** Reduce el logotipo a 400 px y lo guarda en /opticas/{id}.logoUrl (lo usan todas las pantallas y los PDF). */
    private void subirLogo(Uri foto) {
        if (!Sesion.esAdmin()) return;
        binding.btnCambiarLogo.setEnabled(false);
        Imagenes.convertir(foto, Imagenes.LADO_LOGO,
                datos -> Sesion.opticaRef().update("logoUrl", datos)
                        .addOnSuccessListener(x -> {
                            Sesion.getOptica().logoUrl = datos;
                            if (binding == null) return;
                            binding.btnCambiarLogo.setEnabled(true);
                            binding.btnCambiarLogo.setText("Cambiar");
                            mostrarLogo();
                            Aviso.mostrar(getActivity(), "Logotipo actualizado");
                        })
                        .addOnFailureListener(e -> {
                            if (binding != null) binding.btnCambiarLogo.setEnabled(true);
                            mensaje("No se pudo guardar el logotipo: " + e.getMessage());
                        }),
                e -> {
                    if (binding != null) binding.btnCambiarLogo.setEnabled(true);
                    mensaje("No se pudo preparar el logotipo: " + e.getMessage());
                });
    }

    // ---------------- Navegación ----------------

    private void abrirFormulario() {
        Bundle args = new Bundle();
        args.putString(ProductoFormFragment.ARG_PRODUCTO_ID, productoId);
        NavHostFragment.findNavController(this).navigate(R.id.action_detalle_to_productoForm, args);
    }

    /** Va a Punto de venta con el producto, el color elegido y el estampado (y su costo). */
    private void venderProducto() {
        if (producto == null) return;
        StringBuilder detalle = new StringBuilder();
        VarianteColor activo = colorActivo();
        if (activo != null) detalle.append(activo.nombre);
        if (conEstampado()) {
            if (detalle.length() > 0) detalle.append(" · ");
            detalle.append("logo estampado");
        }
        Bundle args = new Bundle();
        args.putString(VentaFragment.ARG_PRODUCTO_ID, productoId);
        args.putString(VentaFragment.ARG_DETALLE, detalle.toString());
        args.putDouble(VentaFragment.ARG_EXTRA, conEstampado() ? producto.costoPersonalizacion : 0);
        NavHostFragment.findNavController(this).navigate(R.id.action_detalle_to_venta, args);
    }

    // ---------------- Apoyo ----------------

    @Nullable
    private VarianteColor colorActivo() {
        if (producto == null || producto.colores == null || producto.colores.isEmpty()) return null;
        return producto.colores.get(Math.min(colorElegido, producto.colores.size() - 1));
    }

    private boolean conEstampado() {
        return producto != null && producto.personalizable && binding.swEstampar.isChecked();
    }

    private double precioFinal() {
        return producto.precio + (conEstampado() ? producto.costoPersonalizacion : 0);
    }

    private boolean tieneLogo() {
        Optica o = Sesion.getOptica();
        return o != null && o.logoUrl != null && !o.logoUrl.isEmpty();
    }

    /** Administrador y vendedor manejan el inventario (agregar o quitar colores). */
    private static boolean puedeEditar() {
        return Sesion.esAdmin() || Sesion.esVendedor();
    }

    private static boolean puedeVender() {
        return Sesion.esAdmin() || Sesion.esVendedor();
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
