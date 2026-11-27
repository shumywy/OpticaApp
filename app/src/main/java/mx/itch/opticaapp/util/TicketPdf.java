package mx.itch.opticaapp.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import androidx.annotation.Nullable;

import com.google.zxing.common.BitMatrix;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import mx.itch.opticaapp.data.Seguimientos;
import mx.itch.opticaapp.model.ItemVenta;
import mx.itch.opticaapp.model.Optica;
import mx.itch.opticaapp.model.Venta;

/**
 * Ticket de venta para impresora térmica de 58 mm, en PDF y solo en negro.
 * El largo de la hoja depende de cuántos renglones lleve: primero se "dibuja" sin lienzo para
 * medir la altura y después se dibuja de verdad en una página de ese tamaño.
 */
public final class TicketPdf {

    /** 58 mm en puntos (1 punto = 1/72 de pulgada). */
    public static final int ANCHO = 164;
    /** Las térmicas de 58 mm imprimen unos 48 mm al centro; esto deja ese espacio libre. */
    private static final float MARGEN_X = 12;
    private static final float MARGEN_Y = 14;

    private static final int NEGRO = 0xFF000000;
    private static final int BLANCO = 0xFFFFFFFF;

    private static final Typeface MONO = Typeface.MONOSPACE;
    private static final Typeface MONO_NEGRITA = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
    private static final Typeface SANS_NEGRITA = Typeface.DEFAULT_BOLD;

    private static final float CUERPO = 7f;
    private static final float CHICA = 6.3f;

    private TicketPdf() {
    }

    /** Resultado: el archivo y el alto de la hoja (lo pide la impresión para el tamaño de papel). */
    public static final class Archivo {
        public final File pdf;
        public final float altoPuntos;

        Archivo(File pdf, float altoPuntos) {
            this.pdf = pdf;
            this.altoPuntos = altoPuntos;
        }
    }

    public static Archivo crear(Context context, Optica optica, Venta v) throws IOException {
        Bitmap logo = Imagenes.decodificar(optica.logoUrl);
        BitMatrix qr = v.tieneSeguimiento() ? Qr.matriz(Seguimientos.url(v.seguimiento)) : null;

        // 1) Medir
        Lienzo medida = new Lienzo(null);
        contenido(medida, optica, v, logo, qr);
        float alto = (float) Math.ceil(medida.y + MARGEN_Y);

        // 2) Dibujar en una hoja de 58 mm × alto
        PdfDocument documento = new PdfDocument();
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(ANCHO, (int) alto, 1).create();
        PdfDocument.Page pagina = documento.startPage(info);
        Canvas canvas = pagina.getCanvas();
        canvas.drawColor(BLANCO);
        contenido(new Lienzo(canvas), optica, v, logo, qr);
        documento.finishPage(pagina);

        File carpeta = new File(context.getCacheDir(), "pdf");
        if (!carpeta.exists()) carpeta.mkdirs();
        String folio = v.folio != null ? v.folio : String.valueOf(System.currentTimeMillis());
        File archivo = new File(carpeta, "ticket_" + folio.replaceAll("[^A-Za-z0-9-]", "") + ".pdf");
        try (FileOutputStream salida = new FileOutputStream(archivo)) {
            documento.writeTo(salida);
        } finally {
            documento.close();
        }
        return new Archivo(archivo, alto);
    }

    /** Todo el ticket, de arriba abajo. Se llama dos veces: para medir y para dibujar. */
    private static void contenido(Lienzo l, Optica o, Venta v, @Nullable Bitmap logo, @Nullable BitMatrix qr) {
        // ----- Encabezado de la óptica -----
        l.logo(logo);
        l.fuente(SANS_NEGRITA, 10f);
        l.texto(texto(o.nombre).toUpperCase(Locale.forLanguageTag("es-MX")), Paint.Align.CENTER);
        l.fuente(MONO, CHICA);
        if (!vacio(o.direccion)) l.texto(o.direccion.trim(), Paint.Align.CENTER);
        if (!vacio(o.estado)) l.texto(o.estado.trim(), Paint.Align.CENTER);
        if (!vacio(o.telefono)) l.texto("Tel. " + Formato.telefono(o.telefono), Paint.Align.CENTER);
        l.punteada();

        // ----- Datos de la venta -----
        l.fuente(SANS_NEGRITA, 8f);
        l.texto("TICKET DE VENTA", Paint.Align.CENTER);
        l.espacio(2);
        l.fuente(MONO, CUERPO);
        l.par("Folio", texto(v.folio), MONO_NEGRITA);
        l.par("Fecha", Fechas.conHora(v.fecha), null);
        if (!vacio(v.vendedorNombre)) l.par("Atendió", v.vendedorNombre, null);
        l.par("Cliente", Formato.nombreCorto(v.pacienteNombre), null);
        l.punteada();

        // ----- Productos -----
        l.fuente(MONO_NEGRITA, CHICA);
        l.par("CANT DESCRIPCIÓN", "IMPORTE", null);
        l.espacio(2);
        l.fuente(MONO, CUERPO);
        for (ItemVenta i : v.items) {
            l.texto(i.cantidad + "  " + texto(i.nombre), Paint.Align.LEFT);
            l.par("   " + i.cantidad + " × " + Formato.moneda(i.precio), Formato.moneda(i.subtotal()), null);
            l.espacio(2);
        }
        l.punteada();

        // ----- Totales y pago -----
        l.fuente(SANS_NEGRITA, 9.5f);
        l.par("TOTAL", Formato.moneda(v.total), MONO_NEGRITA);
        l.espacio(2);
        l.fuente(MONO, CUERPO);
        l.par("Pago", texto(v.metodoPago), null);
        if (v.recibido > 0) {
            l.par("Recibido", Formato.moneda(v.recibido), null);
            l.par("Cambio", Formato.moneda(v.cambio), null);
        }
        if (v.saldo > 0) {
            l.par("Anticipo", Formato.moneda(v.anticipo), null);
            l.espacio(4);
            l.banda("SALDO PENDIENTE", Formato.moneda(v.saldo));
        } else {
            l.par("Pagado", Formato.moneda(v.anticipo), MONO_NEGRITA);
        }

        // ----- Orden de laboratorio -----
        if (v.tieneOrden()) {
            l.punteada();
            l.fuente(MONO_NEGRITA, CUERPO);
            l.texto("ORDEN DE LABORATORIO " + v.ordenFolio, Paint.Align.LEFT);
            l.fuente(MONO, CUERPO);
            if (!vacio(v.ordenDescripcion)) l.texto("Trabajo: " + v.ordenDescripcion, Paint.Align.LEFT);
            if (v.fechaEntrega != null) l.texto("Entrega estimada: " + Fechas.corta(v.fechaEntrega), Paint.Align.LEFT);
            l.texto("Presente este ticket para recoger sus lentes.", Paint.Align.LEFT);
        }

        // ----- QR de seguimiento -----
        if (qr != null) {
            l.espacio(6);
            String leyenda = v.tieneOrden()
                    ? "Escanee para ver el estado de su pedido" + (v.saldo > 0 ? " y su saldo." : ".")
                    : "Escanee para consultar su saldo.";
            l.qr(qr, 62, leyenda + " Folio " + texto(v.folio));
        }

        // ----- Pie -----
        l.punteada();
        l.fuente(SANS_NEGRITA, 8f);
        l.texto("¡Gracias por su compra!", Paint.Align.CENTER);
        l.fuente(MONO, CHICA);
        l.texto("Conserve este ticket para cualquier aclaración.", Paint.Align.CENTER);
        l.texto("Este comprobante no es una factura.", Paint.Align.CENTER);
    }

    private static boolean vacio(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String texto(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Cursor que baja por la hoja. Con canvas null solo mide (avanza "y" sin dibujar).
     * Todas las medidas son en puntos.
     */
    private static final class Lienzo {
        @Nullable
        final Canvas c;
        final float x0 = MARGEN_X;
        final float x1 = ANCHO - MARGEN_X;
        float y = MARGEN_Y;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Lienzo(@Nullable Canvas c) {
            this.c = c;
            p.setColor(NEGRO);
        }

        float ancho() {
            return x1 - x0;
        }

        void fuente(Typeface tipo, float tamano) {
            p.setTypeface(tipo);
            p.setTextSize(tamano);
        }

        void espacio(float puntos) {
            y += puntos;
        }

        /** Avanza un renglón y devuelve la línea base donde va el texto. */
        private float renglon() {
            float base = y + p.getTextSize();
            y = base + p.getTextSize() * 0.38f;
            return base;
        }

        /** Texto partido por palabras para que quepa en el ancho. */
        void texto(String s, Paint.Align alineacion) {
            for (String linea : envolver(s, ancho())) {
                float base = renglon();
                if (c == null) continue;
                p.setTextAlign(alineacion);
                float x = alineacion == Paint.Align.CENTER ? (x0 + x1) / 2
                        : alineacion == Paint.Align.RIGHT ? x1 : x0;
                c.drawText(linea, x, base, p);
            }
            p.setTextAlign(Paint.Align.LEFT);
        }

        /** "Etiqueta ........ valor": la etiqueta a la izquierda y el valor pegado a la derecha. */
        void par(String etiqueta, String valor, @Nullable Typeface tipoValor) {
            Typeface tipoEtiqueta = p.getTypeface();
            Typeface tipo = tipoValor != null ? tipoValor : tipoEtiqueta;
            float disponible = ancho() - p.measureText(etiqueta) - 6;
            // Si la etiqueta ocupa casi todo el renglón, el valor se pasa al renglón de abajo
            boolean abajo = disponible < ancho() / 3;
            p.setTypeface(tipo);
            List<String> lineas = envolver(valor, abajo ? ancho() : disponible);
            p.setTypeface(tipoEtiqueta);

            float base = renglon();
            if (c != null) c.drawText(etiqueta, x0, base, p);
            for (int i = 0; i < lineas.size(); i++) {
                if (i > 0 || abajo) base = renglon();
                if (c == null) continue;
                p.setTypeface(tipo);
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(lineas.get(i), x1, base, p);
                p.setTextAlign(Paint.Align.LEFT);
                p.setTypeface(tipoEtiqueta);
            }
        }

        /** Línea punteada de lado a lado, con aire arriba y abajo. */
        void punteada() {
            y += 5;
            if (c != null) {
                Paint trazo = new Paint(Paint.ANTI_ALIAS_FLAG);
                trazo.setColor(NEGRO);
                trazo.setStrokeWidth(0.8f);
                for (float x = x0; x < x1; x += 4) {
                    c.drawLine(x, y, Math.min(x + 2, x1), y, trazo);
                }
            }
            y += 4;
        }

        /** Franja negra con letras blancas (saldo pendiente): se nota aunque el ticket se decolore. */
        void banda(String etiqueta, String valor) {
            float tamano = 7.5f;
            float alto = tamano * 2.1f;
            if (c != null) {
                Paint fondo = new Paint();
                fondo.setColor(NEGRO);
                c.drawRect(x0 - 3, y, x1 + 3, y + alto, fondo);
                Paint letra = new Paint(Paint.ANTI_ALIAS_FLAG);
                letra.setColor(BLANCO);
                letra.setTypeface(MONO_NEGRITA);
                letra.setTextSize(tamano);
                float base = y + alto / 2 + tamano * 0.35f;
                c.drawText(etiqueta, x0 + 2, base, letra);
                letra.setTextAlign(Paint.Align.RIGHT);
                c.drawText(valor, x1 - 2, base, letra);
            }
            y += alto;
        }

        /** Logotipo de la óptica en gris (la térmica solo imprime negro); sin logo, unos lentes. */
        void logo(@Nullable Bitmap logo) {
            if (logo != null && logo.getWidth() > 0 && logo.getHeight() > 0) {
                float escala = Math.min(90f / logo.getWidth(), 34f / logo.getHeight());
                float w = logo.getWidth() * escala;
                float h = logo.getHeight() * escala;
                if (c != null) {
                    float x = (x0 + x1 - w) / 2;
                    ColorMatrix gris = new ColorMatrix();
                    gris.setSaturation(0);
                    Paint pintura = new Paint(Paint.FILTER_BITMAP_FLAG);
                    pintura.setColorFilter(new ColorMatrixColorFilter(gris));
                    c.drawBitmap(logo, null, new RectF(x, y, x + w, y + h), pintura);
                }
                y += h + 4;
                return;
            }
            float cx = (x0 + x1) / 2;
            float cy = y + 9;
            if (c != null) {
                Paint trazo = new Paint(Paint.ANTI_ALIAS_FLAG);
                trazo.setColor(NEGRO);
                trazo.setStyle(Paint.Style.STROKE);
                trazo.setStrokeWidth(1.3f);
                trazo.setStrokeCap(Paint.Cap.ROUND);
                c.drawCircle(cx - 8, cy, 5.5f, trazo);
                c.drawCircle(cx + 8, cy, 5.5f, trazo);
                c.drawLine(cx - 2.5f, cy, cx + 2.5f, cy, trazo);
                c.drawLine(cx - 13.5f, cy - 0.5f, cx - 16, cy - 7, trazo);
                c.drawLine(cx + 13.5f, cy - 0.5f, cx + 16, cy - 7, trazo);
            }
            y += 19;
        }

        /** QR a la izquierda y su leyenda a la derecha. */
        void qr(BitMatrix matriz, float lado, String leyenda) {
            float inicio = y;
            float xTexto = x0 + lado + 7;
            fuente(MONO, CHICA);
            List<String> lineas = envolver(leyenda, x1 - xTexto);
            float altoTexto = lineas.size() * p.getTextSize() * 1.38f;
            float yTexto = inicio + Math.max(0, (lado - altoTexto) / 2);
            if (c != null) {
                Qr.dibujar(c, matriz, x0, inicio, lado, NEGRO);
                float yy = yTexto;
                for (String linea : lineas) {
                    float base = yy + p.getTextSize();
                    c.drawText(linea, xTexto, base, p);
                    yy = base + p.getTextSize() * 0.38f;
                }
            }
            y = inicio + Math.max(lado, altoTexto);
        }

        /** Parte el texto en renglones que quepan en "ancho" (por palabras; si una no cabe, la corta). */
        List<String> envolver(String texto, float ancho) {
            List<String> lineas = new ArrayList<>();
            if (texto == null || texto.isEmpty()) {
                lineas.add("");
                return lineas;
            }
            StringBuilder actual = new StringBuilder();
            for (String palabra : texto.split("\\s+")) {
                String prueba = actual.length() == 0 ? palabra : actual + " " + palabra;
                if (p.measureText(prueba) <= ancho) {
                    actual.setLength(0);
                    actual.append(prueba);
                    continue;
                }
                if (actual.length() > 0) {
                    lineas.add(actual.toString());
                    actual.setLength(0);
                }
                // Palabra más larga que el renglón: se corta en pedazos
                String resto = palabra;
                while (p.measureText(resto) > ancho) {
                    int caben = Math.max(1, p.breakText(resto, true, ancho, null));
                    lineas.add(resto.substring(0, caben));
                    resto = resto.substring(caben);
                }
                actual.append(resto);
            }
            if (actual.length() > 0) lineas.add(actual.toString());
            return lineas;
        }
    }
}
