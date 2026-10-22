package mx.itch.opticaapp.ui.administracion;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.util.Date;
import java.util.List;
import java.util.Map;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.HojaCorteBinding;
import mx.itch.opticaapp.databinding.ItemMetodoCorteBinding;
import mx.itch.opticaapp.databinding.ItemVentaCorteBinding;
import mx.itch.opticaapp.model.Corte;
import mx.itch.opticaapp.model.ItemVenta;
import mx.itch.opticaapp.model.Usuario;
import mx.itch.opticaapp.model.Venta;
import mx.itch.opticaapp.util.Aviso;
import mx.itch.opticaapp.util.Fechas;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.HojaOpciones;

/**
 * Administración · Corte de caja, en una hoja inferior de Material.
 *
 * Muestra el resumen de un día (cuántas ventas, total vendido, dinero cobrado por método de pago
 * y saldo pendiente) y la lista de esas ventas. El día se cambia con el selector de fechas de
 * Material (MaterialDatePicker), que solo deja elegir hoy o días anteriores.
 *
 * "Guardar corte" lo registra en /opticas/{opticaId}/cortes con el día, quién lo hizo y la hora
 * del servidor; "Cortes anteriores" lista los últimos guardados y al tocar uno muestra ese día.
 *
 * Es un BottomSheetDialogFragment: si se gira la pantalla, la hoja y el día elegido se conservan.
 */
public class CorteDeCaja extends BottomSheetDialogFragment {

    private static final String TAG = "corteDeCaja";
    private static final String TAG_CALENDARIO = "calendarioCorte";
    private static final String ESTADO_DIA = "dia";

    private HojaCorteBinding binding;
    /** 00:00 del día que se está mostrando. */
    private Date dia;
    /** Resumen calculado del día; null mientras se carga. */
    private Corte corte;
    /** Cortes ya guardados de este día (para avisar antes de guardar otro). */
    private int cortesDelDia;
    /** Número de consulta: si se cambia rápido de día, se ignoran las respuestas viejas. */
    private int consulta;

    /** Lo llama MainActivity desde el menú lateral (Administración · Corte de caja). */
    public static void mostrar(FragmentActivity actividad) {
        if (!Sesion.estaActiva() || !Sesion.esAdmin()) return;
        FragmentManager fm = actividad.getSupportFragmentManager();
        if (fm.isStateSaved() || fm.findFragmentByTag(TAG) != null) return;
        new CorteDeCaja().show(fm, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog hoja = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        HojaOpciones.abrirCompleta(hoja);
        return hoja;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = HojaCorteBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!Sesion.estaActiva()) {
            dismissAllowingStateLoss();
            return;
        }
        long guardado = savedInstanceState != null ? savedInstanceState.getLong(ESTADO_DIA, 0) : 0;
        dia = guardado > 0 ? new Date(guardado) : Fechas.inicioDelDia();

        binding.chipDia.setOnClickListener(v -> elegirDia());
        binding.chipDia.setOnCloseIconClickListener(v -> elegirDia());
        binding.btnGuardarCorte.setOnClickListener(v -> confirmarGuardado());
        binding.btnCortesAnteriores.setOnClickListener(v -> mostrarAnteriores());

        // Si se giró la pantalla con el calendario abierto, se vuelve a conectar su botón "Ver corte"
        Fragment abierto = getChildFragmentManager().findFragmentByTag(TAG_CALENDARIO);
        if (abierto instanceof MaterialDatePicker) {
            @SuppressWarnings("unchecked")
            MaterialDatePicker<Long> calendario = (MaterialDatePicker<Long>) abierto;
            alElegirDia(calendario);
        }
        cargar();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (dia != null) outState.putLong(ESTADO_DIA, dia.getTime());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ---------------- Selector de fechas ----------------

    private void elegirDia() {
        FragmentManager fm = getChildFragmentManager();
        if (fm.isStateSaved() || fm.findFragmentByTag(TAG_CALENDARIO) != null) return;
        // El calendario usa medianoche UTC; "hoy" se calcula con la fecha del teléfono para que
        // en la noche (cuando en UTC ya es mañana) no se pueda elegir el día siguiente.
        long hoy = Fechas.paraSelector(new Date());
        CalendarConstraints limites = new CalendarConstraints.Builder()
                .setEnd(hoy)
                .setValidator(DateValidatorPointBackward.before(hoy))
                .build();
        MaterialDatePicker<Long> calendario = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Día del corte")
                .setSelection(Fechas.paraSelector(dia))
                .setCalendarConstraints(limites)
                .setPositiveButtonText("Ver corte")
                .setNegativeButtonText("Cancelar")
                .build();
        alElegirDia(calendario);
        calendario.show(fm, TAG_CALENDARIO);
    }

    private void alElegirDia(MaterialDatePicker<Long> calendario) {
        calendario.addOnPositiveButtonClickListener(seleccion -> {
            if (seleccion == null || binding == null) return;
            dia = Fechas.desdeSelector(seleccion);
            cargar();
        });
    }

    // ---------------- Datos del día ----------------

    /** Lee de Firestore las ventas del día elegido (de 00:00 a 00:00 del día siguiente). */
    private void cargar() {
        int esta = ++consulta;
        corte = null;
        binding.chipDia.setText(Fechas.diaConNombre(dia));
        binding.progresoCorte.setVisibility(View.VISIBLE);
        binding.btnGuardarCorte.setEnabled(false);
        binding.tvCorteGuardado.setVisibility(View.GONE);

        // Rango y orden sobre el mismo campo: no necesita índice compuesto
        Sesion.coleccion("ventas")
                .whereGreaterThanOrEqualTo("fecha", dia)
                .whereLessThan("fecha", Fechas.diaSiguiente(dia))
                .orderBy("fecha", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(resultado -> {
                    if (binding == null || esta != consulta) return;
                    List<DocumentSnapshot> ventas = resultado.getDocuments();
                    corte = calcular(ventas, dia);
                    mostrarResumen(corte);
                    mostrarVentas(ventas);
                    binding.progresoCorte.setVisibility(View.GONE);
                    binding.btnGuardarCorte.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    if (binding == null || esta != consulta) return;
                    binding.progresoCorte.setVisibility(View.GONE);
                    mensaje("No se pudo calcular el corte: " + e.getMessage());
                });
        buscarCorteGuardado(esta);
    }

    /** Suma las ventas del día. */
    private static Corte calcular(List<DocumentSnapshot> ventas, Date dia) {
        Corte c = new Corte();
        c.dia = Fechas.clave(dia);
        c.ventas = ventas.size();
        for (DocumentSnapshot d : ventas) {
            Double total = d.getDouble("total");
            Double anticipo = d.getDouble("anticipo");
            Double saldo = d.getDouble("saldo");
            String metodo = d.getString("metodoPago");
            if (total != null) c.totalVendido += total;
            if (saldo != null) c.saldoPendiente += saldo;
            if (anticipo != null) {
                c.cobrado += anticipo;
                String clave = metodo != null ? metodo : "Otro";
                Double previo = c.porMetodo.get(clave);
                c.porMetodo.put(clave, (previo != null ? previo : 0) + anticipo);
            }
        }
        return c;
    }

    private void mostrarResumen(Corte c) {
        binding.tvVentasCorte.setText(String.valueOf(c.ventas));
        binding.tvTotalCorte.setText(Formato.moneda(c.totalVendido));
        binding.tvCobradoCorte.setText(Formato.moneda(c.cobrado));
        binding.tvSaldoCorte.setText(Formato.moneda(c.saldoPendiente));

        binding.contenedorMetodos.removeAllViews();
        for (Map.Entry<String, Double> e : c.porMetodo.entrySet()) {
            ItemMetodoCorteBinding metodo = ItemMetodoCorteBinding.inflate(
                    getLayoutInflater(), binding.contenedorMetodos, false);
            metodo.tvMetodo.setText(e.getKey());
            metodo.tvMontoMetodo.setText(Formato.moneda(e.getValue()));
            binding.contenedorMetodos.addView(metodo.getRoot());
        }
        boolean hayMetodos = !c.porMetodo.isEmpty();
        binding.tvTituloMetodos.setVisibility(hayMetodos ? View.VISIBLE : View.GONE);
        binding.contenedorMetodos.setVisibility(hayMetodos ? View.VISIBLE : View.GONE);
    }

    /** Renglón por venta: hora, cliente, método, piezas, saldo y total. */
    private void mostrarVentas(List<DocumentSnapshot> ventas) {
        binding.contenedorVentas.removeAllViews();
        binding.tvSinVentas.setVisibility(ventas.isEmpty() ? View.VISIBLE : View.GONE);
        for (DocumentSnapshot d : ventas) {
            Venta v = d.toObject(Venta.class);
            if (v == null) continue;
            int piezas = 0;
            if (v.items != null) {
                for (ItemVenta i : v.items) piezas += i.cantidad;
            }
            StringBuilder detalle = new StringBuilder(v.metodoPago != null ? v.metodoPago : "Otro");
            detalle.append(" · ").append(piezas).append(piezas == 1 ? " pieza" : " piezas");
            if (v.saldo > 0) detalle.append(" · Debe ").append(Formato.moneda(v.saldo));

            ItemVentaCorteBinding fila = ItemVentaCorteBinding.inflate(
                    getLayoutInflater(), binding.contenedorVentas, false);
            fila.tvHoraVenta.setText(Fechas.hora(v.fecha));
            fila.tvClienteVenta.setText(v.pacienteNombre != null && !v.pacienteNombre.isEmpty()
                    ? v.pacienteNombre : "Público en general");
            fila.tvDetalleVenta.setText(detalle);
            fila.tvTotalVenta.setText(Formato.moneda(v.total));
            binding.contenedorVentas.addView(fila.getRoot());
        }
    }

    /** Si ya hay cortes guardados de este día, muestra el más reciente (hora y quién lo hizo). */
    private void buscarCorteGuardado(int esta) {
        Sesion.coleccion("cortes")
                .whereEqualTo("dia", Fechas.clave(dia))
                .get()
                .addOnSuccessListener(resultado -> {
                    if (binding == null || esta != consulta) return;
                    List<Corte> cortes = resultado.toObjects(Corte.class);
                    cortesDelDia = cortes.size();
                    Corte ultimo = null;
                    for (Corte c : cortes) {
                        if (ultimo == null || (c.fecha != null && (ultimo.fecha == null || c.fecha.after(ultimo.fecha)))) {
                            ultimo = c;
                        }
                    }
                    if (ultimo == null) {
                        binding.tvCorteGuardado.setVisibility(View.GONE);
                        return;
                    }
                    String texto = "Corte guardado"
                            + (ultimo.fecha != null ? " el " + Fechas.conHora(ultimo.fecha) : "")
                            + " por " + ultimo.realizadoPorNombre
                            + (cortesDelDia > 1 ? " (" + cortesDelDia + " cortes de este día)" : "");
                    binding.tvCorteGuardado.setText(texto);
                    binding.tvCorteGuardado.setVisibility(View.VISIBLE);
                });
    }

    // ---------------- Guardar y consultar cortes ----------------

    /** Si ya hay un corte de este día (por ejemplo de otro turno), pregunta antes de guardar otro. */
    private void confirmarGuardado() {
        if (corte == null) return;
        if (cortesDelDia == 0) {
            guardar();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Ya hay un corte de este día")
                .setMessage("¿Guardar otro corte del " + Fechas.corta(dia) + "? Se conservan los dos.")
                .setPositiveButton("Guardar otro", (d, w) -> guardar())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Guarda el corte en Firestore; la fecha la pone el servidor (@ServerTimestamp). */
    private void guardar() {
        if (corte == null || !Sesion.estaActiva()) return;
        Usuario u = Sesion.getUsuario();
        corte.realizadoPorId = u.id;
        corte.realizadoPorNombre = u.nombre;
        binding.btnGuardarCorte.setEnabled(false);
        Activity actividad = requireActivity();
        Sesion.coleccion("cortes").add(corte)
                .addOnSuccessListener(x -> {
                    Aviso.mostrar(actividad, "Corte de caja guardado");
                    if (isAdded()) dismissAllowingStateLoss();
                })
                .addOnFailureListener(e -> {
                    if (binding != null) binding.btnGuardarCorte.setEnabled(true);
                    mensaje("No se pudo guardar el corte: " + e.getMessage());
                });
    }

    /** Hoja con los últimos 15 cortes guardados; al tocar uno se muestra ese día. */
    private void mostrarAnteriores() {
        Sesion.coleccion("cortes")
                .orderBy("fecha", Query.Direction.DESCENDING)
                .limit(15)
                .get()
                .addOnSuccessListener(resultado -> {
                    if (binding == null || !isAdded()) return;
                    List<Corte> cortes = resultado.toObjects(Corte.class);
                    if (cortes.isEmpty()) {
                        mensaje("Todavía no hay cortes guardados");
                        return;
                    }
                    HojaOpciones hoja = HojaOpciones.con(requireContext(), "Cortes anteriores")
                            .subtitulo("Los últimos " + cortes.size() + " guardados · toca uno para ver ese día");
                    for (Corte c : cortes) {
                        Date diaDelCorte = Fechas.desdeClave(c.dia);
                        String titulo = diaDelCorte != null ? Fechas.diaConNombre(diaDelCorte) : c.dia;
                        String detalle = c.ventas + (c.ventas == 1 ? " venta" : " ventas")
                                + " · vendido " + Formato.moneda(c.totalVendido)
                                + " · cobrado " + Formato.moneda(c.cobrado)
                                + "\n" + (c.fecha != null ? Fechas.conHora(c.fecha) + " · " : "") + c.realizadoPorNombre;
                        hoja.opcion(R.drawable.ic_recibo, titulo, detalle, () -> {
                            if (binding == null || diaDelCorte == null) return;
                            dia = diaDelCorte;
                            cargar();
                        });
                    }
                    hoja.mostrar();
                })
                .addOnFailureListener(e -> mensaje("No se pudieron leer los cortes: " + e.getMessage()));
    }

    private void mensaje(String texto) {
        if (getContext() != null) Toast.makeText(getContext(), texto, Toast.LENGTH_LONG).show();
    }
}
