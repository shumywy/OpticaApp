package mx.itch.opticaapp.util;

import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.model.Documento;
import mx.itch.opticaapp.model.Usuario;

/**
 * Registra en Firestore cada PDF que genera la app (recetas y notas de venta):
 * /opticas/{opticaId}/documentos/{documentoId}, con quién lo generó, cuándo y a qué paciente o
 * venta corresponde. Si el archivo pesa 600 KB o menos, también se guarda completo en Base64.
 */
public final class Documentos {

    /** Con este tope el documento queda por debajo del límite de 1 MB de Firestore. */
    private static final long MAXIMO_BYTES = 600 * 1024;

    private Documentos() {
    }

    public static void registrar(File pdf, String tipo, String descripcion, String referenciaId, String pacienteNombre) {
        if (!Sesion.estaActiva()) return;
        Documento d = new Documento();
        d.tipo = tipo;
        d.descripcion = descripcion;
        d.referenciaId = referenciaId != null ? referenciaId : "";
        d.pacienteNombre = pacienteNombre != null ? pacienteNombre : "";
        d.nombreArchivo = pdf.getName();
        d.tamanoBytes = pdf.length();
        d.pdf = "";
        if (d.tamanoBytes <= MAXIMO_BYTES) {
            try {
                byte[] bytes = Files.readAllBytes(pdf.toPath());
                d.pdf = "data:application/pdf;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
                d.archivoIncluido = true;
            } catch (IOException e) {
                Log.w("Documentos", "No se pudo leer el PDF para guardarlo", e);
            }
        }
        Usuario u = Sesion.getUsuario();
        d.generadoPorId = u.id;
        d.generadoPorNombre = u.nombre;
        // La fecha la pone el servidor (@ServerTimestamp). Si falla, el PDF se comparte de todos modos.
        Sesion.coleccion("documentos").add(d)
                .addOnFailureListener(e -> Log.w("Documentos", "No se pudo registrar el PDF", e));
    }
}
