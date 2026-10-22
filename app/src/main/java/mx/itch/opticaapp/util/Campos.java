package mx.itch.opticaapp.util;

import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputLayout;

import mx.itch.opticaapp.R;

/**
 * Ayudas para los campos de texto de Material (TextInputLayout + TextInputEditText).
 *
 * En los layouts cada campo es un TextInputLayout (etiqueta flotante, contador, prefijo "$",
 * sufijos "mm" / "°", ojo de la contraseña) con su TextInputEditText adentro. El código sigue
 * trabajando con el EditText (binding.etNombre) y esta clase se encarga del contenedor:
 *
 *   Campos.error(binding.etNombre, Validaciones.nombrePersona(texto));
 *
 * pinta el mensaje en rojo debajo del campo (o lo quita si es null) y lo borra solo en cuanto
 * el usuario corrige el texto.
 */
public final class Campos {

    private Campos() {
    }

    /** TextInputLayout que contiene al campo, o null si es un EditText suelto (p. ej. la búsqueda). */
    @Nullable
    public static TextInputLayout contenedor(View campo) {
        // TextInputLayout guarda el EditText dentro de un FrameLayout propio: se sube hasta encontrarlo
        ViewParent padre = campo.getParent();
        for (int nivel = 0; nivel < 3 && padre != null; nivel++) {
            if (padre instanceof TextInputLayout) return (TextInputLayout) padre;
            padre = padre.getParent();
        }
        return null;
    }

    /**
     * Muestra el mensaje de error debajo del campo, o lo quita si es null.
     *
     * @return true si el campo quedó sin error (para encadenar: ok &= Campos.error(...)).
     */
    public static boolean error(EditText campo, @Nullable String mensaje) {
        TextInputLayout contenedor = contenedor(campo);
        if (contenedor == null) {
            campo.setError(mensaje);
            return mensaje == null;
        }
        if (mensaje == null) {
            quitarError(contenedor);
        } else {
            contenedor.setErrorEnabled(true);
            contenedor.setError(mensaje);
            limpiarAlCorregir(campo, contenedor);
        }
        return mensaje == null;
    }

    /**
     * Para celdas angostas (tabla de graduación): solo pinta el contorno de rojo, sin texto debajo,
     * para no deformar la tabla. El mensaje se muestra aparte, debajo de la tabla.
     *
     * @return true si la celda quedó sin error.
     */
    public static boolean errorEnCelda(EditText campo, @Nullable String mensaje) {
        TextInputLayout contenedor = contenedor(campo);
        if (contenedor == null) {
            campo.setError(mensaje);
            return mensaje == null;
        }
        pintarCelda(contenedor, mensaje != null);
        if (mensaje != null) limpiarAlCorregir(campo, contenedor).celda = true;
        return mensaje == null;
    }

    private static void pintarCelda(TextInputLayout contenedor, boolean conError) {
        if (conError) {
            int rojo = ContextCompat.getColor(contenedor.getContext(), R.color.rojo);
            // Lista con estados para que también cambie el contorno sin foco (no solo el enfocado)
            contenedor.setBoxStrokeColorStateList(new ColorStateList(
                    new int[][]{{android.R.attr.state_focused}, {}}, new int[]{rojo, rojo}));
        } else {
            ColorStateList normal = ContextCompat.getColorStateList(contenedor.getContext(), R.color.sel_campo_borde);
            if (normal != null) contenedor.setBoxStrokeColorStateList(normal);
        }
    }

    /** Activa o desactiva el campo junto con su contenedor (contorno y etiqueta en gris). */
    public static void habilitar(EditText campo, boolean habilitado) {
        TextInputLayout contenedor = contenedor(campo);
        if (contenedor != null) contenedor.setEnabled(habilitado);
        campo.setEnabled(habilitado);
    }

    /** Muestra u oculta el campo completo (con su etiqueta, ayuda y contador). */
    public static void mostrar(EditText campo, boolean visible) {
        TextInputLayout contenedor = contenedor(campo);
        View vista = contenedor != null ? contenedor : campo;
        vista.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) error(campo, null);
    }

    private static void quitarError(TextInputLayout contenedor) {
        contenedor.setError(null);
        // Sin esto queda reservado el espacio del mensaje aunque ya no haya error
        contenedor.setErrorEnabled(false);
    }

    /**
     * Borra el error en cuanto el usuario cambia el texto. Se guarda el texto que tenía el campo
     * al marcar el error: si una validación vuelve a marcarlo mientras se escribe, no se borra.
     */
    private static LimpiarError limpiarAlCorregir(EditText campo, TextInputLayout contenedor) {
        Object marca = campo.getTag(R.id.campo_limpia_error);
        LimpiarError limpiador;
        if (marca instanceof LimpiarError) {
            limpiador = (LimpiarError) marca;
        } else {
            limpiador = new LimpiarError(contenedor);
            campo.addTextChangedListener(limpiador);
            campo.setTag(R.id.campo_limpia_error, limpiador);
        }
        limpiador.textoConError = campo.getText() != null ? campo.getText().toString() : "";
        limpiador.celda = false;
        return limpiador;
    }

    private static final class LimpiarError implements TextWatcher {

        private final TextInputLayout contenedor;
        String textoConError = "";
        /** true si el error es solo el contorno rojo de una celda (errorEnCelda). */
        boolean celda;

        LimpiarError(TextInputLayout contenedor) {
            this.contenedor = contenedor;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (s.toString().equals(textoConError)) return;
            if (celda) {
                pintarCelda(contenedor, false);
                celda = false;
            } else if (contenedor.getError() != null) {
                quitarError(contenedor);
            }
        }
    }
}
