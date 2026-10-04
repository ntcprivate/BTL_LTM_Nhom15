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

    public RegisterView() {
        setAlignment(Pos.CENTER);
        setSpacing(12);
        setPadding(new Insets(30));
        Label title = new Label("Draw and Guess");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");
        usernameField.setPromptText("Tên đăng nhập (tối đa 50 ký tự)");
        passwordField.setPromptText("Mật khẩu");
        confirmPasswordField.setPromptText("Nhập lại mật khẩu");
        usernameField.setMaxWidth(320);
        passwordField.setMaxWidth(320);
        confirmPasswordField.setMaxWidth(320);
        registerButton.setDefaultButton(true);
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(420);
        getChildren().addAll(title, new Label("Đăng ký tài khoản"), usernameField,
                passwordField, confirmPasswordField, registerButton, loginLink, statusLabel);
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
        statusLabel.setStyle(success ? "-fx-text-fill: #16723b;" : "-fx-text-fill: #b42318;");
    }

    public void clearPasswords() {
        passwordField.clear();
        confirmPasswordField.clear();
    }
}
