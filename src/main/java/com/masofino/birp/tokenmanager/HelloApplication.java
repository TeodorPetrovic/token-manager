package com.masofino.birp.tokenmanager;

import com.dustinredmond.fxtrayicon.FXTrayIcon;
import com.sun.net.httpserver.HttpsServer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;

public class HelloApplication extends Application {

    private Stage primaryStage;
    private FXTrayIcon trayIcon;

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

        Platform.runLater(this::setupSystemTray);
        startHttpServer();
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

    private void startHttpServer() throws IOException {
        HttpsServer server = HttpsServer.create(new InetSocketAddress(8443), 0);
        server.createContext("/api", httpExchange -> {
            String response = "Hello from middleware";
            httpExchange.sendResponseHeaders(200, response.length());
            httpExchange.getResponseBody().write(response.getBytes());
            httpExchange.close();
        });
        server.setExecutor(null);
        server.start();
    }
}