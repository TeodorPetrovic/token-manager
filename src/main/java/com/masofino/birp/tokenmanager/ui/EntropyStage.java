package com.masofino.birp.tokenmanager.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;

public class EntropyStage extends Stage {
    private final ByteArrayOutputStream data = new ByteArrayOutputStream();
    private final ProgressBar bar = new ProgressBar(0);
    private final Label label = new Label("Move mouse to generate randomness");
    private final int target = 4096;

    public EntropyStage() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        root.setTop(label);
        root.setCenter(bar);

        Scene scene = new Scene(root, 600, 500);
        scene.setOnMouseMoved(e -> {
            data.write((int) e.getX());
            data.write((int) e.getY());
            data.write((int) (e.getX() + e.getY()));
            data.write((int) (e.getX() * e.getY()));
            if (data.size() >= target) {
                close();
            }
            bar.setProgress((double) data.size() / target);
        });

        setScene(scene);
        setTitle("Entropy");
    }

    public SecureRandom awaitRandom() {
        showAndWait();
        byte[] seed = data.toByteArray();
        return new SecureRandom(seed);
    }
}
