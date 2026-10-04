package com.nhom15.drawguess.client.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class LoginView extends VBox {
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Button loginButton = new Button("Đăng nhập");
    private final Hyperlink registerLink = new Hyperlink("Chưa có tài khoản? Đăng ký");
    private final Label statusLabel = new Label("Nhập tài khoản để đăng nhập.");

    public LoginView() {
        setAlignment(Pos.CENTER);
        setSpacing(12);
        setPadding(new Insets(30));
        Label title = new Label("Draw and Guess");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");
        usernameField.setPromptText("Tên đăng nhập");
        passwordField.setPromptText("Mật khẩu");
        usernameField.setMaxWidth(320);
        passwordField.setMaxWidth(320);
        loginButton.setDefaultButton(true);
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(420);
        getChildren().addAll(title, new Label("Đăng nhập"), usernameField,
                passwordField, loginButton, registerLink, statusLabel);
    }

    public String getUsername() {
        return usernameField.getText().trim();
    }

    public String getPassword() {
        return passwordField.getText();
    }

    public void setUsername(String username) {
        usernameField.setText(username);
    }

    public void clearPassword() {
        passwordField.clear();
    }

    public void setOnLogin(Runnable action) {
        loginButton.setOnAction(event -> action.run());
    }

    public void setOnRegister(Runnable action) {
        registerLink.setOnAction(event -> action.run());
    }

    public void setBusy(boolean busy) {
        usernameField.setDisable(busy);
        passwordField.setDisable(busy);
        loginButton.setDisable(busy);
        registerLink.setDisable(busy);
    }

    public void showMessage(String message, boolean success) {
        statusLabel.setText(message);
        statusLabel.setStyle(success ? "-fx-text-fill: #16723b;" : "-fx-text-fill: #b42318;");
    }
}
