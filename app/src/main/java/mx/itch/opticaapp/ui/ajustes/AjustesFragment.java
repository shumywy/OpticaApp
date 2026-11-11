package mx.itch.opticaapp.ui.ajustes;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mx.itch.opticaapp.MainActivity;
import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Preferencias;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentAjustesBinding;
import mx.itch.opticaapp.databinding.ItemPaletaBinding;
import mx.itch.opticaapp.model.Usuario;
import mx.itch.opticaapp.util.Animaciones;
import mx.itch.opticaapp.util.Formato;
import mx.itch.opticaapp.util.Temas;

/**
 * Pantalla 9: Configuración de la app en este teléfono:
 * apariencia (tema de color y modo), modo sin imágenes, texto grande, fondo de pantalla y cerrar sesión.
 * (El perfil, la óptica y el personal están en el menú lateral → Administración.)
 *
 * Apariencia: al tocar un tema o un modo, la vista previa cambia de color con una animación;
 * "Aplicar cambios" lo guarda en SharedPreferences para este usuario y vuelve a dibujar la app.
 */
public class AjustesFragment extends Fragment {

    private static final String ESTADO_PALETA = "paleta_elegida";
    private static final String ESTADO_MODO = "modo_elegido";
    private static final long DURACION_COLOR_MS = 320;

    private FragmentAjustesBinding binding;

    /** Lo que está aplicado (guardado) y lo que el usuario está viendo en la vista previa. */
    private Temas.Paleta paletaGuardada, paletaElegida;
    private int modoGuardado, modoElegido;
    private final List<ItemPaletaBinding> tarjetasPaleta = new ArrayList<>();
    /** Colores que muestra ahora la vista previa (índices Temas.V_...). */
    private int[] coloresVista;
    private ValueAnimator animacionColores;
    /** Colores ya calculados por tema y modo ("carey-oscuro"), para no recalcularlos en cada toque. */
    private final Map<String, int[]> coloresPorTema = new HashMap<>();
    // Fondos de la vista previa: se crean una vez y solo se les cambia el color
    private final GradientDrawable fondoPrevia = new GradientDrawable();
    private final GradientDrawable fondoEncabezado = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0, 0});
    private final GradientDrawable fondoAvatar = new GradientDrawable();
    private final GradientDrawable fondoRol = new GradientDrawable();
    private final GradientDrawable fondoPrimario = new GradientDrawable();
    private final GradientDrawable fondoTonal = new GradientDrawable();
    private final GradientDrawable[] fondosRoles = new GradientDrawable[4];

    private final ActivityResultLauncher<PickVisualMediaRequest> elegirFondo =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) guardarFondo(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAjustesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!Sesion.estaActiva()) return;

        configurarTema(savedInstanceState);
        configurarApariencia();
        binding.filaCerrarSesion.setOnClickListener(v -> cerrarSesion());

        // Animación de entrada: título en cascada y la hoja que sube
        Animaciones.entrada(this, binding.encabezado, binding.hoja);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (paletaElegida != null) {
            outState.putString(ESTADO_PALETA, paletaElegida.id);
            outState.putInt(ESTADO_MODO, modoElegido);
        }
    }

    // ---------------- Apariencia: tema de color y modo ----------------

    private void configurarTema(@Nullable Bundle estado) {
        Context ctx = requireContext();
        paletaGuardada = Preferencias.getPaleta(ctx);
        modoGuardado = Preferencias.getTema(ctx);
        // Si se giró la pantalla a medio elegir, se conserva lo que estaba viendo
        paletaElegida = estado != null && estado.containsKey(ESTADO_PALETA)
                ? Temas.Paleta.desdeId(estado.getString(ESTADO_PALETA)) : paletaGuardada;
        modoElegido = estado != null ? estado.getInt(ESTADO_MODO, modoGuardado) : modoGuardado;

        prepararVistaPrevia();

        // Una tarjeta por tema
        tarjetasPaleta.clear();
        binding.contenedorPaletas.removeAllViews();
        for (Temas.Paleta paleta : Temas.Paleta.values()) {
            ItemPaletaBinding tarjeta = ItemPaletaBinding.inflate(getLayoutInflater(), binding.contenedorPaletas, false);
            tarjeta.tvNombrePaleta.setText(paleta.nombre);
            tarjeta.tvLemaPaleta.setText(paleta.lema);
            tarjeta.getRoot().setTag(paleta);
            tarjeta.getRoot().setOnClickListener(v -> {
                if (paletaElegida == paleta) return;
                paletaElegida = paleta;
                actualizarTema(true);
            });
            tarjetasPaleta.add(tarjeta);
            binding.contenedorPaletas.addView(tarjeta.getRoot());
        }

        // Modo: 0 = el del teléfono, 1 = claro, 2 = oscuro
        int[] opcionesModo = {R.id.rbTemaSistema, R.id.rbTemaClaro, R.id.rbTemaOscuro};
        binding.rgTema.check(opcionesModo[modoElegido]);
        binding.rgTema.setOnCheckedChangeListener((grupo, idMarcado) -> {
            for (int i = 0; i < opcionesModo.length; i++) {
                if (opcionesModo[i] == idMarcado && i != modoElegido) {
                    modoElegido = i;
                    actualizarTema(true);
                }
            }
        });

        binding.btnAplicarTema.setOnClickListener(v ->
                ((MainActivity) requireActivity()).aplicarApariencia(paletaElegida, modoElegido, v));

        actualizarTema(false);
    }

    /** Fondos redondeados de la vista previa y los datos de quien está usando la app. */
    private void prepararVistaPrevia() {
        float dp = getResources().getDisplayMetrics().density;
        fondoPrevia.setCornerRadius(18 * dp);
        binding.vistaPrevia.setBackground(fondoPrevia);
        binding.vistaPrevia.setClipToOutline(true);   // el encabezado respeta las esquinas redondas
        binding.previaEncabezado.setBackground(fondoEncabezado);
        fondoAvatar.setShape(GradientDrawable.OVAL);
        binding.previaAvatar.setBackground(fondoAvatar);
        fondoRol.setCornerRadius(10 * dp);
        binding.previaRol.setBackground(fondoRol);
        fondoPrimario.setCornerRadius(19 * dp);
        binding.previaPrimario.setBackground(fondoPrimario);
        fondoTonal.setCornerRadius(19 * dp);
        binding.previaTonal.setBackground(fondoTonal);
        View[] roles = {binding.rolPrimario, binding.rolSecundario, binding.rolTerciario, binding.rolError};
        for (int i = 0; i < roles.length; i++) {
            fondosRoles[i] = new GradientDrawable();
            fondosRoles[i].setCornerRadius(8 * dp);
            roles[i].setBackground(fondosRoles[i]);
        }

        Usuario u = Sesion.getUsuario();
        binding.previaOptica.setText(Sesion.getOptica() != null ? Sesion.getOptica().nombre : "Óptica App");
        binding.previaAvatar.setText(Formato.iniciales(u.nombre));
        binding.previaRol.setText(Usuario.nombreRol(u.rol));
    }

    /**
     * Pinta la vista previa y las tarjetas con lo elegido (sin aplicarlo todavía) y muestra
     * "Aplicar cambios" solo si es distinto de lo que ya está aplicado.
     */
    private void actualizarTema(boolean animar) {
        if (binding == null) return;
        Context ctx = requireContext();
        boolean oscuro = Temas.esNoche(ctx, modoElegido);
        int[] destino = colores(ctx, paletaElegida, oscuro);
        pintarVistaPrevia(destino, animar);

        // Tarjetas: franjas con los colores de cada tema en el modo elegido; contorno y palomita en la elegida
        float dp = getResources().getDisplayMetrics().density;
        int acento = Temas.color(ctx, R.attr.colorAcento);
        int borde = Temas.color(ctx, R.attr.colorBorde);
        for (ItemPaletaBinding t : tarjetasPaleta) {
            Temas.Paleta paleta = (Temas.Paleta) t.getRoot().getTag();
            int[] c = colores(ctx, paleta, oscuro);
            t.franjaPrimario.setBackgroundColor(c[Temas.V_ACENTO]);
            t.franjaSecundario.setBackgroundColor(c[Temas.V_MARCA]);
            t.franjaTerciario.setBackgroundColor(c[Temas.V_TERCERO]);

            boolean elegida = paleta == paletaElegida;
            GradientDrawable contorno = new GradientDrawable();
            contorno.setCornerRadius(16 * dp);
            contorno.setColor(Temas.color(ctx, R.attr.colorSuperficie));
            contorno.setStroke(Math.round((elegida ? 2 : 1) * dp), elegida ? acento : borde);
            t.getRoot().setBackground(contorno);

            GradientDrawable circulo = new GradientDrawable();
            circulo.setShape(GradientDrawable.OVAL);
            if (elegida) {
                circulo.setColor(acento);
                t.ivElegida.setImageResource(R.drawable.ic_check);
                t.ivElegida.setColorFilter(Temas.color(ctx, R.attr.colorSobreAcento));
            } else {
                circulo.setStroke(Math.round(1.5f * dp), Temas.color(ctx, R.attr.colorTextoTenue));
                t.ivElegida.setImageDrawable(null);
            }
            t.ivElegida.setBackground(circulo);
            t.tvActual.setVisibility(paleta == paletaGuardada ? View.VISIBLE : View.GONE);
            t.getRoot().setSelected(elegida);
            t.getRoot().setContentDescription("Tema " + paleta.nombre + ": " + paleta.lema.replace(" · ", ", ")
                    + (elegida ? ". Elegido" : "") + (paleta == paletaGuardada ? ". Es el actual" : ""));
        }

        boolean hayCambios = paletaElegida != paletaGuardada || modoElegido != modoGuardado;
        binding.btnAplicarTema.setVisibility(hayCambios ? View.VISIBLE : View.GONE);
    }

    private int[] colores(Context ctx, Temas.Paleta paleta, boolean oscuro) {
        String clave = paleta.id + (oscuro ? "-oscuro" : "-claro");
        int[] c = coloresPorTema.get(clave);
        if (c == null) {
            c = Temas.coloresDeVistaPrevia(ctx, paleta, oscuro);
            coloresPorTema.put(clave, c);
        }
        return c;
    }

    /** Cambia los colores de la vista previa; si animar, pasa de los actuales a los nuevos en 320 ms. */
    private void pintarVistaPrevia(int[] destino, boolean animar) {
        if (animacionColores != null) animacionColores.cancel();
        if (!animar || coloresVista == null) {
            coloresVista = destino.clone();
            aplicarColoresVista(coloresVista);
            return;
        }
        int[] origen = coloresVista.clone();
        int[] intermedio = new int[destino.length];
        ArgbEvaluator mezcla = new ArgbEvaluator();
        animacionColores = ValueAnimator.ofFloat(0f, 1f);
        animacionColores.setDuration(DURACION_COLOR_MS);
        animacionColores.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            for (int i = 0; i < destino.length; i++) {
                intermedio[i] = (int) mezcla.evaluate(f, origen[i], destino[i]);
            }
            coloresVista = intermedio.clone();
            aplicarColoresVista(intermedio);
        });
        animacionColores.start();
    }

    private void aplicarColoresVista(int[] c) {
        if (binding == null) return;
        float dp = getResources().getDisplayMetrics().density;
        fondoPrevia.setColor(c[Temas.V_SUPERFICIE]);
        fondoPrevia.setStroke(Math.round(dp), c[Temas.V_BORDE]);
        fondoEncabezado.setColors(new int[]{c[Temas.V_DEGRADADO_INICIO], c[Temas.V_DEGRADADO_FIN]});
        binding.previaOptica.setTextColor(c[Temas.V_TEXTO]);
        fondoAvatar.setColor(c[Temas.V_MARCA_SUAVE]);
        binding.previaAvatar.setTextColor(c[Temas.V_MARCA]);
        binding.previaTitulo.setTextColor(c[Temas.V_TEXTO]);
        fondoRol.setColor(c[Temas.V_MARCA_SUAVE]);
        binding.previaRol.setTextColor(c[Temas.V_MARCA]);
        binding.previaTexto.setTextColor(c[Temas.V_TEXTO_SECUNDARIO]);
        fondoPrimario.setColor(c[Temas.V_ACENTO]);
        binding.previaPrimario.setTextColor(c[Temas.V_SOBRE_ACENTO]);
        fondoTonal.setColor(c[Temas.V_TONAL]);
        binding.previaTonal.setTextColor(c[Temas.V_TEXTO]);
        fondosRoles[0].setColor(c[Temas.V_ACENTO]);
        fondosRoles[1].setColor(c[Temas.V_MARCA]);
        fondosRoles[2].setColor(c[Temas.V_TERCERO]);
        fondosRoles[3].setColor(c[Temas.V_ERROR]);
    }

    // ---------------- Apariencia: imágenes, letra y fondo ----------------

    private void configurarApariencia() {
        Context ctx = requireContext();

        binding.swSinImagenes.setChecked(Preferencias.sinImagenes(ctx));
        binding.swSinImagenes.setOnCheckedChangeListener((boton, activo) -> Preferencias.setSinImagenes(ctx, activo));

        binding.swTextoGrande.setChecked(Preferencias.textoGrande(ctx));
        binding.swTextoGrande.setOnCheckedChangeListener((boton, activo) -> {
            Preferencias.setTextoGrande(ctx, activo);
            requireActivity().recreate();   // vuelve a crear la pantalla con el nuevo tamaño de letra
        });

        mostrarEstadoFondo();
        binding.filaFondo.setOnClickListener(v -> elegirFondo.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        binding.btnQuitarFondo.setOnClickListener(v -> {
            File fondo = Preferencias.archivoFondo(ctx);
            if (fondo.exists() && !fondo.delete()) {
                mensaje("No se pudo quitar el fondo");
            }
            ((MainActivity) requireActivity()).aplicarFondo();
            mostrarEstadoFondo();
        });
    }

    /** "Imagen personalizada" o "Ninguno", y el botón Quitar solo si hay fondo. */
    private void mostrarEstadoFondo() {
        if (binding == null) return;
        boolean hayFondo = Preferencias.archivoFondo(requireContext()).exists();
        binding.tvFondo.setText(hayFondo ? "Imagen personalizada" : "Ninguno · toca para elegir");
        binding.btnQuitarFondo.setVisibility(hayFondo ? View.VISIBLE : View.GONE);
    }

    /** Copia la imagen elegida dentro de la app para que siga disponible aunque se borre de la galería. */
    private void guardarFondo(Uri uri) {
        if (!isAdded()) return;
        File destino = Preferencias.archivoFondo(requireContext());
        try (InputStream entrada = requireContext().getContentResolver().openInputStream(uri);
             OutputStream salida = new FileOutputStream(destino)) {
            if (entrada == null) throw new IOException("No se pudo abrir la imagen");
            byte[] buffer = new byte[8192];
            int leidos;
            while ((leidos = entrada.read(buffer)) != -1) {
                salida.write(buffer, 0, leidos);
            }
        } catch (IOException e) {
            mensaje("No se pudo guardar el fondo: " + e.getMessage());
            return;
        }
        ((MainActivity) requireActivity()).aplicarFondo();
        mostrarEstadoFondo();
    }

    // ---------------- Sesión ----------------

    private void cerrarSesion() {
        // MainActivity también quita el listener del menú lateral antes de salir
        ((MainActivity) requireActivity()).cerrarSesion();
    }

    private void mensaje(String texto) {
        if (getContext() != null) {
            Toast.makeText(getContext(), texto, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        if (animacionColores != null) animacionColores.cancel();
        tarjetasPaleta.clear();
        super.onDestroyView();
        binding = null;
    }
}
