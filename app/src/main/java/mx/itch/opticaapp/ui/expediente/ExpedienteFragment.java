package mx.itch.opticaapp.ui.expediente;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.firestore.DocumentReference;
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
import mx.itch.opticaapp.databinding.FragmentExpedienteBinding;
import mx.itch.opticaapp.model.Consulta;
import mx.itch.opticaapp.model.Paciente;
import mx.itch.opticaapp.util.Fechas;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.Pdf;
import mx.itch.opticaapp.util.Validaciones;

/**
 * Pantalla 5: datos del paciente, captura de graduación con validaciones,
 * historial de consultas y receta en PDF.
 * Recibe el id del paciente en los argumentos (Bundle) desde PacientesFragment.
 */
public class ExpedienteFragment extends Fragment {

    public static final String ARG_PACIENTE_ID = "pacienteId";

    private FragmentExpedienteBinding binding;
    private String pacienteId;
    private Paciente paciente;
    private ConsultasAdapter adaptador;
    private ListenerRegistration escuchaPaciente;
    private ListenerRegistration escuchaConsultas;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExpedienteBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!Sesion.estaActiva()) return;
        pacienteId = getArguments() != null ? getArguments().getString(ARG_PACIENTE_ID) : null;

        ArrayAdapter<CharSequence> tipos = ArrayAdapter.createFromResource(requireContext(),
                R.array.tipos_lente, android.R.layout.simple_spinner_item);
        tipos.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spTipoLente.setAdapter(tipos);

        adaptador = new ConsultasAdapter(this::llenarFormulario);
        binding.rvHistorial.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvHistorial.setAdapter(adaptador);

        // El vendedor puede ver el expediente pero no modificarlo
        boolean puedeEditar = !Sesion.esVendedor();
        for (EditText campo : camposDelFormulario()) {
            campo.setEnabled(puedeEditar);
        }
        binding.spTipoLente.setEnabled(puedeEditar);
        binding.btnGuardar.setVisibility(puedeEditar ? View.VISIBLE : View.GONE);

        binding.btnGuardar.setOnClickListener(v -> guardar());
        binding.btnPdf.setOnClickListener(v -> generarReceta());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!Sesion.estaActiva() || pacienteId == null) return;
        DocumentReference ref = pacienteRef();

        escuchaPaciente = ref.addSnapshotListener((doc, error) -> {
            if (binding == null || doc == null) return;
            paciente = doc.toObject(Paciente.class);
            if (paciente == null) return;
            binding.tvIniciales.setText(Formato.iniciales(paciente.nombre));
            binding.tvPaciente.setText(paciente.nombre);
            String edad = paciente.edad > 0 ? paciente.edad + " años · " : "";
            String telefono = paciente.telefono == null || paciente.telefono.isEmpty() ? "" : "Tel. " + paciente.telefono + " · ";
            binding.tvDatosPaciente.setText(edad + telefono + "Paciente desde " + Fechas.corta(paciente.fechaRegistro));
        });

        escuchaConsultas = ref.collection("consultas").addSnapshotListener((resultado, error) -> {
            if (binding == null || resultado == null) return;
            List<Consulta> consultas = new ArrayList<>(resultado.toObjects(Consulta.class));
            // Más reciente primero
            Collections.sort(consultas, (a, b) -> {
                if (a.fecha == null || b.fecha == null) return 0;
                return b.fecha.compareTo(a.fecha);
            });
            adaptador.mostrar(consultas);
            binding.tvSinHistorial.setVisibility(consultas.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (escuchaPaciente != null) escuchaPaciente.remove();
        if (escuchaConsultas != null) escuchaConsultas.remove();
        escuchaPaciente = null;
        escuchaConsultas = null;
    }

    private DocumentReference pacienteRef() {
        return Sesion.coleccion("pacientes").document(pacienteId);
    }

    private EditText[] camposDelFormulario() {
        return new EditText[]{
                binding.etOdEsfera, binding.etOdCilindro, binding.etOdEje, binding.etOdAdicion,
                binding.etOiEsfera, binding.etOiCilindro, binding.etOiEje, binding.etOiAdicion,
                binding.etDistanciaPupilar, binding.etAgudezaVisual, binding.etObservaciones};
    }

    // ---------------- Formulario ----------------

    /**
     * Lee y valida el formulario. Si algo está mal marca el campo con error y devuelve null.
     */
    @Nullable
    private Consulta leerFormulario() {
        Double odEsf = Formato.leerNumero(binding.etOdEsfera.getText().toString());
        Double odCil = Formato.leerNumero(binding.etOdCilindro.getText().toString());
        Integer odEje = Formato.leerEntero(binding.etOdEje.getText().toString());
        Double odAdd = Formato.leerNumero(binding.etOdAdicion.getText().toString());
        Double oiEsf = Formato.leerNumero(binding.etOiEsfera.getText().toString());
        Double oiCil = Formato.leerNumero(binding.etOiCilindro.getText().toString());
        Integer oiEje = Formato.leerEntero(binding.etOiEje.getText().toString());
        Double oiAdd = Formato.leerNumero(binding.etOiAdicion.getText().toString());
        Double dip = Formato.leerNumero(binding.etDistanciaPupilar.getText().toString());

        boolean ok = marcar(binding.etOdEsfera, Validaciones.esfera(odEsf));
        ok &= marcar(binding.etOdCilindro, Validaciones.cilindro(odCil));
        ok &= marcar(binding.etOdEje, Validaciones.eje(odEje, odCil));
        ok &= marcar(binding.etOdAdicion, Validaciones.adicion(odAdd));
        ok &= marcar(binding.etOiEsfera, Validaciones.esfera(oiEsf));
        ok &= marcar(binding.etOiCilindro, Validaciones.cilindro(oiCil));
        ok &= marcar(binding.etOiEje, Validaciones.eje(oiEje, oiCil));
        ok &= marcar(binding.etOiAdicion, Validaciones.adicion(oiAdd));
        ok &= marcar(binding.etDistanciaPupilar, Validaciones.distanciaPupilar(dip));
        if (!ok) {
            mensaje("Revisa los campos marcados");
            return null;
        }

        Consulta c = new Consulta();
        c.fecha = new Date();
        c.odEsfera = odEsf;
        c.odCilindro = odCil != null ? odCil : 0;
        c.odEje = odEje != null ? odEje : 0;
        c.odAdicion = odAdd != null ? odAdd : 0;
        c.oiEsfera = oiEsf;
        c.oiCilindro = oiCil != null ? oiCil : 0;
        c.oiEje = oiEje != null ? oiEje : 0;
        c.oiAdicion = oiAdd != null ? oiAdd : 0;
        c.distanciaPupilar = dip != null ? dip : 0;
        c.agudezaVisual = binding.etAgudezaVisual.getText().toString().trim();
        c.tipoLente = binding.spTipoLente.getSelectedItem().toString();
        c.observaciones = binding.etObservaciones.getText().toString().trim();
        c.optometristaId = Sesion.getUsuario().id;
        c.optometristaNombre = Sesion.getUsuario().nombre;
        return c;
    }

    /** Pone o quita el error de un campo. Devuelve true si no hay error. */
    private boolean marcar(EditText campo, String error) {
        campo.setError(error);
        return error == null;
    }

    /** Copia una consulta del historial al formulario (para verla o imprimirla otra vez). */
    private void llenarFormulario(Consulta c) {
        binding.etOdEsfera.setText(numero(c.odEsfera));
        binding.etOdCilindro.setText(numero(c.odCilindro));
        binding.etOdEje.setText(c.odEje > 0 ? String.valueOf(c.odEje) : "");
        binding.etOdAdicion.setText(c.odAdicion > 0 ? numero(c.odAdicion) : "");
        binding.etOiEsfera.setText(numero(c.oiEsfera));
        binding.etOiCilindro.setText(numero(c.oiCilindro));
        binding.etOiEje.setText(c.oiEje > 0 ? String.valueOf(c.oiEje) : "");
        binding.etOiAdicion.setText(c.oiAdicion > 0 ? numero(c.oiAdicion) : "");
        binding.etDistanciaPupilar.setText(c.distanciaPupilar > 0 ? numero(c.distanciaPupilar) : "");
        binding.etAgudezaVisual.setText(c.agudezaVisual);
        binding.etObservaciones.setText(c.observaciones);
        String[] tipos = getResources().getStringArray(R.array.tipos_lente);
        for (int i = 0; i < tipos.length; i++) {
            if (tipos[i].equals(c.tipoLente)) binding.spTipoLente.setSelection(i);
        }
        mensaje("Consulta del " + Fechas.corta(c.fecha) + " cargada en el formulario");
    }

    private String numero(double valor) {
        return String.format(Locale.US, "%.2f", valor);
    }

    // ---------------- Acciones ----------------

    /** Guarda la consulta y actualiza el resumen del paciente en una sola escritura. */
    private void guardar() {
        Consulta c = leerFormulario();
        if (c == null) return;
        DocumentReference ref = pacienteRef();
        WriteBatch lote = FirebaseFirestore.getInstance().batch();
        lote.set(ref.collection("consultas").document(), c);
        lote.update(ref, "ultimaConsulta", c.fecha,
                "resumenGraduacion", Formato.resumenGraduacion(c.odEsfera, c.oiEsfera));
        binding.btnGuardar.setEnabled(false);
        lote.commit()
                .addOnSuccessListener(x -> {
                    mensaje("Graduación guardada");
                    if (binding != null) binding.btnGuardar.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    mensaje("No se pudo guardar: " + e.getMessage());
                    if (binding != null) binding.btnGuardar.setEnabled(true);
                });
    }

    /** Genera la receta con lo que está en el formulario y abre el menú para compartirla. */
    private void generarReceta() {
        if (paciente == null) {
            mensaje("Espera a que carguen los datos del paciente");
            return;
        }
        Consulta c = leerFormulario();
        if (c == null) return;
        try {
            File pdf = Pdf.receta(requireContext(), Sesion.getOptica(), paciente, c);
            Pdf.compartir(requireContext(), pdf);
        } catch (IOException e) {
            mensaje("No se pudo crear el PDF: " + e.getMessage());
        }
    }

    private void mensaje(String texto) {
        if (getContext() != null) {
            Toast.makeText(getContext(), texto, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
