package mx.itch.opticaapp;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavGraph;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.io.File;

import mx.itch.opticaapp.data.Preferencias;
import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.databinding.ActivityMainBinding;
import mx.itch.opticaapp.databinding.NavHeaderBinding;
import mx.itch.opticaapp.model.Optica;
import mx.itch.opticaapp.model.Usuario;
import mx.itch.opticaapp.util.Imagenes;

/**
 * Única Activity de la app. Contiene la barra superior, el contenedor de Fragments
 * (NavHostFragment), la barra de navegación inferior y el menú lateral (Navigation Drawer).
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    /** Vistas del encabezado del menú lateral (foto, nombre, correo). */
    private NavHeaderBinding encabezado;
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;

    /** Escucha en tiempo real el documento /users/{uid} para llenar el encabezado del menú. */
    private ListenerRegistration escuchaUsuario;
    /** Hace que "atrás" cierre el menú lateral cuando está abierto. */
    private OnBackPressedCallback cerrarMenuConAtras;

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

        // El encabezado lo crea NavigationView a partir de app:headerLayout
        encabezado = NavHeaderBinding.bind(binding.navView.getHeaderView(0));

        ajustarEspacios();
        setSupportActionBar(binding.toolbar);
        // La barra superior solo lleva íconos: cada pantalla dibuja su título grande sobre el degradado
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.navHost);
        navController = navHost.getNavController();
        cargarGrafo(Sesion.estaActiva());

        // Pantallas principales: muestran el botón ☰ (abre el menú lateral) en lugar de la flecha de "atrás"
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.loginFragment, R.id.panelFragment, R.id.pacientesFragment,
                R.id.inventarioFragment, R.id.ventaFragment, R.id.laboratorioFragment)
                .setOpenableLayout(binding.drawer)
                .build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.bottomNav, navController);
        // NavigationUI marca la sección actual; la navegación la hacemos nosotros para usar nuestras animaciones
        binding.bottomNav.setOnItemSelectedListener(this::navegarSeccion);
        configurarMenuLateral();

        // Muestra u oculta las barras según la pantalla
        navController.addOnDestinationChangedListener((controller, destino, argumentos) -> {
            binding.bottomNav.setVisibility(esPantallaPrincipal(destino) ? View.VISIBLE : View.GONE);
            // El login y el panel tienen su propio encabezado, como en el diseño
            boolean sinBarraSuperior = destino.getId() == R.id.loginFragment || destino.getId() == R.id.panelFragment;
            binding.toolbar.setVisibility(sinBarraSuperior ? View.GONE : View.VISIBLE);
            ponerIconoDeNavegacion(destino);
            // El menú lateral solo se puede abrir cuando ya hay una sesión iniciada
            boolean conMenu = Sesion.estaActiva() && destino.getId() != R.id.loginFragment
                    && destino.getId() != R.id.registroFragment;
            binding.drawer.setDrawerLockMode(conMenu
                    ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            invalidateOptionsMenu();
        });

        if (Sesion.estaActiva()) {
            configurarMenuPorRol();
        }
        aplicarFondo();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Sesion.estaActiva()) {
            escucharUsuario();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Al salir de la app ya no hace falta seguir escuchando a Firestore
        dejarDeEscucharUsuario();
    }

    /**
     * Deja espacio para la barra de estado, la barra de navegación del sistema y el teclado.
     * El contenido recibe el espacio como padding y el menú lateral recibe los mismos datos
     * para que su encabezado no quede debajo de la barra de estado.
     */
    private void ajustarEspacios() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawer, (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets teclado = insets.getInsets(WindowInsetsCompat.Type.ime());
            // Arriba el degradado llega detrás de la barra de estado; abajo una franja blanca
            // ocupa el lugar de la barra de navegación del sistema (o del teclado)
            binding.main.setPadding(barras.left, barras.top, barras.right, 0);
            ViewGroup.LayoutParams franja = binding.espacioInferior.getLayoutParams();
            franja.height = Math.max(barras.bottom, teclado.bottom);
            binding.espacioInferior.setLayoutParams(franja);
            ViewCompat.dispatchApplyWindowInsets(binding.navView, insets);
            return WindowInsetsCompat.CONSUMED;
        });

        View cabecera = encabezado.getRoot();
        int arribaOriginal = cabecera.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(cabecera, (v, insets) -> {
            int barraDeEstado = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            v.setPadding(v.getPaddingLeft(), arribaOriginal + barraDeEstado, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }

    /**
     * Ícono de la izquierda de la barra superior, como en el diseño:
     * cuatro puntos (abre el menú lateral) en las secciones principales y "‹" en las demás.
     * Se llama después del listener de NavigationUI, así que reemplaza su ícono de hamburguesa.
     */
    private void ponerIconoDeNavegacion(NavDestination destino) {
        ActionBar barra = getSupportActionBar();
        if (barra == null || destino.getId() == R.id.loginFragment) return;
        boolean principal = appBarConfiguration.getTopLevelDestinations().contains(destino.getId());
        barra.setHomeAsUpIndicator(principal ? R.drawable.ic_puntos : R.drawable.ic_atras);
        barra.setHomeActionContentDescription(principal ? R.string.menu_abrir : R.string.atras);
    }

    /** Carga el mapa de navegación empezando en el login o en el panel. */
    private void cargarGrafo(boolean conSesion) {
        NavGraph grafo = navController.getNavInflater().inflate(R.navigation.nav_graph);
        grafo.setStartDestination(conSesion ? R.id.panelFragment : R.id.loginFragment);
        navController.setGraph(grafo, null);
    }

    /**
     * La llaman LoginFragment / RegistroFragment al iniciar sesión y cerrarSesion() al salir.
     * Reinicia la navegación para que "atrás" no regrese a la pantalla anterior.
     */
    public void irAInicio(boolean conSesion) {
        binding.drawer.closeDrawer(GravityCompat.START, false);
        cargarGrafo(conSesion);
        if (conSesion) {
            configurarMenuPorRol();
            escucharUsuario();
        } else {
            dejarDeEscucharUsuario();
            limpiarEncabezado();
        }
        invalidateOptionsMenu();
    }

    /** Cambia de sección como si se tocara el botón de la barra inferior (lo usa el panel). */
    public void irASeccion(int idDestino) {
        binding.bottomNav.setSelectedItemId(idDestino);
    }

    /** Abre Perfil y ajustes (lo usa la tarjeta de perfil del panel y el encabezado del menú). */
    public void abrirPerfil() {
        NavDestination actual = navController.getCurrentDestination();
        if (actual != null && actual.getId() == R.id.ajustesFragment) return;   // ya está abierta
        navController.navigate(R.id.ajustesFragment, null, opcionesDetalle().build());
    }

    // ---------------- Animaciones entre pantallas ----------------

    /** Ir a una pantalla "hija" (detalle, formulario, ajustes): entra desde la derecha y al regresar sale a la derecha. */
    private NavOptions.Builder opcionesDetalle() {
        return new NavOptions.Builder()
                .setEnterAnim(R.anim.nav_entrar)
                .setExitAnim(R.anim.nav_salir)
                .setPopEnterAnim(R.anim.nav_regresar_entrar)
                .setPopExitAnim(R.anim.nav_regresar_salir);
    }

    /** Cambiar de sección: la anterior se desvanece y la nueva aparece creciendo un poco. */
    private NavOptions.Builder opcionesSeccion() {
        return new NavOptions.Builder()
                .setEnterAnim(R.anim.nav_seccion_entrar)
                .setExitAnim(R.anim.nav_seccion_salir)
                .setPopEnterAnim(R.anim.nav_seccion_entrar)
                .setPopExitAnim(R.anim.nav_seccion_salir);
    }

    /**
     * Navega a la sección de un botón de la barra inferior o del menú lateral.
     * Hace lo mismo que NavigationUI.onNavDestinationSelected (guardar y restaurar el historial
     * de cada sección), pero con nuestras animaciones. Las opciones marcadas como
     * menuCategory="secondary" (Configuración) se abren encima de la pantalla actual.
     */
    private boolean navegarSeccion(MenuItem item) {
        boolean secundaria = (item.getOrder() & Menu.CATEGORY_SECONDARY) != 0;
        NavOptions.Builder opciones = secundaria ? opcionesDetalle() : opcionesSeccion();
        opciones.setLaunchSingleTop(true).setRestoreState(true);
        if (!secundaria) {
            opciones.setPopUpTo(navController.getGraph().getStartDestinationId(), false, true);
        }
        NavDestination actual = navController.getCurrentDestination();
        if (actual != null && actual.getId() == item.getItemId()) return true;   // ya estamos ahí
        try {
            navController.navigate(item.getItemId(), null, opciones.build());
        } catch (IllegalArgumentException e) {
            return false;
        }
        NavDestination nuevo = navController.getCurrentDestination();
        return nuevo != null && nuevo.getId() == item.getItemId();
    }

    /** Abre el menú lateral (lo usa el botón ☰ del panel, que no tiene barra superior). */
    public void abrirMenu() {
        binding.drawer.openDrawer(GravityCompat.START);
    }

    /** Sale de la cuenta y regresa al inicio de sesión (la usan el menú lateral y Ajustes). */
    public void cerrarSesion() {
        // Primero se quita el listener: sin sesión, las reglas de Firestore rechazarían la lectura
        dejarDeEscucharUsuario();
        FirebaseAuth.getInstance().signOut();
        Sesion.cerrar();
        irAInicio(false);
    }

    // ---------------- Menú lateral ----------------

    private void configurarMenuLateral() {
        // NavigationUI marca la sección actual y navega al tocar una opción
        NavigationUI.setupWithNavController(binding.navView, navController);

        // Se reemplaza el listener de NavigationUI para atender también "Cerrar sesión",
        // que no es una pantalla del nav_graph
        binding.navView.setNavigationItemSelectedListener(item -> {
            binding.drawer.closeDrawer(GravityCompat.START);
            if (item.getItemId() == R.id.accionCerrarSesion) {
                confirmarCierreDeSesion();
                return false;
            }
            return navegarSeccion(item);
        });

        // Tocar la foto o el nombre abre Perfil y ajustes
        encabezado.encabezadoMenu.setOnClickListener(v -> {
            binding.drawer.closeDrawer(GravityCompat.START);
            abrirPerfil();
        });

        // "Atrás" con el menú abierto lo cierra en lugar de salir de la pantalla
        cerrarMenuConAtras = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                binding.drawer.closeDrawer(GravityCompat.START);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, cerrarMenuConAtras);
        binding.drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View menu) {
                cerrarMenuConAtras.setEnabled(true);
            }

            @Override
            public void onDrawerClosed(@NonNull View menu) {
                cerrarMenuConAtras.setEnabled(false);
            }
        });
    }

    private void confirmarCierreDeSesion() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Quieres salir de tu cuenta en este teléfono?")
                .setPositiveButton("Cerrar sesión", (dialogo, boton) -> cerrarSesion())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Lee /users/{uid} de Firestore y llena el encabezado del menú. Es un listener en tiempo real:
     * si el usuario cambia su foto en Ajustes (o desde otro teléfono), el menú se actualiza solo.
     */
    private void escucharUsuario() {
        dejarDeEscucharUsuario();
        FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
        if (!Sesion.estaActiva() || actual == null) return;

        // Mientras llega la respuesta se muestra lo que se cargó al iniciar sesión
        mostrarEncabezado(Sesion.getUsuario());

        escuchaUsuario = FirebaseFirestore.getInstance().collection("users").document(actual.getUid())
                .addSnapshotListener((documento, error) -> {
                    if (error != null || documento == null || !documento.exists()) return;
                    Usuario usuario = documento.toObject(Usuario.class);
                    if (usuario == null) return;
                    // Mantiene al día la sesión en memoria para las demás pantallas
                    Usuario enSesion = Sesion.getUsuario();
                    if (enSesion != null) {
                        enSesion.nombre = usuario.nombre;
                        enSesion.fotoUrl = usuario.fotoUrl;
                    }
                    mostrarEncabezado(usuario);
                });
    }

    private void dejarDeEscucharUsuario() {
        if (escuchaUsuario != null) {
            escuchaUsuario.remove();
            escuchaUsuario = null;
        }
    }

    private void mostrarEncabezado(Usuario usuario) {
        if (usuario == null) return;
        encabezado.tvNombreMenu.setText(usuario.nombre != null && !usuario.nombre.isEmpty()
                ? usuario.nombre : "Sin nombre");

        // El correo viene del documento; si no está, se toma el de la cuenta de Firebase
        String correo = usuario.email;
        FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
        if ((correo == null || correo.isEmpty()) && actual != null) {
            correo = actual.getEmail();
        }
        encabezado.tvCorreoMenu.setText(correo != null ? correo : "");

        Optica optica = Sesion.getOptica();
        String rol = Usuario.nombreRol(usuario.rol);
        encabezado.tvOpticaMenu.setText(optica != null ? optica.nombre + " · " + rol : rol);

        Imagenes.mostrar(encabezado.ivFotoMenu, usuario.fotoUrl, R.drawable.ic_perfil);
    }

    private void limpiarEncabezado() {
        encabezado.tvNombreMenu.setText("");
        encabezado.tvCorreoMenu.setText("");
        encabezado.tvOpticaMenu.setText("");
        Imagenes.mostrar(encabezado.ivFotoMenu, null, R.drawable.ic_perfil);
    }

    /** Oculta del menú lateral y de la barra inferior los módulos que el rol no usa. */
    private void configurarMenuPorRol() {
        // El optometrista no usa inventario ni ventas. El vendedor sí ve Pacientes,
        // pero solo para consultar (eso lo controla PacientesFragment).
        boolean optometrista = Sesion.esOptometrista();
        for (Menu menu : new Menu[]{binding.bottomNav.getMenu(), binding.navView.getMenu()}) {
            menu.findItem(R.id.inventarioFragment).setVisible(!optometrista);
            menu.findItem(R.id.ventaFragment).setVisible(!optometrista);
        }
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
            abrirPerfil();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        // En pantallas principales abre el menú lateral; en las demás regresa
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
    }
}
