package mx.itch.opticaapp.ui.administracion;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentPersonalBinding;
import mx.itch.opticaapp.model.Usuario;
import mx.itch.opticaapp.util.Animaciones;
import mx.itch.opticaapp.util.HojaOpciones;

/**
 * Administración · Personal (solo administrador): lista en tiempo real de quién trabaja en la óptica.
 * Al tocar a alguien se le puede cambiar el rol o mandarle un correo para crear otra contraseña.
 * El botón + abre "Nueva cuenta".
 */
public class PersonalFragment extends Fragment {

    private FragmentPersonalBinding binding;
    private PersonalAdapter adaptador;
    private ListenerRegistration escucha;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPersonalBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!Sesion.estaActiva()) return;

        adaptador = new PersonalAdapter(Sesion.getUsuario().id, this::opcionesDePersona);
        binding.rvPersonal.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPersonal.setAdapter(adaptador);
        binding.tvResumen.setText("Código " + Sesion.getOptica().codigoInvitacion);

        binding.fabAgregar.setVisibility(Sesion.esAdmin() ? View.VISIBLE : View.GONE);
        binding.fabAgregar.setOnClickListener(v -> NavHostFragment.findNavController(this)
                .navigate(R.id.action_personal_to_nuevaCuenta));

        // Animación de entrada: título en cascada, hoja que sube y el botón + con rebote
        if (Animaciones.entrada(this, binding.encabezado, binding.hoja)) {
            Animaciones.aparecer(binding.fabAgregar, 450);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!Sesion.estaActiva()) return;
        // Tiempo real: si alguien se une con el código o se crea una cuenta, aparece solo
        escucha = FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("opticaId", Sesion.getOptica().id)
                .addSnapshotListener((resultado, error) -> {
                    if (binding == null) return;
                    if (error != null) {
                        mensaje("No se pudo leer el personal: " + error.getMessage());
                        return;
                    }
                    if (resultado == null) return;
                    mostrar(resultado.toObjects(Usuario.class));
                });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (escucha != null) {
            escucha.remove();
            escucha = null;
        }
    }

    /** Ordena: administradores, optometristas y vendedores; dentro de cada rol por nombre. */
    private void mostrar(List<Usuario> personas) {
        List<Usuario> orden = new ArrayList<>(personas);
        orden.sort((a, b) -> {
            int porRol = Integer.compare(pesoRol(a.rol), pesoRol(b.rol));
            if (porRol != 0) return porRol;
            String na = a.nombre != null ? a.nombre : "";
            String nb = b.nombre != null ? b.nombre : "";
            return na.compareToIgnoreCase(nb);
        });
        adaptador.mostrar(orden);
        Animaciones.listaUnaVez(this, binding.rvPersonal);   // las personas entran una tras otra

        int total = orden.size();
        binding.tvResumen.setText(total + (total == 1 ? " persona" : " personas")
                + " · Código " + Sesion.getOptica().codigoInvitacion);
        binding.tvVacio.setVisibility(total <= 1 ? View.VISIBLE : View.GONE);
    }

    private static int pesoRol(String rol) {
        if (Usuario.ROL_ADMIN.equals(rol)) return 0;
        if (Usuario.ROL_OPTOMETRISTA.equals(rol)) return 1;
        return 2;
    }

    // ---------------- Opciones de una persona ----------------

    private void opcionesDePersona(Usuario persona) {
        if (!Sesion.esAdmin()) return;
        if (persona.id != null && persona.id.equals(Sesion.getUsuario().id)) {
            mensaje("Tus datos se cambian en Mi perfil. Tu rol no lo puedes cambiar tú mismo.");
            return;
        }
        HojaOpciones.con(requireContext(), persona.nombre)
                .subtitulo(Usuario.nombreRol(persona.rol) + (persona.email != null ? " · " + persona.email : ""))
                .opcion(R.drawable.ic_perfil, "Cambiar rol", "Define qué secciones puede usar", () -> cambiarRol(persona))
                .opcion(R.drawable.ic_correo, "Enviarle correo para crear otra contraseña",
                        "Por si olvidó la suya", () -> restablecerContrasena(persona))
                .mostrar();
    }

    private void cambiarRol(Usuario persona) {
        // El dueño de la óptica (quien la registró) siempre es administrador
        if (persona.id != null && persona.id.equals(Sesion.getOptica().ownerId)) {
            mensaje("No se puede cambiar el rol del dueño de la óptica");
            return;
        }
        String[] roles = getResources().getStringArray(R.array.roles_todos);
        int[] iconos = {R.drawable.ic_llave, R.drawable.ic_pacientes, R.drawable.ic_ventas};
        String[] detalles = {
                "Todo, incluida Administración y el corte de caja",
                "Pacientes, expedientes y laboratorio",
                "Ventas, inventario y consulta de pacientes"};
        HojaOpciones hoja = HojaOpciones.con(requireContext(), "Rol de " + persona.nombre)
                .subtitulo("Elige qué puede hacer en la app");
        for (int i = 0; i < roles.length; i++) {
            String nuevo = Usuario.rolDesdeTexto(roles[i]);
            String nombre = roles[i];
            hoja.elegible(iconos[i], nombre, detalles[i], nuevo.equals(persona.rol),
                    () -> guardarRol(persona, nuevo, nombre));
        }
        hoja.mostrar();
    }

    private void guardarRol(Usuario persona, String nuevo, String nombre) {
        if (nuevo.equals(persona.rol)) return;
        // Las reglas de Firestore solo dejan hacer esto al administrador de la misma óptica
        FirebaseFirestore.getInstance().collection("users").document(persona.id)
                .update("rol", nuevo)
                .addOnSuccessListener(x -> mensaje("Ahora " + persona.nombre + " es " + nombre))
                .addOnFailureListener(e -> mensaje("No se pudo cambiar: " + e.getMessage()));
    }

    /** Por si a alguien se le olvida su contraseña: Firebase le manda un enlace a su correo. */
    private void restablecerContrasena(Usuario persona) {
        if (persona.email == null || persona.email.isEmpty()) {
            mensaje("Esta persona no tiene correo registrado");
            return;
        }
        FirebaseAuth.getInstance().sendPasswordResetEmail(persona.email)
                .addOnSuccessListener(x -> mensaje("Se envió el correo a " + persona.email))
                .addOnFailureListener(e -> mensaje("No se pudo enviar: " + e.getMessage()));
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
        adaptador = null;
    }
}
