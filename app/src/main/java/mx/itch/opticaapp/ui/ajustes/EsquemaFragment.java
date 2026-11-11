package mx.itch.opticaapp.ui.ajustes;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import mx.itch.opticaapp.R;
import mx.itch.opticaapp.data.Preferencias;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.FragmentEsquemaBinding;
import mx.itch.opticaapp.databinding.ItemNucleoBinding;
import mx.itch.opticaapp.databinding.ItemTonosBinding;
import mx.itch.opticaapp.databinding.VistaComponentesBinding;
import mx.itch.opticaapp.util.Animaciones;
import mx.itch.opticaapp.util.EsquemaM3;
import mx.itch.opticaapp.util.Temas;

/**
 * Esquema de color de un tema (Ajustes › Apariencia › Ver esquema completo), como en la app Atrium
 * y en Material Theme Builder: colores núcleo, los 48 roles de Material 3 en claro u oscuro,
 * las 6 paletas tonales y el muestrario de componentes pintado con ese tema.
 *
 * Se puede ver el esquema de cualquiera de los tres temas sin aplicarlo: los componentes se
 * inflan con un Context que tiene ese tema (Temas.contextoDe), no con el de la pantalla.
 * Tocar cualquier color abre su ficha (FichaColor).
 */
public class EsquemaFragment extends Fragment {

    /** Argumentos: id de la paleta ("cristal", "clinica", "carey") y si se abre en oscuro. */
    public static final String ARG_PALETA = "paleta";
    public static final String ARG_OSCURO = "oscuro";

    private static final String[] ORIGEN_NUCLEO = {
            "Acento del tema · personalizado", "Secundario del tema · personalizado",
            "Terciario del tema · personalizado", "Rojo de error de la app",
            "Derivado del primario", "Derivado del primario"};

    private FragmentEsquemaBinding binding;
    private Temas.Paleta paleta;
    private boolean oscuro;
    /** Llena y vacía los indicadores de progreso del muestrario; se detiene al salir. */
    private ValueAnimator progreso;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentEsquemaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!Sesion.estaActiva()) return;
        Context ctx = requireContext();
        Bundle args = getArguments();
        paleta = Temas.Paleta.desdeId(args != null && args.getString(ARG_PALETA) != null
                ? args.getString(ARG_PALETA) : Preferencias.getPaleta(ctx).id);
        oscuro = savedInstanceState != null ? savedInstanceState.getBoolean(ARG_OSCURO)
                : args != null && args.containsKey(ARG_OSCURO) ? args.getBoolean(ARG_OSCURO) : Temas.esNoche(ctx);

        binding.tvNombreEsquema.setText(paleta.nombre);
        binding.tvLemaEsquema.setText(paleta.lema + ". " + paleta.descripcion);
        construirNucleo();
        construirTonos();

        binding.grupoModoEsquema.check(oscuro ? R.id.btnEsquemaOscuro : R.id.btnEsquemaClaro);
        binding.grupoModoEsquema.addOnButtonCheckedListener((grupo, id, marcado) -> {
            if (!marcado) return;
            boolean nuevo = id == R.id.btnEsquemaOscuro;
            if (nuevo == oscuro) return;
            oscuro = nuevo;
            mostrarEsquema(true);
        });
        mostrarEsquema(false);

        Animaciones.entrada(this, binding.encabezado, binding.hoja);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(ARG_OSCURO, oscuro);
    }

    @Override
    public void onDestroyView() {
        if (progreso != null) progreso.cancel();
        super.onDestroyView();
        binding = null;
    }

    // ---------------- Roles y componentes (cambian con claro / oscuro) ----------------

    private void mostrarEsquema(boolean animar) {
        int[] roles = Temas.roles(requireContext(), paleta, oscuro);
        Runnable construir = () -> {
            if (binding == null) return;
            binding.tvTituloRoles.setText(oscuro ? "Esquema oscuro" : "Esquema claro");
            // La tarjeta usa el fondo del propio esquema, como Material Theme Builder
            binding.tarjetaRoles.setBackground(tarjeta(roles[EsquemaM3.SURFACE_CONTAINER_LOWEST],
                    roles[EsquemaM3.OUTLINE_VARIANT]));
            CuadriculaEsquema.construir(binding.cuadriculaRoles, roles,
                    rol -> FichaColor.mostrarRol(requireContext(), rol, roles));
            inflarComponentes(roles);
        };
        if (!animar) {
            construir.run();
            return;
        }
        // Se desvanece, se reconstruye con el otro modo y vuelve a aparecer
        View[] partes = {binding.tarjetaRoles, binding.contenedorComponentes};
        for (View p : partes) p.animate().alpha(0f).setDuration(150).start();
        binding.tarjetaRoles.postDelayed(() -> {
            construir.run();
            if (binding == null) return;
            for (View p : partes) {
                p.setScaleX(0.97f);
                p.setScaleY(0.97f);
                p.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380)
                        .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
            }
        }, 160);
    }

    /** Infla vista_componentes.xml con el tema que se está viendo (y la tipografía del usuario). */
    private void inflarComponentes(int[] roles) {
        if (progreso != null) progreso.cancel();
        binding.contenedorComponentes.removeAllViews();
        Context tema = Temas.contextoDe(requireContext(), paleta, oscuro);
        tema.getTheme().applyStyle(Preferencias.getFuente(requireContext()).estilo, true);

        VistaComponentesBinding c = VistaComponentesBinding.inflate(LayoutInflater.from(tema),
                binding.contenedorComponentes, false);
        c.getRoot().setBackground(tarjeta(roles[EsquemaM3.SURFACE], roles[EsquemaM3.OUTLINE_VARIANT]));
        c.demoErrorField.setError("Este campo usa el rol Error");
        c.btnDemoDialog.setOnClickListener(v -> new MaterialAlertDialogBuilder(tema)
                .setIcon(R.drawable.ic_paleta)
                .setTitle("Diálogo de ejemplo")
                .setMessage("El fondo usa Surface Container High, el título On Surface y los botones Primary.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Aceptar", null)
                .show());
        binding.contenedorComponentes.addView(c.getRoot());

        // Los indicadores determinados se llenan y vacían en bucle
        progreso = ValueAnimator.ofInt(10, 90);
        progreso.setDuration(2600);
        progreso.setRepeatCount(ValueAnimator.INFINITE);
        progreso.setRepeatMode(ValueAnimator.REVERSE);
        progreso.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        progreso.addUpdateListener(a -> {
            int valor = (int) a.getAnimatedValue();
            c.demoLinear.setProgress(valor);
            c.demoCircular.setProgress(100 - valor);
        });
        progreso.start();
    }

    // ---------------- Núcleo y paletas tonales (iguales en claro y oscuro) ----------------

    private void construirNucleo() {
        Context ctx = requireContext();
        int[] nucleo = Temas.nucleo(ctx, paleta);
        int[][] tonos = Temas.tonos(ctx, paleta);
        binding.listaNucleo.removeAllViews();
        for (int i = 0; i < nucleo.length; i++) {
            final int p = i;
            ItemNucleoBinding fila = ItemNucleoBinding.inflate(getLayoutInflater(), binding.listaNucleo, false);
            fila.muestraNucleo.setBackgroundTintList(ColorStateList.valueOf(nucleo[i]));
            fila.tvNombreNucleo.setText(EsquemaM3.NOMBRES_PALETAS_EN[i]);
            fila.tvOrigenNucleo.setText(ORIGEN_NUCLEO[i]);
            fila.tvHexNucleo.setText(EsquemaM3.hex(nucleo[i]));
            // Al tocarlo se ve el tono 40 de su paleta (el que usa Material 3 en claro)
            fila.getRoot().setOnClickListener(v -> FichaColor.mostrarTono(ctx, p, 4, tonos[p][4]));
            binding.listaNucleo.addView(fila.getRoot());
        }
    }

    private void construirTonos() {
        Context ctx = requireContext();
        int[] nucleo = Temas.nucleo(ctx, paleta);
        int[][] tonos = Temas.tonos(ctx, paleta);
        binding.listaTonos.removeAllViews();
        for (int p = 0; p < tonos.length; p++) {
            final int indicePaleta = p;
            ItemTonosBinding fila = ItemTonosBinding.inflate(getLayoutInflater(), binding.listaTonos, false);
            fila.tvNombreTonos.setText(EsquemaM3.NOMBRES_PALETAS_EN[p] + " · " + EsquemaM3.hex(nucleo[p]));
            CuadriculaEsquema.redondear(ctx, fila.franjaTonos);
            for (int t = 0; t < tonos[p].length; t++) {
                final int indiceTono = t;
                int color = tonos[p][t];
                FrameLayout celda = new FrameLayout(ctx);
                celda.setBackgroundColor(color);
                TextView numero = new TextView(ctx);
                numero.setText(String.valueOf(EsquemaM3.TONOS[t]));
                numero.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
                numero.setTextColor(FichaColor.textoLegible(color));
                celda.addView(numero, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
                celda.setContentDescription(EsquemaM3.NOMBRES_PALETAS_EN[p] + " tono " + EsquemaM3.TONOS[t]);
                celda.setOnClickListener(v -> FichaColor.mostrarTono(ctx, indicePaleta, indiceTono, color));
                fila.franjaTonos.addView(celda, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            }
            binding.listaTonos.addView(fila.getRoot());
        }
    }

    private GradientDrawable tarjeta(int fondo, int borde) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(CuadriculaEsquema.dp(requireContext(), 20));
        d.setColor(fondo);
        d.setStroke(CuadriculaEsquema.dp(requireContext(), 1), borde);
        return d;
    }
}
