package com.masofino.birp.tokenmanager.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EntropyStage extends Stage {
    private final ByteArrayOutputStream data = new ByteArrayOutputStream();
    private final ProgressBar bar = new ProgressBar(0);
    private final Label label = new Label("Move mouse to generate randomness");
    private final Canvas artCanvas = new Canvas(500, 400);
    private final int target = 4096;

    // NEW: cache all segments here
    private final List<double[]> segments = new ArrayList<>();
    private boolean built = false;
    private int lastDrawn = 0;

    public EntropyStage() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("entropy-root");
        root.setPadding(new Insets(20));
        BorderPane.setMargin(bar, new Insets(0, 10, 10, 10));
        bar.setPrefWidth(Double.MAX_VALUE);
        root.setTop(label);
        root.setCenter(artCanvas);
        root.setBottom(bar);

        Scene scene = new Scene(root, 600, 500);
        scene.setOnMouseMoved(e -> {
            data.write((int) e.getX());
            data.write((int) e.getY());
            data.write((int) (e.getX() + e.getY()));
            data.write((int) (e.getX() * e.getY()));
            if (data.size() >= target) {
                close();
            }
            double progress = (double) data.size() / target;
            bar.setProgress(progress);
            drawArt(progress);
            label.setText(String.format("Randomness: %.0f%%", progress * 100));
        });

        setScene(scene);
        setTitle("Entropy");
    }

    private void drawArtOld(double progress) {
        GraphicsContext gc = artCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, artCanvas.getWidth(), artCanvas.getHeight());
        gc.setStroke(Color.web("#76c7c0"));
        gc.setLineWidth(2);
        double centerX = artCanvas.getWidth() / 2;
        double centerY = artCanvas.getHeight() / 2;
        double maxRadius = Math.min(centerX, centerY) - 20;
        double maxTheta = progress * 4 * Math.PI;
        gc.beginPath();
        for (double t = 0; t <= maxTheta; t += 0.05) {
            double r = maxRadius * t / (4 * Math.PI);
            double x = centerX + r * Math.cos(t);
            double y = centerY + r * Math.sin(t);
            if (t == 0) {
                gc.moveTo(x, y);
            } else {
                gc.lineTo(x, y);
            }
        }
        gc.stroke();
    }

    private void drawArt(double progress) {
        // Build the full list exactly once
        if (!built) {
            SecureRandom rng = new SecureRandom(data.toByteArray());
            double startX = artCanvas.getWidth() / 2;
            double startY = artCanvas.getHeight() - 20;
            double initialLength = artCanvas.getHeight() / 4 + rng.nextDouble() * 20;
            double initialAngle = -Math.PI / 2;
            int maxDepth = 8; // tweak for complexity
            generateSegments(rng, startX, startY, initialLength, initialAngle, maxDepth);
            built = true;
        }

        int total = segments.size();
        int toDraw = Math.min(total, (int) (total * progress));
        GraphicsContext gc = artCanvas.getGraphicsContext2D();

        // Draw only the new segments since last time
        for (int i = lastDrawn; i < toDraw; i++) {
            double[] s = segments.get(i);
            // cycle hue 0–360 across the tree
            gc.setStroke(Color.hsb((i / (double) total) * 360, 0.8, 0.8));
            gc.strokeLine(s[0], s[1], s[2], s[3]);
        }
        lastDrawn = toDraw;
    }

    private void generateSegments(Random rng,
                                  double x1, double y1,
                                  double length, double angle,
                                  int depth) {
        if (depth == 0) return;

        double x2 = x1 + length * Math.cos(angle);
        double y2 = y1 + length * Math.sin(angle);
        // store this segment
        segments.add(new double[]{x1, y1, x2, y2});

        // randomize split and shrink
        double splitBase = Math.PI / 6;
        double split = splitBase * (0.8 + rng.nextDouble() * 0.4);
        double shrink = 0.6 + rng.nextDouble() * 0.3;
        double nextLen = length * shrink;

        // recurse left & right
        generateSegments(rng, x2, y2, nextLen, angle - split, depth - 1);
        generateSegments(rng, x2, y2, nextLen, angle + split, depth - 1);
    }

    public SecureRandom awaitRandom() {
        showAndWait();
        byte[] seed = data.toByteArray();
        return new SecureRandom(seed);
    }
}
