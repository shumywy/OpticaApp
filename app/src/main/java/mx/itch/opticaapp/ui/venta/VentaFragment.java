package mx.itch.opticaapp.ui.venta;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentVentaBinding;
import mx.itch.opticaapp.model.ItemVenta;
import mx.itch.opticaapp.model.Orden;
import mx.itch.opticaapp.model.Paciente;
import mx.itch.opticaapp.model.Producto;
import mx.itch.opticaapp.model.Venta;
import mx.itch.opticaapp.util.AlEscribir;
import mx.itch.opticaapp.util.Animaciones;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.Pdf;

/**
 * Pantalla 7: punto de venta.
 * Al confirmar, en una sola escritura (WriteBatch): se guarda la venta, se descuenta el stock
 * de cada producto con FieldValue.increment y, si se pidió, se crea la orden de laboratorio.
 */
public class VentaFragment extends Fragment {

    /** Si llega este argumento (desde el detalle de producto), ese producto se agrega solo al carrito. */
    public static final String ARG_PRODUCTO_ID = "productoId";

    private static final String PUBLICO_GENERAL = "Público general";

    /** Producto que hay que agregar en cuanto llegue la lista de productos de Firestore. */
    private String productoPorAgregar;

    private FragmentVentaBinding binding;
    private ListenerRegistration escuchaPacientes;
    private ListenerRegistration escuchaProductos;

    private final List<Paciente> pacientes = new ArrayList<>();
    private final List<String> nombresPacientes = new ArrayList<>();
    private final List<Producto> productosDisponibles = new ArrayList<>();
    private final List<ItemVenta> carrito = new ArrayList<>();

    private ArrayAdapter<String> adaptadorPacientes;
    private ArrayAdapter<Producto> adaptadorProductos;
    private CarritoAdapter adaptadorCarrito;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentVentaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!Sesion.estaActiva()) return;

        adaptadorPacientes = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, nombresPacientes);
        adaptadorPacientes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spPaciente.setAdapter(adaptadorPacientes);

        adaptadorProductos = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, productosDisponibles);
        adaptadorProductos.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spProducto.setAdapter(adaptadorProductos);


        adaptadorCarrito = new CarritoAdapter(carrito, this::quitarDelCarrito);
        binding.rvCarrito.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCarrito.setAdapter(adaptadorCarrito);

        binding.btnAgregar.setOnClickListener(v -> agregarAlCarrito());
        binding.etAnticipo.addTextChangedListener(new AlEscribir(texto -> actualizarTotales()));
        binding.swOrden.setOnCheckedChangeListener((boton, marcado) ->
                binding.etOrdenDescripcion.setVisibility(marcado ? View.VISIBLE : View.GONE));
        binding.btnConfirmar.setOnClickListener(v -> confirmarVenta());
        actualizarTotales();

        // Se lee una sola vez: se borra del Bundle para no agregarlo de nuevo al regresar a esta pantalla
        if (getArguments() != null && getArguments().containsKey(ARG_PRODUCTO_ID)) {
            productoPorAgregar = getArguments().getString(ARG_PRODUCTO_ID);
            getArguments().remove(ARG_PRODUCTO_ID);
        }

        // Animación de entrada: título en cascada, hoja que sube y el pie con el total al final
        if (Animaciones.entrada(this, binding.encabezado, binding.hoja)) {
            Animaciones.subir(binding.pie, 40 * getResources().getDisplayMetrics().density, 260);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!Sesion.estaActiva()) return;

        escuchaPacientes = Sesion.coleccion("pacientes").addSnapshotListener((resultado, error) -> {
            if (binding == null || resultado == null) return;
            pacientes.clear();
            pacientes.addAll(resultado.toObjects(Paciente.class));
            Collections.sort(pacientes, (a, b) -> texto(a.nombre).compareTo(texto(b.nombre)));
            nombresPacientes.clear();
            nombresPacientes.add(PUBLICO_GENERAL);
            for (Paciente p : pacientes) {
                nombresPacientes.add(p.nombre);
            }
            adaptadorPacientes.notifyDataSetChanged();
        });

        // Solo se ofrecen productos con existencias
        escuchaProductos = Sesion.coleccion("productos").addSnapshotListener((resultado, error) -> {
            if (binding == null || resultado == null) return;
            productosDisponibles.clear();
            for (Producto p : resultado.toObjects(Producto.class)) {
                if (p.stock > 0) productosDisponibles.add(p);
            }
            Collections.sort(productosDisponibles, (a, b) -> texto(a.nombre).compareTo(texto(b.nombre)));
            adaptadorProductos.notifyDataSetChanged();
            agregarProductoPendiente();
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (escuchaPacientes != null) escuchaPacientes.remove();
        if (escuchaProductos != null) escuchaProductos.remove();
        escuchaPacientes = null;
        escuchaProductos = null;
    }

    private String texto(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    // ---------------- Carrito ----------------

    private void agregarAlCarrito() {
        Producto p = (Producto) binding.spProducto.getSelectedItem();
        if (p == null) {
            mensaje("No hay productos con existencias");
            return;
        }
        Integer cantidad = Formato.leerEntero(binding.etCantidad.getText().toString());
        if (cantidad == null || cantidad <= 0) {
            binding.etCantidad.setError("Cantidad no válida");
            return;
        }
        agregar(p, cantidad);
    }

    /** Agrega el producto que se eligió en el detalle de producto ("Vender"). */
    private void agregarProductoPendiente() {
        if (productoPorAgregar == null) return;
        String id = productoPorAgregar;
        productoPorAgregar = null;   // solo una vez
        for (int i = 0; i < productosDisponibles.size(); i++) {
            Producto p = productosDisponibles.get(i);
            if (id.equals(p.id)) {
                binding.spProducto.setSelection(i);
                agregar(p, 1);
                return;
            }
        }
        mensaje("Ese producto ya no tiene existencias");
    }

    private void agregar(Producto p, int cantidad) {
        // Revisa que no se vendan más piezas de las que hay
        ItemVenta existente = null;
        for (ItemVenta i : carrito) {
            if (i.productoId.equals(p.id)) existente = i;
        }
        int yaAgregadas = existente != null ? existente.cantidad : 0;
        if (yaAgregadas + cantidad > p.stock) {
            binding.etCantidad.setError("Solo quedan " + p.stock);
            return;
        }
        // Avisos por renglón (no notifyDataSetChanged) para que RecyclerView anime la entrada o el cambio
        if (existente != null) {
            existente.cantidad += cantidad;
            adaptadorCarrito.notifyItemChanged(carrito.indexOf(existente));
        } else {
            carrito.add(new ItemVenta(p, cantidad));
            adaptadorCarrito.notifyItemInserted(carrito.size() - 1);
        }
        // Si lleva micas, normalmente se manda a laboratorio
        if (Producto.CATEGORIA_MICAS.equals(p.categoria)) {
            binding.swOrden.setChecked(true);
            if (binding.etOrdenDescripcion.getText().toString().trim().isEmpty()) {
                binding.etOrdenDescripcion.setText(p.nombre);
            }
        }
        binding.etCantidad.setText("1");
        actualizarTotales();
    }

    private void quitarDelCarrito(int posicion) {
        if (posicion == RecyclerView.NO_POSITION || posicion >= carrito.size()) return;
        carrito.remove(posicion);
        adaptadorCarrito.notifyItemRemoved(posicion);   // el renglón se desvanece
        actualizarTotales();
    }

    private double total() {
        double total = 0;
        for (ItemVenta i : carrito) {
            total += i.subtotal();
        }
        return total;
    }

    /** Anticipo escrito; si el campo está vacío se toma como pago completo. */
    private Double anticipo() {
        String escrito = binding.etAnticipo.getText().toString().trim();
        if (escrito.isEmpty()) return total();
        return Formato.leerNumero(escrito);
    }

    private void actualizarTotales() {
        if (binding == null) return;
        double total = total();
        Double anticipo = anticipo();
        binding.tvTotal.setText(Formato.moneda(total));
        binding.tvCarritoVacio.setVisibility(carrito.isEmpty() ? View.VISIBLE : View.GONE);
        if (anticipo == null) {
            binding.tvSaldo.setText("Anticipo no válido");
        } else {
            binding.tvSaldo.setText(Formato.moneda(Math.max(0, total - anticipo)));
        }
    }

    // ---------------- Confirmar ----------------

    private void confirmarVenta() {
        if (carrito.isEmpty()) {
            mensaje("Agrega al menos un producto");
            return;
        }
        double total = total();
        Double anticipo = anticipo();
        if (anticipo == null || anticipo < 0 || anticipo > total) {
            binding.etAnticipo.setError("El anticipo debe estar entre $0 y el total");
            return;
        }
        boolean conOrden = binding.swOrden.isChecked();
        String trabajo = binding.etOrdenDescripcion.getText().toString().trim();
        if (conOrden && trabajo.isEmpty()) {
            binding.etOrdenDescripcion.setError("Describe el trabajo para el laboratorio");
            return;
        }

        Venta venta = new Venta();
        int posicionCliente = binding.spPaciente.getSelectedItemPosition();
        if (posicionCliente > 0 && posicionCliente - 1 < pacientes.size()) {
            Paciente p = pacientes.get(posicionCliente - 1);
            venta.pacienteId = p.id;
            venta.pacienteNombre = p.nombre;
        } else {
            venta.pacienteId = "";
            venta.pacienteNombre = PUBLICO_GENERAL;
        }
        venta.items = new ArrayList<>(carrito);
        venta.total = total;
        venta.anticipo = anticipo;
        venta.saldo = total - anticipo;
        venta.metodoPago = metodoDePago();
        venta.vendedorId = Sesion.getUsuario().id;
        venta.vendedorNombre = Sesion.getUsuario().nombre;
        venta.fecha = new Date();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch lote = db.batch();
        DocumentReference ventaRef = Sesion.coleccion("ventas").document();
        lote.set(ventaRef, venta);
        for (ItemVenta i : venta.items) {
            // Resta las piezas vendidas directamente en el servidor
            lote.update(Sesion.coleccion("productos").document(i.productoId),
                    "stock", FieldValue.increment(-i.cantidad));
        }
        if (conOrden) {
            Orden orden = new Orden();
            orden.ventaId = ventaRef.getId();
            orden.pacienteId = venta.pacienteId;
            orden.pacienteNombre = venta.pacienteNombre;
            orden.descripcion = trabajo;
            orden.estatus = Orden.ENVIADO;
            orden.fechaCreacion = venta.fecha;
            orden.fechaEnvio = venta.fecha;
            lote.set(Sesion.coleccion("ordenes").document(), orden);
        }

        mostrarCargando(true);
        lote.commit()
                .addOnSuccessListener(x -> {
                    venta.id = ventaRef.getId();
                    mostrarCargando(false);
                    limpiar();
                    ventaRegistrada(venta);
                })
                .addOnFailureListener(e -> {
                    mostrarCargando(false);
                    mensaje("No se pudo registrar la venta: " + e.getMessage());
                });
    }

    /** Texto del método elegido en el control segmentado. */
    private String metodoDePago() {
        int id = binding.rgMetodo.getCheckedRadioButtonId();
        if (id == R.id.rbTarjeta) return "Tarjeta";
        if (id == R.id.rbTransferencia) return "Transferencia";
        return "Efectivo";
    }

    private void ventaRegistrada(Venta venta) {
        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Venta registrada")
                .setMessage("Total: " + Formato.moneda(venta.total)
                        + "\nAnticipo: " + Formato.moneda(venta.anticipo)
                        + "\nSaldo pendiente: " + Formato.moneda(venta.saldo))
                .setPositiveButton("Nota de venta PDF", (d, w) -> compartirNota(venta))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void compartirNota(Venta venta) {
        try {
            File pdf = Pdf.notaVenta(requireContext(), Sesion.getOptica(), venta);
            Pdf.compartir(requireContext(), pdf);
        } catch (IOException e) {
            mensaje("No se pudo crear el PDF: " + e.getMessage());
        }
    }

    private void limpiar() {
        if (binding == null) return;
        carrito.clear();
        adaptadorCarrito.notifyDataSetChanged();
        binding.etAnticipo.setText("");
        binding.swOrden.setChecked(false);
        binding.etOrdenDescripcion.setText("");
        binding.spPaciente.setSelection(0);
        binding.rgMetodo.check(R.id.rbEfectivo);
        actualizarTotales();
    }

    private void mostrarCargando(boolean cargando) {
        if (binding == null) return;
        binding.progreso.setVisibility(cargando ? View.VISIBLE : View.GONE);
        binding.btnConfirmar.setEnabled(!cargando);
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
