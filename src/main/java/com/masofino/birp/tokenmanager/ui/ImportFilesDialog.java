package com.masofino.birp.tokenmanager.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

public class ImportFilesDialog extends Dialog<ImportFilesDialog.Result> {
    private final TextField certField = new TextField();
    private final TextField keyField = new TextField();

    public record Result(Path certificate, Path privateKey) {}

    public ImportFilesDialog() {
        setTitle("Import Token Files");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        Button certBtn = new Button("Choose...");
        certBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Certificate");
            File f = fc.showOpenDialog(getDialogPane().getScene().getWindow());
            if (f != null) certField.setText(f.getAbsolutePath());
        });

        Button keyBtn = new Button("Choose...");
        keyBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Private Key");
            File f = fc.showOpenDialog(getDialogPane().getScene().getWindow());
            if (f != null) keyField.setText(f.getAbsolutePath());
        });

        grid.addRow(0, new Label("Certificate:"), certField, certBtn);
        grid.addRow(1, new Label("Private Key:"), keyField, keyBtn);
        certField.setPrefWidth(300);
        keyField.setPrefWidth(300);

        getDialogPane().setContent(grid);
    }

    public Optional<Result> showAndGet() {
        setResultConverter(btn -> {
            if (btn == ButtonType.OK && !certField.getText().isEmpty() && !keyField.getText().isEmpty()) {
                return new Result(Path.of(certField.getText()), Path.of(keyField.getText()));
            }
            return null;
        });
        return showAndWait();
    }
}