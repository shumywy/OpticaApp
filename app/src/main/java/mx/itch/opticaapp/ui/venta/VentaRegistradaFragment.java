package mx.itch.opticaapp.ui.venta;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.core.os.BundleCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Seguimientos;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentVentaRegistradaBinding;
import mx.itch.opticaapp.databinding.ItemTicketRenglonBinding;
import mx.itch.opticaapp.model.Documento;
import mx.itch.opticaapp.model.ItemVenta;
import mx.itch.opticaapp.model.Optica;
import mx.itch.opticaapp.model.Venta;
import mx.itch.opticaapp.util.Animaciones;
import mx.itch.opticaapp.util.Aviso;
import mx.itch.opticaapp.util.Documentos;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.HojaOpciones;
import mx.itch.opticaapp.util.Imagenes;
import mx.itch.opticaapp.util.Impresion;
import mx.itch.opticaapp.util.Pdf;
import mx.itch.opticaapp.util.Qr;
import mx.itch.opticaapp.util.TicketPdf;

/**
 * Se abre al cobrar: muestra el ticket tal como lo verá el cliente y permite mandarlo por
 * WhatsApp, imprimirlo (58 mm o cualquier impresora), guardarlo como PDF o empezar otra venta.
 * Si la venta lleva orden de laboratorio o saldo, el ticket trae un QR con el estado del pedido.
 */
public class VentaRegistradaFragment extends Fragment {

    public static final String ARG_VENTA = "venta";
    /** Teléfono del paciente (para abrir su chat de WhatsApp); vacío en "Público general". */
    public static final String ARG_TELEFONO = "telefono";
    /** Se marca al registrar el ticket en documentos, para no repetirlo si se gira la pantalla. */
    private static final String ARG_REGISTRADO = "registrado";

    private static final Locale MX = Locale.forLanguageTag("es-MX");

    private FragmentVentaRegistradaBinding binding;
    private Venta venta;
    private String telefono;
    @Nullable
    private TicketPdf.Archivo ticket;

    /** "Guardar PDF": Android pregunta dónde (Descargas, Drive...) sin pedir permisos. */
    private final ActivityResultLauncher<String> guardar = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"), this::guardarEn);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentVentaRegistradaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        venta = args != null ? BundleCompat.getSerializable(args, ARG_VENTA, Venta.class) : null;
        telefono = args != null ? args.getString(ARG_TELEFONO, "") : "";
        if (venta == null || !Sesion.estaActiva()) {
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        float densidad = getResources().getDisplayMetrics().density;
        binding.ticket.setBackground(new PapelTicket(color(R.attr.colorSuperficie), 0x33141228, densidad));
        mostrarTicket(Sesion.getOptica());

        binding.btnWhatsApp.setOnClickListener(v -> enviarPorWhatsApp());
        binding.btnImprimir.setOnClickListener(v -> imprimir());
        binding.btnGuardar.setOnClickListener(v -> guardar.launch(nombreArchivo()));
        binding.btnNuevaVenta.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        binding.btnMas.setOnClickListener(v -> masOpciones());

        if (Animaciones.entrada(this, binding.encabezado, binding.ticket)) {
            Animaciones.subir(binding.pie, 40 * densidad, 260);
        }
    }

    // ---------------- Vista previa ----------------

    private void mostrarTicket(Optica o) {
        binding.tvSubtitulo.setText("Folio " + texto(venta.folio) + " · " + fechaLarga(venta));

        Bitmap logo = Imagenes.decodificar(o.logoUrl);
        if (logo != null) {
            binding.ivLogo.setImageBitmap(logo);
            ImageViewCompat.setImageTintList(binding.ivLogo, null);
        }
        binding.tvOptica.setText(texto(o.nombre));
        StringBuilder datos = new StringBuilder(texto(o.direccion));
        if (!texto(o.estado).isEmpty()) datos.append(datos.length() > 0 ? ", " : "").append(o.estado.trim());
        if (!texto(o.telefono).isEmpty()) {
            datos.append(datos.length() > 0 ? " · " : "").append(Formato.telefono(o.telefono));
        }
        binding.tvDatosOptica.setText(datos);
        binding.tvDatosOptica.setVisibility(datos.length() > 0 ? View.VISIBLE : View.GONE);

        binding.tvCliente.setText(texto(venta.pacienteNombre));
        binding.tvAtendio.setText(texto(venta.vendedorNombre));
        binding.filaAtendio.setVisibility(texto(venta.vendedorNombre).isEmpty() ? View.GONE : View.VISIBLE);

        LayoutInflater inflater = getLayoutInflater();
        binding.llRenglones.removeAllViews();
        for (ItemVenta i : venta.items) {
            ItemTicketRenglonBinding r = ItemTicketRenglonBinding.inflate(inflater, binding.llRenglones, true);
            r.tvNombre.setText(i.nombre);
            r.tvDetalle.setText(i.cantidad + " × " + Formato.moneda(i.precio));
            r.tvImporte.setText(Formato.moneda(i.subtotal()));
        }

        binding.tvTotal.setText(Formato.moneda(venta.total));
        boolean conSaldo = venta.saldo > 0;
        binding.tvPagoEtiqueta.setText((conSaldo ? "Anticipo · " : "Pagado · ") + texto(venta.metodoPago));
        binding.tvPago.setText(Formato.moneda(venta.anticipo));
        if (venta.recibido > 0) {
            binding.filaCambio.setVisibility(View.VISIBLE);
            binding.tvRecibido.setText("Recibido " + Formato.moneda(venta.recibido));
            binding.tvCambio.setText("Cambio " + Formato.moneda(venta.cambio));
        }
        binding.filaSaldo.setVisibility(conSaldo ? View.VISIBLE : View.GONE);
        binding.tvSaldo.setText(Formato.moneda(venta.saldo));

        // Orden y QR
        if (!venta.tieneOrden() && !venta.tieneSeguimiento()) return;
        binding.bloqueSeguimiento.setVisibility(View.VISIBLE);
        if (venta.tieneOrden()) {
            binding.tvOrden.setText("Orden " + venta.ordenFolio
                    + (texto(venta.ordenDescripcion).isEmpty() ? "" : " · " + venta.ordenDescripcion));
            if (venta.fechaEntrega != null) {
                binding.tvEntrega.setText("Tus lentes estarán listos el "
                        + new SimpleDateFormat("d 'de' MMMM", MX).format(venta.fechaEntrega));
            } else {
                binding.tvEntrega.setVisibility(View.GONE);
            }
        } else {
            binding.tvOrden.setText("Seguimiento del saldo");
            binding.tvEntrega.setVisibility(View.GONE);
        }
        // Siempre negro sobre blanco (también en modo oscuro): algunas cámaras no leen QR invertidos
        Bitmap qr = venta.tieneSeguimiento()
                ? Qr.imagen(Seguimientos.url(venta.seguimiento), (int) (96 * getResources().getDisplayMetrics().density),
                0xFF111111, 0xFFFFFFFF)
                : null;
        if (qr != null) {
            binding.ivQr.setImageBitmap(qr);
            binding.tvQrLeyenda.setText(venta.tieneOrden()
                    ? "Escanea con la cámara para ver el estado de tu pedido" + (conSaldo ? " y tu saldo." : ".")
                    : "Escanea con la cámara para consultar tu saldo.");
        } else {
            binding.ivQr.setVisibility(View.GONE);
            binding.tvQrLeyenda.setText("Presenta este ticket para recoger tus lentes.");
        }
    }

    /** "23 nov 2026, 11:40" */
    private static String fechaLarga(Venta v) {
        if (v.fecha == null) return "";
        return new SimpleDateFormat("d MMM yyyy, HH:mm", MX).format(v.fecha).replace(".", "");
    }

    // ---------------- Acciones ----------------

    /** Genera el PDF la primera vez (o si Android limpió la caché) y lo deja registrado en Firebase. */
    @Nullable
    private TicketPdf.Archivo ticket() {
        if (ticket != null && ticket.pdf.exists()) return ticket;
        try {
            ticket = TicketPdf.crear(requireContext(), Sesion.getOptica(), venta);
        } catch (IOException e) {
            mensaje("No se pudo crear el ticket: " + e.getMessage());
            return null;
        }
        Bundle args = getArguments();
        if (args != null && !args.getBoolean(ARG_REGISTRADO, false)) {
            Documentos.registrar(ticket.pdf, Documento.TICKET, "Ticket " + texto(venta.folio) + " · "
                    + Formato.moneda(venta.total), venta.id, venta.pacienteNombre);
            args.putBoolean(ARG_REGISTRADO, true);
        }
        return ticket;
    }

    private String nombreArchivo() {
        return "Ticket " + texto(venta.folio) + ".pdf";
    }

    /**
     * Abre WhatsApp (o WhatsApp Business) con el PDF y un mensaje. Si el paciente tiene teléfono,
     * se intenta abrir directo su chat; si no está en los contactos del teléfono, WhatsApp pide
     * elegir a quién mandarlo. Sin WhatsApp instalado se abre el menú normal de compartir.
     */
    private void enviarPorWhatsApp() {
        TicketPdf.Archivo t = ticket();
        if (t == null) return;
        Context ctx = requireContext();
        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", t.pdf);
        Intent envio = new Intent(Intent.ACTION_SEND);
        envio.setType("application/pdf");
        envio.putExtra(Intent.EXTRA_STREAM, uri);
        envio.putExtra(Intent.EXTRA_TEXT, mensajeParaCliente());
        envio.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        String numero = numeroWhatsApp(telefono);
        if (!numero.isEmpty()) envio.putExtra("jid", numero + "@s.whatsapp.net");

        for (String paquete : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
            envio.setPackage(paquete);
            try {
                startActivity(envio);
                return;
            } catch (ActivityNotFoundException noEsta) {
                // se prueba con el siguiente
            }
        }
        envio.setPackage(null);
        envio.removeExtra("jid");
        startActivity(Intent.createChooser(envio, "Enviar ticket"));
    }

    /** 10 dígitos de México → con lada internacional 52. Vacío si no parece un número válido. */
    private static String numeroWhatsApp(@Nullable String telefono) {
        if (telefono == null) return "";
        String d = telefono.replaceAll("[^0-9]", "");
        if (d.length() == 10) return "52" + d;
        if (d.length() == 12 && d.startsWith("52")) return d;
        return "";
    }

    private String mensajeParaCliente() {
        String nombre = texto(venta.pacienteNombre);
        String saludo = venta.pacienteId == null || venta.pacienteId.isEmpty() || nombre.isEmpty()
                ? "Hola" : "Hola " + nombre.split("\\s+")[0];
        StringBuilder m = new StringBuilder(saludo).append(", gracias por tu compra en ")
                .append(texto(Sesion.getOptica().nombre)).append(". Te mando tu ticket ")
                .append(texto(venta.folio)).append('.');
        if (venta.tieneSeguimiento()) {
            m.append("\n\nAquí puedes ver cómo va tu pedido: ").append(Seguimientos.url(venta.seguimiento));
        }
        return m.toString();
    }

    private void imprimir() {
        TicketPdf.Archivo t = ticket();
        if (t == null) return;
        Impresion.imprimir(requireContext(), t.pdf, "Ticket " + texto(venta.folio), t.altoPuntos);
    }

    private void guardarEn(@Nullable Uri destino) {
        if (destino == null || !isAdded()) return;   // canceló
        TicketPdf.Archivo t = ticket();
        if (t == null) return;
        try (InputStream entrada = new FileInputStream(t.pdf);
             OutputStream salida = requireContext().getContentResolver().openOutputStream(destino)) {
            if (salida == null) throw new IOException("No se pudo abrir el destino");
            byte[] bloque = new byte[8192];
            int leidos;
            while ((leidos = entrada.read(bloque)) > 0) {
                salida.write(bloque, 0, leidos);
            }
            Aviso.mostrar(getActivity(), "Ticket guardado", R.drawable.ic_descargar);
        } catch (IOException e) {
            mensaje("No se pudo guardar: " + e.getMessage());
        }
    }

    private void masOpciones() {
        HojaOpciones hoja = HojaOpciones.con(requireContext(), "Ticket " + texto(venta.folio))
                .subtitulo(texto(venta.pacienteNombre) + " · " + Formato.moneda(venta.total))
                .opcion(R.drawable.ic_compartir, "Compartir con otra app", "Correo, Drive, Telegram…", () -> {
                    TicketPdf.Archivo t = ticket();
                    if (t != null) Pdf.compartir(requireContext(), t.pdf);
                })
                .opcion(R.drawable.ic_documento, "Nota de venta tamaño carta", "El formato de antes, en hoja carta", this::notaCarta);
        if (venta.tieneSeguimiento()) {
            hoja.opcion(R.drawable.ic_copiar, "Copiar enlace de seguimiento", "El mismo que abre el QR", () -> {
                ClipboardManager portapapeles = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (portapapeles == null) return;
                portapapeles.setPrimaryClip(ClipData.newPlainText("Seguimiento", Seguimientos.url(venta.seguimiento)));
                Aviso.mostrar(getActivity(), "Enlace copiado", R.drawable.ic_copiar);
            });
        }
        hoja.mostrar();
    }

    private void notaCarta() {
        try {
            File pdf = Pdf.notaVenta(requireContext(), Sesion.getOptica(), venta);
            Documentos.registrar(pdf, Documento.NOTA_VENTA, "Nota de venta · " + Formato.moneda(venta.total),
                    venta.id, venta.pacienteNombre);
            Pdf.compartir(requireContext(), pdf);
        } catch (IOException e) {
            mensaje("No se pudo crear el PDF: " + e.getMessage());
        }
    }

    // ---------------- Utilidades ----------------

    private int color(@AttrRes int atributo) {
        TypedValue valor = new TypedValue();
        requireContext().getTheme().resolveAttribute(atributo, valor, true);
        return valor.data;
    }

    private static String texto(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    private void mensaje(String texto) {
        if (getContext() != null) Toast.makeText(getContext(), texto, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
