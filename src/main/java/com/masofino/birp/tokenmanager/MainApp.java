package com.masofino.birp.tokenmanager;

import com.dustinredmond.fxtrayicon.FXTrayIcon;
import com.masofino.birp.tokenmanager.configs.encryption.TokenManager;
import com.masofino.birp.tokenmanager.configs.properties.AppConfig;
import com.masofino.birp.tokenmanager.configs.properties.CorsFilter;
import com.masofino.birp.tokenmanager.controllers.MainController;
import com.masofino.birp.tokenmanager.entities.Token;
import com.sun.net.httpserver.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.stream.Collectors;

public class MainApp extends Application {

    private Stage primaryStage;
    private FXTrayIcon trayIcon;
    private final AppConfig appConfig = AppConfig.getInstance();
    private TokenManager tokenManager;

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        appConfig.setProperty("url.stylesheet", String.valueOf(MainApp.class.getResource("style.css")));
        appConfig.setProperty("url.image", String.valueOf(MainApp.class.getResource("tray.png")));

        FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        scene.getStylesheets().add(appConfig.getProperty("url.stylesheet"));

        primaryStage.setTitle("Hello!");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(this::hideToTray);
        primaryStage.getIcons().add(new Image(appConfig.getProperty("url.image")));
        primaryStage.show();

        MainController controller = fxmlLoader.getController();
        TokenManager manager = new TokenManager();
        controller.setTokenManager(manager);
        this.tokenManager = manager;

        Platform.runLater(this::setupSystemTray);
        startHttpsServer();
    }

    public static void main(String[] args) {
        launch();
    }

    private void hideToTray(WindowEvent event) {
        event.consume();
        primaryStage.hide();
    }

    private void setupSystemTray() {
        trayIcon = new FXTrayIcon(primaryStage, new Image(appConfig.getProperty("url.image")));
        trayIcon.setApplicationTitle("Token Manager");

        // Create menu items
        MenuItem openItem = new MenuItem("Open");
        openItem.setOnAction(e -> Platform.runLater(() -> {
            System.out.println("Open clicked");
            primaryStage.show();
            primaryStage.toFront();
        }));

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> {
            Platform.exit();
            trayIcon.hide();
        });

        // Add menu items to the tray icon
        trayIcon.addMenuItem(openItem);
        trayIcon.addSeparator();
        trayIcon.addMenuItem(exitItem);

        // Display the tray icon
        trayIcon.show();
    }

    private void startHttpsServer() {
        try {
            HttpsServer https = HttpsServer.create(new InetSocketAddress(8443), 0);
            Path certPath = Path.of(appConfig.getProperty("certificate.path"));
            Path privPath = Path.of(appConfig.getProperty("private.path"));
            SSLContext sslContext = createSSLContext(certPath, privPath);
            https.setHttpsConfigurator(new HttpsConfigurator(sslContext));

            // 3) create each context explicitly
            HttpContext statusContext    = https.createContext("/api/status",    this::handleStatus);
            HttpContext tokensContext    = https.createContext("/api/tokens",    this::handleTokenNames);
            HttpContext publicKeyContext = https.createContext("/api/public-key", this::handlePublicKey);
            HttpContext decryptCtx = https.createContext("/api/decrypt/file", this::handleDecrypt);

            // 4) attach the CORS filter to each
            statusContext.getFilters().add(new CorsFilter());
            tokensContext.getFilters().add(new CorsFilter());
            publicKeyContext.getFilters().add(new CorsFilter());
            decryptCtx.getFilters().add(new CorsFilter());

            https.setExecutor(null);
            https.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDecrypt(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1); ex.close(); return;
        }

        // 1) parse out fileId and token name
        String path = ex.getRequestURI().getPath();      // "/api/decrypt/file/123"
        String[] parts = path.split("/");
        int fileId = Integer.parseInt(parts[parts.length - 1]);

        String query = ex.getRequestURI().getQuery();    // "token=MyCert"
        String tokenName = null;
        if (query != null && query.startsWith("token=")) {
            tokenName = URLDecoder.decode(query.substring(6), StandardCharsets.UTF_8);
        }
        if (tokenName == null) {
            ex.sendResponseHeaders(400, -1); ex.close(); return;
        }

        // 2) prompt user for PIN
        char[] pin = PinDialog.showAndWait();  // your existing dialog
        if (pin == null || pin.length == 0) {
            ex.sendResponseHeaders(401, -1); ex.close(); return;
        }

        try {
            // 3) decrypt the on-prem private key
            Token token = tokenManager.getTokens().stream()
                    .filter(t -> t.getName().equals(tokenName))
                    .findFirst()
                    .orElseThrow();
            byte[] pkBytes = tokenManager.decrypt(token, pin);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkBytes);
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(spec);

            // 4) fetch the encrypted file + envelope metadata from your storage backend
            String backendUrl = appConfig.getProperty("backend.url");
            // e.g. "https://api.mycompany.com"
            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(backendUrl + "/api/storage/download/file/" + fileId))
                    // forward auth if needed:
                    .header("Authorization", ex.getRequestHeaders().getFirst("Authorization"))
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = client.send(
                    req,
                    HttpResponse.BodyHandlers.ofInputStream()
            );
            if (resp.statusCode() != 200) {
                ex.sendResponseHeaders(resp.statusCode(), -1);
                ex.close();
                return;
            }

            // 5) pull out X-Envelope-Key & IV
            String envKeyB64 = resp.headers().firstValue("X-Envelope-Key").orElseThrow();
            String ivB64     = resp.headers().firstValue("X-Envelope-IV").orElseThrow();

            byte[] encryptedAesKey = Base64.getDecoder().decode(envKeyB64);
            byte[] ivBytes         = Base64.getDecoder().decode(ivB64);

            // RSA-decrypt the AES key
            Cipher rsa = Cipher.getInstance("RSA");
            rsa.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] aesKeyBytes = rsa.doFinal(encryptedAesKey);
            SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

            // 6) set up GCM decryptor
            int tagBits = Integer.parseInt(appConfig.getProperty("encryption.gcm-tag-size"));
            GCMParameterSpec gcmSpec = new GCMParameterSpec(tagBits, ivBytes);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec);

            // prepare response headers
            Headers rH = ex.getResponseHeaders();
            String cd = resp.headers().firstValue("Content-Disposition").orElse("");
            rH.add("Content-Disposition", cd);
            rH.add("Content-Type", resp.headers().firstValue("Content-Type")
                    .orElse("application/octet-stream"));

            // 7) stream back decrypted payload
            ex.sendResponseHeaders(200, 0);  // chunked
            try (CipherInputStream cis = new CipherInputStream(resp.body(), cipher);
                 OutputStream os       = ex.getResponseBody()) {

                byte[] buf = new byte[8192];
                int len;
                while ((len = cis.read(buf)) != -1) {
                    os.write(buf, 0, len);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            ex.sendResponseHeaders(500, -1);
        } finally {
            ex.close();
        }
    }


    private void handleStatus(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            ex.close();
            return;
        }

        String json = "{\"status\": \"active\"}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, data.length);
        ex.getResponseBody().write(data);
        ex.close();
    }

    private void handleTokenNames(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            ex.close();
            return;
        }

        String json = this.tokenManager.getTokens().stream()
                .map(t -> "\"" + t.getName().replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, data.length);
        ex.getResponseBody().write(data);
        ex.close();
    }

    private void handlePublicKey(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            ex.close();
            return;
        }

        String query = ex.getRequestURI().getQuery();
        String name;
        if (query != null && query.startsWith("token=")) {
            name = query.substring(6);
        } else {
            name = null;
        }

        Token token = this.tokenManager.getTokens().stream()
                .filter(t -> t.getName().equals(name))
                .findFirst()
                .orElse(null);

        if (token == null) {
            ex.sendResponseHeaders(404, -1);
        } else {
            try (PEMParser parser = new PEMParser(new java.io.FileReader(token.getCertificatePath().toFile()))) {
                X509CertificateHolder holder = (X509CertificateHolder) parser.readObject();
                byte[] pub = holder.getSubjectPublicKeyInfo().getEncoded();
                String pem = "-----BEGIN PUBLIC KEY-----\n" + java.util.Base64.getEncoder().encodeToString(pub) + "\n-----END PUBLIC KEY-----";
                byte[] data = pem.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, data.length);
                ex.getResponseBody().write(data);
            }
        }
        ex.close();
    }

    private SSLContext createSSLContext(Path certPath, Path keyPath) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        PEMParser certReader = new PEMParser(new FileReader(certPath.toFile()));
        X509CertificateHolder certHolder = (X509CertificateHolder) certReader.readObject();
        certReader.close();

        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(certHolder);
        PEMParser keyReader = new PEMParser(new FileReader(keyPath.toFile()));
        Object obj = keyReader.readObject();
        keyReader.close();

        PrivateKeyInfo pkInfo = obj instanceof PEMKeyPair ? ((PEMKeyPair) obj).getPrivateKeyInfo() : (PrivateKeyInfo) obj;
        PrivateKey key = new JcaPEMKeyConverter().getPrivateKey(pkInfo);
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null);
        ks.setKeyEntry("alias", key, new char[0], new Certificate[]{cert});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, new char[0]);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }
}