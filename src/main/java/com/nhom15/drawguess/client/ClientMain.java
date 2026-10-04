package com.nhom15.drawguess.client;

import com.nhom15.drawguess.client.controller.AuthController;
import com.nhom15.drawguess.client.network.TCPClient;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.layout.StackPane;

public class ClientMain extends Application {
    private AuthController authController;

    @Override
    public void start(Stage stage) {
        StackPane root = new StackPane();
        authController = new AuthController(root, new TCPClient());
        stage.setTitle("Draw and Guess");
        stage.setScene(new Scene(root, 1000, 760));
        stage.setMinWidth(900);
        stage.setMinHeight(720);
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
