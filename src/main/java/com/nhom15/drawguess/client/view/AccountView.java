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
        setAlignment(Pos.CENTER);
        setSpacing(16);
        setPadding(new Insets(30));
        Label welcome = new Label("Xin chào, " + user.getUsername());
        welcome.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        String role = "ADMIN".equals(user.getRole()) ? "Quản trị viên" : "Người chơi";
        logoutButton.setOnAction(event -> logout.run());
        getChildren().addAll(welcome, new Label("Vai trò: " + role), statusLabel, logoutButton);
    }

    public void setLoggingOut() {
        logoutButton.setDisable(true);
        statusLabel.setText("Đang đăng xuất...");
    }
}
