package mx.itch.opticaapp.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

import mx.itch.opticaapp.databinding.HojaOpcionesBinding;
import mx.itch.opticaapp.databinding.ItemHojaOpcionBinding;

/**
 * Hoja inferior de opciones de Material (BottomSheetDialog), en lugar de un diálogo al centro.
 * En el celular se alcanza mejor con el pulgar y se cierra deslizándola hacia abajo.
 *
 * <pre>
 *     HojaOpciones.con(requireContext(), "Foto de perfil")
 *             .opcion(R.drawable.ic_camara, "Tomar foto", this::tomarFoto)
 *             .opcion(R.drawable.ic_imagen, "Elegir de la galería", this::abrirGaleria)
 *             .mostrar();
 * </pre>
 *
 * Para elegir un valor (rol, estatus) se usa elegible(...): la opción actual lleva una palomita.
 */
public final class HojaOpciones {

    private static final class Opcion {
        @DrawableRes int icono;
        String texto;
        String detalle;
        boolean actual;
        Runnable accion;
    }

    private final Context ctx;
    private final String titulo;
    private String subtitulo;
    private final List<Opcion> opciones = new ArrayList<>();

    private HojaOpciones(Context ctx, String titulo) {
        this.ctx = ctx;
        this.titulo = titulo;
    }

    public static HojaOpciones con(Context ctx, String titulo) {
        return new HojaOpciones(ctx, titulo);
    }

    /** Texto gris debajo del título (por ejemplo el teléfono del paciente). */
    public HojaOpciones subtitulo(@Nullable String texto) {
        this.subtitulo = texto;
        return this;
    }

    public HojaOpciones opcion(@DrawableRes int icono, String texto, Runnable accion) {
        return opcion(icono, texto, null, accion);
    }

    public HojaOpciones opcion(@DrawableRes int icono, String texto, @Nullable String detalle, Runnable accion) {
        Opcion o = new Opcion();
        o.icono = icono;
        o.texto = texto;
        o.detalle = detalle;
        o.accion = accion;
        opciones.add(o);
        return this;
    }

    /** Opción de una lista para elegir un valor; la actual se marca con palomita. */
    public HojaOpciones elegible(@DrawableRes int icono, String texto, @Nullable String detalle,
                                 boolean actual, Runnable accion) {
        opcion(icono, texto, detalle, accion);
        opciones.get(opciones.size() - 1).actual = actual;
        return this;
    }

    public BottomSheetDialog mostrar() {
        BottomSheetDialog hoja = new BottomSheetDialog(ctx);
        HojaOpcionesBinding vista = HojaOpcionesBinding.inflate(LayoutInflater.from(hoja.getContext()));
        vista.tvTituloHoja.setText(titulo);
        if (subtitulo != null && !subtitulo.isEmpty()) {
            vista.tvSubtituloHoja.setText(subtitulo);
            vista.tvSubtituloHoja.setVisibility(View.VISIBLE);
        }
        for (Opcion o : opciones) {
            ItemHojaOpcionBinding fila = ItemHojaOpcionBinding.inflate(
                    LayoutInflater.from(hoja.getContext()), vista.contenedorOpciones, false);
            fila.ivIconoOpcion.setImageResource(o.icono);
            fila.tvTextoOpcion.setText(o.texto);
            if (o.detalle != null && !o.detalle.isEmpty()) {
                fila.tvDetalleOpcion.setText(o.detalle);
                fila.tvDetalleOpcion.setVisibility(View.VISIBLE);
            }
            fila.ivMarcaOpcion.setVisibility(o.actual ? View.VISIBLE : View.GONE);
            fila.getRoot().setSelected(o.actual);
            fila.getRoot().setOnClickListener(v -> {
                hoja.dismiss();
                o.accion.run();
            });
            vista.contenedorOpciones.addView(fila.getRoot());
        }
        hoja.setContentView(vista.getRoot());
        abrirCompleta(hoja);
        hoja.show();
        return hoja;
    }

    /** Abre la hoja completa (sin el estado intermedio "a medias", que en horizontal corta opciones). */
    public static void abrirCompleta(BottomSheetDialog hoja) {
        BottomSheetBehavior<?> comportamiento = hoja.getBehavior();
        comportamiento.setSkipCollapsed(true);
        comportamiento.setState(BottomSheetBehavior.STATE_EXPANDED);
    }
}
