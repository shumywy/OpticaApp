package mx.itch.opticaapp.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import mx.itch.opticaapp.model.Consulta;
import mx.itch.opticaapp.model.ItemVenta;
import mx.itch.opticaapp.model.Optica;
import mx.itch.opticaapp.model.Paciente;
import mx.itch.opticaapp.model.Venta;

/**
 * Genera la receta óptica y la nota de venta en PDF con la clase PdfDocument de Android
 * (no necesita librerías externas) y permite compartirlas.
 */
public final class Pdf {

    // Hoja tamaño carta en puntos (1 punto = 1/72 de pulgada)
    private static final int ANCHO = 612;
    private static final int ALTO = 792;
    private static final int MARGEN = 50;

    private Pdf() {
    }

    /** Una línea de texto del PDF y si va en negritas. */
    private static class Linea {
        final String texto;
        final boolean negrita;
        final float tamano;

        Linea(String texto, boolean negrita, float tamano) {
            this.texto = texto;
            this.negrita = negrita;
            this.tamano = tamano;
        }
    }

    public static File receta(Context context, Optica optica, Paciente paciente, Consulta c) throws IOException {
        List<Linea> l = new ArrayList<>();
        encabezado(l, optica, "RECETA ÓPTICA");
        l.add(new Linea("Paciente: " + paciente.nombre, false, 12));
        if (paciente.edad > 0) l.add(new Linea("Edad: " + paciente.edad + " años", false, 12));
        l.add(new Linea("Fecha: " + Fechas.corta(c.fecha != null ? c.fecha : new Date()), false, 12));
        l.add(new Linea("", false, 12));
        l.add(new Linea("Ojo        Esfera     Cilindro     Eje      Adición", true, 12));
        l.add(new Linea(renglonOjo("OD", c.odEsfera, c.odCilindro, c.odEje, c.odAdicion), false, 12));
        l.add(new Linea(renglonOjo("OI", c.oiEsfera, c.oiCilindro, c.oiEje, c.oiAdicion), false, 12));
        l.add(new Linea("", false, 12));
        if (c.distanciaPupilar > 0) l.add(new Linea("Distancia pupilar: " + c.distanciaPupilar + " mm", false, 12));
        if (!vacio(c.agudezaVisual)) l.add(new Linea("Agudeza visual: " + c.agudezaVisual, false, 12));
        if (!vacio(c.tipoLente)) l.add(new Linea("Tipo de lente: " + c.tipoLente, false, 12));
        if (!vacio(c.observaciones)) l.add(new Linea("Observaciones: " + c.observaciones, false, 12));
        l.add(new Linea("", false, 12));
        l.add(new Linea("", false, 12));
        l.add(new Linea("______________________________", false, 12));
        l.add(new Linea("Optometrista: " + (vacio(c.optometristaNombre) ? "" : c.optometristaNombre), false, 12));
        return escribir(context, "receta_" + System.currentTimeMillis() + ".pdf", l, Imagenes.decodificar(optica.logoUrl));
    }

    public static File notaVenta(Context context, Optica optica, Venta v) throws IOException {
        List<Linea> l = new ArrayList<>();
        encabezado(l, optica, "NOTA DE VENTA");
        l.add(new Linea("Fecha: " + Fechas.conHora(v.fecha), false, 12));
        l.add(new Linea("Cliente: " + v.pacienteNombre, false, 12));
        l.add(new Linea("Atendió: " + (vacio(v.vendedorNombre) ? "" : v.vendedorNombre), false, 12));
        l.add(new Linea("", false, 12));
        l.add(new Linea("Cant.   Producto                                   Importe", true, 12));
        for (ItemVenta i : v.items) {
            l.add(new Linea(i.cantidad + "       " + i.nombre + "   " + Formato.moneda(i.subtotal()), false, 12));
        }
        l.add(new Linea("", false, 12));
        l.add(new Linea("Total: " + Formato.moneda(v.total), true, 13));
        l.add(new Linea("Anticipo (" + v.metodoPago + "): " + Formato.moneda(v.anticipo), false, 12));
        l.add(new Linea("Saldo pendiente: " + Formato.moneda(v.saldo), true, 12));
        return escribir(context, "nota_" + System.currentTimeMillis() + ".pdf", l, Imagenes.decodificar(optica.logoUrl));
    }

    private static void encabezado(List<Linea> l, Optica optica, String titulo) {
        l.add(new Linea(optica.nombre, true, 18));
        String datos = (vacio(optica.direccion) ? "" : optica.direccion)
                + (vacio(optica.estado) ? "" : ", " + optica.estado)
                + (vacio(optica.telefono) ? "" : "  ·  Tel. " + optica.telefono);
        l.add(new Linea(datos, false, 10));
        l.add(new Linea("", false, 12));
        l.add(new Linea(titulo, true, 14));
        l.add(new Linea("", false, 12));
    }

    private static String renglonOjo(String ojo, double esf, double cil, int eje, double add) {
        return ojo + "         " + Formato.dioptria(esf) + "       " + Formato.dioptria(cil)
                + "        " + (eje > 0 ? eje + "°" : "—") + "       " + (add > 0 ? Formato.dioptria(add) : "—");
    }

    private static boolean vacio(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Dibuja las líneas en una hoja y guarda el PDF en la caché de la app.
     * Si la óptica tiene logotipo, lo pone arriba a la derecha (máximo 120 × 64 puntos).
     */
    private static File escribir(Context context, String nombreArchivo, List<Linea> lineas,
                                 @Nullable Bitmap logo) throws IOException {
        PdfDocument documento = new PdfDocument();
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(ANCHO, ALTO, 1).create();
        PdfDocument.Page pagina = documento.startPage(info);
        Canvas canvas = pagina.getCanvas();
        Paint pincel = new Paint();
        pincel.setAntiAlias(true);

        if (logo != null) {
            float escala = Math.min(120f / logo.getWidth(), 64f / logo.getHeight());
            float ancho = logo.getWidth() * escala;
            float alto = logo.getHeight() * escala;
            RectF lugar = new RectF(ANCHO - MARGEN - ancho, MARGEN, ANCHO - MARGEN, MARGEN + alto);
            canvas.drawBitmap(logo, null, lugar, new Paint(Paint.FILTER_BITMAP_FLAG));
        }

        float y = MARGEN;
        for (Linea linea : lineas) {
            pincel.setTextSize(linea.tamano);
            pincel.setTypeface(Typeface.create(Typeface.DEFAULT, linea.negrita ? Typeface.BOLD : Typeface.NORMAL));
            y += linea.tamano + 6;
            canvas.drawText(linea.texto, MARGEN, y, pincel);
        }
        documento.finishPage(pagina);

        File carpeta = new File(context.getCacheDir(), "pdf");
        if (!carpeta.exists()) {
            carpeta.mkdirs();
        }
        File archivo = new File(carpeta, nombreArchivo);
        try (FileOutputStream salida = new FileOutputStream(archivo)) {
            documento.writeTo(salida);
        } finally {
            documento.close();
        }
        return archivo;
    }

    /** Abre el menú de Android para enviar el PDF (WhatsApp, correo, Drive, etc.). */
    public static void compartir(Context context, File pdf) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", pdf);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, "Compartir PDF"));
    }
}
