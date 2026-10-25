package com.nhom15.drawguess.client.view;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

// Phủ lên màn hình đang chơi khi mất kết nối: báo đang thử kết nối lại và cho phép dừng để về màn hình đăng nhập.
// Dùng cùng phần đầu thẻ với các hộp thoại để nhìn đồng bộ.
public class ReconnectOverlay extends StackPane {
    private final Label attempt = new Label();
    private final Button cancel = new Button("Về màn hình đăng nhập");

    public ReconnectOverlay(Runnable onCancel) {
        UiTheme.apply(this);
        getStyleClass().addAll("reconnect-overlay", "dialog-warning");
        setAccessibleRole(javafx.scene.AccessibleRole.DIALOG);

        VBox hero = ThemedDialog.hero(Icons.Glyph.WIFI_OFF, "Mất kết nối tới server",
                "Game đang tự kết nối lại. Khi kết nối được, bạn sẽ về đúng phòng hoặc trận đang chơi.");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(18, 18);
        attempt.getStyleClass().add("dialog-countdown-text");
        HBox progress = new HBox(8, spinner, attempt);
        progress.setAlignment(Pos.CENTER);

        cancel.getStyleClass().add("ghost-button");
        cancel.setOnAction(event -> {
            cancel.setDisable(true);
            attempt.setText("Đang dừng...");
            onCancel.run();
        });
        HBox actions = new HBox(cancel);
        actions.setAlignment(Pos.CENTER);

        VBox card = new VBox(16, hero, progress, actions);
        card.getStyleClass().add("reconnect-card");
        card.setMaxSize(470, Region.USE_PREF_SIZE);
        getChildren().add(card);
        sceneProperty().addListener((observable, before, scene) -> {
            if (scene != null) { ThemedDialog.popIn(card); }
        });
    }

    // Đưa focus khỏi các nút của màn hình phía sau để Enter/Space không bấm nhầm khi đang mất kết nối.
    public void focusAction() {
        cancel.requestFocus();
    }

    public void showAttempt(int current, int total) {
        attempt.setText("Đang thử kết nối lại (lần " + current + "/" + total + ")...");
    }
}
