package mx.itch.opticaapp.ui.administracion;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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


import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentOpticaBinding;
import mx.itch.opticaapp.model.Optica;
import mx.itch.opticaapp.util.Animaciones;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.Aviso;
import mx.itch.opticaapp.util.Imagenes;
import mx.itch.opticaapp.util.SelectorDeFoto;
import mx.itch.opticaapp.util.Validaciones;

/**
 * Administración · Mi óptica (solo administrador): nombre, teléfono, estado, dirección,
 * meta semanal, logotipo y el código de invitación para copiarlo o compartirlo.
 */
public class OpticaFragment extends Fragment {

    private FragmentOpticaBinding binding;

    /** Cámara o galería para el logotipo. */
    private final SelectorDeFoto selectorLogo = new SelectorDeFoto(this, this::subirLogo);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentOpticaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!Sesion.estaActiva()) return;
        Optica o = Sesion.getOptica();

        ArrayAdapter<CharSequence> estados = ArrayAdapter.createFromResource(requireContext(),
                R.array.estados_mexico, android.R.layout.simple_spinner_item);
        estados.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spEstado.setAdapter(estados);

        binding.etNombre.setText(o.nombre);
        binding.etTelefono.setText(o.telefono);
        binding.etDireccion.setText(o.direccion);
        if (o.metaSemanal > 0) binding.etMeta.setText(String.format(Locale.US, "%.2f", o.metaSemanal));
        String[] lista = getResources().getStringArray(R.array.estados_mexico);
        for (int i = 0; i < lista.length; i++) {
            if (lista[i].equals(o.estado)) binding.spEstado.setSelection(i);
        }
        binding.tvCodigo.setText(o.codigoInvitacion);
        Imagenes.mostrar(binding.ivLogo, o.logoUrl, R.drawable.ic_tienda);

        binding.btnLogo.setOnClickListener(v -> elegirOrigenDelLogo());
        binding.ivLogo.setOnClickListener(v -> elegirOrigenDelLogo());
        binding.btnCopiar.setOnClickListener(v -> copiarCodigo());
        binding.btnCompartir.setOnClickListener(v -> compartirCodigo());
        binding.btnGuardar.setOnClickListener(v -> guardar());

        // Solo el administrador puede cambiar los datos (lo revisan también las reglas de Firestore)
        boolean admin = Sesion.esAdmin();
        binding.btnGuardar.setVisibility(admin ? View.VISIBLE : View.GONE);
        binding.btnLogo.setVisibility(admin ? View.VISIBLE : View.GONE);
        binding.tvAyudaLogo.setVisibility(admin ? View.VISIBLE : View.GONE);

        // Animación de entrada: título en cascada, el logotipo crece y la hoja sube
        if (Animaciones.entrada(this, binding.encabezado, binding.hoja)) {
            Animaciones.aparecer(binding.ivLogo, 150);
            Animaciones.aparecer(binding.btnLogo, 350);
        }
    }

    // ---------------- Guardar ----------------

    private void guardar() {
        String nombre = texto(binding.etNombre);
        String telefono = texto(binding.etTelefono);
        String direccion = texto(binding.etDireccion);
        String metaTexto = texto(binding.etMeta);

        // Se revisan todos los campos para marcar todos los errores a la vez
        boolean ok = marcar(binding.etNombre, Validaciones.nombreComercial(nombre, "el nombre de la óptica"));
        ok &= marcar(binding.etTelefono, Validaciones.telefono(telefono, true));
        ok &= marcar(binding.etDireccion, Validaciones.direccion(direccion, true));
        ok &= marcar(binding.etMeta, Validaciones.importe(metaTexto, 9_999_999.99));
        if (!ok) return;
        Double meta = metaTexto.isEmpty() ? Double.valueOf(0) : Formato.leerNumero(metaTexto);

        Map<String, Object> cambios = new HashMap<>();
        cambios.put("nombre", nombre);
        cambios.put("telefono", Validaciones.soloDigitos(telefono));   // se guardan solo los 10 dígitos
        cambios.put("estado", binding.spEstado.getSelectedItem().toString());
        cambios.put("direccion", direccion);
        cambios.put("metaSemanal", meta);

        mostrarCargando(true);
        Sesion.opticaRef().update(cambios)
                .addOnSuccessListener(x -> {
                    // También se actualiza la copia en memoria para que el panel y el menú lo vean
                    Optica o = Sesion.getOptica();
                    o.nombre = nombre;
                    o.telefono = (String) cambios.get("telefono");
                    o.estado = (String) cambios.get("estado");
                    o.direccion = (String) cambios.get("direccion");
                    o.metaSemanal = meta;
                    mostrarCargando(false);
                    mensaje("Datos de la óptica guardados");
                })
                .addOnFailureListener(e -> error("No se pudo guardar", e));
    }

    // ---------------- Logotipo ----------------

    private void elegirOrigenDelLogo() {
        if (!Sesion.esAdmin()) return;
        selectorLogo.elegir("Logotipo de la óptica");
    }

    /** Reduce el logotipo a 400 px y lo guarda como texto en el campo logoUrl de /opticas/{id}. */
    private void subirLogo(Uri foto) {
        Optica o = Sesion.getOptica();
        Imagenes.mostrarUri(binding.ivLogo, foto);
        mostrarCargando(true);
        Imagenes.convertir(foto, Imagenes.LADO_LOGO,
                datos -> Sesion.opticaRef().update("logoUrl", datos)
                        .addOnSuccessListener(x -> {
                            o.logoUrl = datos;
                            mostrarCargando(false);
                            Aviso.mostrar(getActivity(), "Logotipo actualizado");
                        })
                        .addOnFailureListener(e -> error("No se pudo guardar el logotipo", e)),
                e -> error("No se pudo preparar el logotipo", e));
    }

    // ---------------- Código de invitación ----------------

    private void copiarCodigo() {
        ClipboardManager portapapeles = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        portapapeles.setPrimaryClip(ClipData.newPlainText("Código", Sesion.getOptica().codigoInvitacion));
        mensaje("Código copiado");
    }

    /** Abre WhatsApp, correo, etc. con un mensaje listo para la persona invitada. */
    private void compartirCodigo() {
        Optica o = Sesion.getOptica();
        String texto = "Únete a " + o.nombre + " en Óptica App.\n"
                + "1. Descarga la app.\n"
                + "2. Toca \"Unirme con código\".\n"
                + "3. Escribe este código: " + o.codigoInvitacion;
        Intent enviar = new Intent(Intent.ACTION_SEND);
        enviar.setType("text/plain");
        enviar.putExtra(Intent.EXTRA_TEXT, texto);
        startActivity(Intent.createChooser(enviar, "Compartir código"));
    }

    // ---------------- Apoyo ----------------

    /** Texto del campo sin espacios al inicio o al final, ni espacios dobles en medio. */
    private static String texto(EditText campo) {
        return campo.getText().toString().trim().replaceAll("\\s+", " ");
    }

    /** Pone o quita el error de un campo. Devuelve true si no hay error. */
    private static boolean marcar(EditText campo, String error) {
        campo.setError(error);
        return error == null;
    }

    private void mostrarCargando(boolean cargando) {
        if (binding == null) return;
        binding.progreso.setVisibility(cargando ? View.VISIBLE : View.GONE);
        binding.btnGuardar.setEnabled(!cargando);
    }

    private void error(String texto, Exception e) {
        mostrarCargando(false);
        mensaje(texto + (e != null && e.getMessage() != null ? ": " + e.getMessage() : ""));
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
