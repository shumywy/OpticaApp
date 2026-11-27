package mx.itch.opticaapp.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/** Códigos QR con ZXing (solo el núcleo: arma la matriz de cuadritos, no usa la cámara). */
public final class Qr {

    /** Margen blanco alrededor del código en la imagen de pantalla, en módulos (cuadritos). */
    private static final int MARGEN_PANTALLA = 2;

    private Qr() {
    }

    /** Matriz sin margen: cada celda es un módulo. Devuelve null si el texto no se pudo codificar. */
    @Nullable
    public static BitMatrix matriz(String texto) {
        Map<EncodeHintType, Object> pistas = new EnumMap<>(EncodeHintType.class);
        // Nivel M: aguanta ~15 % de daño (papel térmico gastado o arrugado)
        pistas.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        pistas.put(EncodeHintType.MARGIN, 0);
        pistas.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        try {
            return new QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0, pistas);
        } catch (WriterException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Dibuja el código en un Canvas (el del PDF) como rectángulos: queda nítido a cualquier escala.
     * Los módulos negros seguidos de cada fila se dibujan como un solo rectángulo para que no
     * aparezcan rayitas blancas entre ellos al imprimir.
     */
    public static void dibujar(Canvas canvas, BitMatrix m, float x, float y, float lado, int color) {
        Paint tinta = new Paint();
        tinta.setAntiAlias(false);
        tinta.setStyle(Paint.Style.FILL);
        tinta.setColor(color);
        float modulo = lado / m.getWidth();
        for (int fila = 0; fila < m.getHeight(); fila++) {
            int inicio = -1;
            for (int col = 0; col <= m.getWidth(); col++) {
                boolean negro = col < m.getWidth() && m.get(col, fila);
                if (negro && inicio < 0) {
                    inicio = col;
                } else if (!negro && inicio >= 0) {
                    canvas.drawRect(x + inicio * modulo, y + fila * modulo,
                            x + col * modulo, y + (fila + 1) * modulo, tinta);
                    inicio = -1;
                }
            }
        }
    }

    /**
     * Imagen para mostrar en pantalla. Cada módulo mide un número entero de píxeles, así no se
     * ve borroso al escalar. Devuelve null si el texto no se pudo codificar.
     */
    @Nullable
    public static Bitmap imagen(String texto, int ladoMinimoPx, int colorTinta, int colorFondo) {
        BitMatrix m = matriz(texto);
        if (m == null) return null;
        int modulos = m.getWidth() + MARGEN_PANTALLA * 2;
        int escala = Math.max(1, (int) Math.ceil(ladoMinimoPx / (double) modulos));
        int lado = modulos * escala;
        int[] pixeles = new int[lado * lado];
        for (int py = 0; py < lado; py++) {
            int fila = py / escala - MARGEN_PANTALLA;
            for (int px = 0; px < lado; px++) {
                int col = px / escala - MARGEN_PANTALLA;
                boolean negro = fila >= 0 && col >= 0 && fila < m.getHeight() && col < m.getWidth()
                        && m.get(col, fila);
                pixeles[py * lado + px] = negro ? colorTinta : colorFondo;
            }
        }
        return Bitmap.createBitmap(pixeles, lado, lado, Bitmap.Config.ARGB_8888);
    }
}
