package com.nhom15.drawguess.client.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class RegisterView extends VBox {
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final PasswordField confirmPasswordField = new PasswordField();
    private final Button registerButton = new Button("Đăng ký");
    private final Hyperlink loginLink = new Hyperlink("Đã có tài khoản? Đăng nhập");
    private final Label statusLabel = new Label("Nhập thông tin để tạo tài khoản.");
    private final Label serverSummary = new Label();

    public RegisterView() {
        UiTheme.apply(this);
        getStyleClass().add("auth-card");
        setAlignment(Pos.CENTER);
        setSpacing(14);
        setPadding(new Insets(30, 38, 30, 38));
        setMaxWidth(480);
        setMaxHeight(USE_PREF_SIZE);
        Label brand = new Label("✦  STARRY DRAW  ✦");
        brand.getStyleClass().add("brand-chip");
        Label title = new Label("Draw and Guess");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Bắt đầu hành trình dưới bầu trời đầy sao");
        subtitle.getStyleClass().add("subtle-text");
        ArtworkCard painting = new ArtworkCard(
                "/images/Cafe-Terrace-at-Night-1888.jpg", "Café Terrace at Night • 1888", 404, 116);
        usernameField.setPromptText("Tên đăng nhập (tối đa 50 ký tự)");
        passwordField.setPromptText("Mật khẩu");
        confirmPasswordField.setPromptText("Nhập lại mật khẩu");
        usernameField.setMaxWidth(Double.MAX_VALUE);
        passwordField.setMaxWidth(Double.MAX_VALUE);
        confirmPasswordField.setMaxWidth(Double.MAX_VALUE);
        registerButton.setDefaultButton(true);
        registerButton.setMaxWidth(Double.MAX_VALUE);
        registerButton.getStyleClass().add("primary-button");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        UiTheme.status(statusLabel, "info");
        serverSummary.getStyleClass().add("server-summary");
        serverSummary.setWrapText(true);
        serverSummary.setMaxWidth(Double.MAX_VALUE);
        serverSummary.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        getChildren().addAll(painting, brand, title, subtitle,
                field("Tên đăng nhập", usernameField), field("Mật khẩu", passwordField),
                field("Nhập lại mật khẩu", confirmPasswordField), registerButton, loginLink,
                serverSummary, statusLabel);
    }

    private VBox field(String text, javafx.scene.Node input) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        VBox group = new VBox(6, label, input);
        group.setMaxWidth(Double.MAX_VALUE);
        return group;
    }

    // Tài khoản được tạo trên máy chủ đang nhập ở màn hình đăng nhập.
    public void setServerAddress(String address) {
        serverSummary.setText("Tài khoản sẽ được tạo trên máy chủ " + address
                + ". Muốn đổi máy chủ, hãy quay lại màn hình đăng nhập.");
    }

    public String getUsername() {
        return usernameField.getText().trim();
    }

    public String getPassword() {
        return passwordField.getText();
    }

    public String getConfirmPassword() {
        return confirmPasswordField.getText();
    }

    public void setOnRegister(Runnable action) {
        registerButton.setOnAction(event -> action.run());
    }

    public void setBusy(boolean busy) {
        usernameField.setDisable(busy);
        passwordField.setDisable(busy);
        confirmPasswordField.setDisable(busy);
        registerButton.setDisable(busy);
        loginLink.setDisable(busy);
    }

    public void setOnLogin(Runnable action) {
        loginLink.setOnAction(event -> action.run());
    }

    public void showMessage(String message, boolean success) {
        statusLabel.setText(message);
        UiTheme.status(statusLabel, success ? "success" : "error");
    }

    public void clearPasswords() {
        passwordField.clear();
        confirmPasswordField.clear();
    }
}
