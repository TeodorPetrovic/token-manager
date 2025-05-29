package com.masofino.birp.tokenmanager.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.Optional;

public class AddTokenDialog extends Dialog<AddTokenDialog.Result> {
    private final TextField nameField = new TextField();
    private final ComboBox<String> modeBox = new ComboBox<>(FXCollections.observableArrayList("Import", "Generate"));

    public record Result(String name, String mode) {}

    public AddTokenDialog() {
        setTitle("Add Token");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label("Name:"), nameField);
        grid.addRow(1, new Label("Mode:"), modeBox);
        modeBox.getSelectionModel().selectFirst();
        getDialogPane().setContent(grid);
    }

    public Optional<Result> showAndGet() {
        setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                return new Result(nameField.getText(), modeBox.getValue());
            }
            return null;
        });
        Optional<Result> res = showAndWait();
        return res;
    }
}