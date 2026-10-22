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
import android.util.Base64;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.widget.ImageViewCompat;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Preferencias;

/**
 * Fotos de la app: cámara, conversión para guardarlas en Firestore y carga con Glide.
 *
 * Las fotos (perfil, logotipo y productos) NO van a Firebase Storage: se reducen, se comprimen
 * en JPEG y se guardan como texto dentro del mismo documento de Firestore, en el campo
 * fotoUrl o logoUrl, con el formato "data:image/jpeg;base64,...". Glide sabe mostrar ese
 * texto igual que una URL, así que las pantallas no cambian.
 */
public final class Imagenes {

    /** Lado más largo (en píxeles) según el uso. Más chico = documento más ligero. */
    public static final int LADO_PERFIL = 320;
    public static final int LADO_LOGO = 400;
    public static final int LADO_PRODUCTO = 640;

    /**
     * Tope del JPEG ya comprimido: 120 KB (unos 160 KB en Base64). Un documento de Firestore
     * puede pesar hasta 1 MB, así que queda espacio de sobra para los demás campos.
     */
    private static final int MAXIMO_BYTES = 120 * 1024;
    private static final String PREFIJO = "data:image/jpeg;base64,";

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

    // ---------------- Convertir para Firestore ----------------

    /**
     * Convierte la foto elegida (cámara o galería; JPEG, PNG, WebP o HEIC) en el texto
     * "data:image/jpeg;base64,..." listo para guardarlo en Firestore. No sube nada: quien la llama
     * guarda el texto con update() o set(). El resultado llega en el hilo principal.
     *
     * @param ladoMaximo LADO_PERFIL, LADO_LOGO o LADO_PRODUCTO
     */
    public static void convertir(Uri imagen, int ladoMaximo,
                                 OnSuccessListener<String> alTerminar, OnFailureListener alFallar) {
        // Contexto de la aplicación: sigue disponible aunque el usuario cambie de pantalla
        Context ctx = FirebaseApp.getInstance().getApplicationContext();
        HILO.execute(() -> {
            try {
                byte[] jpeg = prepararJpeg(ctx.getContentResolver(), imagen, ladoMaximo);
                String texto = PREFIJO + Base64.encodeToString(jpeg, Base64.NO_WRAP);
                PRINCIPAL.post(() -> alTerminar.onSuccess(texto));
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                Exception causa = new Exception("No se pudo leer la imagen elegida. Prueba con otra foto.", e);
                PRINCIPAL.post(() -> alFallar.onFailure(causa));
            }
        });
    }

    /** Lee la imagen, la reduce, la endereza y la comprime hasta que quede por debajo de MAXIMO_BYTES. */
    private static byte[] prepararJpeg(ContentResolver cr, Uri uri, int ladoMaximo) throws IOException {
        Bitmap original = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? decodificarConImageDecoder(cr, uri, ladoMaximo)
                : decodificarConBitmapFactory(cr, uri, ladoMaximo);

        // JPEG no tiene transparencia: un PNG con fondo transparente quedaría negro, así que va sobre blanco
        Bitmap imagen = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas lienzo = new Canvas(imagen);
        lienzo.drawColor(Color.WHITE);
        lienzo.drawBitmap(original, 0, 0, null);
        original.recycle();

        // Primero baja la calidad; si aun así pesa mucho, reduce el tamaño un 20 % cada vez
        int calidad = 80;
        byte[] jpeg = comprimir(imagen, calidad);
        while (jpeg.length > MAXIMO_BYTES && calidad > 50) {
            calidad -= 10;
            jpeg = comprimir(imagen, calidad);
        }
        while (jpeg.length > MAXIMO_BYTES && imagen.getWidth() > 64 && imagen.getHeight() > 64) {
            Bitmap menor = Bitmap.createScaledBitmap(imagen,
                    Math.round(imagen.getWidth() * 0.8f), Math.round(imagen.getHeight() * 0.8f), true);
            imagen.recycle();
            imagen = menor;
            jpeg = comprimir(imagen, calidad);
        }
        imagen.recycle();
        return jpeg;
    }

    /**
     * Convierte el texto "data:image/jpeg;base64,..." guardado en Firestore en un Bitmap
     * (lo usa el PDF para dibujar el logotipo). Devuelve null si está vacío o no es una imagen guardada así.
     */
    @Nullable
    public static Bitmap decodificar(@Nullable String datos) {
        if (datos == null || !datos.startsWith("data:image")) return null;
        int coma = datos.indexOf(',');
        if (coma < 0) return null;
        try {
            byte[] bytes = Base64.decode(datos.substring(coma + 1), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] comprimir(Bitmap imagen, int calidad) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        imagen.compress(Bitmap.CompressFormat.JPEG, calidad, salida);
        return salida.toByteArray();
    }

    /** Android 9+: lee JPEG, PNG, WebP y HEIC, y ya aplica el giro guardado en la foto (EXIF). */
    private static Bitmap decodificarConImageDecoder(ContentResolver cr, Uri uri, int ladoMaximo) throws IOException {
        ImageDecoder.Source fuente = ImageDecoder.createSource(cr, uri);
        return ImageDecoder.decodeBitmap(fuente, (decodificador, info, origen) -> {
            int ancho = info.getSize().getWidth();
            int alto = info.getSize().getHeight();
            float escala = Math.min(1f, ladoMaximo / (float) Math.max(ancho, alto));
            if (escala < 1f) {
                decodificador.setTargetSize(Math.max(1, Math.round(ancho * escala)), Math.max(1, Math.round(alto * escala)));
            }
            // En memoria normal (no de la GPU) para poder dibujarla y comprimirla
            decodificador.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
    }

    /** Android 8: BitmapFactory reduce al leer (inSampleSize) y el giro se aplica a mano con ExifInterface. */
    private static Bitmap decodificarConBitmapFactory(ContentResolver cr, Uri uri, int ladoMaximo) throws IOException {
        BitmapFactory.Options medidas = new BitmapFactory.Options();
        medidas.inJustDecodeBounds = true;
        try (InputStream entrada = abrir(cr, uri)) {
            BitmapFactory.decodeStream(entrada, null, medidas);
        }
        int muestra = 1;
        while (Math.max(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= ladoMaximo) {
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
        float escala = Math.min(1f, ladoMaximo / (float) Math.max(bmp.getWidth(), bmp.getHeight()));
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

    // ---------------- Mostrar ----------------

    /**
     * Muestra una foto guardada (texto "data:image/jpeg;base64,..." o una URL de internet).
     * Si no hay foto o está activo el modo sin imágenes, muestra un ícono gris centrado en su lugar.
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

    /** Muestra una foto recién tomada o elegida de la galería (todavía no guardada). */
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
