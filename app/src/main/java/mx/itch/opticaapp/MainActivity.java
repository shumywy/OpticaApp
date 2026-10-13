package mx.itch.opticaapp;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavGraph;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;

import java.io.File;

import mx.itch.opticaapp.data.Preferencias;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.ActivityMainBinding;

/**
 * Única Activity de la app. Contiene la barra superior, el contenedor de Fragments
 * (NavHostFragment) y la barra de navegación inferior.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;

    @Override
    protected void attachBaseContext(Context base) {
        // Aplica la opción "Texto grande" de Ajustes antes de crear la pantalla
        super.attachBaseContext(Preferencias.conTamanoDeTexto(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Si Android cerró la app en segundo plano se pierde la sesión en memoria;
        // en ese caso no restauramos las pantallas anteriores y empezamos desde el login.
        super.onCreate(Sesion.estaActiva() ? savedInstanceState : null);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Deja espacio para la barra de estado, la barra de navegación del sistema y el teclado
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets teclado = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(barras.left, barras.top, barras.right, Math.max(barras.bottom, teclado.bottom));
            return WindowInsetsCompat.CONSUMED;
        });

        setSupportActionBar(binding.toolbar);

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.navHost);
        navController = navHost.getNavController();
        cargarGrafo(Sesion.estaActiva());

        // Pantallas principales: no muestran flecha de "atrás"
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.loginFragment, R.id.panelFragment, R.id.pacientesFragment,
                R.id.inventarioFragment, R.id.ventaFragment, R.id.laboratorioFragment).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.bottomNav, navController);

        // Muestra u oculta las barras según la pantalla
        navController.addOnDestinationChangedListener((controller, destino, argumentos) -> {
            int barraInferior = esPantallaPrincipal(destino) ? View.VISIBLE : View.GONE;
            binding.bottomNav.setVisibility(barraInferior);
            binding.divisorNav.setVisibility(barraInferior);
            // El login y el panel tienen su propio encabezado, como en el diseño
            boolean sinBarraSuperior = destino.getId() == R.id.loginFragment || destino.getId() == R.id.panelFragment;
            binding.toolbar.setVisibility(sinBarraSuperior ? View.GONE : View.VISIBLE);
            invalidateOptionsMenu();
        });

        if (Sesion.estaActiva()) {
            configurarMenuPorRol();
        }
        aplicarFondo();
    }

    /** Carga el mapa de navegación empezando en el login o en el panel. */
    private void cargarGrafo(boolean conSesion) {
        NavGraph grafo = navController.getNavInflater().inflate(R.navigation.nav_graph);
        grafo.setStartDestination(conSesion ? R.id.panelFragment : R.id.loginFragment);
        navController.setGraph(grafo, null);
    }

    /**
     * La llaman LoginFragment / RegistroFragment al iniciar sesión y AjustesFragment al cerrarla.
     * Reinicia la navegación para que "atrás" no regrese a la pantalla anterior.
     */
    public void irAInicio(boolean conSesion) {
        cargarGrafo(conSesion);
        if (conSesion) {
            configurarMenuPorRol();
        }
        invalidateOptionsMenu();
    }

    /** Cambia de sección como si se tocara el botón de la barra inferior (lo usa el panel). */
    public void irASeccion(int idDestino) {
        binding.bottomNav.setSelectedItemId(idDestino);
    }

    /** Abre Perfil y ajustes (lo usa la tarjeta de perfil del panel). */
    public void abrirPerfil() {
        navController.navigate(R.id.ajustesFragment);
    }

    /** Oculta de la barra inferior los módulos que el rol no usa. */
    private void configurarMenuPorRol() {
        // El optometrista no usa inventario ni ventas. El vendedor sí ve Pacientes,
        // pero solo para consultar (eso lo controla PacientesFragment).
        Menu menu = binding.bottomNav.getMenu();
        boolean optometrista = Sesion.esOptometrista();
        menu.findItem(R.id.inventarioFragment).setVisible(!optometrista);
        menu.findItem(R.id.ventaFragment).setVisible(!optometrista);
    }

    private boolean esPantallaPrincipal(NavDestination destino) {
        int id = destino.getId();
        return id == R.id.panelFragment || id == R.id.pacientesFragment || id == R.id.inventarioFragment
                || id == R.id.ventaFragment || id == R.id.laboratorioFragment;
    }

    /** Muestra el fondo de pantalla elegido en Ajustes (si hay uno). */
    public void aplicarFondo() {
        File fondo = Preferencias.archivoFondo(this);
        if (fondo.exists()) {
            binding.ivFondo.setVisibility(View.VISIBLE);
            // La "firma" con la fecha del archivo obliga a Glide a recargar si se cambió la imagen
            Glide.with(this).load(fondo).signature(new ObjectKey(fondo.lastModified())).into(binding.ivFondo);
        } else {
            Glide.with(this).clear(binding.ivFondo);
            binding.ivFondo.setVisibility(View.GONE);
        }
    }

    // ---------------- Menú de la barra superior (botón de perfil) ----------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_principal, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        NavDestination actual = navController != null ? navController.getCurrentDestination() : null;
        boolean visible = Sesion.estaActiva() && actual != null && actual.getId() != R.id.ajustesFragment
                && actual.getId() != R.id.loginFragment && actual.getId() != R.id.registroFragment;
        menu.findItem(R.id.accionPerfil).setVisible(visible);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.accionPerfil) {
            navController.navigate(R.id.ajustesFragment);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
    }
}
