package com.nhom15.drawguess.client.view;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.net.URL;
import java.util.Objects;

public final class UiTheme {
    private static final String STYLESHEET = stylesheetUrl();

    private UiTheme() {
    }

    static void apply(Parent root) {
        if (!root.getStylesheets().contains(STYLESHEET)) {
            root.getStylesheets().add(STYLESHEET);
        }
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

    private static String stylesheetUrl() {
        URL resource = UiTheme.class.getResource("/styles/app.css");
        return Objects.requireNonNull(resource, "Thiếu /styles/app.css").toExternalForm();
    }
}
