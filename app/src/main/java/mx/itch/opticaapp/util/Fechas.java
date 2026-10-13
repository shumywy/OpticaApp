package mx.itch.opticaapp.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Funciones de apoyo para fechas. */
public final class Fechas {

    private static final Locale MX = Locale.forLanguageTag("es-MX");

    private Fechas() {
    }

    public static String corta(Date fecha) {
        if (fecha == null) return "—";
        return new SimpleDateFormat("dd/MM/yyyy", MX).format(fecha);
    }

    public static String conHora(Date fecha) {
        if (fecha == null) return "—";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", MX).format(fecha);
    }

    /** Hoy a las 00:00. */
    public static Date inicioDelDia() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /** El lunes de esta semana a las 00:00 (la semana empieza en lunes). */
    public static Date inicioDeSemana() {
        Calendar c = Calendar.getInstance();
        c.setTime(inicioDelDia());
        int dia = c.get(Calendar.DAY_OF_WEEK);           // domingo = 1 ... sábado = 7
        int diasDesdeLunes = (dia + 5) % 7;              // lunes = 0 ... domingo = 6
        c.add(Calendar.DAY_OF_MONTH, -diasDesdeLunes);
        return c.getTime();
    }

    /** Días que faltan para terminar la semana contando hoy (lunes = 7 ... domingo = 1). */
    public static int diasRestantesDeSemana() {
        int dia = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        int diasDesdeLunes = (dia + 5) % 7;
        return 7 - diasDesdeLunes;
    }
}
