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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Historial;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentVentaBinding;
import mx.itch.opticaapp.model.Documento;
import mx.itch.opticaapp.model.ItemVenta;
import mx.itch.opticaapp.model.Movimiento;
import mx.itch.opticaapp.model.Orden;
import mx.itch.opticaapp.model.Paciente;
import mx.itch.opticaapp.model.Producto;
import mx.itch.opticaapp.model.Venta;
import mx.itch.opticaapp.util.AlEscribir;
import mx.itch.opticaapp.util.Animaciones;
import mx.itch.opticaapp.util.Campos;
import mx.itch.opticaapp.util.Documentos;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.Pdf;
import mx.itch.opticaapp.util.Validaciones;

/**
 * Pantalla 7: punto de venta.
 * Al confirmar, en una sola escritura (WriteBatch): se guarda la venta, se descuenta el stock
 * de cada producto con FieldValue.increment y, si se pidió, se crea la orden de laboratorio.
 */
public class VentaFragment extends Fragment {

    /** Si llega este argumento (desde el detalle de producto), ese producto se agrega solo al carrito. */
    public static final String ARG_PRODUCTO_ID = "productoId";
    /** Color y personalización elegidos en el detalle ("Gris Oxford · logo estampado"). */
    public static final String ARG_DETALLE = "detalle";
    /** Costo adicional por pieza (estampado del logotipo). */
    public static final String ARG_EXTRA = "extra";

    private static final String PUBLICO_GENERAL = "Público general";

    /** Producto que hay que agregar en cuanto llegue la lista de productos de Firestore. */
    private String productoPorAgregar;
    private String detallePorAgregar;
    private double extraPorAgregar;

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
                Campos.mostrar(binding.etOrdenDescripcion, marcado));
        binding.btnConfirmar.setOnClickListener(v -> confirmarVenta());
        actualizarTotales();

        // Se lee una sola vez: se borra del Bundle para no agregarlo de nuevo al regresar a esta pantalla
        if (getArguments() != null && getArguments().containsKey(ARG_PRODUCTO_ID)) {
            productoPorAgregar = getArguments().getString(ARG_PRODUCTO_ID);
            detallePorAgregar = getArguments().getString(ARG_DETALLE);
            extraPorAgregar = getArguments().getDouble(ARG_EXTRA, 0);
            getArguments().remove(ARG_PRODUCTO_ID);
            getArguments().remove(ARG_DETALLE);
            getArguments().remove(ARG_EXTRA);
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
        String error = Validaciones.entero(binding.etCantidad.getText().toString(), 1, 999, "la cantidad");
        if (error != null) {
            Campos.error(binding.etCantidad, error);
            return;
        }
        agregar(p, Formato.leerEntero(binding.etCantidad.getText().toString()));
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
                agregar(p, 1, detallePorAgregar, extraPorAgregar);
                return;
            }
        }
        mensaje("Ese producto ya no tiene existencias");
    }

    private void agregar(Producto p, int cantidad) {
        agregar(p, cantidad, null, 0);
    }

    /**
     * Agrega piezas al carrito. Con detalle (color o estampado) se crea un renglón propio:
     * "Paño Twins · Gris Oxford" y "Paño Twins · Azul Marino" son renglones distintos.
     */
    private void agregar(Producto p, int cantidad, String detalle, double extra) {
        ItemVenta nuevo = new ItemVenta(p, cantidad, detalle, extra);
        // Revisa que no se vendan más piezas de las que hay (sumando todos los renglones del producto)
        ItemVenta existente = null;
        int yaAgregadas = 0;
        for (ItemVenta i : carrito) {
            if (!i.productoId.equals(p.id)) continue;
            yaAgregadas += i.cantidad;
            if (i.nombre.equals(nuevo.nombre) && i.precio == nuevo.precio) existente = i;
        }
        if (yaAgregadas + cantidad > p.stock) {
            int disponibles = p.stock - yaAgregadas;
            Campos.error(binding.etCantidad, disponibles > 0
                    ? "Solo puedes agregar " + disponibles + " más (quedan " + p.stock + ")"
                    : "Ya agregaste todas las piezas disponibles (" + p.stock + ")");
            return;
        }
        Campos.error(binding.etCantidad, null);
        // Avisos por renglón (no notifyDataSetChanged) para que RecyclerView anime la entrada o el cambio
        if (existente != null) {
            existente.cantidad += cantidad;
            adaptadorCarrito.notifyItemChanged(carrito.indexOf(existente));
        } else {
            carrito.add(nuevo);
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
        String anticipoTexto = binding.etAnticipo.getText().toString();
        String errorAnticipo = Validaciones.importe(anticipoTexto, total);
        Double escrito = Formato.leerNumero(anticipoTexto);
        if (escrito != null && escrito > total) {
            errorAnticipo = "No puede ser mayor que el total (" + Formato.moneda(total) + ")";
        }
        if (errorAnticipo != null) {
            Campos.error(binding.etAnticipo, errorAnticipo);
            return;
        }
        double anticipo = anticipo();
        boolean conPaciente = binding.spPaciente.getSelectedItemPosition() > 0;

        // Un saldo pendiente necesita a quién cobrárselo: no se permite con "Público en general"
        if (!conPaciente && anticipo < total) {
            Campos.error(binding.etAnticipo, "Para dejar saldo pendiente elige un paciente registrado");
            return;
        }
        boolean conOrden = binding.swOrden.isChecked();
        String trabajo = binding.etOrdenDescripcion.getText().toString().trim().replaceAll("\\s+", " ");
        if (conOrden) {
            // La orden de laboratorio se entrega a un paciente: debe estar registrado
            if (!conPaciente) {
                mensaje("Para mandar una orden a laboratorio elige un paciente registrado");
                return;
            }
            String errorTrabajo = Validaciones.textoLibre(trabajo, true, 5, Validaciones.LARGO_TRABAJO,
                    "el trabajo para el laboratorio");
            if (errorTrabajo != null) {
                Campos.error(binding.etOrdenDescripcion, errorTrabajo);
                return;
            }
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
        venta.saldo = Math.round((total - anticipo) * 100) / 100.0;   // sin residuos de decimales
        venta.metodoPago = metodoDePago();
        venta.vendedorId = Sesion.getUsuario().id;
        venta.vendedorNombre = Sesion.getUsuario().nombre;
        venta.fecha = new Date();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch lote = db.batch();
        DocumentReference ventaRef = Sesion.coleccion("ventas").document();
        lote.set(ventaRef, venta);
        Map<String, Integer> existencias = new HashMap<>();   // para el historial: existencias antes de cada renglón
        for (Producto p : productosDisponibles) existencias.put(p.id, p.stock);
        for (ItemVenta i : venta.items) {
            // Resta las piezas vendidas directamente en el servidor
            lote.update(Sesion.coleccion("productos").document(i.productoId),
                    "stock", FieldValue.increment(-i.cantidad));
            // Y deja el registro en el historial del producto, ligado a esta venta
            Integer antes = existencias.get(i.productoId);
            int previas = antes != null ? antes : 0;
            Historial.registrar(lote, i.productoId, Movimiento.VENTA,
                    "Venta de " + i.cantidad + " × " + i.nombre + " a " + Formato.moneda(i.precio)
                            + " · cliente: " + venta.pacienteNombre,
                    -i.cantidad, previas, ventaRef.getId());
            existencias.put(i.productoId, previas - i.cantidad);
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
            // Queda registrada en Firebase (opticas/{id}/documentos) con el PDF incluido
            Documentos.registrar(pdf, Documento.NOTA_VENTA, "Nota de venta · " + Formato.moneda(venta.total),
                    venta.id, venta.pacienteNombre);
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
