package mx.itch.opticaapp.ui.inventario;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SnapshotMetadata;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Historial;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentProductoDetalleBinding;
import mx.itch.opticaapp.databinding.HojaColorBinding;
import mx.itch.opticaapp.databinding.ItemColorBinding;
import mx.itch.opticaapp.databinding.ItemMovimientoBinding;
import mx.itch.opticaapp.model.Movimiento;
import mx.itch.opticaapp.model.Optica;
import mx.itch.opticaapp.model.Producto;
import mx.itch.opticaapp.model.Usuario;
import mx.itch.opticaapp.model.VarianteColor;
import mx.itch.opticaapp.ui.venta.VentaFragment;
import mx.itch.opticaapp.util.AlEscribir;
import mx.itch.opticaapp.util.Animaciones;
import mx.itch.opticaapp.util.Aviso;
import mx.itch.opticaapp.util.Campos;
import mx.itch.opticaapp.util.Fechas;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.HojaOpciones;
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
    private ListenerRegistration escuchaHistorial;
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
        DocumentReference ref = Sesion.coleccion("productos").document(productoId);
        // MetadataChanges.INCLUDE: también avisa cuando un cambio pendiente termina de subirse a Firebase
        escucha = ref.addSnapshotListener(MetadataChanges.INCLUDE, (doc, error) -> {
            if (binding == null) return;
            if (error != null) {
                mensaje("No se pudo leer el producto: " + error.getMessage());
                return;
            }
            if (doc == null || !doc.exists()) {
                // Lo borraron (por ejemplo, desde el formulario): regresamos al catálogo
                if (doc != null && !doc.getMetadata().isFromCache()) {
                    NavHostFragment.findNavController(this).popBackStack();
                }
                return;
            }
            // ESTIMATE: mientras el servidor no confirma la fecha, se usa la hora del teléfono
            producto = doc.toObject(Producto.class, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
            mostrar();
            mostrarSincronizacion(doc.getMetadata());
        });
        escuchaHistorial = ref.collection("movimientos")
                .orderBy("fecha", Query.Direction.DESCENDING)
                .limit(20)
                .addSnapshotListener((resultado, error) -> {
                    if (binding == null || resultado == null) return;
                    mostrarHistorial(resultado.toObjects(Movimiento.class,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE));
                });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (escucha != null) {
            escucha.remove();
            escucha = null;
        }
        if (escuchaHistorial != null) {
            escuchaHistorial.remove();
            escuchaHistorial = null;
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

    // ---------------- Firebase e historial ----------------

    /**
     * Píldora con el estado del producto en Firebase:
     * verde = ya está en el servidor; naranja = hay cambios guardados en el teléfono que aún no se suben;
     * gris = sin conexión, se muestra la última copia guardada en el teléfono.
     */
    private void mostrarSincronizacion(SnapshotMetadata metadatos) {
        int fondo;
        int color;
        String texto;
        if (metadatos.hasPendingWrites()) {
            texto = "Pendiente de subir";
            fondo = R.drawable.bg_pildora_naranja;
            color = R.color.naranja;
        } else if (metadatos.isFromCache()) {
            texto = "Sin conexión";
            fondo = R.drawable.bg_pildora_gris;
            color = R.color.texto_secundario;
        } else {
            texto = "Guardado en Firebase";
            fondo = R.drawable.bg_pildora_verde;
            color = R.color.verde;
        }
        int tinte = ContextCompat.getColor(requireContext(), color);
        binding.tvSincronizacion.setText(texto);
        binding.tvSincronizacion.setBackgroundResource(fondo);
        binding.tvSincronizacion.setTextColor(tinte);
        Drawable nube = ContextCompat.getDrawable(requireContext(), R.drawable.ic_nube);
        if (nube != null) {
            nube = DrawableCompat.wrap(nube.mutate());
            DrawableCompat.setTint(nube, tinte);
            int lado = Math.round(14 * getResources().getDisplayMetrics().density);
            nube.setBounds(0, 0, lado, lado);
            binding.tvSincronizacion.setCompoundDrawablesRelative(nube, null, null, null);
        }

        // Quién registró el producto y quién lo cambió por última vez
        StringBuilder registro = new StringBuilder();
        if (producto.fechaRegistro != null && producto.registradoPorNombre != null) {
            registro.append("Registrado el ").append(Fechas.corta(producto.fechaRegistro))
                    .append(" por ").append(producto.registradoPorNombre);
        } else {
            registro.append("Registrado antes de que existiera el historial");
        }
        if (producto.fechaActualizacion != null && producto.actualizadoPorNombre != null
                && (producto.fechaRegistro == null
                || producto.fechaActualizacion.getTime() - producto.fechaRegistro.getTime() > 60_000)) {
            registro.append(" · Última actualización el ").append(Fechas.conHora(producto.fechaActualizacion))
                    .append(" por ").append(producto.actualizadoPorNombre);
        }
        binding.tvRegistro.setText(registro);
    }

    /** Renglones del historial: más reciente primero. */
    private void mostrarHistorial(List<Movimiento> movimientos) {
        binding.contenedorHistorial.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (Movimiento m : movimientos) {
            ItemMovimientoBinding fila = ItemMovimientoBinding.inflate(inflater, binding.contenedorHistorial, false);
            fila.tvTipo.setText(Movimiento.nombreTipo(m.tipo));
            fila.tvDescripcion.setText(m.descripcion);
            fila.tvDescripcion.setVisibility(m.descripcion == null || m.descripcion.isEmpty() ? View.GONE : View.VISIBLE);
            fila.tvFecha.setText((m.fecha != null ? Fechas.conHora(m.fecha) : "Ahora")
                    + (m.usuarioNombre != null && !m.usuarioNombre.isEmpty() ? " · " + m.usuarioNombre : ""));
            fila.ivTipo.setImageResource(iconoDe(m.tipo));

            if (m.cantidad == 0) {
                fila.tvCantidad.setVisibility(View.GONE);
                fila.tvExistencias.setVisibility(View.GONE);
            } else {
                boolean entra = m.cantidad > 0;
                fila.tvCantidad.setText((entra ? "+" : "−") + Math.abs(m.cantidad));
                fila.tvCantidad.setBackgroundResource(entra ? R.drawable.bg_pildora_verde : R.drawable.bg_pildora_rojo);
                fila.tvCantidad.setTextColor(ContextCompat.getColor(requireContext(), entra ? R.color.verde : R.color.rojo));
                fila.tvExistencias.setText("Quedan " + Math.max(0, m.existenciasDespues));
            }
            binding.contenedorHistorial.addView(fila.getRoot());
        }
        binding.tvSinHistorial.setVisibility(movimientos.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static int iconoDe(String tipo) {
        if (Movimiento.ALTA.equals(tipo)) return R.drawable.ic_agregar;
        if (Movimiento.VENTA.equals(tipo)) return R.drawable.ic_ventas;
        if (Movimiento.AJUSTE.equals(tipo)) return R.drawable.ic_inventario;
        if (Movimiento.BAJA.equals(tipo)) return R.drawable.ic_alerta;
        return R.drawable.ic_editar;
    }

    /**
     * Agrega o quita un color en una sola escritura junto con su registro en el historial
     * y los datos de quién hizo el cambio.
     */
    private void cambiarColores(FieldValue operacion, String descripcion) {
        Usuario u = Sesion.getUsuario();
        Map<String, Object> cambios = new HashMap<>();
        cambios.put("colores", operacion);
        cambios.put("fechaActualizacion", FieldValue.serverTimestamp());
        cambios.put("actualizadoPorId", u.id);
        cambios.put("actualizadoPorNombre", u.nombre);
        WriteBatch lote = FirebaseFirestore.getInstance().batch();
        lote.update(Sesion.coleccion("productos").document(productoId), cambios);
        Historial.registrar(lote, productoId, Movimiento.EDICION, descripcion, 0, producto.stock, null);
        lote.commit().addOnFailureListener(e -> mensaje("No se pudo guardar el cambio: " + e.getMessage()));
    }

    // ---------------- Colores ----------------

    /**
     * Hoja inferior "Agregar color exacto": muestra en vivo mientras se escribe el código,
     * nombre y código con su error debajo de cada campo. Se guarda con arrayUnion + historial.
     */
    private void dialogoAgregarColor() {
        BottomSheetDialog hoja = new BottomSheetDialog(requireContext());
        HojaColorBinding d = HojaColorBinding.inflate(LayoutInflater.from(hoja.getContext()));
        d.muestraPrevia.setSeleccionada(true);
        d.muestraPrevia.setColor(ContextCompat.getColor(requireContext(), R.color.texto_tenue));
        // Vista previa en vivo mientras se escriben el nombre y el código
        d.etNombreColor.addTextChangedListener(new AlEscribir(texto ->
                d.tvMuestraNombre.setText(texto.trim().isEmpty() ? "Nuevo color" : texto.trim())));
        d.etHexColor.addTextChangedListener(new AlEscribir(texto -> {
            if (Validaciones.colorHex(texto) == null) {
                String hex = Validaciones.normalizarHex(texto);
                d.muestraPrevia.setColor(new VarianteColor("", hex).colorAndroid());
                d.tvMuestraHex.setText(hex);
            } else {
                d.muestraPrevia.setColor(ContextCompat.getColor(requireContext(), R.color.texto_tenue));
                d.tvMuestraHex.setText("Escribe el código, por ejemplo #3C434A");
            }
        }));
        d.etHexColor.setOnEditorActionListener((v, accion, evento) -> {
            d.btnAgregarColor.performClick();
            return true;
        });

        d.btnCancelarColor.setOnClickListener(v -> hoja.dismiss());
        d.btnAgregarColor.setOnClickListener(v -> {
            if (producto == null) return;
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
            Campos.error(d.etNombreColor, errorNombre);
            Campos.error(d.etHexColor, errorHex);
            if (errorNombre != null || errorHex != null) return;

            colorElegido = producto.colores.size();   // el nuevo queda elegido
            cambiarColores(FieldValue.arrayUnion(mapaColor(nombre, hex)),
                    "Color agregado: " + nombre + " (" + hex + ")");
            hoja.dismiss();
        });

        hoja.setContentView(d.getRoot());
        HojaOpciones.abrirCompleta(hoja);
        if (hoja.getWindow() != null) {
            hoja.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        hoja.show();
    }

    private void confirmarQuitarColor(VarianteColor c) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Quitar color")
                .setMessage("¿Quitar " + c.descripcion() + " de las variantes de este producto?")
                .setPositiveButton("Quitar", (d, w) -> {
                    colorElegido = 0;
                    cambiarColores(FieldValue.arrayRemove(mapaColor(c.nombre, c.hex)),
                            "Color quitado: " + c.descripcion());
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
