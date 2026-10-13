package mx.itch.opticaapp;

import android.app.Application;

import mx.itch.opticaapp.data.Preferencias;

/** Se ejecuta una vez al abrir la app, antes que cualquier pantalla. */
public class OpticaApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Aplica el tema (claro / oscuro / sistema) que el usuario eligió en Ajustes
        Preferencias.aplicarTema(this);
    }
}
