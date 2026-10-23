package com.nhom15.drawguess.client.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class LoginView extends VBox {
    private final TextField serverHostField = new TextField();
    private final TextField serverPortField = new TextField();
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Button loginButton = new Button("Đăng nhập");
    private final Hyperlink registerLink = new Hyperlink("Chưa có tài khoản? Đăng ký");
    private final Label statusLabel = new Label("Nhập tài khoản để đăng nhập.");

    public LoginView() {
        this("localhost", 5555);
    }

    public LoginView(String serverHost, int serverPort) {
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
        Label subtitle = new Label("Mỗi nét vẽ là một vì sao");
        subtitle.getStyleClass().add("subtle-text");
        ArtworkCard painting = new ArtworkCard(
                "/images/The-Starry-Night-1889.jpg", "Vincent van Gogh • 1889", 404, 122);
        serverHostField.setPromptText("Địa chỉ server, ví dụ 192.168.1.10");
        serverHostField.setText(serverHost);
        serverPortField.setPromptText("Cổng");
        serverPortField.setText(String.valueOf(serverPort));
        usernameField.setPromptText("Tên đăng nhập");
        passwordField.setPromptText("Mật khẩu");
        serverHostField.setMaxWidth(Double.MAX_VALUE);
        serverPortField.setPrefWidth(100);
        usernameField.setMaxWidth(Double.MAX_VALUE);
        passwordField.setMaxWidth(Double.MAX_VALUE);
        loginButton.setDefaultButton(true);
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.getStyleClass().add("primary-button");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        UiTheme.status(statusLabel, "info");

        VBox hostGroup = field("Địa chỉ server", serverHostField);
        VBox portGroup = field("Cổng", serverPortField);
        HBox.setHgrow(hostGroup, Priority.ALWAYS);
        HBox serverRow = new HBox(10, hostGroup, portGroup);
        serverRow.setMaxWidth(Double.MAX_VALUE);

        VBox usernameGroup = field("Tên đăng nhập", usernameField);
        VBox passwordGroup = field("Mật khẩu", passwordField);
        getChildren().addAll(painting, brand, title, subtitle, serverRow,
                new javafx.scene.control.Separator(), usernameGroup, passwordGroup,
                loginButton, registerLink, statusLabel);
    }

    private VBox field(String text, javafx.scene.Node input) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        VBox group = new VBox(6, label, input);
        group.setMaxWidth(Double.MAX_VALUE);
        return group;
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
        serverHostField.setDisable(busy);
        serverPortField.setDisable(busy);
        usernameField.setDisable(busy);
        passwordField.setDisable(busy);
        loginButton.setDisable(busy);
        registerLink.setDisable(busy);
    }

    public void showMessage(String message, boolean success) {
        statusLabel.setText(message);
        UiTheme.status(statusLabel, success ? "success" : "error");
    }
}
