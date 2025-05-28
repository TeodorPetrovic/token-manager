package com.masofino.birp.tokenmanager.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;

public class MainController {

    @FXML
    private ListView<String> tokenList;

    @FXML
    private void onAddToken() {
        // Stub: Implement file selection + PIN + encryption logic here
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Enter PIN");
        dialog.setContentText("4-digit PIN:");
        dialog.showAndWait().ifPresent(pin -> {
            if (pin.matches("\\d{4}")) {
                tokenList.getItems().add("EncryptedToken.key");
            } else {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.setContentText("Invalid PIN");
                a.show();
            }
        });
    }
}
