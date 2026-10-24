package com.nhom15.drawguess.client.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Map;

public class LoginView extends VBox {
    private static final String DEFAULT_STATUS = "Nhập tài khoản để đăng nhập.";
    private final TextField serverHostField = new TextField();
    private final TextField serverPortField = new TextField();
    private final TextField usernameField = new TextField();
    private final PasswordInput passwordInput = new PasswordInput("Mật khẩu");
    private final Button loginButton = new Button("Đăng nhập");
    private final Hyperlink registerLink = new Hyperlink("Chưa có tài khoản? Đăng ký");
    private final Label statusLabel = new Label(DEFAULT_STATUS);
    private final Label serverSummary = new Label();
    private final Hyperlink serverToggle = new Hyperlink("Đổi máy chủ");
    private final HBox serverRow;
    private final Map<AuthField, FormField> fields;

    public LoginView() {
        this("localhost", 5555);
    }

    public LoginView(String serverHost, int serverPort) {
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
        Label subtitle = new Label("Mỗi nét vẽ là một vì sao");
        subtitle.getStyleClass().add("subtle-text");
        ArtworkCard painting = new ArtworkCard(
                "/images/The-Starry-Night-1889.jpg", "Vincent van Gogh • 1889", 404, 122);
        serverHostField.setPromptText("Địa chỉ server, ví dụ 192.168.1.10");
        serverHostField.setText(serverHost);
        serverPortField.setPromptText("Cổng");
        serverPortField.setText(String.valueOf(serverPort));
        usernameField.setPromptText("Tên đăng nhập");
        serverHostField.setMaxWidth(Double.MAX_VALUE);
        serverPortField.setPrefWidth(100);
        usernameField.setMaxWidth(Double.MAX_VALUE);
        loginButton.setDefaultButton(true);
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.getStyleClass().add("primary-button");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        UiTheme.status(statusLabel, "info");

        FormField hostGroup = new FormField("Địa chỉ server", serverHostField);
        FormField portGroup = new FormField("Cổng", serverPortField);
        HBox.setHgrow(hostGroup, Priority.ALWAYS);
        // Cột cổng giữ hẹp; dòng lỗi dài sẽ xuống dòng thay vì làm cột phình ra.
        portGroup.setMinWidth(140);
        portGroup.setPrefWidth(140);
        portGroup.setMaxWidth(140);
        serverRow = new HBox(10, hostGroup, portGroup);
        serverRow.setMaxWidth(Double.MAX_VALUE);
        // Phần lớn người chơi không cần đổi máy chủ nên mặc định chỉ hiện một dòng tóm tắt.
        serverSummary.getStyleClass().add("server-summary");
        serverHostField.textProperty().addListener((observable, before, after) -> updateServerSummary());
        serverPortField.textProperty().addListener((observable, before, after) -> updateServerSummary());
        serverToggle.setOnAction(event -> showServerSettings(!serverRow.isVisible()));
        HBox summaryRow = new HBox(6, serverSummary, serverToggle);
        summaryRow.setAlignment(Pos.CENTER);
        updateServerSummary();
        showServerSettings(false);

        FormField usernameGroup = new FormField("Tên đăng nhập", usernameField);
        FormField passwordGroup = new FormField("Mật khẩu", passwordInput.field(), passwordInput);
        fields = Map.of(AuthField.USERNAME, usernameGroup, AuthField.PASSWORD, passwordGroup,
                AuthField.SERVER_HOST, hostGroup, AuthField.SERVER_PORT, portGroup);
        getChildren().addAll(painting, brand, title, subtitle, usernameGroup, passwordGroup,
                loginButton, registerLink, new javafx.scene.control.Separator(), summaryRow, serverRow, statusLabel);
    }

    private void updateServerSummary() {
        serverSummary.setText("Máy chủ: " + getServerAddress());
    }

    public String getServerAddress() {
        return (getServerHost().isEmpty() ? "?" : getServerHost()) + ":" + (getServerPort().isEmpty() ? "?" : getServerPort());
    }

    public void showServerSettings(boolean visible) {
        serverRow.setVisible(visible);
        serverRow.setManaged(visible);
        serverToggle.setText(visible ? "Ẩn cài đặt máy chủ" : "Đổi máy chủ");
    }

    public String getServerHost() {
        return serverHostField.getText().trim();
    }

    public String getServerPort() {
        return serverPortField.getText().trim();
    }

    public String getUsername() {
        return usernameField.getText().trim();
    }

    public String getPassword() {
        return passwordInput.getText();
    }

    public void setUsername(String username) {
        usernameField.setText(username);
    }

    public void clearPassword() {
        passwordInput.clear();
    }

    public void setOnLogin(Runnable action) {
        loginButton.setOnAction(event -> action.run());
    }

    public void setOnRegister(Runnable action) {
        registerLink.setOnAction(event -> action.run());
    }

    public void setBusy(boolean busy) {
        serverHostField.setDisable(busy);
        serverPortField.setDisable(busy);
        serverToggle.setDisable(busy);
        usernameField.setDisable(busy);
        passwordInput.setDisable(busy);
        loginButton.setDisable(busy);
        registerLink.setDisable(busy);
    }

    public void showMessage(String message, boolean success) {
        statusLabel.setText(message);
        UiTheme.status(statusLabel, success ? "success" : "error");
    }

    // Lỗi hiện ngay dưới từng ô sai; con trỏ nhảy về ô sai đầu tiên theo thứ tự trên màn hình.
    public void showFieldErrors(Map<AuthField, String> errors) {
        clearFieldErrors();
        if (errors.containsKey(AuthField.SERVER_HOST) || errors.containsKey(AuthField.SERVER_PORT)) {
            showServerSettings(true);
        }
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
}
