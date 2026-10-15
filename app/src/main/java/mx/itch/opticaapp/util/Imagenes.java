package mx.itch.opticaapp.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.widget.ImageViewCompat;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageException;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Preferencias;

/** Cámara, subida a Firebase Storage y carga de imágenes con Glide. */
public final class Imagenes {

    /** Lado más largo de la imagen que se sube (en píxeles). Sobra para perfil, logotipo y productos. */
    private static final int LADO_MAXIMO = 1600;
    private static final int CALIDAD_JPEG = 85;

    /** Hilo aparte para leer y comprimir la imagen sin congelar la pantalla. */
    private static final ExecutorService HILO = Executors.newSingleThreadExecutor();
    private static final Handler PRINCIPAL = new Handler(Looper.getMainLooper());

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
     *
     * Antes de subirla la convierte en un JPEG de máximo 1600 px (normalmente 150–400 KB):
     * las fotos de la galería pueden pesar más de 10 MB (el límite de storage.rules),
     * venir en HEIC, PNG o WebP, o estar giradas. Así siempre se sube una imagen chica,
     * derecha y que de verdad es JPEG.
     */
    public static void subir(Uri imagen, String ruta, OnSuccessListener<String> alTerminar, OnFailureListener alFallar) {
        Context ctx = FirebaseApp.getInstance().getApplicationContext();
        HILO.execute(() -> {
            byte[] jpeg;
            try {
                jpeg = prepararJpeg(ctx.getContentResolver(), imagen);
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                Exception causa = new Exception("No se pudo leer la imagen elegida. Prueba con otra foto.", e);
                PRINCIPAL.post(() -> alFallar.onFailure(causa));
                return;
            }
            PRINCIPAL.post(() -> subirBytes(jpeg, ruta, alTerminar, alFallar));
        });
    }

    private static void subirBytes(byte[] jpeg, String ruta, OnSuccessListener<String> alTerminar, OnFailureListener alFallar) {
        StorageReference ref = FirebaseStorage.getInstance().getReference(ruta);
        // Se indica que es imagen porque las reglas de Storage solo aceptan imágenes
        StorageMetadata datos = new StorageMetadata.Builder().setContentType("image/jpeg").build();
        ref.putBytes(jpeg, datos)
                .continueWithTask(tarea -> {
                    if (!tarea.isSuccessful() && tarea.getException() != null) {
                        throw tarea.getException();
                    }
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> alTerminar.onSuccess(uri.toString()))
                .addOnFailureListener(e -> alFallar.onFailure(new Exception(explicarError(e), e)));
    }

    /** Lee la imagen (cualquier formato), la reduce, la endereza y la comprime en JPEG. */
    private static byte[] prepararJpeg(ContentResolver cr, Uri uri) throws IOException {
        Bitmap original = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? decodificarConImageDecoder(cr, uri)
                : decodificarConBitmapFactory(cr, uri);

        // JPEG no tiene transparencia: un PNG con fondo transparente quedaría negro, así que va sobre blanco
        Bitmap fondoBlanco = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas lienzo = new Canvas(fondoBlanco);
        lienzo.drawColor(Color.WHITE);
        lienzo.drawBitmap(original, 0, 0, null);
        original.recycle();

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        fondoBlanco.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida);
        fondoBlanco.recycle();
        return salida.toByteArray();
    }

    /** Android 9+: lee JPEG, PNG, WebP y HEIC, y ya aplica el giro guardado en la foto (EXIF). */
    private static Bitmap decodificarConImageDecoder(ContentResolver cr, Uri uri) throws IOException {
        ImageDecoder.Source fuente = ImageDecoder.createSource(cr, uri);
        return ImageDecoder.decodeBitmap(fuente, (decodificador, info, origen) -> {
            int ancho = info.getSize().getWidth();
            int alto = info.getSize().getHeight();
            float escala = Math.min(1f, LADO_MAXIMO / (float) Math.max(ancho, alto));
            if (escala < 1f) {
                decodificador.setTargetSize(Math.max(1, Math.round(ancho * escala)), Math.max(1, Math.round(alto * escala)));
            }
            // En memoria normal (no de la GPU) para poder dibujarla y comprimirla
            decodificador.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
    }

    /** Android 8: BitmapFactory reduce al leer (inSampleSize) y el giro se aplica a mano con ExifInterface. */
    private static Bitmap decodificarConBitmapFactory(ContentResolver cr, Uri uri) throws IOException {
        BitmapFactory.Options medidas = new BitmapFactory.Options();
        medidas.inJustDecodeBounds = true;
        try (InputStream entrada = abrir(cr, uri)) {
            BitmapFactory.decodeStream(entrada, null, medidas);
        }
        int muestra = 1;
        while (Math.max(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= LADO_MAXIMO) {
            muestra *= 2;
        }
        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inSampleSize = muestra;
        Bitmap bmp;
        try (InputStream entrada = abrir(cr, uri)) {
            bmp = BitmapFactory.decodeStream(entrada, null, opciones);
        }
        if (bmp == null) throw new IOException("Formato de imagen no compatible");

        int grados = 0;
        try (InputStream entrada = abrir(cr, uri)) {
            int orientacion = new ExifInterface(entrada)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orientacion == ExifInterface.ORIENTATION_ROTATE_90) grados = 90;
            else if (orientacion == ExifInterface.ORIENTATION_ROTATE_180) grados = 180;
            else if (orientacion == ExifInterface.ORIENTATION_ROTATE_270) grados = 270;
        } catch (IOException ignorada) {
            // Sin datos EXIF (PNG, capturas de pantalla): se deja como está
        }
        float escala = Math.min(1f, LADO_MAXIMO / (float) Math.max(bmp.getWidth(), bmp.getHeight()));
        if (grados == 0 && escala == 1f) return bmp;
        Matrix ajuste = new Matrix();
        ajuste.postScale(escala, escala);
        ajuste.postRotate(grados);
        Bitmap ajustada = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), ajuste, true);
        if (ajustada != bmp) bmp.recycle();
        return ajustada;
    }

    private static InputStream abrir(ContentResolver cr, Uri uri) throws IOException {
        InputStream entrada = cr.openInputStream(uri);
        if (entrada == null) throw new IOException("No se pudo abrir la imagen");
        return entrada;
    }

    /** Traduce los errores de Storage a algo que se entienda en un Toast. */
    public static String explicarError(Exception e) {
        if (e instanceof StorageException) {
            StorageException se = (StorageException) e;
            if (se.getHttpResultCode() == 402) {
                return "Storage necesita el plan Blaze en este proyecto de Firebase";
            }
            switch (se.getErrorCode()) {
                case StorageException.ERROR_NOT_AUTHORIZED:
                    return "Storage rechazó la imagen: revisa que las reglas de firebase/storage.rules estén publicadas";
                case StorageException.ERROR_NOT_AUTHENTICATED:
                    return "Tu sesión expiró; vuelve a iniciar sesión";
                case StorageException.ERROR_BUCKET_NOT_FOUND:
                case StorageException.ERROR_PROJECT_NOT_FOUND:
                case StorageException.ERROR_OBJECT_NOT_FOUND:
                    return "Storage no está activado en Firebase (Compilación → Storage; requiere plan Blaze)";
                case StorageException.ERROR_QUOTA_EXCEEDED:
                    return "Se terminó la cuota de Storage del proyecto";
                case StorageException.ERROR_RETRY_LIMIT_EXCEEDED:
                    return "Sin conexión a internet; inténtalo de nuevo";
                default:
                    break;
            }
        }
        return e.getMessage() != null ? e.getMessage() : "Error desconocido";
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
