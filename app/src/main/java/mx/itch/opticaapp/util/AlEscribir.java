package mx.itch.opticaapp.util;

import android.text.Editable;
import android.text.TextWatcher;

import java.util.function.Consumer;

/**
 * TextWatcher simplificado: solo avisa el texto nuevo cada vez que el usuario escribe.
 * Uso: editText.addTextChangedListener(new AlEscribir(texto -> filtrar(texto)));
 */
public class AlEscribir implements TextWatcher {

    private final Consumer<String> accion;

    public AlEscribir(Consumer<String> accion) {
        this.accion = accion;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        accion.accept(s.toString());
    }
}
