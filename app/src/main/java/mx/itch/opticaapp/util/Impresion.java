package mx.itch.opticaapp.util;

import android.content.Context;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Manda un PDF ya generado al servicio de impresión de Android (PrintManager).
 * Funciona con cualquier impresora que tenga su complemento instalado en el teléfono:
 * de oficina, Wi-Fi o térmica Bluetooth de 58 mm (por ejemplo con RawBT), y también
 * con "Guardar como PDF".
 */
public final class Impresion {

    /** 58 mm en milésimas de pulgada, la unidad que usa PrintAttributes. */
    private static final int ANCHO_58_MM_MILS = 2283;

    private Impresion() {
    }

    /**
     * @param altoPuntos alto de la hoja del PDF; con él se sugiere un papel de 58 mm del mismo largo.
     *                   La impresora elegida puede usar otro tamaño: Android ajusta la página.
     */
    public static void imprimir(Context context, File pdf, String nombreTrabajo, float altoPuntos) {
        PrintManager impresion = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
        if (impresion == null) return;
        int altoMils = Math.max(ANCHO_58_MM_MILS, Math.round(altoPuntos / 72f * 1000f));
        PrintAttributes sugeridos = new PrintAttributes.Builder()
                .setMediaSize(new PrintAttributes.MediaSize("OPTICA_TICKET_58", "Ticket 58 mm",
                        ANCHO_58_MM_MILS, altoMils))
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build();
        impresion.print(nombreTrabajo, new AdaptadorPdf(pdf), sugeridos);
    }

    /** Entrega el archivo tal cual: ya viene con una sola página del tamaño correcto. */
    private static final class AdaptadorPdf extends PrintDocumentAdapter {

        private final File pdf;

        AdaptadorPdf(File pdf) {
            this.pdf = pdf;
        }

        @Override
        public void onLayout(PrintAttributes anteriores, PrintAttributes nuevos, CancellationSignal cancelar,
                             LayoutResultCallback respuesta, Bundle extras) {
            if (cancelar.isCanceled()) {
                respuesta.onLayoutCancelled();
                return;
            }
            PrintDocumentInfo info = new PrintDocumentInfo.Builder(pdf.getName())
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build();
            respuesta.onLayoutFinished(info, !nuevos.equals(anteriores));
        }

        @Override
        public void onWrite(PageRange[] paginas, ParcelFileDescriptor destino, CancellationSignal cancelar,
                            WriteResultCallback respuesta) {
            try (InputStream entrada = new FileInputStream(pdf);
                 OutputStream salida = new FileOutputStream(destino.getFileDescriptor())) {
                byte[] bloque = new byte[8192];
                int leidos;
                while ((leidos = entrada.read(bloque)) > 0) {
                    if (cancelar.isCanceled()) {
                        respuesta.onWriteCancelled();
                        return;
                    }
                    salida.write(bloque, 0, leidos);
                }
                respuesta.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
            } catch (IOException e) {
                respuesta.onWriteFailed(e.getMessage());
            }
        }
    }
}
