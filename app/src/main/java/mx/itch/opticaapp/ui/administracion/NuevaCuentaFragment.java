package mx.itch.opticaapp.ui.administracion;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentNuevaCuentaBinding;
import mx.itch.opticaapp.model.Optica;
import mx.itch.opticaapp.model.Usuario;
import mx.itch.opticaapp.util.AlEscribir;
import mx.itch.opticaapp.util.Animaciones;
import mx.itch.opticaapp.util.Validaciones;

/**
 * Administración · Nueva cuenta (solo administrador): registra a un optometrista o vendedor
 * con correo y contraseña sin cerrar la sesión del administrador.
 *
 * ¿Cómo? FirebaseAuth inicia sesión automáticamente con la cuenta que acaba de crear.
 * Para no "sacar" al administrador, la cuenta se crea en una segunda instancia de Firebase
 * (llamada "altas") que usa el mismo proyecto. Esa instancia guarda el documento /users/{uid}
 * como si fuera la persona nueva uniéndose con el código de invitación (lo que piden las reglas
 * de Firestore) y al final cierra su sesión. La sesión principal nunca se toca.
 */
public class NuevaCuentaFragment extends Fragment {

    /** Nombre de la segunda instancia de Firebase que solo se usa para dar de alta cuentas. */
    private static final String APP_ALTAS = "altas";

    private FragmentNuevaCuentaBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentNuevaCuentaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!Sesion.estaActiva()) return;
        if (!Sesion.esAdmin()) {
            // El menú ya oculta esta opción, pero por si se llega de otra forma
            mensaje("Solo el administrador puede crear cuentas");
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        // Las reglas de la contraseña se marcan con ✓ mientras se escribe
        binding.tvReglas.setText(Validaciones.reglasContrasena(""));
        binding.etContrasena.addTextChangedListener(new AlEscribir(
                texto -> binding.tvReglas.setText(Validaciones.reglasContrasena(texto))));
        binding.btnCrear.setOnClickListener(v -> crearCuenta());

        Animaciones.entrada(this, binding.encabezado, binding.hoja);
    }

    // ---------------- Validar ----------------

    private void crearCuenta() {
        String nombre = texto(binding.etNombre);
        String correo = texto(binding.etCorreo).toLowerCase(Locale.ROOT);
        String contrasena = binding.etContrasena.getText().toString();
        String confirmar = binding.etConfirmar.getText().toString();

        String problema = Validaciones.nombrePersona(nombre);
        if (problema != null) {
            binding.etNombre.setError(problema);
            binding.etNombre.requestFocus();
            return;
        }
        problema = Validaciones.correo(correo);
        if (problema == null && correo.equalsIgnoreCase(Sesion.getUsuario().email)) {
            problema = "Es tu propio correo; escribe el de la persona nueva";
        }
        if (problema != null) {
            binding.etCorreo.setError(problema);
            binding.etCorreo.requestFocus();
            return;
        }
        problema = Validaciones.contrasena(contrasena);
        if (problema != null) {
            binding.etContrasena.setError(problema);
            binding.etContrasena.requestFocus();
            return;
        }
        if (!contrasena.equals(confirmar)) {
            binding.etConfirmar.setError("Las contraseñas no coinciden");
            binding.etConfirmar.requestFocus();
            return;
        }
        String rol = binding.rgRol.getCheckedRadioButtonId() == R.id.rbVendedor
                ? Usuario.ROL_VENDEDOR : Usuario.ROL_OPTOMETRISTA;

        mostrarCargando(true);
        darDeAlta(nombre, correo, contrasena, rol);
    }

    // ---------------- Crear la cuenta en la instancia "altas" ----------------

    /** Devuelve la segunda instancia de Firebase; la crea la primera vez con la misma configuración. */
    private static FirebaseApp instanciaAltas(Context ctx) {
        for (FirebaseApp app : FirebaseApp.getApps(ctx)) {
            if (APP_ALTAS.equals(app.getName())) return app;
        }
        return FirebaseApp.initializeApp(ctx, FirebaseApp.getInstance().getOptions(), APP_ALTAS);
    }

    private void darDeAlta(String nombre, String correo, String contrasena, String rol) {
        FirebaseApp altas = instanciaAltas(requireContext().getApplicationContext());
        FirebaseAuth authAltas = FirebaseAuth.getInstance(altas);
        Optica optica = Sesion.getOptica();

        authAltas.createUserWithEmailAndPassword(correo, contrasena)
                .addOnSuccessListener(resultado -> {
                    FirebaseUser nuevo = resultado.getUser();
                    if (nuevo == null) {
                        error("Firebase no regresó la cuenta nueva", null);
                        return;
                    }
                    Usuario usuario = new Usuario();
                    usuario.nombre = nombre;
                    usuario.email = correo;
                    usuario.rol = rol;
                    usuario.opticaId = optica.id;
                    usuario.fotoUrl = "";
                    // Igual que si la persona se uniera sola con el código: así lo aceptan las reglas
                    usuario.codigoUsado = optica.codigoInvitacion;

                    FirebaseFirestore.getInstance(altas).collection("users").document(nuevo.getUid())
                            .set(usuario)
                            .addOnSuccessListener(x -> {
                                authAltas.signOut();
                                listo(nombre, correo, rol);
                            })
                            .addOnFailureListener(e -> {
                                // Sin su documento la cuenta no sirve: se borra para poder intentarlo de nuevo
                                nuevo.delete().addOnCompleteListener(t -> authAltas.signOut());
                                error("No se pudo guardar la cuenta", e);
                            });
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        mostrarCargando(false);
                        if (binding != null) binding.etCorreo.setError("Ese correo ya tiene una cuenta");
                    } else if (e instanceof FirebaseAuthWeakPasswordException) {
                        mostrarCargando(false);
                        if (binding != null) binding.etContrasena.setError("Firebase dice que la contraseña es débil");
                    } else {
                        error("No se pudo crear la cuenta", e);
                    }
                });
    }

    // ---------------- Terminar ----------------

    private void listo(String nombre, String correo, String rol) {
        if (!isAdded()) return;
        mostrarCargando(false);
        String optica = Sesion.getOptica().nombre;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cuenta creada")
                .setMessage(nombre + " ya puede entrar como " + Usuario.nombreRol(rol).toLowerCase(Locale.ROOT)
                        + " con el correo " + correo + " y la contraseña que escribiste.\n\n"
                        + "Recomiéndale cambiarla en Mi perfil la primera vez que entre.")
                .setCancelable(false)
                .setPositiveButton("Listo", (d, w) -> regresar())
                .setNeutralButton("Avisarle", (d, w) -> {
                    avisar(nombre, correo, optica);
                    regresar();
                })
                .show();
    }

    /** Mensaje para WhatsApp o correo. La contraseña no se manda: se entrega en persona. */
    private void avisar(String nombre, String correo, String optica) {
        String texto = "Hola " + nombre + ", ya tienes cuenta en Óptica App (" + optica + ").\n"
                + "Entra con tu correo: " + correo + "\n"
                + "La contraseña te la doy en persona; cámbiala en Mi perfil cuando entres.";
        Intent enviar = new Intent(Intent.ACTION_SEND);
        enviar.setType("text/plain");
        enviar.putExtra(Intent.EXTRA_TEXT, texto);
        startActivity(Intent.createChooser(enviar, "Avisar a " + nombre));
    }

    private void regresar() {
        if (isAdded()) NavHostFragment.findNavController(this).popBackStack();
    }

    // ---------------- Apoyo ----------------

    /** Texto del campo sin espacios al inicio o al final, ni espacios dobles en medio. */
    private static String texto(EditText campo) {
        return campo.getText().toString().trim().replaceAll("\\s+", " ");
    }

    private void mostrarCargando(boolean cargando) {
        if (binding == null) return;
        binding.progreso.setVisibility(cargando ? View.VISIBLE : View.GONE);
        binding.btnCrear.setEnabled(!cargando);
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
