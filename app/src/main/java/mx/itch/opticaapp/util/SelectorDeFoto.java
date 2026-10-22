package mx.itch.opticaapp.util;

import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import java.util.function.Consumer;

import mx.itch.opticaapp.R;

/**
 * Pregunta "Tomar foto" o "Elegir de la galería" en una hoja inferior y entrega la imagen elegida.
 *
 * Se crea como campo del Fragment, porque registerForActivityResult debe llamarse antes de onCreate:
 * <pre>
 *     private final SelectorDeFoto selector = new SelectorDeFoto(this, this::usarFoto);
 *     ...
 *     selector.elegir("Logotipo");
 * </pre>
 */
public class SelectorDeFoto {

    private final Fragment fragmento;
    private final ActivityResultLauncher<Uri> tomarFoto;
    private final ActivityResultLauncher<PickVisualMediaRequest> elegirFoto;
    private Uri uriCamara;

    public SelectorDeFoto(Fragment fragmento, Consumer<Uri> alElegir) {
        this.fragmento = fragmento;
        tomarFoto = fragmento.registerForActivityResult(new ActivityResultContracts.TakePicture(), seTomo -> {
            if (Boolean.TRUE.equals(seTomo) && uriCamara != null) alElegir.accept(uriCamara);
        });
        elegirFoto = fragmento.registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) alElegir.accept(uri);
        });
    }

    /** Muestra la hoja inferior con las dos opciones. */
    public void elegir(String titulo) {
        HojaOpciones.con(fragmento.requireContext(), titulo)
                .opcion(R.drawable.ic_camara, "Tomar foto", "Con la cámara del teléfono", this::abrirCamara)
                .opcion(R.drawable.ic_imagen, "Elegir de la galería", "Una foto que ya tengas guardada", this::abrirGaleria)
                .mostrar();
    }

    private void abrirCamara() {
        if (!fragmento.isAdded()) return;
        uriCamara = Imagenes.nuevaUriParaFoto(fragmento.requireContext());
        tomarFoto.launch(uriCamara);
    }

    private void abrirGaleria() {
        if (!fragmento.isAdded()) return;
        elegirFoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }
}
