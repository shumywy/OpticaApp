package mx.itch.opticaapp.ui.ajustes;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import mx.itch.opticaapp.MainActivity;
import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Preferencias;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentAjustesBinding;
import mx.itch.opticaapp.util.Animaciones;

/**
 * Pantalla 9: Configuración de la app en este teléfono:
 * tema, modo sin imágenes, texto grande, fondo de pantalla y cerrar sesión.
 * (El perfil, la óptica y el personal están en el menú lateral → Administración.)
 */
public class AjustesFragment extends Fragment {

    private FragmentAjustesBinding binding;

    private final ActivityResultLauncher<PickVisualMediaRequest> elegirFondo =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) guardarFondo(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAjustesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!Sesion.estaActiva()) return;

        configurarApariencia();
        binding.filaCerrarSesion.setOnClickListener(v -> cerrarSesion());

        // Animación de entrada: título en cascada y la hoja que sube
        Animaciones.entrada(this, binding.encabezado, binding.hoja);
    }

    // ---------------- Apariencia ----------------

    private void configurarApariencia() {
        Context ctx = requireContext();

        // Pestañas del tema: 0 = sistema, 1 = claro, 2 = oscuro
        int[] opcionesTema = {R.id.rbTemaSistema, R.id.rbTemaClaro, R.id.rbTemaOscuro};
        binding.rgTema.check(opcionesTema[Preferencias.getTema(ctx)]);
        binding.rgTema.setOnCheckedChangeListener((grupo, idMarcado) -> {
            for (int i = 0; i < opcionesTema.length; i++) {
                // Solo si cambió; Android vuelve a dibujar la pantalla con el nuevo tema
                if (opcionesTema[i] == idMarcado && i != Preferencias.getTema(ctx)) {
                    Preferencias.setTema(ctx, i);
                }
            }
        });

        binding.swSinImagenes.setChecked(Preferencias.sinImagenes(ctx));
        binding.swSinImagenes.setOnCheckedChangeListener((boton, activo) -> Preferencias.setSinImagenes(ctx, activo));

        binding.swTextoGrande.setChecked(Preferencias.textoGrande(ctx));
        binding.swTextoGrande.setOnCheckedChangeListener((boton, activo) -> {
            Preferencias.setTextoGrande(ctx, activo);
            requireActivity().recreate();   // vuelve a crear la pantalla con el nuevo tamaño de letra
        });

        mostrarEstadoFondo();
        binding.filaFondo.setOnClickListener(v -> elegirFondo.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        binding.btnQuitarFondo.setOnClickListener(v -> {
            File fondo = Preferencias.archivoFondo(ctx);
            if (fondo.exists() && !fondo.delete()) {
                mensaje("No se pudo quitar el fondo");
            }
            ((MainActivity) requireActivity()).aplicarFondo();
            mostrarEstadoFondo();
        });
    }

    /** "Imagen personalizada" o "Ninguno", y el botón Quitar solo si hay fondo. */
    private void mostrarEstadoFondo() {
        if (binding == null) return;
        boolean hayFondo = Preferencias.archivoFondo(requireContext()).exists();
        binding.tvFondo.setText(hayFondo ? "Imagen personalizada" : "Ninguno · toca para elegir");
        binding.btnQuitarFondo.setVisibility(hayFondo ? View.VISIBLE : View.GONE);
    }

    /** Copia la imagen elegida dentro de la app para que siga disponible aunque se borre de la galería. */
    private void guardarFondo(Uri uri) {
        if (!isAdded()) return;
        File destino = Preferencias.archivoFondo(requireContext());
        try (InputStream entrada = requireContext().getContentResolver().openInputStream(uri);
             OutputStream salida = new FileOutputStream(destino)) {
            if (entrada == null) throw new IOException("No se pudo abrir la imagen");
            byte[] buffer = new byte[8192];
            int leidos;
            while ((leidos = entrada.read(buffer)) != -1) {
                salida.write(buffer, 0, leidos);
            }
        } catch (IOException e) {
            mensaje("No se pudo guardar el fondo: " + e.getMessage());
            return;
        }
        ((MainActivity) requireActivity()).aplicarFondo();
        mostrarEstadoFondo();
    }

    // ---------------- Sesión ----------------

    private void cerrarSesion() {
        // MainActivity también quita el listener del menú lateral antes de salir
        ((MainActivity) requireActivity()).cerrarSesion();
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
