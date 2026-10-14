package mx.itch.opticaapp.ui.panel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import mx.itch.opticaapp.MainActivity;
import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentPanelBinding;
import mx.itch.opticaapp.model.Optica;
import mx.itch.opticaapp.model.Orden;
import mx.itch.opticaapp.model.Producto;
import mx.itch.opticaapp.model.Usuario;
import mx.itch.opticaapp.util.Animaciones;
import mx.itch.opticaapp.util.Fechas;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.Imagenes;

/**
 * Pantalla 3: resumen del negocio. Los datos se actualizan solos (listeners en tiempo real).
 * Avance semanal = ventas de lunes a hoy ÷ meta semanal × 100.
 */
public class PanelFragment extends Fragment {

    private FragmentPanelBinding binding;
    /** Listeners activos; se quitan al salir de la pantalla. */
    private final List<ListenerRegistration> escuchas = new ArrayList<>();

    // Datos para el botón de avisos (campana)
    private int productosConStockBajo = 0;
    private int ordenesListasParaEntregar = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPanelBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!Sesion.estaActiva()) return;

        Usuario u = Sesion.getUsuario();
        Optica o = Sesion.getOptica();
        binding.tvSaludo.setText(saludo());
        binding.tvNombre.setText(u.nombre);
        binding.tvOptica.setText(o.nombre + " · " + Usuario.nombreRol(u.rol));
        Imagenes.mostrar(binding.ivAvatar, u.fotoUrl, R.drawable.ic_perfil);

        MainActivity actividad = (MainActivity) requireActivity();
        binding.tarjetaMenu.setOnClickListener(v -> actividad.abrirMenu());
        binding.tarjetaPerfil.setOnClickListener(v -> actividad.abrirPerfil());
        binding.tarjetaAvisos.setOnClickListener(v -> mostrarAvisos());

        // Cada rol ve los indicadores que le sirven
        boolean venta = Sesion.esAdmin() || Sesion.esVendedor();
        boolean clinica = Sesion.esAdmin() || Sesion.esOptometrista();
        binding.layoutAvance.setVisibility(venta ? View.VISIBLE : View.GONE);
        binding.tileVentas.setVisibility(venta ? View.VISIBLE : View.GONE);
        binding.tileStock.setVisibility(venta ? View.VISIBLE : View.GONE);
        binding.tileMeta.setVisibility(venta ? View.VISIBLE : View.GONE);
        binding.tilePacientes.setVisibility(clinica ? View.VISIBLE : View.GONE);

        // Al tocar un indicador se abre su sección
        binding.tileVentas.setOnClickListener(v -> actividad.irASeccion(R.id.ventaFragment));
        binding.tilePacientes.setOnClickListener(v -> actividad.irASeccion(R.id.pacientesFragment));
        binding.tileOrdenes.setOnClickListener(v -> actividad.irASeccion(R.id.laboratorioFragment));
        binding.tileStock.setOnClickListener(v -> actividad.irASeccion(R.id.inventarioFragment));
        binding.tileMeta.setOnClickListener(v -> actividad.abrirPerfil());

        // Acción rápida (círculo azul): nueva venta, o pacientes para el optometrista
        if (venta) {
            binding.ivAccion.setImageResource(R.drawable.ic_ventas);
            binding.tvAccion.setText("Nueva");
            binding.tvAccionEtiqueta.setText("venta");
            binding.tileAccion.setOnClickListener(v -> actividad.irASeccion(R.id.ventaFragment));
        } else {
            binding.ivAccion.setImageResource(R.drawable.ic_pacientes);
            binding.tvAccion.setText("Ver");
            binding.tvAccionEtiqueta.setText("expedientes");
            binding.tileAccion.setOnClickListener(v -> actividad.irASeccion(R.id.pacientesFragment));
        }
        binding.tvMeta.setText(o.metaSemanal > 0 ? Formato.moneda(o.metaSemanal) : "Sin meta");

        // Animación de entrada: íconos y saludo en cascada, la tarjeta de avance crece,
        // la hoja sube y los mosaicos aparecen uno tras otro
        if (Animaciones.entrada(this, binding.encabezado, binding.hoja)) {
            float dp = getResources().getDisplayMetrics().density;
            Animaciones.cascada(binding.barraIconos, -12 * dp, 0);
            Animaciones.aparecer(binding.layoutAvance, 180);
            Animaciones.aparecerEnCascada(Arrays.asList(binding.tileVentas, binding.tilePacientes,
                    binding.tileOrdenes, binding.tileStock, binding.tileMeta, binding.tileAccion), 320);
        }
    }

    private String saludo() {
        int hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hora < 12) return "¡Buenos días!";
        if (hora < 19) return "¡Buenas tardes!";
        return "¡Buenas noches!";
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!Sesion.estaActiva()) return;
        if (Sesion.esAdmin() || Sesion.esVendedor()) {
            escucharVentas();
            escucharStock();
        }
        if (Sesion.esAdmin() || Sesion.esOptometrista()) {
            escucharPacientesDeHoy();
        }
        escucharOrdenes();
    }

    @Override
    public void onStop() {
        super.onStop();
        for (ListenerRegistration r : escuchas) {
            r.remove();
        }
        escuchas.clear();
    }

    /** Ventas desde el lunes: calcula el avance semanal y las ventas de hoy. */
    private void escucharVentas() {
        Date lunes = Fechas.inicioDeSemana();
        Date hoy = Fechas.inicioDelDia();
        escuchas.add(Sesion.coleccion("ventas")
                .whereGreaterThanOrEqualTo("fecha", lunes)
                .addSnapshotListener((resultado, error) -> {
                    if (binding == null || resultado == null) return;
                    double semana = 0;
                    double deHoy = 0;
                    for (DocumentSnapshot d : resultado.getDocuments()) {
                        Double total = d.getDouble("total");
                        Date fecha = d.getDate("fecha");
                        if (total == null) continue;
                        semana += total;
                        if (fecha != null && !fecha.before(hoy)) {
                            deHoy += total;
                        }
                    }
                    mostrarAvance(semana);
                    binding.tvVentasHoy.setText(Formato.moneda(deHoy));
                }));
    }

    private void mostrarAvance(double vendidoEnLaSemana) {
        double meta = Sesion.getOptica().metaSemanal;
        if (meta <= 0) {
            binding.tvAvancePorcentaje.setText("—");
            binding.barraAvance.setProgressCompat(0, true);
            binding.tvAvanceMonto.setText(Formato.moneda(vendidoEnLaSemana) + " vendidos esta semana");
            binding.tvFaltante.setText("Define una meta semanal en Perfil y ajustes");
            return;
        }
        double porcentaje = vendidoEnLaSemana / meta * 100;
        binding.tvAvancePorcentaje.setText(String.format(Locale.US, "%.0f %%", porcentaje));
        // setProgressCompat(..., true) hace que la barra se llene con animación
        binding.barraAvance.setProgressCompat((int) Math.min(100, Math.round(porcentaje)), true);
        binding.tvAvanceMonto.setText(Formato.moneda(vendidoEnLaSemana) + " de " + Formato.moneda(meta));
        double faltante = meta - vendidoEnLaSemana;
        if (faltante > 0) {
            binding.tvFaltante.setText("Faltan " + Formato.moneda(faltante) + " · quedan "
                    + Fechas.diasRestantesDeSemana() + " días");
        } else {
            binding.tvFaltante.setText("¡Meta cumplida!");
        }
    }

    /** Pacientes cuya última consulta fue hoy. */
    private void escucharPacientesDeHoy() {
        escuchas.add(Sesion.coleccion("pacientes")
                .whereGreaterThanOrEqualTo("ultimaConsulta", Fechas.inicioDelDia())
                .addSnapshotListener((resultado, error) -> {
                    if (binding == null || resultado == null) return;
                    binding.tvPacientesHoy.setText(String.valueOf(resultado.size()));
                }));
    }

    /** Órdenes que todavía no se entregan; cuenta aparte las que ya llegaron del laboratorio. */
    private void escucharOrdenes() {
        escuchas.add(Sesion.coleccion("ordenes")
                .whereIn("estatus", Arrays.asList(Orden.ENVIADO, Orden.EN_PROCESO, Orden.RECIBIDO))
                .addSnapshotListener((resultado, error) -> {
                    if (binding == null || resultado == null) return;
                    int recibidas = 0;
                    for (DocumentSnapshot d : resultado.getDocuments()) {
                        if (Orden.RECIBIDO.equals(d.getString("estatus"))) recibidas++;
                    }
                    ordenesListasParaEntregar = recibidas;
                    binding.tvOrdenes.setText(String.valueOf(resultado.size()));
                    actualizarPuntoDeAvisos();
                }));
    }

    /** Productos con existencias en el mínimo o agotados (se compara en el teléfono). */
    private void escucharStock() {
        escuchas.add(Sesion.coleccion("productos")
                .addSnapshotListener((resultado, error) -> {
                    if (binding == null || resultado == null) return;
                    int bajos = 0;
                    for (Producto p : resultado.toObjects(Producto.class)) {
                        if (p.stock <= p.stockMinimo) bajos++;
                    }
                    productosConStockBajo = bajos;
                    binding.tvStockBajo.setText(String.valueOf(bajos));
                    actualizarPuntoDeAvisos();
                }));
    }

    /** El punto rojo de la campana aparece si hay algo pendiente. */
    private void actualizarPuntoDeAvisos() {
        boolean hayAvisos = productosConStockBajo > 0 || ordenesListasParaEntregar > 0;
        binding.puntoAvisos.setVisibility(hayAvisos ? View.VISIBLE : View.GONE);
    }

    private void mostrarAvisos() {
        StringBuilder texto = new StringBuilder();
        if (ordenesListasParaEntregar > 0) {
            texto.append("• ").append(ordenesListasParaEntregar)
                    .append(" orden(es) ya llegaron del laboratorio y están listas para entregar.\n");
        }
        if (productosConStockBajo > 0 && (Sesion.esAdmin() || Sesion.esVendedor())) {
            texto.append("• ").append(productosConStockBajo)
                    .append(" producto(s) con stock bajo o agotados.\n");
        }
        if (texto.length() == 0) {
            texto.append("No hay avisos por ahora.");
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Avisos")
                .setMessage(texto.toString().trim())
                .setPositiveButton("Entendido", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
