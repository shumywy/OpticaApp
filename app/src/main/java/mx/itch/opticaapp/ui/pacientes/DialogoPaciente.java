package mx.itch.opticaapp.ui.pacientes;

import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Date;

import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.DialogPacienteBinding;
import mx.itch.opticaapp.model.Paciente;
import mx.itch.opticaapp.util.Aviso;
import mx.itch.opticaapp.util.Campos;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.Validaciones;

/**
 * Diálogo para registrar un paciente nuevo o editar los datos de uno existente
 * (nombre, teléfono y edad). Lo usan la lista de Pacientes y el Expediente.
 *
 * Antes de guardar: valida los tres campos y busca en Firestore si ya hay otro paciente con el
 * mismo teléfono o el mismo nombre; si lo hay, pide confirmación para no duplicar expedientes.
 */
public final class DialogoPaciente {

    private DialogoPaciente() {
    }

    /** @param existente null para registrar uno nuevo; el paciente a editar en otro caso. */
    public static void mostrar(Fragment fragmento, @Nullable Paciente existente) {
        DialogPacienteBinding d = DialogPacienteBinding.inflate(fragmento.getLayoutInflater());
        boolean edicion = existente != null;
        if (edicion) {
            d.etNombre.setText(existente.nombre);
            d.etTelefono.setText(Validaciones.soloDigitos(existente.telefono));
            if (existente.edad > 0) d.etEdad.setText(String.valueOf(existente.edad));
        }

        AlertDialog dialogo = new MaterialAlertDialogBuilder(fragmento.requireContext())
                .setTitle(edicion ? "Editar paciente" : "Nuevo paciente")
                .setView(d.getRoot())
                .setPositiveButton("Guardar", null)   // se configura abajo para validar antes de cerrar
                .setNegativeButton("Cancelar", null)
                .create();

        dialogo.setOnShowListener(x -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nombre = d.etNombre.getText().toString().trim().replaceAll("\\s+", " ");
            String telefono = d.etTelefono.getText().toString();
            String edadTexto = d.etEdad.getText().toString();

            // Se revisan los tres campos para marcar todos los errores a la vez
            boolean ok = marcar(d.etNombre, Validaciones.nombrePersona(nombre));
            ok &= marcar(d.etTelefono, Validaciones.telefono(telefono, true));
            ok &= marcar(d.etEdad, Validaciones.edad(edadTexto));
            if (!ok) return;

            String digitos = Validaciones.soloDigitos(telefono);   // se guardan solo los 10 dígitos
            int edad = Formato.leerEntero(edadTexto);
            if (edicion && nombre.equals(existente.nombre) && digitos.equals(existente.telefono)
                    && edad == existente.edad) {
                dialogo.dismiss();   // no cambió nada: no se escribe en la base de datos
                return;
            }

            v.setEnabled(false);
            buscarParecido(nombre, digitos, edicion ? existente.id : null).addOnCompleteListener(tarea -> {
                v.setEnabled(true);
                DocumentSnapshot parecido = tarea.isSuccessful() ? tarea.getResult() : null;
                Runnable guardar = () -> {
                    if (edicion) {
                        actualizar(fragmento, existente.id, nombre, digitos, edad);
                    } else {
                        registrar(fragmento, nombre, digitos, edad);
                    }
                    dialogo.dismiss();
                };
                if (parecido == null) {
                    guardar.run();
                    return;
                }
                boolean mismoTelefono = digitos.equals(parecido.getString("telefono"));
                new MaterialAlertDialogBuilder(fragmento.requireContext())
                        .setTitle("¿Paciente repetido?")
                        .setMessage("Ya existe " + parecido.getString("nombre")
                                + (mismoTelefono ? " con el teléfono " + Formato.telefono(digitos) : " con el mismo nombre")
                                + ". ¿Guardar de todos modos?")
                        .setPositiveButton("Guardar", (dd, w) -> guardar.run())
                        .setNegativeButton("Revisar", null)
                        .show();
            });
        }));
        dialogo.show();
    }

    /**
     * Otro paciente con el mismo teléfono o el mismo nombre (sin contar al que se edita), o null.
     * Se hacen las dos consultas a la vez; si no hay conexión, Firestore responde con lo guardado en el teléfono.
     */
    private static Task<DocumentSnapshot> buscarParecido(String nombre, String telefono, @Nullable String idPropio) {
        Task<QuerySnapshot> porTelefono = Sesion.coleccion("pacientes").whereEqualTo("telefono", telefono).limit(3).get();
        Task<QuerySnapshot> porNombre = Sesion.coleccion("pacientes").whereEqualTo("nombre", nombre).limit(3).get();
        return Tasks.whenAllSuccess(porTelefono, porNombre).continueWith(t -> {
            for (Object resultado : t.getResult()) {
                for (DocumentSnapshot doc : ((QuerySnapshot) resultado).getDocuments()) {
                    if (!doc.getId().equals(idPropio)) return doc;
                }
            }
            return null;
        });
    }

    private static void registrar(Fragment fragmento, String nombre, String telefono, int edad) {
        Paciente p = new Paciente();
        p.nombre = nombre;
        p.telefono = telefono;
        p.edad = edad;
        p.fechaRegistro = new Date();
        Sesion.coleccion("pacientes").add(p)
                .addOnFailureListener(e -> mensaje(fragmento, "No se pudo guardar: " + e.getMessage()));
        Aviso.mostrar(fragmento.getActivity(), "Paciente registrado");
    }

    /** Solo cambia nombre, teléfono y edad; la graduación y el historial no se tocan. */
    private static void actualizar(Fragment fragmento, String id, String nombre, String telefono, int edad) {
        Sesion.coleccion("pacientes").document(id)
                .update("nombre", nombre, "telefono", telefono, "edad", edad)
                .addOnFailureListener(e -> mensaje(fragmento, "No se pudo actualizar: " + e.getMessage()));
        Aviso.mostrar(fragmento.getActivity(), "Datos del paciente actualizados");
    }

    /** Pone o quita el error de un campo. Devuelve true si no hay error. */
    private static boolean marcar(EditText campo, String error) {
        Campos.error(campo, error);
        return error == null;
    }

    private static void mensaje(Fragment fragmento, String texto) {
        if (fragmento.getContext() != null) {
            Toast.makeText(fragmento.getContext(), texto, Toast.LENGTH_LONG).show();
        }
    }
}
