package mx.itch.opticaapp.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Funciones de apoyo para fechas. */
public final class Fechas {

    private static final Locale MX = Locale.forLanguageTag("es-MX");

    private Fechas() {
    }

    public static String corta(Date fecha) {
        if (fecha == null) return "—";
        return new SimpleDateFormat("dd/MM/yyyy", MX).format(fecha);
    }

    private static final String[] MESES = {"ene", "feb", "mar", "abr", "may", "jun",
            "jul", "ago", "sep", "oct", "nov", "dic"};

    /** Fecha corta para listas: "7 oct" este año, "7 oct 2025" si es de otro año. */
    public static String diaMes(Date fecha) {
        if (fecha == null) return "—";
        Calendar c = Calendar.getInstance();
        int anioActual = c.get(Calendar.YEAR);
        c.setTime(fecha);
        String texto = c.get(Calendar.DAY_OF_MONTH) + " " + MESES[c.get(Calendar.MONTH)];
        return c.get(Calendar.YEAR) == anioActual ? texto : texto + " " + c.get(Calendar.YEAR);
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

    /** "10:42" (24 horas). */
    public static String hora(Date fecha) {
        if (fecha == null) return "—";
        return new SimpleDateFormat("HH:mm", MX).format(fecha);
    }

    /** Clave del día para guardar en Firestore: "2026-10-20". */
    public static String clave(Date dia) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(dia);
    }

    /** "2026-10-20" → 20 de octubre a las 00:00 (null si el texto no tiene ese formato). */
    public static Date desdeClave(String clave) {
        if (clave == null) return null;
        try {
            SimpleDateFormat formato = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            formato.setLenient(false);
            return formato.parse(clave);
        } catch (java.text.ParseException e) {
            return null;
        }
    }

    /** El mismo día a las 00:00. */
    public static Date inicioDe(Date fecha) {
        Calendar c = Calendar.getInstance();
        c.setTime(fecha);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /** El día siguiente a las 00:00 (con Calendar, para respetar cambios de horario). */
    public static Date diaSiguiente(Date inicioDelDia) {
        Calendar c = Calendar.getInstance();
        c.setTime(inicioDelDia);
        c.add(Calendar.DAY_OF_MONTH, 1);
        return c.getTime();
    }

    /** "Hoy · 20 oct", "Ayer · 19 oct" o "Lunes · 12 oct" (con el año si es de otro año). */
    public static String diaConNombre(Date dia) {
        Date inicio = inicioDe(dia);
        Date hoy = inicioDelDia();
        Calendar ayer = Calendar.getInstance();
        ayer.setTime(hoy);
        ayer.add(Calendar.DAY_OF_MONTH, -1);
        String nombre;
        if (inicio.equals(hoy)) {
            nombre = "Hoy";
        } else if (inicio.equals(ayer.getTime())) {
            nombre = "Ayer";
        } else {
            String semana = new SimpleDateFormat("EEEE", MX).format(inicio);
            nombre = semana.substring(0, 1).toUpperCase(MX) + semana.substring(1);
        }
        return nombre + " · " + diaMes(inicio);
    }

    /**
     * MaterialDatePicker trabaja con la medianoche UTC del día elegido. Esto la convierte al
     * mismo día del calendario a las 00:00 en la hora del teléfono (sin recorrer un día).
     */
    public static Date desdeSelector(long medianocheUtc) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(medianocheUtc);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
        return local.getTime();
    }

    /** Lo contrario de desdeSelector: el día local como medianoche UTC para preseleccionarlo. */
    public static long paraSelector(Date dia) {
        Calendar local = Calendar.getInstance();
        local.setTime(dia);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

    /** Días que faltan para terminar la semana contando hoy (lunes = 7 ... domingo = 1). */
    public static int diasRestantesDeSemana() {
        int dia = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        int diasDesdeLunes = (dia + 5) % 7;
        return 7 - diasDesdeLunes;
    }
}
