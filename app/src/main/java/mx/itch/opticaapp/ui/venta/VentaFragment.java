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
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Folios;
import mx.itch.opticaapp.data.Historial;
import mx.itch.opticaapp.data.Seguimientos;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentVentaBinding;
import mx.itch.opticaapp.model.ItemVenta;
import mx.itch.opticaapp.model.Movimiento;
import mx.itch.opticaapp.model.Orden;
import mx.itch.opticaapp.model.Paciente;
import mx.itch.opticaapp.model.Producto;
import mx.itch.opticaapp.model.Venta;
import mx.itch.opticaapp.util.AlEscribir;
import mx.itch.opticaapp.util.Animaciones;
import mx.itch.opticaapp.util.Campos;
import mx.itch.opticaapp.util.Fechas;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.Validaciones;

/**
 * Pantalla 7: punto de venta.
 * Al confirmar se reservan los folios (transacción en contadores/folios) y luego, en una sola
 * escritura (WriteBatch): se guarda la venta, se descuenta el stock de cada producto con
 * FieldValue.increment, se crea la orden de laboratorio si se pidió y el documento público de
 * seguimiento al que lleva el QR del ticket. Al terminar se abre la pantalla del ticket.
 */
public class VentaFragment extends Fragment {

    /** Si llega este argumento (desde el detalle de producto), ese producto se agrega solo al carrito. */
    public static final String ARG_PRODUCTO_ID = "productoId";
    /** Color y personalización elegidos en el detalle ("Gris Oxford · logo estampado"). */
    public static final String ARG_DETALLE = "detalle";
    /** Costo adicional por pieza (estampado del logotipo). */
    public static final String ARG_EXTRA = "extra";

    private static final String PUBLICO_GENERAL = "Público general";

    private static final String TAG_ENTREGA = "calendario_entrega";
    private static final String ESTADO_ENTREGA = "entrega";
    /** Días que se proponen para la entrega de una orden de laboratorio. */
    private static final int DIAS_ENTREGA = 7;

    /** Fecha estimada de entrega de la orden (null mientras no se activa el switch). */
    private Date entrega;

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
        if (savedInstanceState != null && savedInstanceState.getLong(ESTADO_ENTREGA, 0) > 0) {
            entrega = new Date(savedInstanceState.getLong(ESTADO_ENTREGA));
        }
        binding.swOrden.setOnCheckedChangeListener((boton, marcado) -> {
            Campos.mostrar(binding.etOrdenDescripcion, marcado);
            Campos.mostrar(binding.etEntrega, marcado);
            if (marcado && entrega == null) ponerEntrega(entregaPorOmision());
        });
        binding.etEntrega.setOnClickListener(v -> elegirEntrega());
        binding.tilEntrega.setEndIconOnClickListener(v -> elegirEntrega());
        // Si se giró la pantalla con el calendario abierto, se vuelve a conectar su botón
        Fragment abierto = getChildFragmentManager().findFragmentByTag(TAG_ENTREGA);
        if (abierto instanceof MaterialDatePicker) {
            @SuppressWarnings("unchecked")
            MaterialDatePicker<Long> calendario = (MaterialDatePicker<Long>) abierto;
            alElegirEntrega(calendario);
        }
        // "Efectivo recibido" solo tiene sentido en efectivo
        binding.rgMetodo.setOnCheckedChangeListener((grupo, id) -> {
            Campos.mostrar(binding.etRecibido, id == R.id.rbEfectivo);
            actualizarTotales();
        });
        binding.etRecibido.addTextChangedListener(new AlEscribir(texto -> actualizarTotales()));
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

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (entrega != null) outState.putLong(ESTADO_ENTREGA, entrega.getTime());
    }

    private String texto(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    // ---------------- Entrega estimada ----------------

    private Date entregaPorOmision() {
        Calendar c = Calendar.getInstance();
        c.setTime(Fechas.inicioDelDia());
        c.add(Calendar.DAY_OF_MONTH, DIAS_ENTREGA);
        return c.getTime();
    }

    private void ponerEntrega(Date dia) {
        entrega = dia;
        binding.etEntrega.setText(Fechas.diaConNombre(dia));
        Campos.error(binding.etEntrega, null);
    }

    /** Calendario de Material que solo deja elegir de hoy en adelante. */
    private void elegirEntrega() {
        FragmentManager fm = getChildFragmentManager();
        if (fm.isStateSaved() || fm.findFragmentByTag(TAG_ENTREGA) != null) return;
        long hoy = Fechas.paraSelector(new Date());
        CalendarConstraints limites = new CalendarConstraints.Builder()
                .setStart(hoy)
                .setValidator(DateValidatorPointForward.from(hoy))
                .build();
        MaterialDatePicker<Long> calendario = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Entrega estimada")
                .setSelection(Fechas.paraSelector(entrega != null ? entrega : entregaPorOmision()))
                .setCalendarConstraints(limites)
                .setPositiveButtonText("Listo")
                .setNegativeButtonText("Cancelar")
                .build();
        alElegirEntrega(calendario);
        calendario.show(fm, TAG_ENTREGA);
    }

    private void alElegirEntrega(MaterialDatePicker<Long> calendario) {
        calendario.addOnPositiveButtonClickListener(seleccion -> {
            if (seleccion == null || binding == null) return;
            ponerEntrega(Fechas.desdeSelector(seleccion));
        });
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
        // Cambio en vivo debajo de "Efectivo recibido"
        Double recibido = Formato.leerNumero(binding.etRecibido.getText().toString());
        if (anticipo != null && recibido != null && anticipo > 0 && recibido >= anticipo) {
            binding.tilRecibido.setHelperText("Cambio: " + Formato.moneda(recibido - anticipo));
        } else {
            binding.tilRecibido.setHelperText("Para calcular el cambio del ticket");
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

        // Efectivo recibido (opcional): no puede ser menos de lo que se paga ahora
        double recibido = 0;
        String recibidoTexto = binding.etRecibido.getText().toString().trim();
        if (binding.rgMetodo.getCheckedRadioButtonId() == R.id.rbEfectivo && !recibidoTexto.isEmpty()) {
            String errorRecibido = Validaciones.importe(recibidoTexto, 1_000_000);
            Double leido = Formato.leerNumero(recibidoTexto);
            if (errorRecibido == null && leido != null && leido < anticipo) {
                errorRecibido = "Es menos de lo que paga ahora (" + Formato.moneda(anticipo) + ")";
            }
            if (errorRecibido != null || leido == null) {
                Campos.error(binding.etRecibido, errorRecibido != null ? errorRecibido : "Solo números");
                return;
            }
            Campos.error(binding.etRecibido, null);
            recibido = leido;
        }

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
            if (entrega == null) {
                Campos.error(binding.etEntrega, "Elige cuándo estarán listos");
                return;
            }
        }

        Venta venta = new Venta();
        String telefono = "";
        int posicionCliente = binding.spPaciente.getSelectedItemPosition();
        if (posicionCliente > 0 && posicionCliente - 1 < pacientes.size()) {
            Paciente p = pacientes.get(posicionCliente - 1);
            venta.pacienteId = p.id;
            venta.pacienteNombre = p.nombre;
            telefono = p.telefono != null ? p.telefono : "";
        } else {
            venta.pacienteId = "";
            venta.pacienteNombre = PUBLICO_GENERAL;
        }
        venta.items = new ArrayList<>(carrito);
        venta.total = total;
        venta.anticipo = anticipo;
        venta.saldo = Math.round((total - anticipo) * 100) / 100.0;   // sin residuos de decimales
        venta.metodoPago = metodoDePago();
        venta.recibido = recibido;
        venta.cambio = recibido > 0 ? Math.round((recibido - anticipo) * 100) / 100.0 : 0;
        venta.vendedorId = Sesion.getUsuario().id;
        venta.vendedorNombre = Sesion.getUsuario().nombre;
        venta.fecha = new Date();
        venta.ordenFolio = "";
        venta.ordenDescripcion = "";
        venta.seguimiento = "";

        Orden orden = null;
        if (conOrden) {
            orden = new Orden();
            orden.pacienteId = venta.pacienteId;
            orden.pacienteNombre = venta.pacienteNombre;
            orden.descripcion = trabajo;
            orden.estatus = Orden.ENVIADO;
            orden.fechaCreacion = venta.fecha;
            orden.fechaEnvio = venta.fecha;
            orden.fechaEntrega = entrega;
            venta.ordenDescripcion = trabajo;
            venta.fechaEntrega = entrega;
        }

        // Existencias de cada producto antes de la venta (para el historial), tomadas ahora y no
        // al terminar de reservar el folio, por si mientras tanto llega otra lectura de Firestore
        Map<String, Integer> existencias = new HashMap<>();
        for (Producto p : productosDisponibles) existencias.put(p.id, p.stock);

        mostrarCargando(true);
        Orden ordenFinal = orden;
        String telefonoFinal = telefono;
        Folios.reservar(true, conOrden).addOnCompleteListener(t ->
                guardarVenta(venta, ordenFinal, t.isSuccessful() ? t.getResult() : null, existencias, telefonoFinal));
    }

    /**
     * Segunda parte del cobro, ya con los folios. Si no se pudieron reservar (sin conexión),
     * se usa un folio provisional y la venta se guarda igual: Firestore la sube al volver la red.
     */
    private void guardarVenta(Venta venta, @Nullable Orden orden, @Nullable long[] folios,
                              Map<String, Integer> existencias, String telefono) {
        DocumentReference ventaRef = Sesion.coleccion("ventas").document();
        venta.folio = folios != null ? Folios.venta(folios[0]) : Folios.provisional("V", ventaRef.getId());

        WriteBatch lote = FirebaseFirestore.getInstance().batch();
        DocumentReference ordenRef = null;
        if (orden != null) {
            ordenRef = Sesion.coleccion("ordenes").document();
            orden.folio = folios != null ? Folios.orden(folios[1]) : Folios.provisional("L", ordenRef.getId());
            orden.ventaId = ventaRef.getId();
            orden.ventaFolio = venta.folio;
            venta.ordenFolio = orden.folio;
        }
        // Documento público del QR: solo si hay algo que seguir (orden de laboratorio o saldo)
        DocumentReference seguimientoRef = null;
        if (orden != null || venta.saldo > 0) {
            seguimientoRef = Seguimientos.nuevo();
            venta.seguimiento = seguimientoRef.getId();
            if (orden != null) orden.seguimiento = venta.seguimiento;
        }
        if (orden != null) lote.set(ordenRef, orden);
        lote.set(ventaRef, venta);

        for (ItemVenta i : venta.items) {
            // Resta las piezas vendidas directamente en el servidor
            lote.update(Sesion.coleccion("productos").document(i.productoId),
                    "stock", FieldValue.increment(-i.cantidad));
            // Y deja el registro en el historial del producto, ligado a esta venta
            Integer antes = existencias.get(i.productoId);
            int previas = antes != null ? antes : 0;
            Historial.registrar(lote, i.productoId, Movimiento.VENTA,
                    "Venta " + venta.folio + " de " + i.cantidad + " × " + i.nombre + " a " + Formato.moneda(i.precio)
                            + " · cliente: " + venta.pacienteNombre,
                    -i.cantidad, previas, ventaRef.getId());
            existencias.put(i.productoId, previas - i.cantidad);
        }

        lote.commit()
                .addOnSuccessListener(x -> {
                    venta.id = ventaRef.getId();
                    mostrarCargando(false);
                    limpiar();
                    abrirTicket(venta, telefono);
                })
                .addOnFailureListener(e -> {
                    mostrarCargando(false);
                    mensaje("No se pudo registrar la venta: " + e.getMessage());
                });
        // Aparte del lote: si falla, la venta se guarda igual (ver Seguimientos)
        if (seguimientoRef != null) {
            Seguimientos.publicar(seguimientoRef, Seguimientos.desde(venta, orden, Sesion.getOptica()));
        }
    }

    /** Texto del método elegido en el control segmentado. */
    private String metodoDePago() {
        int id = binding.rgMetodo.getCheckedRadioButtonId();
        if (id == R.id.rbTarjeta) return "Tarjeta";
        if (id == R.id.rbTransferencia) return "Transferencia";
        return "Efectivo";
    }

    /** Pantalla del ticket (si el usuario sigue en Punto de venta). */
    private void abrirTicket(Venta venta, String telefono) {
        if (!isAdded()) return;
        NavController nav = NavHostFragment.findNavController(this);
        if (nav.getCurrentDestination() == null || nav.getCurrentDestination().getId() != R.id.ventaFragment) return;
        Bundle args = new Bundle();
        args.putSerializable(VentaRegistradaFragment.ARG_VENTA, venta);
        args.putString(VentaRegistradaFragment.ARG_TELEFONO, telefono);
        nav.navigate(R.id.action_venta_to_registrada, args);
    }

    private void limpiar() {
        if (binding == null) return;
        carrito.clear();
        adaptadorCarrito.notifyDataSetChanged();
        binding.etAnticipo.setText("");
        binding.etRecibido.setText("");
        binding.swOrden.setChecked(false);
        binding.etOrdenDescripcion.setText("");
        binding.etEntrega.setText("");
        entrega = null;
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
