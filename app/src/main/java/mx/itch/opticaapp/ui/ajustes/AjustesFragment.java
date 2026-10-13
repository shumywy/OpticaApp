package mx.itch.opticaapp.ui.ajustes;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import mx.itch.opticaapp.MainActivity;
import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Preferencias;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentAjustesBinding;
import mx.itch.opticaapp.model.Optica;
import mx.itch.opticaapp.model.Usuario;
import mx.itch.opticaapp.util.Fechas;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.Imagenes;

/**
 * Pantalla 9: foto de perfil (cámara + Storage), tema, fondo de pantalla, modo sin imágenes,
 * texto grande y, para el administrador, personal, código de invitación, corte de caja y meta.
 */
public class AjustesFragment extends Fragment {

    private FragmentAjustesBinding binding;
    private Uri uriCamara;

    private final ActivityResultLauncher<Uri> tomarFoto =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), seTomo -> {
                if (Boolean.TRUE.equals(seTomo)) subirFotoDePerfil(uriCamara);
            });

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
        Usuario u = Sesion.getUsuario();
        Optica o = Sesion.getOptica();

        binding.tvNombre.setText(u.nombre);
        binding.tvRol.setText(o.nombre + " · " + Usuario.nombreRol(u.rol));
        binding.tvCorreo.setText(u.email);
        Imagenes.mostrar(binding.ivFoto, u.fotoUrl);
        binding.btnFoto.setOnClickListener(v -> {
            uriCamara = Imagenes.nuevaUriParaFoto(requireContext());
            tomarFoto.launch(uriCamara);
        });

        configurarApariencia();

        // Opciones del administrador
        binding.layoutAdmin.setVisibility(Sesion.esAdmin() ? View.VISIBLE : View.GONE);
        binding.tvCodigo.setText(o.codigoInvitacion);
        mostrarMeta();
        binding.filaCodigo.setOnClickListener(v -> copiarCodigo());
        binding.filaPersonal.setOnClickListener(v -> mostrarPersonal());
        binding.filaCorte.setOnClickListener(v -> corteDeCaja());
        binding.filaMeta.setOnClickListener(v -> editarMeta());

        binding.filaCerrarSesion.setOnClickListener(v -> cerrarSesion());
    }

    // ---------------- Apariencia ----------------

    private void configurarApariencia() {
        Context ctx = requireContext();

        // Control segmentado del tema: 0 = sistema, 1 = claro, 2 = oscuro
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
        binding.swSinImagenes.setOnCheckedChangeListener((boton, activo) -> {
            Preferencias.setSinImagenes(ctx, activo);
            Imagenes.mostrar(binding.ivFoto, Sesion.getUsuario().fotoUrl);
        });

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

    private void mostrarMeta() {
        if (binding == null) return;
        double meta = Sesion.getOptica().metaSemanal;
        binding.tvMetaActual.setText(meta > 0 ? Formato.moneda(meta) : "Sin definir");
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

    // ---------------- Foto de perfil ----------------

    private void subirFotoDePerfil(Uri foto) {
        String uid = Sesion.getUsuario().id;
        mostrarCargando(true);
        Imagenes.subir(foto, "users/" + uid + "/perfil.jpg",
                url -> FirebaseFirestore.getInstance().collection("users").document(uid)
                        .update("fotoUrl", url)
                        .addOnSuccessListener(x -> {
                            Sesion.getUsuario().fotoUrl = url;
                            mostrarCargando(false);
                            if (binding != null) Imagenes.mostrar(binding.ivFoto, url);
                            mensaje("Foto actualizada");
                        })
                        .addOnFailureListener(e -> {
                            mostrarCargando(false);
                            mensaje("No se pudo guardar la foto: " + e.getMessage());
                        }),
                e -> {
                    mostrarCargando(false);
                    mensaje("No se pudo subir la foto a Storage: " + e.getMessage());
                });
    }

    // ---------------- Opciones del administrador ----------------

    private void copiarCodigo() {
        ClipboardManager portapapeles = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        portapapeles.setPrimaryClip(ClipData.newPlainText("Código", Sesion.getOptica().codigoInvitacion));
        mensaje("Código copiado");
    }

    /** Lista el personal de la óptica; al tocar a alguien se le puede cambiar el rol. */
    private void mostrarPersonal() {
        FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("opticaId", Sesion.getOptica().id)
                .get()
                .addOnSuccessListener(resultado -> {
                    if (!isAdded()) return;
                    List<Usuario> personal = resultado.toObjects(Usuario.class);
                    String[] renglones = new String[personal.size()];
                    for (int i = 0; i < personal.size(); i++) {
                        Usuario p = personal.get(i);
                        renglones[i] = p.nombre + " — " + Usuario.nombreRol(p.rol);
                    }
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Personal (" + personal.size() + ")")
                            .setItems(renglones, (d, posicion) -> cambiarRol(personal.get(posicion)))
                            .setPositiveButton("Cerrar", null)
                            .show();
                })
                .addOnFailureListener(e -> mensaje("No se pudo leer el personal: " + e.getMessage()));
    }

    private void cambiarRol(Usuario persona) {
        if (persona.id.equals(Sesion.getUsuario().id)) {
            mensaje("No puedes cambiar tu propio rol");
            return;
        }
        String[] roles = getResources().getStringArray(R.array.roles_todos);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Rol de " + persona.nombre)
                .setItems(roles, (d, posicion) -> FirebaseFirestore.getInstance().collection("users")
                        .document(persona.id)
                        .update("rol", Usuario.rolDesdeTexto(roles[posicion]))
                        .addOnSuccessListener(x -> mensaje("Rol actualizado"))
                        .addOnFailureListener(e -> mensaje("No se pudo cambiar: " + e.getMessage())))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Resumen de las ventas de hoy: cuántas, total vendido y dinero cobrado por método de pago. */
    private void corteDeCaja() {
        Sesion.coleccion("ventas")
                .whereGreaterThanOrEqualTo("fecha", Fechas.inicioDelDia())
                .get()
                .addOnSuccessListener(resultado -> {
                    if (!isAdded()) return;
                    double vendido = 0;
                    double cobrado = 0;
                    Map<String, Double> porMetodo = new LinkedHashMap<>();
                    for (DocumentSnapshot d : resultado.getDocuments()) {
                        Double total = d.getDouble("total");
                        Double anticipo = d.getDouble("anticipo");
                        String metodo = d.getString("metodoPago");
                        if (total != null) vendido += total;
                        if (anticipo != null) {
                            cobrado += anticipo;
                            String clave = metodo != null ? metodo : "Otro";
                            Double previo = porMetodo.get(clave);
                            porMetodo.put(clave, (previo != null ? previo : 0) + anticipo);
                        }
                    }
                    StringBuilder texto = new StringBuilder();
                    texto.append("Ventas: ").append(resultado.size())
                            .append("\nTotal vendido: ").append(Formato.moneda(vendido))
                            .append("\nDinero cobrado hoy: ").append(Formato.moneda(cobrado));
                    for (Map.Entry<String, Double> e : porMetodo.entrySet()) {
                        texto.append("\n   • ").append(e.getKey()).append(": ").append(Formato.moneda(e.getValue()));
                    }
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Corte de caja · " + Fechas.corta(Fechas.inicioDelDia()))
                            .setMessage(texto.toString())
                            .setPositiveButton("Cerrar", null)
                            .show();
                })
                .addOnFailureListener(e -> mensaje("No se pudo calcular el corte: " + e.getMessage()));
    }

    private void editarMeta() {
        EditText campo = new EditText(requireContext());
        campo.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        campo.setHint("Meta en pesos");
        double actual = Sesion.getOptica().metaSemanal;
        if (actual > 0) campo.setText(String.format(Locale.US, "%.2f", actual));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Meta de ventas semanal")
                .setView(campo)
                .setPositiveButton("Guardar", (d, w) -> {
                    Double meta = Formato.leerNumero(campo.getText().toString());
                    if (meta == null || meta < 0) {
                        mensaje("Meta no válida");
                        return;
                    }
                    Sesion.opticaRef().update("metaSemanal", meta)
                            .addOnSuccessListener(x -> {
                                Sesion.getOptica().metaSemanal = meta;
                                mostrarMeta();
                                mensaje("Meta actualizada");
                            })
                            .addOnFailureListener(e -> mensaje("No se pudo guardar: " + e.getMessage()));
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---------------- Sesión ----------------

    private void cerrarSesion() {
        FirebaseAuth.getInstance().signOut();
        Sesion.cerrar();
        ((MainActivity) requireActivity()).irAInicio(false);
    }

    private void mostrarCargando(boolean cargando) {
        if (binding == null) return;
        binding.progreso.setVisibility(cargando ? View.VISIBLE : View.GONE);
        binding.btnFoto.setEnabled(!cargando);
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
