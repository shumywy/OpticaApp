package mx.itch.opticaapp.ui.registro;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import mx.itch.opticaapp.MainActivity;
import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentRegistroBinding;
import mx.itch.opticaapp.model.Optica;
import mx.itch.opticaapp.model.Usuario;
import mx.itch.opticaapp.util.AlEscribir;
import mx.itch.opticaapp.util.Animaciones;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.Imagenes;
import mx.itch.opticaapp.util.Validaciones;

/**
 * Pantalla 2: crea la cuenta y después
 * - registra una óptica nueva (el usuario queda como Administrador), o
 * - se une a una óptica existente con su código de invitación.
 */
public class RegistroFragment extends Fragment {

    public static final String ARG_MODO = "modo";
    public static final String ARG_COMPLETAR = "completar";
    public static final String MODO_NUEVA = "nueva";
    public static final String MODO_UNIRME = "unirme";

    private FragmentRegistroBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    /** true cuando el usuario ya inició sesión (por ejemplo con Google) y solo le faltan sus datos. */
    private boolean completar;
    /** Imagen elegida para el logotipo (puede quedar en null). */
    private Uri logoUri;
    /** Archivo donde la cámara guardará la foto. */
    private Uri uriCamara;

    // Abre la cámara; recibe true si se tomó la foto
    private final ActivityResultLauncher<Uri> tomarFoto =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), seTomo -> {
                if (Boolean.TRUE.equals(seTomo)) {
                    mostrarLogo(uriCamara);
                }
            });

    // Abre el selector de fotos de Android; recibe la imagen elegida o null
    private final ActivityResultLauncher<PickVisualMediaRequest> elegirImagen =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    mostrarLogo(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRegistroBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        Bundle args = getArguments();
        completar = args != null && args.getBoolean(ARG_COMPLETAR, false);
        String modo = args != null ? args.getString(ARG_MODO, MODO_NUEVA) : MODO_NUEVA;

        llenarSpinner(binding.spEstado, R.array.estados_mexico);
        llenarSpinner(binding.spRol, R.array.roles_union);
        binding.spEstado.setSelection(12); // Guerrero

        // Si ya inició sesión con Google, no pedimos correo ni contraseña
        binding.layoutCuenta.setVisibility(completar ? View.GONE : View.VISIBLE);
        FirebaseUser actual = auth.getCurrentUser();
        if (completar && actual != null && actual.getDisplayName() != null) {
            binding.etNombre.setText(actual.getDisplayName());
        }

        // Lista de reglas de la contraseña que se actualiza al escribir
        binding.tvReglas.setText(Validaciones.reglasContrasena(""));
        binding.etContrasena.addTextChangedListener(new AlEscribir(
                texto -> binding.tvReglas.setText(Validaciones.reglasContrasena(texto))));

        binding.rgModo.setOnCheckedChangeListener((grupo, idMarcado) -> mostrarModo());
        binding.rgModo.check(MODO_UNIRME.equals(modo) ? R.id.rbUnirme : R.id.rbNueva);

        binding.btnLogoCamara.setOnClickListener(v -> {
            uriCamara = Imagenes.nuevaUriParaFoto(requireContext());
            tomarFoto.launch(uriCamara);
        });
        binding.btnLogoGaleria.setOnClickListener(v -> elegirImagen.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        binding.btnContinuar.setOnClickListener(v -> continuar());

        // Animación de entrada: el título baja en cascada y la hoja blanca sube
        Animaciones.entrada(this, binding.encabezado, binding.hoja);
    }

    private void llenarSpinner(Spinner spinner, int arreglo) {
        ArrayAdapter<CharSequence> adaptador = ArrayAdapter.createFromResource(requireContext(), arreglo,
                android.R.layout.simple_spinner_item);
        adaptador.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adaptador);
    }

    private boolean esNueva() {
        return binding.rgModo.getCheckedRadioButtonId() == R.id.rbNueva;
    }

    private void mostrarModo() {
        binding.layoutNueva.setVisibility(esNueva() ? View.VISIBLE : View.GONE);
        binding.layoutUnirme.setVisibility(esNueva() ? View.GONE : View.VISIBLE);
    }

    private void mostrarLogo(Uri uri) {
        logoUri = uri;
        if (binding != null) {
            Imagenes.mostrarUri(binding.ivLogo, uri);
        }
    }

    /** Texto del campo sin espacios al inicio o al final, ni espacios dobles en medio. */
    private String texto(android.widget.EditText campo) {
        return campo.getText().toString().trim().replaceAll("\\s+", " ");
    }

    // ---------------- Validación ----------------

    /** Revisa todos los campos visibles y marca cada error en su campo. Devuelve true si todo es correcto. */
    private boolean validar() {
        boolean ok = marcar(binding.etNombre, Validaciones.nombrePersona(texto(binding.etNombre)));
        if (!completar) {
            ok &= marcar(binding.etCorreo, Validaciones.correo(texto(binding.etCorreo)));
            String contrasena = binding.etContrasena.getText().toString();
            String error = Validaciones.contrasena(contrasena);
            if (error == null && !contrasena.equals(binding.etConfirmar.getText().toString())) {
                ok &= marcar(binding.etConfirmar, "Las contraseñas no coinciden");
            } else {
                binding.etConfirmar.setError(null);
            }
            ok &= marcar(binding.etContrasena, error);
        }
        if (esNueva()) {
            ok &= marcar(binding.etOpticaNombre,
                    Validaciones.nombreComercial(texto(binding.etOpticaNombre), "el nombre de la óptica"));
            ok &= marcar(binding.etTelefono, Validaciones.telefono(texto(binding.etTelefono), true));
            ok &= marcar(binding.etDireccion, Validaciones.direccion(texto(binding.etDireccion), true));
            ok &= marcar(binding.etMeta, Validaciones.importe(texto(binding.etMeta), 9_999_999.99));
        } else {
            ok &= marcar(binding.etCodigo, Validaciones.codigoInvitacion(texto(binding.etCodigo)));
        }
        return ok;
    }

    /** Pone o quita el error de un campo. Devuelve true si no hay error. */
    private static boolean marcar(android.widget.EditText campo, String error) {
        campo.setError(error);
        return error == null;
    }

    // ---------------- Guardado ----------------

    private void continuar() {
        if (!validar()) return;
        mostrarCargando(true);
        if (completar) {
            guardarDatos();
        } else {
            auth.createUserWithEmailAndPassword(texto(binding.etCorreo), binding.etContrasena.getText().toString())
                    .addOnSuccessListener(r -> guardarDatos())
                    .addOnFailureListener(e -> error("No se pudo crear la cuenta", e));
        }
    }

    private void guardarDatos() {
        FirebaseUser actual = auth.getCurrentUser();
        if (actual == null) {
            error("La sesión se cerró, vuelve a intentarlo", null);
            return;
        }
        Usuario usuario = new Usuario();
        usuario.nombre = texto(binding.etNombre);
        usuario.email = actual.getEmail();
        usuario.fotoUrl = "";
        if (esNueva()) {
            crearOptica(actual.getUid(), usuario);
        } else {
            unirseAOptica(actual.getUid(), usuario);
        }
    }

    /** Crea la óptica, su código de invitación y el usuario administrador en una sola escritura. */
    private void crearOptica(String uid, Usuario usuario) {
        DocumentReference opticaRef = db.collection("opticas").document();

        Optica optica = new Optica();
        optica.nombre = texto(binding.etOpticaNombre);
        optica.telefono = Validaciones.soloDigitos(texto(binding.etTelefono));   // se guardan solo los 10 dígitos
        optica.estado = binding.spEstado.getSelectedItem().toString();
        optica.direccion = texto(binding.etDireccion);
        Double meta = Formato.leerNumero(texto(binding.etMeta));
        optica.metaSemanal = meta != null ? meta : 0;
        optica.codigoInvitacion = generarCodigo();
        optica.ownerId = uid;
        optica.logoUrl = "";

        usuario.rol = Usuario.ROL_ADMIN;
        usuario.opticaId = opticaRef.getId();
        usuario.codigoUsado = "";

        Map<String, Object> codigo = new HashMap<>();
        codigo.put("opticaId", opticaRef.getId());

        WriteBatch lote = db.batch();
        lote.set(opticaRef, optica);
        lote.set(db.collection("codigos").document(optica.codigoInvitacion), codigo);
        lote.set(db.collection("users").document(uid), usuario);
        lote.commit()
                .addOnSuccessListener(x -> {
                    optica.id = opticaRef.getId();
                    usuario.id = uid;
                    if (logoUri == null) {
                        terminar(usuario, optica);
                        return;
                    }
                    // El logo se guarda después, cuando el usuario ya pertenece a la óptica
                    // (se reduce a 400 px y va como texto en el campo logoUrl)
                    Imagenes.convertir(logoUri, Imagenes.LADO_LOGO,
                            datos -> {
                                optica.logoUrl = datos;
                                opticaRef.update("logoUrl", datos);
                                terminar(usuario, optica);
                            },
                            e -> {
                                mensaje("La óptica se creó, pero no se pudo guardar el logo: " + e.getMessage());
                                terminar(usuario, optica);
                            });
                })
                .addOnFailureListener(e -> error("No se pudo registrar la óptica", e));
    }

    /** Busca el código en /codigos y crea el usuario con el rol elegido. */
    private void unirseAOptica(String uid, Usuario usuario) {
        String codigo = texto(binding.etCodigo).toUpperCase(Locale.ROOT);
        db.collection("codigos").document(codigo).get()
                .addOnSuccessListener(doc -> {
                    if (binding == null) return;
                    String opticaId = doc.getString("opticaId");
                    if (!doc.exists() || opticaId == null) {
                        mostrarCargando(false);
                        binding.etCodigo.setError("No existe una óptica con ese código");
                        return;
                    }
                    usuario.rol = Usuario.rolDesdeTexto(binding.spRol.getSelectedItem().toString());
                    usuario.opticaId = opticaId;
                    usuario.codigoUsado = codigo;
                    db.collection("users").document(uid).set(usuario)
                            .addOnSuccessListener(x -> db.collection("opticas").document(opticaId).get()
                                    .addOnSuccessListener(docOptica -> {
                                        Optica optica = docOptica.toObject(Optica.class);
                                        if (optica == null) {
                                            error("No se encontró la óptica", null);
                                            return;
                                        }
                                        usuario.id = uid;
                                        terminar(usuario, optica);
                                    })
                                    .addOnFailureListener(e -> error("No se pudo leer la óptica", e)))
                            .addOnFailureListener(e -> error("No se pudo guardar tu usuario", e));
                })
                .addOnFailureListener(e -> error("No se pudo revisar el código", e));
    }

    private void terminar(Usuario usuario, Optica optica) {
        if (!isAdded()) return;
        Sesion.iniciar(usuario, optica);
        ((MainActivity) requireActivity()).irAInicio(true);
    }

    /** Código tipo "OPT-7K2Q9X". Se omiten letras que se confunden (O/0, I/1). */
    private String generarCodigo() {
        String letras = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom azar = new SecureRandom();
        StringBuilder sb = new StringBuilder("OPT-");
        for (int i = 0; i < 6; i++) {
            sb.append(letras.charAt(azar.nextInt(letras.length())));
        }
        return sb.toString();
    }

    // ---------------- Apoyo ----------------

    private void mostrarCargando(boolean cargando) {
        if (binding == null) return;
        binding.progreso.setVisibility(cargando ? View.VISIBLE : View.GONE);
        binding.btnContinuar.setEnabled(!cargando);
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
