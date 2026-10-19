package mx.itch.opticaapp.ui.administracion;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;

import mx.itch.opticaapp.data.Sesion;
import mx.itch.opticaapp.util.Fechas;
import mx.itch.opticaapp.util.Formato;

/**
 * Administración · Corte de caja: resumen de las ventas de hoy (cuántas, total vendido
 * y dinero cobrado por método de pago). Se abre como diálogo desde el menú lateral.
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
                    double vendido = 0;
                    double cobrado = 0;
                    Map<String, Double> porMetodo = new LinkedHashMap<>();
                    for (DocumentSnapshot d : resultado.getDocuments()) {
                        Double total = d.getDouble("total");
                        Double anticipo = d.getDouble("anticipo");
                        String metodo = d.getString("metodoPago");
                        if (total != null) vendido += total;
                        if (anticipo != null) {
                            cobrado += anticipo;
                            String clave = metodo != null ? metodo : "Otro";
                            Double previo = porMetodo.get(clave);
                            porMetodo.put(clave, (previo != null ? previo : 0) + anticipo);
                        }
                    }
                    StringBuilder texto = new StringBuilder();
                    texto.append("Ventas: ").append(resultado.size())
                            .append("\nTotal vendido: ").append(Formato.moneda(vendido))
                            .append("\nDinero cobrado hoy: ").append(Formato.moneda(cobrado));
                    for (Map.Entry<String, Double> e : porMetodo.entrySet()) {
                        texto.append("\n   • ").append(e.getKey()).append(": ").append(Formato.moneda(e.getValue()));
                    }
                    new MaterialAlertDialogBuilder(ctx)
                            .setTitle("Corte de caja · " + Fechas.corta(Fechas.inicioDelDia()))
                            .setMessage(texto.toString())
                            .setPositiveButton("Cerrar", null)
                            .show();
                })
                .addOnFailureListener(e -> Toast.makeText(ctx,
                        "No se pudo calcular el corte: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
