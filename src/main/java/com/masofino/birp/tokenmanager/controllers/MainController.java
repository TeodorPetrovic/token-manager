package com.masofino.birp.tokenmanager.controllers;

import com.masofino.birp.tokenmanager.configs.encryption.TokenManager;
import com.masofino.birp.tokenmanager.configs.properties.AppConfig;
import com.masofino.birp.tokenmanager.entities.Token;
import com.masofino.birp.tokenmanager.ui.EntropyStage;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import javafx.stage.Window;
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
    }

    @FXML
    private void onAddToken() {
        if (tokenManager == null) return;

        TextInputDialog nameDialog = new TextInputDialog();
        nameDialog.setTitle("Token Name");
        nameDialog.setContentText("Name:");
        java.util.Optional<String> optName = nameDialog.showAndWait();
        if (optName.isEmpty()) return;
        String name = optName.get();

        ChoiceDialog<String> choice = new ChoiceDialog<>("Import", "Import", "Generate");
        choice.setTitle("Add Token");
        choice.setContentText("Mode:");
        java.util.Optional<String> modeOpt = choice.showAndWait();
        if (modeOpt.isEmpty()) return;
        String mode = modeOpt.get();

        TextInputDialog pinDialog = new TextInputDialog();
        pinDialog.setTitle("Enter PIN");
        pinDialog.setContentText("4-digit PIN:");
        java.util.Optional<String> pinOpt = pinDialog.showAndWait();
        if (pinOpt.isEmpty() || !pinOpt.get().matches("\\d{4}")) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Invalid PIN");
            a.show();
            return;
        }
        String pin = pinOpt.get();

        try {
            if ("Import".equals(mode)) {
                FileChooser fc = new FileChooser();
                fc.setTitle("Select Public Key");
                File pub = fc.showOpenDialog(getWindow());
                if (pub == null) return;
                fc.setTitle("Select Private Key");
                File priv = fc.showOpenDialog(getWindow());
                if (priv == null) return;
                tokenManager.importToken(name, pub.toPath(), priv.toPath(), pin);
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.setContentText("Invalid PIN");
                a.show();
            } else {
                EntropyStage es = new EntropyStage();
                SecureRandom rand = es.awaitRandom();
                tokenManager.generateToken(name, rand, pin);
            }

            tokenList.getItems().setAll(tokenManager.getTokens().stream().map(Token::getName).toList());
        } catch (IOException | GeneralSecurityException e) {
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
            appConfig.setWorkspacePath(dir.toPath().toString());
            try {
                tokenManager = new TokenManager(dir.toPath());
                tokenList.getItems().setAll(tokenManager.getTokens().stream().map(Token::getName).toList());
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, e.getMessage()).show();
            }
        }
    }
}
