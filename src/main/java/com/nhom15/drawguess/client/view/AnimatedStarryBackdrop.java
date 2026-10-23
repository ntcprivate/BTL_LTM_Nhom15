package com.nhom15.drawguess.client.view;

import javafx.animation.AnimationTimer;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.stage.Window;

import java.net.URL;
import java.util.Objects;

/** Full-window Starry Night painting with subtle animated light and parallax. */
public final class AnimatedStarryBackdrop extends StackPane {
    private static final double[][] STARS = {
            {0.06, 0.13, 2.7, 0.0}, {0.14, 0.30, 1.9, 1.4}, {0.23, 0.09, 2.4, 2.2},
            {0.32, 0.23, 1.7, 3.7}, {0.43, 0.12, 2.8, 0.8}, {0.53, 0.31, 2.0, 4.5},
            {0.63, 0.10, 2.2, 2.8}, {0.73, 0.24, 1.8, 5.1}, {0.91, 0.32, 2.6, 1.9},
            {0.10, 0.61, 2.1, 3.2}, {0.20, 0.79, 1.7, 0.5}, {0.34, 0.68, 2.3, 4.1},
            {0.48, 0.87, 1.8, 2.4}, {0.61, 0.74, 2.5, 5.4}, {0.78, 0.83, 2.0, 1.1},
            {0.91, 0.69, 2.7, 3.9}, {0.96, 0.52, 1.6, 0.2}, {0.38, 0.48, 1.8, 5.8}
    };

    private final Image paintingImage;
    private final ImageView painting;
    private final Canvas effects = new Canvas();
    private final AnimationTimer animation;
    private double targetX;
    private double targetY;
    private double currentX;
    private double currentY;
    private long lastFrame;

    public AnimatedStarryBackdrop(Region host) {
        URL resource = Objects.requireNonNull(
                AnimatedStarryBackdrop.class.getResource("/images/The-Starry-Night-1889.jpg"),
                "Thiếu ảnh nền The-Starry-Night-1889.jpg");
        paintingImage = new Image(resource.toExternalForm(), false);
        painting = new ImageView(paintingImage);
        painting.setPreserveRatio(false);
        painting.setSmooth(true);
        painting.setScaleX(1.06);
        painting.setScaleY(1.06);
        painting.fitWidthProperty().bind(host.widthProperty());
        painting.fitHeightProperty().bind(host.heightProperty());

        Region veil = new Region();
        veil.getStyleClass().add("starry-veil");
        veil.prefWidthProperty().bind(host.widthProperty());
        veil.prefHeightProperty().bind(host.heightProperty());

        effects.widthProperty().bind(host.widthProperty());
        effects.heightProperty().bind(host.heightProperty());
        effects.setMouseTransparent(true);
        setMouseTransparent(true);
        prefWidthProperty().bind(host.widthProperty());
        prefHeightProperty().bind(host.heightProperty());
        getChildren().addAll(painting, veil, effects);

        host.widthProperty().addListener((observable, oldValue, newValue) -> updateViewport(host));
        host.heightProperty().addListener((observable, oldValue, newValue) -> updateViewport(host));
        host.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            if (host.getWidth() > 0 && host.getHeight() > 0) {
                targetX = (event.getX() / host.getWidth() - 0.5) * -14;
                targetY = (event.getY() / host.getHeight() - 0.5) * -9;
            }
        });

        animation = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastFrame != 0 && now - lastFrame < 33_000_000L) {
                    return;
                }
                lastFrame = now;
                currentX += (targetX - currentX) * 0.055;
                currentY += (targetY - currentY) * 0.055;
                painting.setTranslateX(currentX);
                painting.setTranslateY(currentY);
                drawEffects(now / 1_000_000_000.0);
            }
        };

        sceneProperty().addListener((observable, oldScene, newScene) -> observeScene(newScene));
        updateViewport(host);
    }

    private void observeScene(Scene scene) {
        animation.stop();
        if (scene == null) {
            return;
        }
        scene.windowProperty().addListener((observable, oldWindow, newWindow) -> observeWindow(newWindow));
        observeWindow(scene.getWindow());
    }

    private void observeWindow(Window window) {
        animation.stop();
        if (window == null) {
            return;
        }
        window.showingProperty().addListener((observable, wasShowing, isShowing) -> {
            if (isShowing) {
                lastFrame = 0;
                animation.start();
            } else {
                animation.stop();
            }
        });
        if (window.isShowing()) {
            lastFrame = 0;
            animation.start();
        }
    }

    private void updateViewport(Region host) {
        double width = host.getWidth() > 0 ? host.getWidth() : host.getPrefWidth();
        double height = host.getHeight() > 0 ? host.getHeight() : host.getPrefHeight();
        if (width <= 0 || height <= 0 || paintingImage.getWidth() <= 0 || paintingImage.getHeight() <= 0) {
            return;
        }
        double targetRatio = width / height;
        double imageRatio = paintingImage.getWidth() / paintingImage.getHeight();
        if (imageRatio > targetRatio) {
            double viewportWidth = paintingImage.getHeight() * targetRatio;
            painting.setViewport(new Rectangle2D((paintingImage.getWidth() - viewportWidth) / 2, 0,
                    viewportWidth, paintingImage.getHeight()));
        } else {
            double viewportHeight = paintingImage.getWidth() / targetRatio;
            painting.setViewport(new Rectangle2D(0, (paintingImage.getHeight() - viewportHeight) / 2,
                    paintingImage.getWidth(), viewportHeight));
        }
    }

    private void drawEffects(double time) {
        double width = effects.getWidth();
        double height = effects.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        GraphicsContext graphics = effects.getGraphicsContext2D();
        graphics.clearRect(0, 0, width, height);
        graphics.setGlobalBlendMode(BlendMode.SCREEN);
        drawFlow(graphics, width, height, 0.22, time, Color.web("#69a9e6", 0.17), 13);
        drawFlow(graphics, width, height, 0.58, time + 2.1, Color.web("#244fba", 0.20), 19);
        drawFlow(graphics, width, height, 0.76, time + 4.4, Color.web("#f0c64b", 0.10), 5);
        drawMoonGlow(graphics, width * 0.86, height * 0.15,
                Math.min(width, height) * (0.085 + Math.sin(time * 0.75) * 0.004));
        drawTwinklingStars(graphics, width, height, time);
        graphics.setGlobalBlendMode(BlendMode.SRC_OVER);
    }

    private void drawFlow(GraphicsContext graphics, double width, double height, double baseY,
                          double time, Color color, double lineWidth) {
        double drift = Math.sin(time * 0.32) * height * 0.018;
        double wave = Math.cos(time * 0.24) * height * 0.025;
        graphics.setStroke(color);
        graphics.setLineWidth(lineWidth);
        graphics.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        graphics.beginPath();
        graphics.moveTo(-width * 0.08, height * baseY + drift);
        graphics.bezierCurveTo(width * 0.15, height * (baseY + 0.20) - wave,
                width * 0.30, height * (baseY - 0.11) + drift,
                width * 0.49, height * (baseY + 0.07) + wave);
        graphics.bezierCurveTo(width * 0.68, height * (baseY + 0.25) - drift,
                width * 0.82, height * (baseY - 0.10) - wave,
                width * 1.08, height * (baseY + 0.12) + drift);
        graphics.stroke();
    }

    private void drawMoonGlow(GraphicsContext graphics, double x, double y, double radius) {
        graphics.setFill(new RadialGradient(0, 0, x, y, radius, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#fff3a8", 0.42)),
                new Stop(0.38, Color.web("#f7c948", 0.20)),
                new Stop(1, Color.TRANSPARENT)));
        graphics.fillOval(x - radius, y - radius, radius * 2, radius * 2);
    }

    private void drawTwinklingStars(GraphicsContext graphics, double width, double height, double time) {
        for (double[] star : STARS) {
            double pulse = 0.62 + 0.38 * Math.sin(time * 1.35 + star[3]);
            double radius = star[2] * (0.88 + pulse * 0.35);
            double x = width * star[0];
            double y = height * star[1];
            graphics.setFill(Color.web("#ffe58a", 0.08 + pulse * 0.14));
            graphics.fillOval(x - radius * 3.4, y - radius * 3.4, radius * 6.8, radius * 6.8);
            graphics.setFill(Color.web("#fff4ba", 0.55 + pulse * 0.38));
            graphics.fillOval(x - radius, y - radius, radius * 2, radius * 2);
            graphics.setStroke(Color.web("#ffd34e", 0.34 + pulse * 0.42));
            graphics.setLineWidth(1.1);
            graphics.strokeLine(x - radius * 2.5, y, x + radius * 2.5, y);
            graphics.strokeLine(x, y - radius * 2.5, x, y + radius * 2.5);
        }
    }
}
