package cl.nuam;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Operación y continuidad: modo mantención (HU-24), registro de caídas, consultas lentas (HU-23.6), vencimiento del certificado (HU-21.4) e integridad del log (HU-27). */
public final class Operacion {
    private Operacion() {}
    private static volatile boolean activa;
    private static volatile String aviso = "", programada = "";
    private static final Path FLAG = Paths.get(Config.DATA_DIR, "mantencion.json"), HB = Paths.get(Config.DATA_DIR, "heartbeat.txt");
    private static final ConcurrentHashMap<String, Long> ULTIMA_LENTA = new ConcurrentHashMap<>();

    // ------------------------------------------------------------ mantención (HU-24.2, 24.6)
    public static boolean enMantencion() { return activa; }

    public static Map<String, Object> estado() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("mantencion", activa); o.put("aviso", aviso); o.put("programada", programada);
        Long d = Tls.diasParaVencer(); if (d != null) o.put("certificadoDias", d);
        return o;
    }

    static void cargar() {
        try {
            if (Files.exists(FLAG)) {
                Map<String, Object> m = Json.parseObj(Files.readString(FLAG, StandardCharsets.UTF_8));
                activa = Json.bool(m.get("activa")); aviso = Json.str(m, "aviso") == null ? "" : Json.str(m, "aviso");
                programada = Json.str(m, "programada") == null ? "" : Json.str(m, "programada");
            }
        } catch (Exception e) { System.err.println("[mantencion] " + e); }
    }

    /** Solo ADMIN. La mantención programada debe quedar fuera del horario hábil (L-V 08:00-19:00) y avisarse con ≥ 48 h. */
    public static Map<String, Object> configurar(Connection c, Auth.Principal p, Map<String, Object> d) throws Exception {
        boolean act = Json.bool(d.get("activa"));
        String av = Json.str(d, "aviso"), prog = Json.str(d, "programada");
        if (prog != null) {
            LocalDateTime t;
            try { t = LocalDateTime.parse(prog); } catch (Exception e) { throw ApiException.invalid("Fecha programada inválida (use AAAA-MM-DDTHH:MM)").with("campo", "programada"); }
            if (t.isBefore(LocalDateTime.now().plusHours(48))) throw ApiException.invalid("La mantención debe avisarse con al menos 48 horas de anticipación").with("campo", "programada");
            DayOfWeek w = t.getDayOfWeek(); int h = t.getHour();
            if (w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY && h >= 8 && h < 19)
                throw ApiException.invalid("La mantención debe programarse fuera del horario hábil (lunes a viernes, 08:00 a 19:00)").with("campo", "programada");
        }
        activa = act; aviso = av == null ? "" : av; programada = prog == null ? "" : prog;
        Files.createDirectories(FLAG.getParent());
        Files.writeString(FLAG, Json.write(Map.of("activa", activa, "aviso", aviso, "programada", programada)), StandardCharsets.UTF_8);
        Audit.log(c, p, act ? "MANTENCION_ACTIVADA" : "MANTENCION_CONFIG", "sistema", null, "Modo mantención=" + act + (prog != null ? ", programada " + prog : "") + (av != null ? ", aviso: " + av : ""));
        return estado();
    }

    // ------------------------------------------------------------ caídas (HU-24.4) y vigilancia
    public static void iniciar() {
        cargar();
        try {
            Files.createDirectories(HB.getParent());
            if (Files.exists(HB)) {
                long ult = Long.parseLong(Files.readString(HB).trim()), ahora = System.currentTimeMillis();
                if (ahora - ult > 120_000) {
                    Audit.logNew(null, "SERVICIO_CAIDA", "sistema", null, "Servicio fuera de línea desde " + Instant.ofEpochMilli(ult) + " hasta " + Instant.ofEpochMilli(ahora)
                            + " (" + (ahora - ult) / 60000 + " min). Causa: detención o caída del proceso.", "SISTEMA");
                }
            }
        } catch (Exception e) { System.err.println("[heartbeat] " + e); }
        Audit.logNew(null, "SERVICIO_INICIO", "sistema", null, "Servicio iniciado", "SISTEMA");
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "vigilancia"); t.setDaemon(true); return t; });
        ex.scheduleAtFixedRate(() -> { try { Files.writeString(HB, String.valueOf(System.currentTimeMillis())); } catch (Exception ignore) { } }, 0, 30, TimeUnit.SECONDS);
        ex.scheduleAtFixedRate(() -> { try { revisarCertificado(); } catch (Throwable t) { System.err.println("[cert] " + t); } }, 10, 86_400, TimeUnit.SECONDS);
    }

    public static void apagar() { try { Files.deleteIfExists(HB); } catch (Exception ignore) { } }

    // ------------------------------------------------------------ alertas al administrador
    public static void notificarAdmins(String tipo, String mensaje) {
        try {
            Db.tx(c -> {
                for (Map<String, Object> a : Db.q(c, "SELECT usuario_id FROM usuario WHERE rol = 'ADMIN' AND activo")) {
                    if (Db.count(c, "SELECT count(*) FROM notificacion WHERE usuario_id = ? AND tipo = ? AND dia = current_date", a.get("usuario_id"), tipo) > 0) continue;
                    Db.exec(c, "INSERT INTO notificacion (usuario_id, tipo, mensaje) VALUES (?,?,?)", a.get("usuario_id"), tipo, mensaje);
                }
                return null;
            });
        } catch (Exception e) { System.err.println("[notificarAdmins] " + e); }
    }

    static void revisarCertificado() {
        Long d = Tls.diasParaVencer();
        if (d != null && d <= 30) {
            String m = d < 0 ? "El certificado TLS está vencido." : "El certificado TLS vence en " + d + " día(s). Renuévelo.";
            Audit.logNew(null, "CERTIFICADO_POR_VENCER", "sistema", null, m, "SISTEMA");
            notificarAdmins("CERT_TLS", m);
        }
    }

    /** HU-23.6: consulta que supera el umbral → queda en el log y alerta al administrador (máx. una alerta por ruta cada 10 min). */
    public static void lenta(String metodo, String ruta, long ms) {
        String k = metodo + " " + ruta.replaceAll("\\d+", "#");
        long now = System.currentTimeMillis();
        Long prev = ULTIMA_LENTA.get(k);
        if (prev != null && now - prev < 600_000) return;
        ULTIMA_LENTA.put(k, now);
        Audit.logNew(null, "CONSULTA_LENTA", "sistema", null, k + " tardó " + ms + " ms (umbral " + Config.SLOW_MS + " ms)", "SISTEMA");
        notificarAdmins("RENDIMIENTO", "Consulta lenta: " + k + " tardó " + ms + " ms.");
    }

    // ------------------------------------------------------------ integridad del log (HU-27.5/27.6)
    public static Map<String, Object> verificarLog(Connection c) throws Exception {
        Map<String, Object> r = Db.one(c, "WITH x AS (SELECT cadena, hash, hash_prev, lag(hash) OVER w AS ph, lag(cadena) OVER w AS pc, " +
                "auditoria_calc_hash(hash_prev, cadena, usuario_id, accion, entidad_afectada, registro_id, detalle, origen, fecha) AS calc " +
                "FROM auditoria WHERE cadena IS NOT NULL WINDOW w AS (ORDER BY cadena)) " +
                "SELECT count(*) AS total, min(cadena) FILTER (WHERE calc <> hash OR hash_prev <> coalesce(ph, 'GENESIS') OR (pc IS NOT NULL AND cadena <> pc + 1)) AS rota FROM x");
        long previos = Db.count(c, "SELECT count(*) FROM auditoria WHERE cadena IS NULL");
        Object rota = r.get("rota");
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("ok", rota == null); o.put("eventosVerificados", r.get("total")); o.put("eventosAnterioresALaCadena", previos);
        o.put("primerEventoAlterado", rota);
        o.put("mensaje", rota == null ? "Integridad correcta: la cadena de hashes no presenta alteraciones." : "¡ALTERACIÓN DETECTADA! La cadena se rompe en el evento #" + rota + ".");
        return o;
    }
}
