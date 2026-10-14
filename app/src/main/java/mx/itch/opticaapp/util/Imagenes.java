package mx.itch.opticaapp.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.widget.ImageViewCompat;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.File;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Preferencias;

/** Cámara, subida a Firebase Storage y carga de imágenes con Glide. */
public final class Imagenes {

    private Imagenes() {
    }

    /**
     * Crea un archivo vacío en la caché y devuelve su Uri para que la cámara guarde ahí la foto.
     * Se usa con ActivityResultContracts.TakePicture.
     */
    public static Uri nuevaUriParaFoto(Context context) {
        File carpeta = new File(context.getCacheDir(), "imagenes");
        if (!carpeta.exists()) {
            carpeta.mkdirs();
        }
        File archivo = new File(carpeta, "foto_" + System.currentTimeMillis() + ".jpg");
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", archivo);
    }

    /**
     * Sube la imagen a Firebase Storage en la ruta indicada y devuelve la URL de descarga.
     * Ejemplo de ruta: "opticas/{opticaId}/productos/{productoId}.jpg"
     */
    public static void subir(Uri imagen, String ruta, OnSuccessListener<String> alTerminar, OnFailureListener alFallar) {
        StorageReference ref = FirebaseStorage.getInstance().getReference(ruta);
        // Se indica que es imagen porque las reglas de Storage solo aceptan imágenes
        StorageMetadata datos = new StorageMetadata.Builder().setContentType("image/jpeg").build();
        ref.putFile(imagen, datos)
                .continueWithTask(tarea -> {
                    if (!tarea.isSuccessful() && tarea.getException() != null) {
                        throw tarea.getException();
                    }
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> alTerminar.onSuccess(uri.toString()))
                .addOnFailureListener(alFallar);
    }

    /**
     * Muestra una foto desde internet. Si no hay URL o está activo el modo sin imágenes,
     * muestra un ícono gris centrado en su lugar.
     */
    public static void mostrar(ImageView vista, String url) {
        mostrar(vista, url, R.drawable.ic_imagen);
    }

    /** Igual que mostrar(), pero eligiendo el ícono para cuando no hay foto (por ejemplo, ic_perfil). */
    public static void mostrar(ImageView vista, String url, @DrawableRes int iconoSinFoto) {
        Context c = vista.getContext();
        if (url == null || url.isEmpty() || Preferencias.sinImagenes(c)) {
            Glide.with(vista).clear(vista);
            mostrarIcono(vista, iconoSinFoto);
            return;
        }
        prepararParaFoto(vista);
        Glide.with(vista).load(url).error(iconoSinFoto).into(vista);
    }

    /** Muestra una foto recién tomada o elegida de la galería (todavía no subida). */
    public static void mostrarUri(ImageView vista, Uri uri) {
        prepararParaFoto(vista);
        Glide.with(vista).load(uri).into(vista);
    }

    /** Ícono gris centrado que ocupa cerca del 40 % del lado más corto, para cuando no hay foto. */
    private static void mostrarIcono(ImageView vista, @DrawableRes int icono) {
        vista.setTag(R.id.imagen_es_icono, Boolean.TRUE);
        vista.setScaleType(ImageView.ScaleType.FIT_CENTER);
        vista.setImageResource(icono);
        ImageViewCompat.setImageTintList(vista,
                ColorStateList.valueOf(ContextCompat.getColor(vista.getContext(), R.color.texto_tenue)));
        Runnable ajustar = () -> {
            // Si mientras tanto ya se puso una foto (listas que reciclan renglones), no se toca
            if (!Boolean.TRUE.equals(vista.getTag(R.id.imagen_es_icono))) return;
            int ancho = vista.getWidth();
            int alto = vista.getHeight();
            int lado = Math.min(ancho, alto) * 2 / 5;
            if (lado <= 0) return;
            int margenX = (ancho - lado) / 2;
            int margenY = (alto - lado) / 2;
            vista.setPadding(margenX, margenY, margenX, margenY);
        };
        if (vista.getWidth() > 0) {
            ajustar.run();
        } else {
            vista.post(ajustar);   // todavía no se mide: se ajusta en cuanto tenga tamaño
        }
    }

    /** Quita el margen y el color del ícono para que la foto ocupe todo el espacio. */
    private static void prepararParaFoto(ImageView vista) {
        vista.setTag(R.id.imagen_es_icono, Boolean.FALSE);
        vista.setPadding(0, 0, 0, 0);
        vista.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ImageViewCompat.setImageTintList(vista, null);
    }
}
