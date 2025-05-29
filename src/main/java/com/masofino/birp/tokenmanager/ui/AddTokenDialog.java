package com.masofino.birp.tokenmanager.ui;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.util.Optional;

public class AddTokenDialog extends Dialog<AddTokenDialog.Result> {
    private final TextField nameField = new TextField();
    private final ComboBox<String> modeBox = new ComboBox<>(FXCollections.observableArrayList("Import", "Generate"));
    private final Label errorLabel = new Label();
    public record Result(String name, String mode) {}

    public AddTokenDialog() {
        setTitle("Add Token");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label("Name:"), nameField);
        modeBox.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(modeBox, Priority.ALWAYS);
        grid.addRow(1, new Label("Mode:"), modeBox);
        grid.add(errorLabel, 0, 2, 3, 1);
        errorLabel.setStyle("-fx-text-fill: red");

        modeBox.getSelectionModel().selectFirst();
        getDialogPane().setContent(grid);

        Node okButton = getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(ActionEvent.ACTION, e -> {
            if (nameField.getText().trim().isEmpty()) {
                errorLabel.setText("Name required");
                e.consume();
            } else {
                errorLabel.setText("");
            }
        });
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