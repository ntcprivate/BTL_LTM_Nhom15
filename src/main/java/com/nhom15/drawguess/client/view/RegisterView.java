package com.nhom15.drawguess.client.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.Map;

public class RegisterView extends VBox {
    private static final String DEFAULT_STATUS = "Nhập thông tin để tạo tài khoản.";
    private final TextField usernameField = new TextField();
    private final PasswordInput passwordInput = new PasswordInput("Mật khẩu");
    private final PasswordInput confirmPasswordInput = new PasswordInput("Nhập lại mật khẩu");
    private final Button registerButton = new Button("Đăng ký");
    private final Hyperlink loginLink = new Hyperlink("Đã có tài khoản? Đăng nhập");
    private final Label statusLabel = new Label(DEFAULT_STATUS);
    private final Label serverSummary = new Label();
    private final Map<AuthField, FormField> fields;

    public RegisterView() {
        UiTheme.apply(this);
        getStyleClass().add("auth-card");
        setAlignment(Pos.CENTER);
        setSpacing(12);
        setPadding(new Insets(30, 38, 30, 38));
        setMaxWidth(480);
        setMaxHeight(USE_PREF_SIZE);
        Label brand = UiTheme.brandChip();
        Label title = new Label("Draw and Guess");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Bắt đầu hành trình dưới bầu trời đầy sao");
        subtitle.getStyleClass().add("subtle-text");
        ArtworkCard painting = new ArtworkCard(
                "/images/Cafe-Terrace-at-Night-1888.jpg", "Café Terrace at Night • 1888", 404, 116);
        usernameField.setPromptText("Tên đăng nhập (tối đa 50 ký tự)");
        usernameField.setMaxWidth(Double.MAX_VALUE);
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
        FormField usernameGroup = new FormField("Tên đăng nhập", usernameField);
        FormField passwordGroup = new FormField("Mật khẩu", passwordInput.field(), passwordInput);
        FormField confirmGroup = new FormField("Nhập lại mật khẩu", confirmPasswordInput.field(), confirmPasswordInput);
        fields = Map.of(AuthField.USERNAME, usernameGroup, AuthField.PASSWORD, passwordGroup,
                AuthField.CONFIRM_PASSWORD, confirmGroup);
        getChildren().addAll(painting, brand, title, subtitle, usernameGroup, passwordGroup, confirmGroup,
                registerButton, loginLink, serverSummary, statusLabel);
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
        return passwordInput.getText();
    }

    public String getConfirmPassword() {
        return confirmPasswordInput.getText();
    }

    public void setOnRegister(Runnable action) {
        registerButton.setOnAction(event -> action.run());
    }

    public void setBusy(boolean busy) {
        usernameField.setDisable(busy);
        passwordInput.setDisable(busy);
        confirmPasswordInput.setDisable(busy);
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

    // Lỗi hiện ngay dưới từng ô sai; con trỏ nhảy về ô sai đầu tiên theo thứ tự trên màn hình.
    public void showFieldErrors(Map<AuthField, String> errors) {
        clearFieldErrors();
        errors.forEach((field, message) -> fields.get(field).showError(message));
        for (AuthField field : AuthField.values()) {
            if (errors.containsKey(field)) {
                fields.get(field).focusInput();
                break;
            }
        }
        statusLabel.setText(DEFAULT_STATUS);
        UiTheme.status(statusLabel, "info");
    }

    public void clearFieldErrors() {
        fields.values().forEach(FormField::clearError);
    }

    public void clearPasswords() {
        passwordInput.clear();
        confirmPasswordInput.clear();
    }
}
