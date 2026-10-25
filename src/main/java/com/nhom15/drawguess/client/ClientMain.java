package com.nhom15.drawguess.client;

import com.nhom15.drawguess.client.controller.AuthController;
import com.nhom15.drawguess.client.network.TCPClient;
import com.nhom15.drawguess.client.view.AnimatedStarryBackdrop;
import com.nhom15.drawguess.client.view.UiTheme;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.layout.StackPane;

public class ClientMain extends Application {
    private AuthController authController;

    @Override
    public void start(Stage stage) {
        StackPane root = new StackPane();
        root.getStyleClass().add("app-screen");
        String stylesheet = java.util.Objects.requireNonNull(
                ClientMain.class.getResource("/styles/app.css"), "Thiếu /styles/app.css").toExternalForm();
        root.getStylesheets().add(stylesheet);
        AnimatedStarryBackdrop backdrop = new AnimatedStarryBackdrop(root);
        StackPane content = new StackPane();
        content.setPadding(new javafx.geometry.Insets(24));
        root.getChildren().addAll(backdrop, content);
        backdrop.followContent(content);
        UiTheme.fitPadding(content);
        authController = new AuthController(content, new TCPClient());
        stage.setTitle("Draw and Guess • Starry Studio");
        Scene scene = new Scene(root, 1100, 800);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
            }
        });
        stage.setScene(scene);
        stage.setMinWidth(980);
        stage.setMinHeight(740);
        stage.setFullScreenExitHint("Nhấn F11 để thoát chế độ toàn màn hình");
        stage.setMaximized(true);
        stage.show();
    }

    @Override
    public void stop() {
        if (authController != null) {
            authController.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
