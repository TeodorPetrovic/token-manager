package com.masofino.birp.tokenmanager.controllers;

import com.masofino.birp.tokenmanager.configs.encryption.TokenManager;
import com.masofino.birp.tokenmanager.configs.properties.AppConfig;
import com.masofino.birp.tokenmanager.entities.Token;
import com.masofino.birp.tokenmanager.ui.AddTokenDialog;
import com.masofino.birp.tokenmanager.ui.EntropyStage;
import com.masofino.birp.tokenmanager.ui.ImportFilesDialog;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

public class MainController {

    @FXML
    private ListView<String> tokenList;

    private TokenManager tokenManager;
    private AppConfig appConfig;

    public void setTokenManager(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
        tokenList.getItems().setAll(tokenManager.getTokens().stream().map(Token::getName).toList());
        tokenList.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            MenuItem view = new MenuItem("View Certificate");
            view.setOnAction(e -> showCertificate(cell.getItem()));
            MenuItem del = new MenuItem("Delete");
            del.setOnAction(e -> deleteToken(cell.getItem()));
            ContextMenu cm = new ContextMenu(view, del);
            cell.setContextMenu(cm);
            return cell;
        });
    }

    @FXML
    private void onAddToken() {
        if (tokenManager == null) return;

        AddTokenDialog addDialog = new AddTokenDialog();
        addDialog.getDialogPane().getStylesheets().add(getClass().getResource("/com/masofino/birp/tokenmanager/style.css").toExternalForm());
        java.util.Optional<AddTokenDialog.Result> addRes = addDialog.showAndGet();
        if (addRes.isEmpty()) return;
        String name = addRes.get().name();
        String mode = addRes.get().mode();
        TextInputDialog pinDialog = new TextInputDialog();
        pinDialog.setTitle("Enter PIN");
        pinDialog.setContentText("4-digit PIN:");
        pinDialog.getDialogPane().getStylesheets().add(getClass().getResource("/com/masofino/birp/tokenmanager/style.css").toExternalForm());
        java.util.Optional<String> pinOpt = pinDialog.showAndWait();
        if (pinOpt.isEmpty() || !pinOpt.get().matches("\\d{4}")) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Invalid PIN");
            a.show();
            return;
        }
        String pin = pinOpt.get();

        try {
            if ("Import".equals(mode)) {
                ImportFilesDialog fileDialog = new ImportFilesDialog();
                fileDialog.getDialogPane().getStylesheets().add(getClass().getResource("/com/masofino/birp/tokenmanager/style.css").toExternalForm());
                java.util.Optional<ImportFilesDialog.Result> fRes = fileDialog.showAndGet();
                if (fRes.isEmpty()) return;
                tokenManager.importToken(name, fRes.get().certificate(), fRes.get().privateKey(), pin);
            } else {
                EntropyStage es = new EntropyStage();
                SecureRandom rand = es.awaitRandom();
                tokenManager.generateToken(name, rand, pin);
            }

            new Alert(Alert.AlertType.INFORMATION, "Token '" + name + "' created successfully").show();
            tokenList.getItems().setAll(tokenManager.getTokens().stream().map(Token::getName).toList());
        } catch (IOException | GeneralSecurityException | OperatorCreationException e) {
            Alert a = new Alert(Alert.AlertType.ERROR, e.getMessage());
            a.show();
        }
    }

    private Window getWindow() {
        return tokenList.getScene().getWindow();
    }

    @FXML
    private void onChangeDirectory() {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Select Key Directory");
        File dir = chooser.showDialog(getWindow());
        if (dir != null) {
            appConfig.setTokensStoragePath(dir.toPath().toString());
            try {
                tokenManager = new TokenManager();
                tokenList.getItems().setAll(tokenManager.getTokens().stream().map(Token::getName).toList());
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, e.getMessage()).show();
            }
        }
    }

    private void showCertificate(String name) {
        Token token = tokenManager.getTokens().stream()
                .filter(t -> t.getName().equals(name)).findFirst().orElse(null);
        if (token == null) return;
        try (PEMParser parser = new PEMParser(new java.io.FileReader(token.getCertificatePath().toFile()))) {
            X509CertificateHolder holder = (X509CertificateHolder) parser.readObject();
            String info = "Subject: " + holder.getSubject() + "\n" +
                    "Issuer: " + holder.getIssuer() + "\n" +
                    "Valid From: " + holder.getNotBefore() + "\n" +
                    "Valid To: " + holder.getNotAfter();
            Alert a = new Alert(Alert.AlertType.INFORMATION, info);
            a.setHeaderText("Certificate Info");
            a.show();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage()).show();
        }
    }

    private void deleteToken(String name) {
        Token token = tokenManager.getTokens().stream()
                .filter(t -> t.getName().equals(name)).findFirst().orElse(null);
        if (token == null) return;
        if (new Alert(Alert.AlertType.CONFIRMATION, "Delete token '" + name + "'?", ButtonType.OK, ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                tokenManager.deleteToken(token);
                tokenList.getItems().setAll(tokenManager.getTokens().stream().map(Token::getName).toList());
            } catch (IOException e) {
                new Alert(Alert.AlertType.ERROR, e.getMessage()).show();
            }
        }
    }
}
