package mx.itch.opticaapp.ui.login;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import mx.itch.opticaapp.MainActivity;
import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentLoginBinding;
import mx.itch.opticaapp.model.Optica;
import mx.itch.opticaapp.model.Usuario;
import mx.itch.opticaapp.ui.registro.RegistroFragment;
import mx.itch.opticaapp.util.Animaciones;
import mx.itch.opticaapp.util.Validaciones;

/** Pantalla 1: inicio de sesión con correo o con Google. */
public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnEntrar.setOnClickListener(v -> entrarConCorreo());
        binding.btnGoogle.setOnClickListener(v -> entrarConGoogle());
        binding.btnOlvide.setOnClickListener(v -> recuperarContrasena());
        binding.btnRegistrar.setOnClickListener(v -> irARegistro(RegistroFragment.MODO_NUEVA, false));
        binding.btnUnirme.setOnClickListener(v -> irARegistro(RegistroFragment.MODO_UNIRME, false));

        // Animación de entrada: el título baja en cascada y la hoja blanca sube
        Animaciones.entrada(this, binding.encabezado, binding.hoja);

        // Si Firebase recuerda una sesión abierta, cargamos sus datos sin pedir la contraseña otra vez
        if (auth.getCurrentUser() != null) {
            cargarSesion(false);
        }
    }

    // ---------------- Correo y contraseña ----------------

    private void entrarConCorreo() {
        String correo = binding.etCorreo.getText().toString().trim();
        String contrasena = binding.etContrasena.getText().toString();
        String errorCorreo = Validaciones.correo(correo);
        if (errorCorreo != null) {
            binding.etCorreo.setError(errorCorreo);
            return;
        }
        if (contrasena.isEmpty()) {
            binding.etContrasena.setError("Escribe tu contraseña");
            return;
        }
        mostrarCargando(true);
        auth.signInWithEmailAndPassword(correo, contrasena)
                .addOnSuccessListener(r -> cargarSesion(true))
                .addOnFailureListener(e -> error("No se pudo iniciar sesión. Revisa tu correo y contraseña.", e));
    }

    private void recuperarContrasena() {
        String correo = binding.etCorreo.getText().toString().trim();
        String errorCorreo = Validaciones.correo(correo);
        if (errorCorreo != null) {
            binding.etCorreo.setError("Escribe tu correo para enviarte el enlace");
            return;
        }
        auth.sendPasswordResetEmail(correo)
                .addOnSuccessListener(x -> mensaje("Te enviamos un correo para cambiar tu contraseña"))
                .addOnFailureListener(e -> error("No se pudo enviar el correo", e));
    }

    // ---------------- Google (Credential Manager) ----------------

    private void entrarConGoogle() {
        // El plugin de google-services crea este texto a partir de google-services.json
        // cuando el inicio con Google está activado en la consola de Firebase.
        int idCliente = getResources().getIdentifier("default_web_client_id", "string",
                requireContext().getPackageName());
        if (idCliente == 0) {
            mensaje("Activa el inicio con Google en Firebase y vuelve a descargar google-services.json");
            return;
        }
        GetGoogleIdOption opcionGoogle = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(idCliente))
                .build();
        GetCredentialRequest solicitud = new GetCredentialRequest.Builder()
                .addCredentialOption(opcionGoogle)
                .build();

        mostrarCargando(true);
        CredentialManager administrador = CredentialManager.create(requireContext());
        administrador.getCredentialAsync(requireActivity(), solicitud, new CancellationSignal(),
                ContextCompat.getMainExecutor(requireContext()),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse respuesta) {
                        usarCredencialDeGoogle(respuesta.getCredential());
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        error("No se pudo iniciar con Google", e);
                    }
                });
    }

    private void usarCredencialDeGoogle(Credential credencial) {
        if (credencial instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credencial.getType())) {
            GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(credencial.getData());
            AuthCredential credencialFirebase = GoogleAuthProvider.getCredential(google.getIdToken(), null);
            auth.signInWithCredential(credencialFirebase)
                    .addOnSuccessListener(r -> cargarSesion(true))
                    .addOnFailureListener(e -> error("Firebase rechazó la cuenta de Google", e));
        } else {
            mostrarCargando(false);
            mensaje("Tipo de cuenta no reconocido");
        }
    }

    // ---------------- Cargar datos del usuario y su óptica ----------------

    /**
     * Lee /users/{uid} y /opticas/{opticaId}. Si el usuario todavía no termina su registro:
     * - al entrar a mano (manual = true) lo mandamos a completar el registro;
     * - al abrir la app (manual = false) cerramos esa sesión incompleta.
     */
    private void cargarSesion(boolean manual) {
        FirebaseUser actual = auth.getCurrentUser();
        if (actual == null) {
            mostrarCargando(false);
            return;
        }
        mostrarCargando(true);
        db.collection("users").document(actual.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (binding == null) return;   // el usuario ya salió de la pantalla
                    Usuario usuario = doc.toObject(Usuario.class);
                    if (!doc.exists() || usuario == null || usuario.opticaId == null) {
                        mostrarCargando(false);
                        if (manual) {
                            irARegistro(RegistroFragment.MODO_NUEVA, true);
                        } else {
                            auth.signOut();
                        }
                        return;
                    }
                    db.collection("opticas").document(usuario.opticaId).get()
                            .addOnSuccessListener(docOptica -> {
                                if (binding == null) return;
                                Optica optica = docOptica.toObject(Optica.class);
                                if (optica == null) {
                                    mostrarCargando(false);
                                    mensaje("No se encontró la óptica de este usuario");
                                    return;
                                }
                                Sesion.iniciar(usuario, optica);
                                ((MainActivity) requireActivity()).irAInicio(true);
                            })
                            .addOnFailureListener(e -> error("No se pudo leer la óptica", e));
                })
                .addOnFailureListener(e -> error("No se pudo leer tu usuario", e));
    }

    private void irARegistro(String modo, boolean completar) {
        NavController nav = NavHostFragment.findNavController(this);
        if (nav.getCurrentDestination() == null || nav.getCurrentDestination().getId() != R.id.loginFragment) {
            return;
        }
        Bundle args = new Bundle();
        args.putString(RegistroFragment.ARG_MODO, modo);
        args.putBoolean(RegistroFragment.ARG_COMPLETAR, completar);
        nav.navigate(R.id.action_login_to_registro, args);
    }

    // ---------------- Apoyo ----------------

    private void mostrarCargando(boolean cargando) {
        if (binding == null) return;
        binding.progreso.setVisibility(cargando ? View.VISIBLE : View.GONE);
        binding.btnEntrar.setEnabled(!cargando);
        binding.btnGoogle.setEnabled(!cargando);
        binding.btnRegistrar.setEnabled(!cargando);
        binding.btnUnirme.setEnabled(!cargando);
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
