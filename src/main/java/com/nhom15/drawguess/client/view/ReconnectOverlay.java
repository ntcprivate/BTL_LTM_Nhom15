package com.nhom15.drawguess.client.view;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

// Phủ lên màn hình đang chơi khi mất kết nối: báo đang thử kết nối lại và cho phép dừng để về màn hình đăng nhập.
public class ReconnectOverlay extends StackPane {
    private final Label attempt = new Label();
    private final Button cancel = new Button("Về màn hình đăng nhập");

    public ReconnectOverlay(Runnable onCancel) {
        UiTheme.apply(this);
        getStyleClass().addAll("reconnect-overlay", "dialog-warning");
        setAccessibleRole(javafx.scene.AccessibleRole.DIALOG);

        Label badge = new Label();
        badge.getStyleClass().add("dialog-badge");
        badge.setGraphic(Icons.icon(Icons.Glyph.WIFI_OFF, 22, badge.textFillProperty()));
        badge.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        badge.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Label title = new Label("Mất kết nối tới server");
        title.getStyleClass().add("dialog-title");
        Label message = new Label("Game đang tự kết nối lại. Khi kết nối được, bạn sẽ về đúng phòng hoặc trận đang chơi.");
        message.getStyleClass().add("dialog-message");
        message.setWrapText(true);
        message.setMinHeight(Region.USE_PREF_SIZE);
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(18, 18);
        attempt.getStyleClass().add("dialog-countdown-text");
        HBox progress = new HBox(8, spinner, attempt);
        progress.setAlignment(Pos.CENTER_LEFT);
        VBox text = new VBox(6, title, message, progress);
        HBox header = new HBox(16, badge, text);

        cancel.getStyleClass().add("ghost-button");
        cancel.setOnAction(event -> {
            cancel.setDisable(true);
            attempt.setText("Đang dừng...");
            onCancel.run();
        });
        HBox actions = new HBox(cancel);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(18, header, actions);
        card.getStyleClass().add("reconnect-card");
        card.setMaxSize(470, Region.USE_PREF_SIZE);
        getChildren().add(card);
    }

    // Đưa focus khỏi các nút của màn hình phía sau để Enter/Space không bấm nhầm khi đang mất kết nối.
    public void focusAction() {
        cancel.requestFocus();
    }

    public void showAttempt(int current, int total) {
        attempt.setText("Đang thử kết nối lại (lần " + current + "/" + total + ")...");
    }
}
