package cl.nuam;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.temporal.ChronoUnit;
import java.time.Instant;
import javax.net.ssl.*;

/** HU-21: HTTPS con TLS 1.3 (mínimo 1.2). Si no existe el almacén de claves, genera un certificado autofirmado de desarrollo con keytool. */
public final class Tls {
    private Tls() {}
    private static X509Certificate cert;

    static SSLContext contexto() throws Exception {
        Path ks = Paths.get(Config.KEYSTORE);
        if (!Files.exists(ks)) {
            if (ks.getParent() != null) Files.createDirectories(ks.getParent());
            String keytool = Paths.get(System.getProperty("java.home"), "bin", "keytool").toString();
            Process pr = new ProcessBuilder(keytool, "-genkeypair", "-alias", "nuam", "-keyalg", "RSA", "-keysize", "2048", "-validity", "365",
                    "-storetype", "PKCS12", "-keystore", ks.toString(), "-storepass", Config.KEYSTORE_PASS, "-keypass", Config.KEYSTORE_PASS,
                    "-dname", "CN=localhost, O=nuam (desarrollo), C=CL", "-ext", "san=dns:localhost,ip:127.0.0.1").redirectErrorStream(true).start();
            String out = new String(pr.getInputStream().readAllBytes());
            if (pr.waitFor() != 0) throw new IOException("No se pudo generar el certificado TLS: " + out);
            System.out.println("[TLS] certificado autofirmado de desarrollo generado en " + ks + " (reemplácelo por uno real en producción)");
        }
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(ks)) { store.load(in, Config.KEYSTORE_PASS.toCharArray()); }
        String alias = store.aliases().nextElement();
        cert = (X509Certificate) store.getCertificate(alias);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(store, Config.KEYSTORE_PASS.toCharArray());
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    static HttpsServer servidor(Http.Router r) throws Exception {
        SSLContext ctx = contexto();
        HttpsServer s = HttpsServer.create(new InetSocketAddress(Config.HTTPS_PORT), 0);
        s.setHttpsConfigurator(new HttpsConfigurator(ctx) {
            @Override public void configure(HttpsParameters p) {
                SSLParameters sp = ctx.getDefaultSSLParameters();
                sp.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});   // HU-21.3: SSL y TLS 1.0/1.1 rechazados
                p.setSSLParameters(sp);
            }
        });
        s.createContext("/", r);
        return s;
    }

    /** HU-21.2/21.7: todo lo que llega por HTTP se redirige a HTTPS (308 conserva el método). */
    static HttpHandler redireccion() {
        return ex -> {
            String host = ex.getRequestHeaders().getFirst("Host");
            host = host == null ? "localhost" : host.replaceAll(":\\d+$", "");
            String path = ex.getRequestURI().getRawPath();
            boolean loop = ex.getRemoteAddress().getAddress().isLoopbackAddress();
            if (loop && path.startsWith("/mock/")) { Main.router.handle(ex); return; }   // simulador SII local
            String dest = "https://" + host + (Config.HTTPS_PORT == 443 ? "" : ":" + Config.HTTPS_PORT) + ex.getRequestURI().toString();
            ex.getResponseHeaders().set("Location", dest);
            ex.sendResponseHeaders(308, -1);
            ex.close();
        };
    }

    /** HU-21.4: días que le quedan al certificado (null si TLS está desactivado). */
    public static Long diasParaVencer() {
        if (cert == null) return null;
        return ChronoUnit.DAYS.between(Instant.now(), cert.getNotAfter().toInstant());
    }
}
