package mx.itch.opticaapp.ui.administracion;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.model.Corte;
import mx.itch.opticaapp.model.Usuario;
import mx.itch.opticaapp.util.Aviso;
import mx.itch.opticaapp.util.Fechas;
import mx.itch.opticaapp.util.Formato;

/**
 * Administración · Corte de caja: resumen de las ventas de hoy (cuántas, total vendido,
 * dinero cobrado por método de pago y saldo pendiente).
 *
 * "Guardar corte" lo registra en /opticas/{opticaId}/cortes con quién lo hizo y la hora del servidor;
 * "Cortes anteriores" muestra los últimos guardados.
 */
public final class CorteDeCaja {

    private CorteDeCaja() {
    }

    public static void mostrar(Context ctx) {
        if (!Sesion.estaActiva()) return;
        Sesion.coleccion("ventas")
                .whereGreaterThanOrEqualTo("fecha", Fechas.inicioDelDia())
                .get()
                .addOnSuccessListener(resultado -> {
                    if (ctx instanceof Activity && ((Activity) ctx).isFinishing()) return;
                    Corte corte = calcular(resultado.getDocuments());
                    new MaterialAlertDialogBuilder(ctx)
                            .setTitle("Corte de caja · " + Fechas.corta(Fechas.inicioDelDia()))
                            .setMessage(texto(corte))
                            .setPositiveButton("Guardar corte", (d, w) -> guardar(ctx, corte))
                            .setNeutralButton("Cortes anteriores", (d, w) -> mostrarAnteriores(ctx))
                            .setNegativeButton("Cerrar", null)
                            .show();
                })
                .addOnFailureListener(e -> Toast.makeText(ctx,
                        "No se pudo calcular el corte: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    /** Suma las ventas del día. */
    private static Corte calcular(List<DocumentSnapshot> ventas) {
        Corte c = new Corte();
        c.dia = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        c.ventas = ventas.size();
        for (DocumentSnapshot d : ventas) {
            Double total = d.getDouble("total");
            Double anticipo = d.getDouble("anticipo");
            Double saldo = d.getDouble("saldo");
            String metodo = d.getString("metodoPago");
            if (total != null) c.totalVendido += total;
            if (saldo != null) c.saldoPendiente += saldo;
            if (anticipo != null) {
                c.cobrado += anticipo;
                String clave = metodo != null ? metodo : "Otro";
                Double previo = c.porMetodo.get(clave);
                c.porMetodo.put(clave, (previo != null ? previo : 0) + anticipo);
            }
        }
        return c;
    }

    private static String texto(Corte c) {
        StringBuilder t = new StringBuilder();
        t.append("Ventas: ").append(c.ventas)
                .append("\nTotal vendido: ").append(Formato.moneda(c.totalVendido))
                .append("\nDinero cobrado: ").append(Formato.moneda(c.cobrado));
        for (Map.Entry<String, Double> e : c.porMetodo.entrySet()) {
            t.append("\n   • ").append(e.getKey()).append(": ").append(Formato.moneda(e.getValue()));
        }
        t.append("\nSaldo pendiente: ").append(Formato.moneda(c.saldoPendiente));
        return t.toString();
    }

    /** Guarda el corte en Firestore; la fecha la pone el servidor (@ServerTimestamp). */
    private static void guardar(Context ctx, Corte corte) {
        Usuario u = Sesion.getUsuario();
        corte.realizadoPorId = u.id;
        corte.realizadoPorNombre = u.nombre;
        Sesion.coleccion("cortes").add(corte)
                .addOnSuccessListener(x -> Aviso.mostrar(ctx instanceof Activity ? (Activity) ctx : null,
                        "Corte de caja guardado"))
                .addOnFailureListener(e -> Toast.makeText(ctx,
                        "No se pudo guardar el corte: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    /** Lista de los últimos 15 cortes guardados. */
    private static void mostrarAnteriores(Context ctx) {
        Sesion.coleccion("cortes")
                .orderBy("fecha", Query.Direction.DESCENDING)
                .limit(15)
                .get()
                .addOnSuccessListener(resultado -> {
                    if (ctx instanceof Activity && ((Activity) ctx).isFinishing()) return;
                    List<Corte> cortes = resultado.toObjects(Corte.class);
                    MaterialAlertDialogBuilder dialogo = new MaterialAlertDialogBuilder(ctx)
                            .setTitle("Cortes anteriores")
                            .setPositiveButton("Cerrar", null);
                    if (cortes.isEmpty()) {
                        dialogo.setMessage("Todavía no hay cortes guardados.");
                    } else {
                        String[] renglones = new String[cortes.size()];
                        for (int i = 0; i < cortes.size(); i++) {
                            Corte c = cortes.get(i);
                            renglones[i] = (c.fecha != null ? Fechas.conHora(c.fecha) : c.dia)
                                    + " · " + c.realizadoPorNombre
                                    + "\n" + c.ventas + (c.ventas == 1 ? " venta" : " ventas")
                                    + " · vendido " + Formato.moneda(c.totalVendido)
                                    + " · cobrado " + Formato.moneda(c.cobrado);
                        }
                        dialogo.setItems(renglones, (d, posicion) -> new MaterialAlertDialogBuilder(ctx)
                                .setTitle("Corte del " + Fechas.corta(cortes.get(posicion).fecha))
                                .setMessage(texto(cortes.get(posicion))
                                        + "\n\nRealizó: " + cortes.get(posicion).realizadoPorNombre)
                                .setPositiveButton("Cerrar", null)
                                .show());
                    }
                    dialogo.show();
                })
                .addOnFailureListener(e -> Toast.makeText(ctx,
                        "No se pudieron leer los cortes: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
