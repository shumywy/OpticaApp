package mx.itch.opticaapp.util;

import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.function.Consumer;

/**
 * Pregunta "Tomar foto" o "Elegir de la galería" y entrega la imagen elegida.
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

    /** Muestra el diálogo con las dos opciones. */
    public void elegir(String titulo) {
        new MaterialAlertDialogBuilder(fragmento.requireContext())
                .setTitle(titulo)
                .setItems(new String[]{"Tomar foto", "Elegir de la galería"}, (d, opcion) -> {
                    if (opcion == 0) {
                        uriCamara = Imagenes.nuevaUriParaFoto(fragmento.requireContext());
                        tomarFoto.launch(uriCamara);
                    } else {
                        elegirFoto.launch(new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build());
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
