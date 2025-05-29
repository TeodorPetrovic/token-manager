package com.masofino.birp.tokenmanager;

import com.dustinredmond.fxtrayicon.FXTrayIcon;
import com.masofino.birp.tokenmanager.configs.encryption.TokenManager;
import com.masofino.birp.tokenmanager.configs.properties.AppConfig;
import com.masofino.birp.tokenmanager.controllers.MainController;
import com.masofino.birp.tokenmanager.entities.Token;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
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
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

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
            https.createContext("/api/public-key", this::handlePublicKey);
            https.setExecutor(null);
            https.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
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