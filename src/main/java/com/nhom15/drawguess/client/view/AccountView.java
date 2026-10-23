package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.UserInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class AccountView extends VBox {
    private final Button logoutButton = new Button("Đăng xuất");
    private final Label statusLabel = new Label("Đăng nhập thành công.");

    public AccountView(UserInfo user, Runnable logout) {
        UiTheme.apply(this);
        getStyleClass().add("auth-card");
        setAlignment(Pos.CENTER);
        setSpacing(16);
        setPadding(new Insets(30));
        setMaxWidth(480);
        setMaxHeight(USE_PREF_SIZE);
        Label welcome = new Label("Xin chào, " + user.getUsername());
        welcome.getStyleClass().add("page-title");
        ArtworkCard painting = new ArtworkCard(
                "/images/The-Langlois-Bridge-at-Arles-1888.jpg", "The Langlois Bridge at Arles • 1888", 420, 132);
        String role = "ADMIN".equals(user.getRole()) ? "Quản trị viên" : "Người chơi";
        logoutButton.setOnAction(event -> logout.run());
        logoutButton.getStyleClass().add("danger-button");
        UiTheme.status(statusLabel, "success");
        getChildren().addAll(painting, welcome, new Label("Vai trò: " + role), statusLabel, logoutButton);
    }

    public void setLoggingOut() {
        logoutButton.setDisable(true);
        statusLabel.setText("Đang đăng xuất...");
    }
}
