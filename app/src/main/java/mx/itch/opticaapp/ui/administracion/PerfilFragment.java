package mx.itch.opticaapp.ui.administracion;

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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentPerfilBinding;
import mx.itch.opticaapp.model.Optica;
import mx.itch.opticaapp.model.Usuario;
import mx.itch.opticaapp.util.Animaciones;
import mx.itch.opticaapp.util.Imagenes;
import mx.itch.opticaapp.util.Validaciones;

/**
 * Administración · Mi perfil (todos los roles): foto (cámara o galería), nombre y cambio de contraseña.
 * El nombre y la foto se guardan en /users/{uid}; el menú lateral se actualiza solo porque lo escucha en tiempo real.
 */
public class PerfilFragment extends Fragment {

    private FragmentPerfilBinding binding;
    private Uri uriCamara;

    private final ActivityResultLauncher<Uri> tomarFoto =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), seTomo -> {
                if (Boolean.TRUE.equals(seTomo)) subirFoto(uriCamara);
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> elegirFoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) subirFoto(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPerfilBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!Sesion.estaActiva()) return;
        Usuario u = Sesion.getUsuario();
        Optica o = Sesion.getOptica();

        binding.tvRol.setText(o.nombre + " · " + Usuario.nombreRol(u.rol));
        binding.etNombre.setText(u.nombre);
        binding.etCorreo.setText(u.email);
        Imagenes.mostrar(binding.ivFoto, u.fotoUrl, R.drawable.ic_perfil);

        binding.btnFoto.setOnClickListener(v -> elegirOrigenDeFoto());
        binding.ivFoto.setOnClickListener(v -> elegirOrigenDeFoto());
        binding.btnGuardar.setOnClickListener(v -> guardarNombre());
        binding.filaContrasena.setOnClickListener(v -> cambiarContrasena());

        // Animación de entrada: título en cascada, la foto crece y la hoja sube
        if (Animaciones.entrada(this, binding.encabezado, binding.hoja)) {
            Animaciones.aparecer(binding.ivFoto, 150);
            Animaciones.aparecer(binding.btnFoto, 350);
        }
    }

    // ---------------- Foto ----------------

    private void elegirOrigenDeFoto() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Foto de perfil")
                .setItems(new String[]{"Tomar foto", "Elegir de la galería"}, (d, opcion) -> {
                    if (opcion == 0) {
                        uriCamara = Imagenes.nuevaUriParaFoto(requireContext());
                        tomarFoto.launch(uriCamara);
                    } else {
                        elegirFoto.launch(new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build());
                    }
                })
                .show();
    }

    /** Reduce la foto a 320 px y la guarda como texto en el campo fotoUrl de /users/{uid}. */
    private void subirFoto(Uri foto) {
        String uid = Sesion.getUsuario().id;
        Imagenes.mostrarUri(binding.ivFoto, foto);   // se ve de inmediato mientras se guarda
        mostrarCargando(true);
        Imagenes.convertir(foto, Imagenes.LADO_PERFIL,
                datos -> FirebaseFirestore.getInstance().collection("users").document(uid)
                        .update("fotoUrl", datos)
                        .addOnSuccessListener(x -> {
                            Sesion.getUsuario().fotoUrl = datos;
                            mostrarCargando(false);
                            mensaje("Foto actualizada");
                        })
                        .addOnFailureListener(e -> error("No se pudo guardar la foto", e)),
                e -> error("No se pudo preparar la foto", e));
    }

    // ---------------- Nombre ----------------

    private void guardarNombre() {
        String nombre = binding.etNombre.getText().toString().trim().replaceAll("\\s+", " ");
        String problema = Validaciones.nombrePersona(nombre);
        if (problema != null) {
            binding.etNombre.setError(problema);
            return;
        }
        if (nombre.equals(Sesion.getUsuario().nombre)) {
            mensaje("No hay cambios que guardar");
            return;
        }
        mostrarCargando(true);
        // Las reglas permiten que cada quien cambie sus datos, pero no su rol ni su óptica
        FirebaseFirestore.getInstance().collection("users").document(Sesion.getUsuario().id)
                .update("nombre", nombre)
                .addOnSuccessListener(x -> {
                    Sesion.getUsuario().nombre = nombre;
                    mostrarCargando(false);
                    mensaje("Datos guardados");
                })
                .addOnFailureListener(e -> error("No se pudo guardar", e));
    }

    // ---------------- Contraseña ----------------

    /** Firebase manda un correo con un enlace para crear una contraseña nueva. */
    private void cambiarContrasena() {
        String correo = Sesion.getUsuario().email;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cambiar contraseña")
                .setMessage("Te enviaremos un correo a " + correo + " con un enlace para crear una contraseña nueva.")
                .setPositiveButton("Enviar correo", (d, w) -> FirebaseAuth.getInstance().sendPasswordResetEmail(correo)
                        .addOnSuccessListener(x -> mensaje("Revisa tu correo"))
                        .addOnFailureListener(e -> mensaje("No se pudo enviar: " + e.getMessage())))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---------------- Apoyo ----------------

    private void mostrarCargando(boolean cargando) {
        if (binding == null) return;
        binding.progreso.setVisibility(cargando ? View.VISIBLE : View.GONE);
        binding.btnGuardar.setEnabled(!cargando);
        binding.btnFoto.setEnabled(!cargando);
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
