package mx.itch.opticaapp.ui.ajustes;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Locale;

import mx.itch.opticaapp.databinding.HojaFichaColorBinding;
import mx.itch.opticaapp.util.EsquemaM3;
import mx.itch.opticaapp.util.HojaOpciones;

/**
 * Hoja inferior con el detalle de un color del esquema (adaptada de la app Atrium):
 * código HEX y RGB, tono, contraste con el color que va encima y para qué se usa.
 */
final class FichaColor {

    private FichaColor() {
    }

    /** Ficha de un rol del esquema (Primary, On Primary, Surface…). */
    static void mostrarRol(Context ctx, int rol, int[] roles) {
        int color = roles[rol];
        int pareja = EsquemaM3.parejaDe(rol);
        String paleta = EsquemaM3.NOMBRES_PALETAS_EN[EsquemaM3.paletaDe(rol)];
        mostrar(ctx, EsquemaM3.NOMBRES[rol],
                "Paleta " + paleta + " · tono T" + EsquemaM3.tonoDe(color),
                color, EsquemaM3.textoSobre(rol, roles),
                pareja >= 0 ? EsquemaM3.NOMBRES[pareja] : null, pareja >= 0 ? roles[pareja] : 0,
                EsquemaM3.USO[rol]);
    }

    /** Ficha de un tono de una paleta tonal (Primary T40, Neutral T90…). */
    static void mostrarTono(Context ctx, int paleta, int indiceTono, int color) {
        int tono = EsquemaM3.TONOS[indiceTono];
        String nombre = EsquemaM3.NOMBRES_PALETAS_EN[paleta];
        int texto = textoLegible(color);
        mostrar(ctx, nombre + " · T" + tono, EsquemaM3.NOMBRES_PALETAS[paleta], color, texto,
                texto == Color.WHITE ? "blanco" : "negro", texto,
                "Tono " + tono + " de la paleta " + nombre + ". Los roles toman tonos fijos de aquí "
                        + "(por ejemplo, en Material 3 Primary usa T40 en claro y T80 en oscuro).");
    }

    /** Blanco o negro, el que contraste más. */
    static int textoLegible(int fondo) {
        return ColorUtils.calculateContrast(Color.WHITE, fondo | 0xFF000000)
                >= ColorUtils.calculateContrast(Color.BLACK, fondo | 0xFF000000) ? Color.WHITE : Color.BLACK;
    }

    private static void mostrar(Context ctx, String titulo, String subtitulo, int color, int colorTexto,
                                @Nullable String nombrePareja, int colorPareja, String uso) {
        BottomSheetDialog hoja = new BottomSheetDialog(ctx);
        HojaFichaColorBinding v = HojaFichaColorBinding.inflate(LayoutInflater.from(hoja.getContext()));

        v.muestraFicha.setBackgroundTintList(ColorStateList.valueOf(color | 0xFF000000));
        v.tvEjemploFicha.setTextColor(colorTexto);
        v.tvTonoFicha.setTextColor(colorTexto);
        v.tvTonoFicha.setText("T" + EsquemaM3.tonoDe(color));
        v.tvTituloFicha.setText(titulo);
        v.tvSubtituloFicha.setText(subtitulo);
        String hex = EsquemaM3.hex(color);
        v.tvHexFicha.setText(hex);
        v.tvRgbFicha.setText(String.format(Locale.ROOT, "%d, %d, %d", Color.red(color), Color.green(color), Color.blue(color)));
        if (nombrePareja != null) {
            double razon = ColorUtils.calculateContrast(colorPareja | 0xFF000000, color | 0xFF000000);
            v.tvEtiquetaContrasteFicha.setText("Contraste con " + nombrePareja);
            v.tvContrasteFicha.setText(clasificar(razon));
        } else {
            v.filaContrasteFicha.setVisibility(View.GONE);
        }
        v.tvUsoFicha.setText(uso);
        v.btnCopiarFicha.setOnClickListener(b -> {
            ClipboardManager portapapeles = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (portapapeles != null) portapapeles.setPrimaryClip(ClipData.newPlainText(titulo, hex));
            Toast.makeText(ctx, hex + " copiado", Toast.LENGTH_SHORT).show();
        });

        hoja.setContentView(v.getRoot());
        HojaOpciones.abrirCompleta(hoja);
        hoja.show();
    }

    /** Contraste según WCAG: AAA ≥ 7, AA ≥ 4.5, texto grande ≥ 3. */
    private static String clasificar(double razon) {
        String nivel = razon >= 7 ? "AAA" : razon >= 4.5 ? "AA" : razon >= 3 ? "solo texto grande" : "decorativo";
        return String.format(Locale.ROOT, "%.1f:1 · %s", razon, nivel);
    }
}
