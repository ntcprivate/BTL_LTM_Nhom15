package com.nhom15.drawguess.client.view;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.net.URL;
import java.util.Objects;

/** A center-cropped painting used as a decorative framed banner. */
final class ArtworkCard extends StackPane {
    private final Image image;

    ArtworkCard(String resourcePath, String caption, double preferredWidth, double height) {
        URL resource = Objects.requireNonNull(
                ArtworkCard.class.getResource(resourcePath),
                "Thiếu ảnh giao diện: " + resourcePath);
        image = new Image(resource.toExternalForm(), false);

        ImageView painting = new ImageView(image);
        painting.setSmooth(true);
        painting.setPreserveRatio(false);
        painting.fitWidthProperty().bind(widthProperty());
        painting.fitHeightProperty().bind(heightProperty());

        Rectangle imageClip = new Rectangle();
        imageClip.widthProperty().bind(widthProperty());
        imageClip.heightProperty().bind(heightProperty());
        imageClip.setArcWidth(22);
        imageClip.setArcHeight(22);
        painting.setClip(imageClip);

        Label title = new Label(caption);
        title.getStyleClass().add("artwork-caption");
        StackPane.setAlignment(title, Pos.BOTTOM_LEFT);

        Rectangle frame = new Rectangle();
        frame.widthProperty().bind(widthProperty().subtract(2));
        frame.heightProperty().bind(heightProperty().subtract(2));
        frame.setArcWidth(22);
        frame.setArcHeight(22);
        frame.setMouseTransparent(true);
        frame.getStyleClass().add("artwork-frame");

        getStyleClass().add("artwork-card");
        setPrefSize(preferredWidth, height);
        setMinWidth(preferredWidth);
        setMaxWidth(preferredWidth);
        setMinHeight(height);
        setMaxHeight(height);
        setAccessibleText(caption);
        getChildren().addAll(painting, title, frame);

        widthProperty().addListener((observable, oldValue, newValue) -> updateViewport());
        heightProperty().addListener((observable, oldValue, newValue) -> updateViewport());
        updateViewport();
    }

    private void updateViewport() {
        double areaWidth = getWidth() > 0 ? getWidth() : getPrefWidth();
        double areaHeight = getHeight() > 0 ? getHeight() : getPrefHeight();
        if (areaWidth <= 0 || areaHeight <= 0 || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return;
        }

        double targetRatio = areaWidth / areaHeight;
        double imageRatio = image.getWidth() / image.getHeight();
        if (imageRatio > targetRatio) {
            double viewportWidth = image.getHeight() * targetRatio;
            setImageViewport((image.getWidth() - viewportWidth) / 2, 0, viewportWidth, image.getHeight());
        } else {
            double viewportHeight = image.getWidth() / targetRatio;
            setImageViewport(0, (image.getHeight() - viewportHeight) / 2, image.getWidth(), viewportHeight);
        }
    }

    private void setImageViewport(double x, double y, double width, double height) {
        ImageView painting = (ImageView) getChildren().getFirst();
        painting.setViewport(new Rectangle2D(x, y, width, height));
    }
}
