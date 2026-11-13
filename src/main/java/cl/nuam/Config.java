package cl.nuam;

import java.net.URI;

/** Configuración por variables de entorno (valores por defecto para desarrollo). */
public final class Config {
    private Config() {}
    static String env(String k, String d) { String v = System.getenv(k); return v == null || v.isBlank() ? d : v; }

    public static final int PORT = Integer.parseInt(env("PORT", "8080"));
    public static final String DB_URL = env("NUAM_DB_URL", "jdbc:postgresql://localhost:5432/nuam_is");
    public static final String DB_USER = env("NUAM_DB_USER", "nuam");
    public static final String DB_PASS = env("NUAM_DB_PASS", "nuam");
    /** HU-20: inactividad máxima de sesión (15 min). */
    public static final int IDLE_SECONDS = Integer.parseInt(env("NUAM_IDLE_SECONDS", "900"));
    /** Modo demo: muestra el código TOTP vigente en la pantalla de 2FA. Poner NUAM_DEMO=0 en producción. */
    public static final boolean DEMO = env("NUAM_DEMO", "1").equals("1");
    public static final boolean SEED = env("NUAM_SEED", "1").equals("1");
    /** HU-21: HTTPS (TLS 1.2+/1.3). El puerto HTTP solo redirige a HTTPS. NUAM_TLS=0 desactiva TLS (solo pruebas locales). */
    public static final boolean TLS = env("NUAM_TLS", "1").equals("1");
    public static final int HTTPS_PORT = Integer.parseInt(env("NUAM_HTTPS_PORT", "8443"));
    public static final String KEYSTORE = env("NUAM_KEYSTORE", "data/nuam.p12");
    /** HU-22.3: la clave del certificado y la de respaldos viven en variables de entorno, separadas de los datos. */
    public static final String KEYSTORE_PASS = env("NUAM_KEYSTORE_PASS", "nuam-tls-dev");
    public static final boolean SECURE_COOKIES = env("NUAM_SECURE_COOKIES", TLS ? "1" : "0").equals("1");
    public static final String DATA_DIR = env("NUAM_DATA_DIR", "data");
    public static final int SLOW_MS = Integer.parseInt(env("NUAM_SLOW_MS", "3000"));
    public static final String BACKUP_DIR = env("NUAM_BACKUP_DIR", "backups");
    public static final String BACKUP_KEY = env("NUAM_BACKUP_KEY", "cambiar-esta-clave-en-produccion");
    public static final int BACKUP_RETENTION_DAYS = Integer.parseInt(env("NUAM_BACKUP_DAYS", "30"));
    public static final String UPLOAD_DIR = env("NUAM_UPLOAD_DIR", "uploads");
    public static final String WEB_DIR = env("NUAM_WEB_DIR", "web");
    public static final String SII_URL = env("NUAM_SII_URL", "http://localhost:" + PORT + "/mock/sii/dj");
    public static final int API_RATE_PER_MIN = Integer.parseInt(env("NUAM_API_RATE", "100"));
    public static final int MAX_FAILED = 5;          // intentos de contraseña
    public static final int MAX_2FA_FAILED = 3;      // intentos de código 2FA
    public static final int LOCK_MINUTES = 15;

    public static String pgHost() { return URI.create(DB_URL.substring(5)).getHost(); }
    public static int pgPort() { int p = URI.create(DB_URL.substring(5)).getPort(); return p < 0 ? 5432 : p; }
    public static String pgDb() { return URI.create(DB_URL.substring(5)).getPath().substring(1); }
}
