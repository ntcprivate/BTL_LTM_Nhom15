package com.nhom15.drawguess.client.view;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.net.URL;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class UiTheme {
    private static final String STYLESHEET = stylesheetUrl();
    // Cài đặt "giảm chuyển động" của hệ điều hành (Windows: Hiệu ứng hoạt ảnh = Tắt); test thay được.
    static BooleanSupplier motionPreference = () -> Platform.getPreferences().isReducedMotion();

    private UiTheme() {
    }

    static void apply(Parent root) {
        if (!root.getStylesheets().contains(STYLESHEET)) {
            root.getStylesheets().add(STYLESHEET);
        }
    }

    // Nhãn thương hiệu "✦ STARRY DRAW ✦": hai ngôi sao là icon vector cùng màu chữ, không phụ thuộc font.
    static Label brandChip() {
        Label brand = new Label("STARRY DRAW");
        brand.getStyleClass().add("brand-chip");
        javafx.scene.text.Text text = new javafx.scene.text.Text(brand.getText());
        text.fillProperty().bind(brand.textFillProperty());
        text.fontProperty().bind(brand.fontProperty());
        javafx.scene.layout.HBox content = new javafx.scene.layout.HBox(8,
                Icons.icon(Icons.Glyph.SPARKLE, 12, brand.textFillProperty()), text,
                Icons.icon(Icons.Glyph.SPARKLE, 12, brand.textFillProperty()));
        content.setAlignment(javafx.geometry.Pos.CENTER);
        brand.setGraphic(content);
        brand.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        return brand;
    }

    static void status(Label label, String kind) {
        label.getStyleClass().removeAll("status-info", "status-success", "status-error");
        if (!label.getStyleClass().contains("status-banner")) {
            label.getStyleClass().add("status-banner");
        }
        label.getStyleClass().add("status-" + kind);
    }

    public static void show(StackPane root, Node view) {
        boolean alreadyVisible = root.getChildren().size() == 1 && root.getChildren().getFirst() == view;
        root.getChildren().setAll(view);
        if (alreadyVisible) {
            return;
        }
        if (reducedMotion()) {
            view.setOpacity(1);
            view.setTranslateY(0);
            return;
        }
        view.setOpacity(0);
        view.setTranslateY(14);
        FadeTransition fade = new FadeTransition(Duration.millis(320), view);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(380), view);
        slide.setFromY(14);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(fade, slide).play();
    }

    static boolean reducedMotion() {
        try {
            return motionPreference.getAsBoolean();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    // Cửa sổ thấp (ví dụ laptop 1280x800 khi phóng to) thì thu nhỏ khoảng đệm quanh nội dung
    // để màn hình game, phòng chờ, đăng ký không bị cắt mép trên/dưới.
    public static void fitPadding(StackPane content) {
        content.heightProperty().addListener((observable, before, height) ->
                content.setPadding(new javafx.geometry.Insets(height.doubleValue() < 800 ? 8 : 24)));
    }

    private static String stylesheetUrl() {
        URL resource = UiTheme.class.getResource("/styles/app.css");
        return Objects.requireNonNull(resource, "Thiếu /styles/app.css").toExternalForm();
    }
}
