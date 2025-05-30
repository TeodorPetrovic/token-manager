package com.masofino.birp.tokenmanager.ui;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.application.Platform;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;

public class PinDialog extends Dialog<String> {
    private final TextField pinField = new TextField();
    private final Label errorLabel = new Label();

    public PinDialog() {
        setTitle("Enter PIN");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label("PIN:"), pinField);
        grid.add(errorLabel, 0, 1, 2, 1);
        getDialogPane().setContent(grid);

        Node okButton = getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(ActionEvent.ACTION, e -> {
            if (!pinField.getText().matches("\\d{4}")) {
                errorLabel.setText("Enter 4-digit PIN");
                e.consume();
            } else {
                errorLabel.setText("");
            }
        });
    }

    /**
     * Shows the dialog and returns the PIN if the user confirmed.
     */
    public Optional<String> showAndGet() {
        setResultConverter(btn -> btn == ButtonType.OK ? pinField.getText() : null);
        return showAndWait();
    }

    public static String requestPin() {
        if (Platform.isFxApplicationThread()) {
            return new PinDialog().showAndGet().orElse(null);
        }

        CompletableFuture<String> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            future.complete(new PinDialog().showAndGet().orElse(null));
        });

        try {
            return future.get();
        } catch (Exception e) {
            return null;
        }
    }
}