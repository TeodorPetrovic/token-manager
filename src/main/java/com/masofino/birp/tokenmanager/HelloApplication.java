package com.masofino.birp.tokenmanager;

import com.dustinredmond.fxtrayicon.FXTrayIcon;
import com.masofino.birp.tokenmanager.configs.encryption.TokenManager;
import com.masofino.birp.tokenmanager.configs.properties.AppConfig;
import com.masofino.birp.tokenmanager.controllers.MainController;
import com.masofino.birp.tokenmanager.entities.Token;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
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
import java.util.Objects;

public class HelloApplication extends Application {

    private Stage primaryStage;
    private FXTrayIcon trayIcon;
    private final AppConfig appConfig = AppConfig.getInstance();
    private TokenManager tokenManager;

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;

        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        scene.getStylesheets().add(Objects.requireNonNull(HelloApplication.class.getResource("style.css")).toExternalForm());

        primaryStage.setTitle("Hello!");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(this::hideToTray);
        primaryStage.show();

        MainController controller = fxmlLoader.getController();
        Path keyDir = Path.of(appConfig.getWorkspacePath());
        TokenManager manager = new TokenManager(keyDir);
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
        trayIcon = new FXTrayIcon(primaryStage, Objects.requireNonNull(HelloApplication.class.getResource("/com/masofino/birp/tokenmanager/tray.png")));
        trayIcon.setApplicationTitle("Token Manager");

        // Create menu items
        MenuItem openItem = new MenuItem("Open");
        openItem.setOnAction(e -> Platform.runLater(() -> {
            System.out.println("Open clicked");
            primaryStage.show();
            primaryStage.toFront();
        }));

        MenuItem inspectCertItem = new MenuItem("Inspect Certificate");
        inspectCertItem.setOnAction(e -> Platform.runLater(() -> {
            System.out.println("Inspect cert clicked");
        }));

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> {
            Platform.exit();
            trayIcon.hide();
        });

        // Add menu items to the tray icon
        trayIcon.addMenuItem(openItem);
        trayIcon.addMenuItem(inspectCertItem);
        trayIcon.addSeparator();
        trayIcon.addMenuItem(exitItem);

        // Display the tray icon
        trayIcon.show();
    }

    private void startHttpsServer() {
        try {
            com.sun.net.httpserver.HttpHandler handler = ex -> {
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
                    byte[] data = java.nio.file.Files.readAllBytes(token.getPublicKeyPath());
                    ex.sendResponseHeaders(200, data.length);
                    ex.getResponseBody().write(data);
                }
                ex.close();
            };

            HttpsServer https = HttpsServer.create(new InetSocketAddress(8443), 0);
            Path certPath = Path.of(appConfig.getProperty("certificate.path"));
            Path privPath = Path.of(appConfig.getProperty("private.path"));
            SSLContext sslContext = createSSLContext(certPath, privPath);

            https.setHttpsConfigurator(new HttpsConfigurator(sslContext));
            https.createContext("/api/public-key", handler);
            https.setExecutor(null);
            https.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
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